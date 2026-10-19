package app.organicmaps.sdk.location;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.annotation.UiThread;
import androidx.core.content.ContextCompat;
import androidx.core.location.GnssStatusCompat;
import androidx.core.location.LocationManagerCompat;
import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.Map;
import app.organicmaps.sdk.bookmarks.data.MapObject;
import app.organicmaps.sdk.routing.JunctionInfo;
import app.organicmaps.sdk.routing.RoutingController;
import app.organicmaps.sdk.util.Config;
import app.organicmaps.sdk.util.NetworkPolicy;
import app.organicmaps.sdk.util.log.Logger;
import org.chromium.base.ObserverList;

public class LocationHelper implements BaseLocationProvider.Listener
{
  // Replaced 0 with 100ms. Seems like it is not working on Lineage, EOS, MicroG, ...
  // https://github.com/organicmaps/organicmaps/issues/11076
  // Probably https://github.com/organicmaps/organicmaps/issues/10133
  // Probably https://github.com/organicmaps/organicmaps/issues/9018
  private static final long INTERVAL_FOLLOW_MS = 100;
  private static final long INTERVAL_IN_CAR_FOLLOW_MS = 200;
  private static final long INTERVAL_NOT_FOLLOW_MS = 3000;
  private static final long INTERVAL_NAVIGATION_MS = 100;
  private static final long INTERVAL_IN_CAR_NAVIGATION_MS = 200;
  private static final long INTERVAL_TRACK_RECORDING = 100;

  private static final long AGPS_EXPIRATION_TIME_MS = 16 * 60 * 60 * 1000; // 16 hours
  private static final long LOCATION_UPDATE_TIMEOUT_MS = 30 * 1000; // 30 seconds

  @NonNull
  private final Context mContext;
  @NonNull
  private final SensorHelper mSensorHelper;
  private final boolean mInCar;

  private static final String TAG = LocationState.LOCATION_TAG;

  private final ObserverList<LocationListener> mListeners = new ObserverList<>();
  private final ObserverList.RewindableIterator<LocationListener> mListenersIterator = mListeners.rewindableIterator();

  @Nullable
  private Location mSavedLocation;
  private long mLastNativeObservationNanos;
  private MapObject mMyPosition;
  @NonNull
  private final LocationProviderFactory mLocationProviderFactory = new LocationProviderFactory();
  @NonNull
  private BaseLocationProvider mLocationProvider;
  @Nullable
  private BaseLocationProvider mOldLocationProvider;
  private long mInterval;
  private boolean mInFirstRun;
  private boolean mActive;
  private boolean mAcquisitionRequested;
  // Availability is refreshed by the existing owner/watchdog, not by every status layout tick.
  private boolean mHasAvailabilitySnapshot;
  private boolean mPermissionSnapshot;
  private boolean mServicesSnapshot;
  private boolean mGnssSubscribed;
  private long mLastGnssSubscribeMs = -1;
  private final LocationAcquisitionPolicy mAcquisition = new LocationAcquisitionPolicy();
  private long mSessionStartedMs;
  private long mLastCallbackMs;
  private long mLastGpsCallbackMs;
  private long mLastSatelliteStatusMs;
  private long mLastAcceptedMs;
  private long mFirstAcceptedMs;
  private long mFirstNativeMs;
  private int mFirstFixMs = -1;
  private long mGnssEventMs;
  private int mSatellites;
  private int mSatellitesUsed;
  private int mRejected;
  private String mLastRejection = "none";
  private String mLastRecovery = "none";
  private long mLastAgpsAttemptMs = -1;
  private String mAgpsResult = "not requested";
  private boolean mAgpsInFlight;
  private final Runnable mAgpsTimeout = () -> mAgpsResult = "command response timed out (still in flight)";
  @Nullable
  private Location mCachedLocation;
  private final Runnable mAcquisitionWatchdog = this::superviseAcquisition;
  private final Handler mHandler;
  private final Runnable mLocationTimeoutRunnable = this::notifyLocationUpdateTimeout;

  @NonNull
  private final GnssStatusCompat.Callback mGnssStatusCallback = new GnssStatusCompat.Callback() {
    @Override
    public void onStarted()
    {
      mGnssEventMs = SystemClock.elapsedRealtime();
      mAcquisition.onGnssProgress();
      Logger.d(TAG);
    }

    @Override
    public void onStopped()
    {
      mGnssEventMs = SystemClock.elapsedRealtime();
      mSatellitesUsed = 0;
      mAcquisition.onGnssStopped(mGnssEventMs);
      Logger.d(TAG);
    }

    @Override
    public void onFirstFix(int ttffMillis)
    {
      mFirstFixMs = ttffMillis;
      mGnssEventMs = SystemClock.elapsedRealtime();
      mAcquisition.onGnssProgress();
      Logger.d(TAG, "ttffMillis = " + ttffMillis);
    }

    @Override
    public void onSatelliteStatusChanged(@NonNull GnssStatusCompat status)
    {
      mGnssEventMs = SystemClock.elapsedRealtime();
      mLastSatelliteStatusMs = mGnssEventMs;
      mAcquisition.onGnssProgress();
      mSatellites = status.getSatelliteCount();
      int used = 0;
      boolean fixed = false;
      for (int i = 0; i < status.getSatelliteCount(); i++)
      {
        if (status.usedInFix(i))
        {
          used++;
          fixed = true;
        }
      }
      mSatellitesUsed = used;
      Logger.d(TAG, "total = " + status.getSatelliteCount() + " used = " + used + " fixed = " + fixed);
    }
  };

  public LocationHelper(@NonNull Context context, @NonNull SensorHelper sensorHelper)
  {
    this(context, sensorHelper, false);
  }

  public LocationHelper(@NonNull Context context, @NonNull SensorHelper sensorHelper, boolean inCar)
  {
    mContext = context;
    mSensorHelper = sensorHelper;
    mInCar = inCar;
    mLocationProvider =
        mInCar ? new AndroidNativeProvider(mContext, this, true) : mLocationProviderFactory.getProvider(mContext, this);
    mHandler = new Handler(Looper.getMainLooper());
  }

  /**
   * @return MapObject.MY_POSITION, null if location is not yet determined or "My position" button is switched off.
   */
  @Nullable
  public MapObject getMyPosition()
  {
    if (!isActive())
    {
      mMyPosition = null;
      return null;
    }

    if (mSavedLocation == null || (mInCar && !isSavedLocationFresh()))
      return null;

    if (mMyPosition == null)
      mMyPosition = MapObject.createMapObject(MapObject.MY_POSITION, "", "", mSavedLocation.getLatitude(),
                                              mSavedLocation.getLongitude());

    return mMyPosition;
  }

  /**
   * Obtains last known location.
   * @return {@code null} if no location is saved.
   */
  @Nullable
  public Location getSavedLocation()
  {
    return mSavedLocation;
  }

  /**
   * Indicates about whether a location provider is polling location updates right now or not.
   */
  public boolean isActive()
  {
    return mActive;
  }

  private void notifyLocationUpdated()
  {
    if (mSavedLocation == null)
      throw new IllegalStateException("No saved location");

    mHandler.removeCallbacks(mLocationTimeoutRunnable);
    mHandler.postDelayed(mLocationTimeoutRunnable, LOCATION_UPDATE_TIMEOUT_MS); // Reset the timeout.

    mLastNativeObservationNanos = 0;
    mListenersIterator.rewind();
    while (mListenersIterator.hasNext())
      mListenersIterator.next().onLocationUpdated(mSavedLocation);

    // If we are still in the first run mode, i.e. user is staying on the first run screens,
    // not on the map, we mustn't post location update to the core. Only this preserving allows us
    // to play nice zoom animation once a user will leave first screens and will see a map.
    if (mInFirstRun)
    {
      Logger.d(TAG, "Location update is obtained and must be ignored, because the app is in a first run mode");
      mListenersIterator.rewind();
      while (mListenersIterator.hasNext())
        mListenersIterator.next().onLocationNativeUpdateSkipped(mSavedLocation);
      return;
    }

    final LocationCompatExtractor.Altitude altitude = LocationCompatExtractor.getAltitude(mSavedLocation);
    final LocationCompatExtractor.Speed speed = LocationCompatExtractor.getSpeed(mSavedLocation);
    final LocationCompatExtractor.Bearing bearing = LocationCompatExtractor.getBearing(mSavedLocation);
    LocationState.nativeLocationUpdated(mSavedLocation.getTime(), mSavedLocation.getElapsedRealtimeNanos(),
                                        mSavedLocation.getLatitude(), mSavedLocation.getLongitude(),
                                        mSavedLocation.getAccuracy(), altitude != null ? altitude.altitude() : 0,
                                        altitude != null ? altitude.accuracy() : -1, speed != null ? speed.speed() : -1,
                                        speed != null ? speed.accuracy() : -1, bearing != null ? bearing.bearing() : -1,
                                        bearing != null ? bearing.accuracy() : -1);
    mLastNativeObservationNanos = mSavedLocation.getElapsedRealtimeNanos();
    if (mFirstNativeMs == 0)
      mFirstNativeMs = SystemClock.elapsedRealtime();
    // Existing pre-native listeners retain their ordering. Metadata consumers get a separate completed boundary.
    mListenersIterator.rewind();
    while (mListenersIterator.hasNext())
      mListenersIterator.next().onLocationUpdatedNative(mSavedLocation);
  }

  private void notifyLocationUpdateTimeout()
  {
    mHandler.removeCallbacks(mLocationTimeoutRunnable);
    if (!isAcquisitionRequested())
    {
      Logger.w(TAG, "Provider is not active");
      return;
    }

    Logger.d(TAG);
    mLastNativeObservationNanos = 0;
    mListenersIterator.rewind();
    while (mListenersIterator.hasNext())
      mListenersIterator.next().onLocationUpdateTimeout();
    if (mInCar && mAcquisitionRequested)
      mHandler.postDelayed(mLocationTimeoutRunnable, LOCATION_UPDATE_TIMEOUT_MS);
  }

  @Override
  public void onLocationChanged(@NonNull Location location)
  {
    if (!mInCar)
      Logger.d(TAG, "provider = " + mLocationProvider.getClass().getSimpleName() + " location = " + location);

    if (mInCar && mAcquisitionRequested)
      mActive = nativeSubscriptionsPresent();
    if (!isActive())
    {
      Logger.w(TAG, "Provider is not active");
      return;
    }

    if (mInCar)
    {
      mLastCallbackMs = SystemClock.elapsedRealtime();
      if (LocationUtils.isFromGpsProvider(location))
      {
        mLastGpsCallbackMs = mLastCallbackMs;
        mAcquisition.onGnssProgress();
      }
      if (!LocationAcquisitionPolicy.isUsableObservation(
              SystemClock.elapsedRealtimeNanos(), location.getElapsedRealtimeNanos(), location.getLatitude(),
              location.getLongitude(), location.getAccuracy(), LocationAcquisitionPolicy.FRESH_FIX_MS))
      {
        rejectObservation("invalid or stale observation");
        return;
      }
    }
    if (!LocationUtils.isAccuracySatisfied(location))
    {
      if (mInCar)
        rejectObservation("accuracy unavailable");
      else
        Logger.w(TAG, "Unsatisfied accuracy for location = " + location);
      return;
    }

    if (mSavedLocation != null)
    {
      if ((mInCar && location.getElapsedRealtimeNanos() <= mSavedLocation.getElapsedRealtimeNanos())
          || !LocationUtils.isLocationBetterThanLast(location, mSavedLocation))
      {
        if (mInCar)
          rejectObservation("duplicate, older or less accurate observation");
        else
          Logger.d(TAG, "The new " + location + " is worse than the last " + mSavedLocation);
        return;
      }
    }

    mLastAcceptedMs = SystemClock.elapsedRealtime();
    if (mFirstAcceptedMs == 0)
      mFirstAcceptedMs = mLastAcceptedMs;
    mSavedLocation = location;
    mMyPosition = null;
    notifyLocationUpdated();
  }

  // Used by GoogleFusedLocationProvider.
  @SuppressWarnings("unused")
  @Override
  @UiThread
  public void onLocationResolutionRequired(@NonNull PendingIntent pendingIntent)
  {
    Logger.d(TAG);

    if (!isActive())
    {
      Logger.w(TAG, "Provider is not active");
      return;
    }

    // Stop provider until location resolution is granted.
    stop();
    LocationState.nativeOnLocationError(LocationState.ERROR_GPS_OFF);

    mListenersIterator.rewind();
    while (mListenersIterator.hasNext())
      mListenersIterator.next().onLocationResolutionRequired(pendingIntent);
  }

  // Used by GoogleFusedLocationProvider.
  @SuppressWarnings("unused")
  @RequiresPermission(anyOf = {ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION})
  @Override
  @UiThread
  public void onFusedLocationUnsupported()
  {
    // Try to downgrade to the native provider first and restart the service before notifying the user.
    Logger.d(TAG, "provider = " + mLocationProvider.getClass().getSimpleName() + " is not supported,"
                      + " downgrading to use native provider");
    if (mInCar)
      LocationState.nativeResetFreeDrivingSession();
    mLocationProvider.stop();
    mLocationProvider = new AndroidNativeProvider(mContext, this, mInCar);
    startProviderSafely();
  }

  // RouteSimulationProvider doesn't really require location permissions.
  @SuppressLint("MissingPermission")
  public void startNavigationSimulation(JunctionInfo[] points)
  {
    Logger.i(TAG);
    mOldLocationProvider = mLocationProvider;
    if (mInCar)
      LocationState.nativeResetFreeDrivingSession();
    mLocationProvider.stop();
    mLocationProvider = new RouteSimulationProvider(mContext, this, points);
    startProviderSafely();
  }

  @SuppressLint("MissingPermission")
  public void stopNavigationSimulation()
  {
    Logger.i(TAG);
    if (mInCar)
      LocationState.nativeResetFreeDrivingSession();
    mLocationProvider.stop();
    if (mOldLocationProvider == null)
      throw new IllegalStateException("Should be called only after startNavigationSimulation()");
    mLocationProvider = mOldLocationProvider;
    startProviderSafely();
  }

  @Override
  @UiThread
  public void onLocationDisabled()
  {
    mHasAvailabilitySnapshot = false;
    Logger.d(TAG, "provider = " + mLocationProvider.getClass().getSimpleName()
                      + " settings = " + LocationUtils.areLocationServicesTurnedOn(mContext));

    // AndroidNativeProvider may have queued this callback before a manual location-mode change
    // stopped the helper. Do not turn that stale provider event back into an app-level recovery
    // request which could override the user's explicit location-off choice.
    if (!isAcquisitionRequested())
    {
      Logger.w(TAG, "Provider is not active");
      return;
    }
    if (mInCar && LocationUtils.checkLocationPermission(mContext)
        && LocationUtils.areLocationServicesTurnedOn(mContext))
    {
      // Provider-specific loss while Location is enabled is not a global settings failure.
      mActive = nativeSubscriptionsPresent();
      return;
    }

    stop();
    if (mInCar && !LocationUtils.checkLocationPermission(mContext))
      LocationState.nativeOnLocationError(LocationState.ERROR_DENIED);
    else
      LocationState.nativeOnLocationError(LocationState.ERROR_GPS_OFF);

    mListenersIterator.rewind();
    while (mListenersIterator.hasNext())
      mListenersIterator.next().onLocationDisabled();
  }

  /**
   * Registers listener to obtain location updates.
   *
   * @param listener    listener to be registered.
   */
  @UiThread
  public void addListener(@NonNull LocationListener listener)
  {
    Logger.d(TAG, "listener: " + listener + " count was: " + mListeners.size());

    mListeners.addObserver(listener);
    if (mSavedLocation != null && (!mInCar || isSavedLocationFresh()))
    {
      listener.onLocationUpdated(mSavedLocation);
      if (!mInFirstRun && mLastNativeObservationNanos > 0
          && mSavedLocation.getElapsedRealtimeNanos() == mLastNativeObservationNanos)
        listener.onLocationUpdatedNative(mSavedLocation);
      else
        listener.onLocationNativeUpdateSkipped(mSavedLocation);
    }
  }

  /**
   * Removes given location listener.
   * @param listener listener to unregister.
   */
  @UiThread
  public void removeListener(@NonNull LocationListener listener)
  {
    Logger.d(TAG, "listener: " + listener + " count was: " + mListeners.size());
    mListeners.removeObserver(listener);
  }

  private long calcLocationUpdatesInterval()
  {
    if (RoutingController.get().isNavigating())
      return mInCar ? INTERVAL_IN_CAR_NAVIGATION_MS : INTERVAL_NAVIGATION_MS;

    if (TrackRecorder.nativeIsTrackRecordingEnabled())
      return INTERVAL_TRACK_RECORDING;

    final int mode = Map.isEngineCreated() ? LocationState.getMode() : LocationState.NOT_FOLLOW_NO_POSITION;
    return switch (mode)
    {
      case LocationState.PENDING_POSITION, LocationState.FOLLOW, LocationState.FOLLOW_AND_ROTATE ->
        mInCar ? INTERVAL_IN_CAR_FOLLOW_MS : INTERVAL_FOLLOW_MS;
      case LocationState.NOT_FOLLOW, LocationState.NOT_FOLLOW_NO_POSITION -> INTERVAL_NOT_FOLLOW_MS;
      default -> throw new IllegalArgumentException("Unsupported location mode: " + mode);
    };
  }

  /**
   * Restart the location with a new refresh interval if changed.
   */
  @RequiresPermission(anyOf = {ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION})
  public void restartWithNewMode()
  {
    if (!isAcquisitionRequested())
    {
      start();
      return;
    }

    final long newInterval = calcLocationUpdatesInterval();
    if (newInterval == mInterval)
      return;

    Logger.i(TAG, "update refresh interval: old = " + mInterval + " new = " + newInterval);
    mLocationProvider.stop();
    mInterval = newInterval;
    startProviderSafely();
  }

  /**
   * Starts polling location updates.
   */
  @RequiresPermission(anyOf = {ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION})
  public void start()
  {
    mHasAvailabilitySnapshot = false;
    if (isAcquisitionRequested())
    {
      Logger.d(TAG, "Already started");
      return;
    }

    Logger.i(TAG);
    mAcquisitionRequested = true;
    mSessionStartedMs = SystemClock.elapsedRealtime();
    mAcquisition.start(mSessionStartedMs);
    mFirstAcceptedMs = mFirstNativeMs = 0;
    mFirstFixMs = -1;
    mGnssEventMs = mLastSatelliteStatusMs = 0;
    mSatellites = mSatellitesUsed = mRejected = 0;
    mLastRejection = "none";
    mLastRecovery = "none";
    checkForAgpsUpdates();

    if (LocationUtils.checkFineLocationPermission(mContext))
    {
      try
      {
        mSensorHelper.start();
      }
      catch (RuntimeException e)
      {
        Logger.w(TAG, "Sensors unavailable: " + e.getClass().getSimpleName());
      }
    }

    final long oldInterval = mInterval;
    mInterval = calcLocationUpdatesInterval();
    Logger.i(TAG, "provider = " + mLocationProvider.getClass().getSimpleName() + " mInFirstRun = " + mInFirstRun
                      + " oldInterval = " + oldInterval + " interval = " + mInterval);
    startProviderSafely();
    if (mInCar && mLocationProvider instanceof AndroidNativeProvider nativeProvider)
      mCachedLocation = nativeProvider.getRecentCachedLocation();
    mHandler.removeCallbacks(mLocationTimeoutRunnable);
    mHandler.postDelayed(mLocationTimeoutRunnable, LOCATION_UPDATE_TIMEOUT_MS);
    if (mInCar)
    {
      mHandler.removeCallbacks(mAcquisitionWatchdog);
      mHandler.postDelayed(mAcquisitionWatchdog, 5_000);
    }
    subscribeToGnssStatusUpdates();
  }

  /**
   * Stops the polling location updates.
   */
  public void stop()
  {
    mLastNativeObservationNanos = 0;
    if (!isAcquisitionRequested())
    {
      Logger.d(TAG, "Already stopped");
      return;
    }

    Logger.i(TAG);
    mAcquisitionRequested = false;
    mHandler.removeCallbacks(mAcquisitionWatchdog);
    if (mInCar)
      LocationState.nativeResetFreeDrivingSession();
    mLocationProvider.stop();
    unsubscribeFromGnssStatusUpdates();
    mSensorHelper.stop();
    mHandler.removeCallbacks(mLocationTimeoutRunnable);
    mActive = false;
  }

  /**
   * Resume location services when entering the foreground.
   */
  public void resumeLocationInForeground()
  {
    resumeLocationInForeground(false);
  }

  /** The InCar launcher may recover a persisted camera mode that was not an explicit user stop. */
  public void resumeLocationInForeground(boolean recoverPersistedNoPosition)
  {
    if (isAcquisitionRequested())
      return;
    else if (!Map.isEngineCreated())
    {
      // LocationState.nativeGetMode() is initialized only after drape creation.
      // https://github.com/organicmaps/organicmaps/issues/1128#issuecomment-1784435190
      Logger.d(TAG, "Engine is not created yet.");
      return;
    }
    else if (LocationState.getMode() == LocationState.NOT_FOLLOW_NO_POSITION && !recoverPersistedNoPosition)
    {
      Logger.i(TAG, "Location updates are stopped by the user manually.");
      return;
    }
    else if (!LocationUtils.checkLocationPermission(mContext))
    {
      Logger.i(TAG, "Permissions ACCESS_FINE_LOCATION and ACCESS_COARSE_LOCATION are not granted");
      return;
    }

    start();
  }

  private void checkForAgpsUpdates()
  {
    if (mAgpsInFlight || !NetworkPolicy.getCurrentNetworkUsageStatus())
      return;
    final long nowMs = SystemClock.elapsedRealtime();
    if (mLastAgpsAttemptMs >= 0 && nowMs - mLastAgpsAttemptMs < 120_000)
      return;
    final long timestamp = System.currentTimeMillis();
    final long previous = Config.getAgpsTimestamp();
    if (previous <= timestamp && timestamp - previous < AGPS_EXPIRATION_TIME_MS)
      return;
    mLastAgpsAttemptMs = nowMs;
    final LocationManager manager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
    mAgpsInFlight = true;
    mAgpsResult = "commands pending";
    mHandler.postDelayed(mAgpsTimeout, 10_000);
    // Vendor Binder implementations may stall. Keep one request off the UI thread and never
    // queue another while it is in flight, even after the diagnostic timeout expires.
    final Thread assistance = new Thread(() -> {
      boolean accepted = false;
      String result;
      try
      {
        final boolean xtra = manager.sendExtraCommand(LocationManager.GPS_PROVIDER, "force_xtra_injection", null);
        final boolean time = manager.sendExtraCommand(LocationManager.GPS_PROVIDER, "force_time_injection", null);
        accepted = xtra && time;
        result = "command acceptance: xtra=" + xtra + ", time=" + time;
      }
      catch (RuntimeException e)
      {
        result = "command unavailable: " + e.getClass().getSimpleName();
      }
      final boolean commandsAccepted = accepted;
      final String commandResult = result;
      mHandler.post(() -> {
        mHandler.removeCallbacks(mAgpsTimeout);
        mAgpsInFlight = false;
        mAgpsResult = commandResult;
        // Acceptance is not proof of a downloaded assistance file or a GNSS fix.
        if (commandsAccepted)
          Config.setAgpsTimestamp(timestamp);
        Logger.i(TAG, mAgpsResult);
      });
    }, "InCarAgpsAssistance");
    assistance.setDaemon(true);
    assistance.start();
  }

  private void subscribeToGnssStatusUpdates()
  {
    if (mGnssSubscribed || !LocationUtils.checkFineLocationPermission(mContext))
      return;
    final long now = SystemClock.elapsedRealtime();
    if (mLastGnssSubscribeMs >= 0 && now - mLastGnssSubscribeMs < 60_000)
      return;
    mLastGnssSubscribeMs = now;
    final LocationManager manager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
    try
    {
      mGnssSubscribed = LocationManagerCompat.registerGnssStatusCallback(
          manager, ContextCompat.getMainExecutor(mContext), mGnssStatusCallback);
    }
    catch (RuntimeException e)
    {
      Logger.w(TAG, "GNSS telemetry unavailable: " + e.getClass().getSimpleName());
    }
  }

  private void unsubscribeFromGnssStatusUpdates()
  {
    mLastGnssSubscribeMs = -1;
    if (!mGnssSubscribed)
      return;
    mGnssSubscribed = false;
    final LocationManager manager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
    try
    {
      LocationManagerCompat.unregisterGnssStatusCallback(manager, mGnssStatusCallback);
    }
    catch (RuntimeException e)
    {
      Logger.w(TAG, "GNSS telemetry unregister failed: " + e.getClass().getSimpleName());
    }
  }

  public boolean isAcquisitionRequested()
  {
    return mInCar ? mAcquisitionRequested : mActive;
  }

  private boolean nativeSubscriptionsPresent()
  {
    return !(mLocationProvider instanceof AndroidNativeProvider provider) || provider.hasSubscriptions();
  }

  @SuppressLint("MissingPermission")
  private void startProviderSafely()
  {
    mAcquisitionRequested = true; // Includes independent simulation and native-provider restoration.
    mActive = true; // Allows synchronous simulation callbacks; native registration is reconciled below.
    if (!mInCar)
    {
      mLocationProvider.start(mInterval);
      return;
    }
    try
    {
      mLocationProvider.start(mInterval);
      mActive = nativeSubscriptionsPresent();
    }
    catch (RuntimeException e)
    {
      mActive = false;
      mLocationProvider.stop();
      Logger.w(TAG, "Location registration failed: " + e.getClass().getSimpleName());
    }
  }

  private void rejectObservation(String reason)
  {
    ++mRejected;
    mLastRejection = reason;
    Logger.d(TAG, "Location observation rejected: " + reason);
  }

  @SuppressLint("MissingPermission")
  private void superviseAcquisition()
  {
    if (!mInCar || !mAcquisitionRequested)
      return;
    try
    {
      refreshAvailabilitySnapshot();
      if (!mPermissionSnapshot || !mServicesSnapshot)
      {
        // Let existing permission/settings coordination own the UI; never request permission here.
        onLocationDisabled();
        return;
      }
      if (mLocationProvider instanceof AndroidNativeProvider provider)
      {
        mActive = provider.hasSubscriptions();
        final boolean gpsExpected = LocationUtils.checkFineLocationPermission(mContext) && provider.isGpsEnabled();
        final long now = SystemClock.elapsedRealtime();
        // A recent receiver fix with continuing used-in-fix satellites, but no GPS delivery for
        // a minute, is a different failure from poor sky view or delivered/rejected observations.
        final boolean deliveryStalled = isGpsDeliveryStalled(now);
        final String reason = deliveryStalled ? "GPS delivery stalled" : "subscription missing or GNSS stopped";
        if (mAcquisition.shouldRecover(now, mActive, gpsExpected, provider.hasGpsSubscription(), deliveryStalled))
          recoverAcquisition(reason);
        checkForAgpsUpdates(); // Connectivity may have returned; attempts are rate limited.
        subscribeToGnssStatusUpdates();
        // Verbose diagnostics are assembled on explicit request only; recovery logs remain event-driven.
      }
    }
    catch (RuntimeException e)
    {
      // A vendor inspection failure is unknown evidence, not proof that Location is disabled.
      Logger.w(TAG, "Acquisition inspection unavailable: " + e.getClass().getSimpleName());
    }
    finally
    {
      if (mAcquisitionRequested)
        mHandler.postDelayed(mAcquisitionWatchdog, 5_000);
    }
  }

  private boolean isGpsDeliveryStalled(long now)
  {
    if (mFirstFixMs < 0 || mSatellitesUsed == 0 || mLastSatelliteStatusMs == 0)
      return false;
    if (now - mLastSatelliteStatusMs > 15_000)
      return false;
    return now - Math.max(mSessionStartedMs, mLastGpsCallbackMs) >= 60_000;
  }

  /** User retry shares the watchdog's cooldown and cannot re-enable an explicitly stopped session. */
  public boolean retryAcquisition()
  {
    if (!mInCar || !mAcquisitionRequested || !(mLocationProvider instanceof AndroidNativeProvider)
        || !LocationUtils.checkLocationPermission(mContext) || !LocationUtils.areLocationServicesTurnedOn(mContext)
        || !mAcquisition.canRetry(SystemClock.elapsedRealtime()))
      return false;
    recoverAcquisition("manual retry");
    return true;
  }

  private void recoverAcquisition(String reason)
  {
    mAcquisition.onRecovery(SystemClock.elapsedRealtime());
    mLastRecovery = reason;
    // Deliberately do not call stop(): route/camera state and last confirmed position survive.
    mLocationProvider.stop();
    startProviderSafely();
  }

  public enum AcquisitionState
  {
    STOPPED,
    WAITING_FOR_PERMISSION,
    PROVIDER_UNAVAILABLE,
    ACQUIRING,
    APPROXIMATE,
    FIX_RECEIVED,
    FIX_STALE,
    RECOVERING
  }

  private void refreshAvailabilitySnapshot()
  {
    mPermissionSnapshot = LocationUtils.checkLocationPermission(mContext);
    mServicesSnapshot = LocationUtils.areLocationServicesTurnedOn(mContext);
    mHasAvailabilitySnapshot = true;
  }

  public AcquisitionState getAcquisitionState()
  {
    refreshAvailabilitySnapshot();
    return getAcquisitionStateSnapshot();
  }

  /** Lightweight UI observation. Fix ages remain live; availability follows the bounded owner probe. */
  public AcquisitionState getAcquisitionStateSnapshot()
  {
    if (!mHasAvailabilitySnapshot)
      refreshAvailabilitySnapshot();
    if (!mPermissionSnapshot)
      return AcquisitionState.WAITING_FOR_PERMISSION;
    if (!mServicesSnapshot)
      return AcquisitionState.PROVIDER_UNAVAILABLE;
    if (!isAcquisitionRequested())
      return AcquisitionState.STOPPED;
    if (!mActive)
      return AcquisitionState.RECOVERING;
    if (mSavedLocation != null)
    {
      final long ageMs = (SystemClock.elapsedRealtimeNanos() - mSavedLocation.getElapsedRealtimeNanos()) / 1_000_000;
      if (ageMs >= 0 && ageMs <= LocationAcquisitionPolicy.FRESH_FIX_MS)
        return LocationUtils.isFromGpsProvider(mSavedLocation) ? AcquisitionState.FIX_RECEIVED
                                                               : AcquisitionState.APPROXIMATE;
      return AcquisitionState.FIX_STALE;
    }
    if (mCachedLocation != null
        && LocationAcquisitionPolicy.isUsableObservation(
            SystemClock.elapsedRealtimeNanos(), mCachedLocation.getElapsedRealtimeNanos(),
            mCachedLocation.getLatitude(), mCachedLocation.getLongitude(), mCachedLocation.getAccuracy(),
            LocationAcquisitionPolicy.CACHE_MAX_AGE_MS))
      return AcquisitionState.APPROXIMATE;
    return AcquisitionState.ACQUIRING;
  }

  private boolean isSavedLocationFresh()
  {
    return mSavedLocation != null
 && LocationAcquisitionPolicy.isUsableObservation(
     SystemClock.elapsedRealtimeNanos(), mSavedLocation.getElapsedRealtimeNanos(), mSavedLocation.getLatitude(),
     mSavedLocation.getLongitude(), mSavedLocation.getAccuracy(), LocationAcquisitionPolicy.FRESH_FIX_MS);
  }

  @Nullable
  public Location getCachedLocation()
  {
    if (mCachedLocation == null
        || !LocationAcquisitionPolicy.isUsableObservation(
            SystemClock.elapsedRealtimeNanos(), mCachedLocation.getElapsedRealtimeNanos(),
            mCachedLocation.getLatitude(), mCachedLocation.getLongitude(), mCachedLocation.getAccuracy(),
            LocationAcquisitionPolicy.CACHE_MAX_AGE_MS))
      return null;
    return new Location(mCachedLocation);
  }

  /** Coordinate-free evidence: never logs or exports latitude/longitude. Ages are monotonic. */
  public String getAcquisitionDiagnostics()
  {
    final long now = SystemClock.elapsedRealtime();
    final long fixAge = mSavedLocation == null
                          ? -1
                          : (SystemClock.elapsedRealtimeNanos() - mSavedLocation.getElapsedRealtimeNanos()) / 1_000_000;
    final boolean gps = mLocationProvider instanceof AndroidNativeProvider p && p.hasGpsSubscription();
    return "state=" + getAcquisitionState() + ", registered=" + mActive + ", gpsRegistered=" + gps
  + ", sessionMs=" + (mAcquisitionRequested ? now - mSessionStartedMs : -1) + ", fixAgeMs=" + fixAge
  + ", accuracyM=" + (mSavedLocation == null ? -1 : mSavedLocation.getAccuracy())
  + ", callbackAgeMs=" + (mLastCallbackMs == 0 ? -1 : now - mLastCallbackMs)
  + ", acceptedAgeMs=" + (mLastAcceptedMs == 0 ? -1 : now - mLastAcceptedMs)
  + ", firstAcceptedMs=" + (mFirstAcceptedMs == 0 ? -1 : mFirstAcceptedMs - mSessionStartedMs)
  + ", firstNativeMs=" + (mFirstNativeMs == 0 ? -1 : mFirstNativeMs - mSessionStartedMs) + ", nativeObservationAgeMs="
  + (mLastNativeObservationNanos == 0 ? -1
                                      : (SystemClock.elapsedRealtimeNanos() - mLastNativeObservationNanos) / 1_000_000)
  + ", gnssFirstFixMs=" + mFirstFixMs + ", satellites=" + mSatellites + ", used=" + mSatellitesUsed
  + ", gnssEventAgeMs=" + (mGnssEventMs == 0 ? -1 : now - mGnssEventMs) + ", telemetryRegistered=" + mGnssSubscribed
  + ", rejected=" + mRejected + ", rejection=" + mLastRejection + ", recoveries=" + mAcquisition.getRecoveries()
  + ", recovery=" + mLastRecovery + ", agps=" + mAgpsResult;
  }

  @UiThread
  public boolean isInFirstRun()
  {
    return mInFirstRun;
  }

  @UiThread
  public void onEnteredIntoFirstRun()
  {
    Logger.i(TAG);
    mLastNativeObservationNanos = 0;
    mInFirstRun = true;
  }

  @UiThread
  public void onExitFromFirstRun()
  {
    Logger.i(TAG);
    if (!mInFirstRun)
      throw new AssertionError("Must be called only after 'onEnteredIntoFirstRun' method!");

    mInFirstRun = false;

    // If there is a location we need just to pass it to the listeners, so that
    // my position state machine will be switched to the FOLLOW state.
    if (mSavedLocation != null && (!mInCar || isSavedLocationFresh()))
    {
      notifyLocationUpdated();
      Logger.d(TAG, "Current location is available, so play the nice zoom animation");
      Framework.nativeRunFirstLaunchAnimation();
    }
  }

  public boolean isGmsLocationProviderAvailable()
  {
    return mLocationProviderFactory.isGmsLocationProviderAvailable(mContext);
  }
}
