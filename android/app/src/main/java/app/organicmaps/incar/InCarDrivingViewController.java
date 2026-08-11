package app.organicmaps.incar;

import android.content.Context;
import android.location.Location;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import app.organicmaps.sdk.Map;
import app.organicmaps.sdk.location.LocationHelper;
import app.organicmaps.sdk.location.LocationListener;
import app.organicmaps.sdk.location.LocationState;
import app.organicmaps.sdk.routing.RoutingController;
import app.organicmaps.sdk.util.log.Logger;

/**
 * Owns the route-independent InCar Driving View session. Camera animation and auto-zoom remain native Drape
 * responsibilities; this class only supplies user/settings/speed intent and UI state.
 */
public final class InCarDrivingViewController implements LocationListener
{
  private static final String TAG = InCarDrivingViewController.class.getSimpleName();

  public enum LocationHealth
  {
    CURRENT,
    STALE,
    UNAVAILABLE
  }

  public static final class Snapshot
  {
    public final boolean enabled;
    public final boolean following;
    public final boolean navigating;
    @NonNull
    public final LocationHealth locationHealth;
    public final boolean hasSpeed;
    public final double speedMps;
    @NonNull
    public final InCarDrivingViewPolicy.ActivationSource activationSource;

    Snapshot(boolean enabled, boolean following, boolean navigating, @NonNull LocationHealth locationHealth,
             boolean hasSpeed, double speedMps, @NonNull InCarDrivingViewPolicy.ActivationSource activationSource)
    {
      this.enabled = enabled;
      this.following = following;
      this.navigating = navigating;
      this.locationHealth = locationHealth;
      this.hasSpeed = hasSpeed;
      this.speedMps = speedMps;
      this.activationSource = activationSource;
    }
  }

  @NonNull
  private final Context mContext;
  @NonNull
  private final LocationHelper mLocationHelper;
  @NonNull
  private final InCarDrivingViewPolicy mPolicy;
  @NonNull
  private final MutableLiveData<Snapshot> mSnapshot = new MutableLiveData<>();

  @NonNull
  private LocationHealth mLocationHealth = LocationHealth.UNAVAILABLE;
  @Nullable
  private Location mLastLocation;
  private int mStartedMapActivities;
  private boolean mLaunchHandled;
  private boolean mNativeStateApplied;
  private boolean mLastNativeEnabled;
  private boolean mLastNativeAutoReturn;
  private boolean mWasNavigating;

  public InCarDrivingViewController(@NonNull Context context, @NonNull LocationHelper locationHelper)
  {
    mContext = context.getApplicationContext();
    mLocationHelper = locationHelper;

    final boolean restored = InCarSettingsStore.restoredDrivingViewEnabled(mContext);
    mPolicy = new InCarDrivingViewPolicy(restored,
                                         restored ? InCarSettingsStore.restoredDrivingViewSource(mContext)
                                                  : InCarDrivingViewPolicy.ActivationSource.OFF);
    mPolicy.beginNewSession();
    mLocationHelper.addListener(this);
    mWasNavigating = RoutingController.get().isNavigating();
    publishSnapshot();
  }

  @NonNull
  public LiveData<Snapshot> getSnapshot()
  {
    return mSnapshot;
  }

  public boolean isEnabled()
  {
    return mPolicy.isEnabled();
  }

  @UiThread
  public void onMapActivityStarted()
  {
    ++mStartedMapActivities;
  }

  @UiThread
  public void onMapActivityStopped()
  {
    if (mStartedMapActivities > 0)
      --mStartedMapActivities;
  }

  @UiThread
  public void onMapActivityResumed()
  {
    if (!mLaunchHandled)
    {
      mLaunchHandled = true;
      if (!mPolicy.isEnabled() && InCarSettingsStore.startDrivingViewOnLaunch(mContext))
      {
        final InCarDrivingViewPolicy.Transition transition = mPolicy.enableFromLaunch();
        persistPolicy();
        applyTransition(transition, true /* recenter */);
      }
    }

    mWasNavigating = RoutingController.get().isNavigating();
    syncNativeState(false /* recenter */);
    publishSnapshot();
  }

  /**
   * Keeps GPS alive only for a still-started map Activity that has an active/automatic driving session. This is the
   * narrow app-owned visibility lease; a fully stopped map does not retain location merely because a preference is on.
   */
  public boolean shouldKeepLocationInBackground()
  {
    return mStartedMapActivities > 0
        && (mPolicy.isEnabled() || InCarSettingsStore.automaticDrivingViewEnabled(mContext));
  }

  @UiThread
  public void onSettingsChanged()
  {
    syncNativeState(false /* recenter */);
    publishSnapshot();
  }

  @UiThread
  public void onDrivingViewButtonPressed()
  {
    if (mPolicy.isEnabled())
    {
      if (Map.isEngineCreated() && LocationState.getMode() != LocationState.FOLLOW_AND_ROTATE)
      {
        syncNativeState(true /* recenter */);
        publishSnapshot();
        return;
      }

      final InCarDrivingViewPolicy.Transition transition = mPolicy.disableManually();
      persistPolicy();
      applyTransition(transition, true /* recenter */);
      return;
    }

    final InCarDrivingViewPolicy.Transition transition = mPolicy.enableManually();
    persistPolicy();
    applyTransition(transition, true /* recenter */);
  }

  @UiThread
  public void recenter()
  {
    if (!mPolicy.isEnabled())
      return;
    syncNativeState(true /* recenter */);
    publishSnapshot();
  }

  @Override
  @UiThread
  public void onLocationUpdated(@NonNull Location location)
  {
    mLastLocation = location;
    mLocationHealth = LocationHealth.CURRENT;

    final boolean navigating = RoutingController.get().isNavigating();
    final boolean navigationJustEnded = mWasNavigating && !navigating;
    mWasNavigating = navigating;

    final boolean hasSpeed = location.hasSpeed() && location.getSpeed() >= 0.0f;
    final InCarDrivingViewPolicy.Transition transition =
        mPolicy.onSpeedSample(true /* locationCurrent */, hasSpeed, hasSpeed ? location.getSpeed() : -1.0,
                              SystemClock.elapsedRealtime(), InCarSettingsStore.automaticDrivingViewEnabled(mContext));
    if (transition != InCarDrivingViewPolicy.Transition.NONE)
    {
      persistPolicy();
      applyTransition(transition, true /* recenter */);
    }
    else
    {
      // Existing route teardown returns ownership to the MyPositionController. Re-assert an enabled free-driving
      // session after navigation finishes so route deactivation cannot leave the preserved session in route UI state.
      syncNativeState(navigationJustEnded && mPolicy.isEnabled());
      publishSnapshot();
    }
  }

  @Override
  @UiThread
  public void onLocationUpdateTimeout()
  {
    mLocationHealth = LocationHealth.STALE;
    mPolicy.onSpeedSample(false /* locationCurrent */, false /* hasSpeed */, -1.0, SystemClock.elapsedRealtime(),
                          InCarSettingsStore.automaticDrivingViewEnabled(mContext));
    publishSnapshot();
  }

  @Override
  @UiThread
  public void onLocationDisabled()
  {
    mLocationHealth = LocationHealth.UNAVAILABLE;
    mPolicy.onSpeedSample(false /* locationCurrent */, false /* hasSpeed */, -1.0, SystemClock.elapsedRealtime(),
                          InCarSettingsStore.automaticDrivingViewEnabled(mContext));
    publishSnapshot();
  }

  private void applyTransition(@NonNull InCarDrivingViewPolicy.Transition transition, boolean recenter)
  {
    if (transition == InCarDrivingViewPolicy.Transition.NONE)
    {
      publishSnapshot();
      return;
    }

    syncNativeState(recenter);
    publishSnapshot();
  }

  private void syncNativeState(boolean recenter)
  {
    if (!Map.isEngineCreated())
    {
      mNativeStateApplied = false;
      return;
    }

    final boolean enabled = mPolicy.isEnabled();
    final boolean autoReturn = InCarSettingsStore.autoReturnDrivingViewEnabled(mContext);
    if (!recenter && mNativeStateApplied && enabled == mLastNativeEnabled && autoReturn == mLastNativeAutoReturn)
      return;

    Logger.i(TAG, "Apply Driving View: enabled=" + enabled + " autoReturn=" + autoReturn + " recenter=" + recenter);
    LocationState.setDrivingViewEnabled(enabled, autoReturn, recenter);
    mNativeStateApplied = true;
    mLastNativeEnabled = enabled;
    mLastNativeAutoReturn = autoReturn;
  }

  private void persistPolicy()
  {
    InCarSettingsStore.persistDrivingViewSession(mContext, mPolicy.isEnabled(), mPolicy.getActivationSource());
  }

  private void publishSnapshot()
  {
    final boolean hasCurrentSpeed =
        mLocationHealth == LocationHealth.CURRENT && mLastLocation != null && mLastLocation.hasSpeed()
        && mLastLocation.getSpeed() >= 0.0f;
    final boolean following = Map.isEngineCreated() && LocationState.getMode() == LocationState.FOLLOW_AND_ROTATE;
    mSnapshot.setValue(new Snapshot(mPolicy.isEnabled(), following, RoutingController.get().isNavigating(),
                                    mLocationHealth, hasCurrentSpeed,
                                    hasCurrentSpeed ? mLastLocation.getSpeed() : Double.NaN,
                                    mPolicy.getActivationSource()));
  }
}
