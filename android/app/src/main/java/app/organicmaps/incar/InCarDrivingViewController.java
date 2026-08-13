package app.organicmaps.incar;

import android.content.Context;
import android.location.Location;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.VisibleForTesting;
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
  private final InCarDrivingViewLifecycle mLifecycle = new InCarDrivingViewLifecycle();
  @NonNull
  private final MutableLiveData<Snapshot> mSnapshot = new MutableLiveData<>();

  @NonNull
  private LocationHealth mLocationHealth = LocationHealth.UNAVAILABLE;
  @Nullable
  private Location mLastLocation;
  private boolean mLaunchHandled;
  private boolean mNativeStateApplied;
  private boolean mLastNativeEnabled;
  private boolean mLastNativeAutoReturn;
  private boolean mLastNativeNavigating;
  private boolean mWasNavigating;

  public InCarDrivingViewController(@NonNull Context context, @NonNull LocationHelper locationHelper)
  {
    mContext = context.getApplicationContext();
    mLocationHelper = locationHelper;

    final boolean restored = InCarSettingsStore.restoredDrivingViewEnabled(mContext);
    final InCarDrivingViewPolicy.ActivationSource restoredSource =
        restored ? InCarSettingsStore.restoredDrivingViewSource(mContext) : InCarDrivingViewPolicy.ActivationSource.OFF;
    mPolicy = new InCarDrivingViewPolicy(restored, restoredSource);
    mPolicy.beginNewSession();
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
  public void onFrameworkReady()
  {
    applyLifecycleTransition(mLifecycle.onFrameworkReady());
  }

  @UiThread
  public void onFrameworkDetached()
  {
    applyLifecycleTransition(mLifecycle.onFrameworkDetached());
  }

  @UiThread
  public void onMapActivityStarted()
  {
    applyLifecycleTransition(mLifecycle.onMapActivityStarted());
  }

  @UiThread
  public void onMapActivityStopped()
  {
    applyLifecycleTransition(mLifecycle.onMapActivityStopped());
  }

  @UiThread
  public void onRenderingCreated()
  {
    mLifecycle.onRenderingCreated();
    syncNativeState(false /* recenter */);
    publishSnapshot();
  }

  @UiThread
  public void onRenderingDetached()
  {
    mLifecycle.onRenderingDetached();
    mNativeStateApplied = false;
    publishSnapshot();
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

    if (mLifecycle.isAttached())
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
    final boolean activeSession = mPolicy.isEnabled() || InCarSettingsStore.automaticDrivingViewEnabled(mContext);
    return mLifecycle.hasStartedMapActivity() && activeSession;
  }

  @UiThread
  public void onSettingsChanged()
  {
    syncNativeState(false /* recenter */);
    publishSnapshot();
  }

  /**
   * Called by the map UI's existing routing layout owner. Route teardown has completed before the regular layout is
   * restored, so this is the deterministic point to return native camera/perspective ownership to an enabled session.
   */
  @UiThread
  public void onRoutingPresentationChanged()
  {
    final boolean navigating = RoutingController.get().isNavigating();
    final boolean navigationJustEnded = mWasNavigating && !navigating;
    mWasNavigating = navigating;

    if (navigationJustEnded && mPolicy.isEnabled())
    {
      // Routing can discard an earlier Driving View application while it owns MyPositionController and tears down
      // navigation perspective afterwards. Invalidate the cache and re-apply only after routing is actually inactive.
      mNativeStateApplied = false;
      syncNativeState(false /* recenter */);
    }
    publishSnapshot();
  }

  @UiThread
  public void onDrivingViewButtonPressed()
  {
    if (mPolicy.isEnabled())
    {
      if (mLifecycle.canAccessNativeState() && Map.isEngineCreated()
          && LocationState.getMode() != LocationState.FOLLOW_AND_ROTATE)
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
      // Keep the location callback as a fallback for routing implementations that do not emit a map-layout transition.
      if (navigationJustEnded && mPolicy.isEnabled())
        mNativeStateApplied = false;
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

  private void applyLifecycleTransition(@NonNull InCarDrivingViewLifecycle.Transition transition)
  {
    if (transition == InCarDrivingViewLifecycle.Transition.ATTACH)
    {
      mWasNavigating = RoutingController.get().isNavigating();
      mLocationHelper.addListener(this);
      syncNativeState(false /* recenter */);
    }
    else if (transition == InCarDrivingViewLifecycle.Transition.DETACH)
    {
      mLocationHelper.removeListener(this);
      mNativeStateApplied = false;
      // A detached controller no longer owns a live location lease, so do not present its last sample as current.
      mLastLocation = null;
      mLocationHealth = LocationHealth.UNAVAILABLE;
    }
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
    if (!mLifecycle.canAccessNativeState() || !Map.isEngineCreated())
    {
      mNativeStateApplied = false;
      return;
    }

    final boolean enabled = mPolicy.isEnabled();
    final boolean autoReturn = InCarSettingsStore.autoReturnDrivingViewEnabled(mContext);
    final boolean navigating = RoutingController.get().isNavigating();
    if (!shouldApplyNativeState(enabled, recenter, mNativeStateApplied, mLastNativeEnabled, autoReturn,
                                mLastNativeAutoReturn, navigating, mLastNativeNavigating))
      return;

    Logger.i(TAG, "Apply Driving View: enabled=" + enabled + " autoReturn=" + autoReturn + " recenter=" + recenter);
    LocationState.setDrivingViewEnabled(enabled, autoReturn, recenter);
    mNativeStateApplied = true;
    mLastNativeEnabled = enabled;
    mLastNativeAutoReturn = autoReturn;
    mLastNativeNavigating = navigating;
  }

  @VisibleForTesting
  static boolean shouldApplyNativeState(boolean enabled, boolean recenter, boolean nativeStateApplied,
                                        boolean lastEnabled, boolean autoReturn, boolean lastAutoReturn,
                                        boolean navigating, boolean lastNavigating)
  {
    // A fresh MyPositionController is already non-Driving-View. Sending an initial disabled state would overwrite its
    // normal startup/deep-link desired location mode before the first fix, so only write disabled after we owned state.
    if (!enabled && !nativeStateApplied && !recenter)
      return false;

    final boolean stateChanged = enabled != lastEnabled || autoReturn != lastAutoReturn || navigating != lastNavigating;
    return recenter || !nativeStateApplied || stateChanged;
  }

  private void persistPolicy()
  {
    InCarSettingsStore.persistDrivingViewSession(mContext, mPolicy.isEnabled(), mPolicy.getActivationSource());
  }

  private void publishSnapshot()
  {
    final boolean hasCurrentSpeed = mLocationHealth == LocationHealth.CURRENT && mLastLocation != null
                                 && mLastLocation.hasSpeed() && mLastLocation.getSpeed() >= 0.0f;
    final boolean canAccessNativeState = mLifecycle.canAccessNativeState() && Map.isEngineCreated();
    final boolean following = canAccessNativeState && LocationState.getMode() == LocationState.FOLLOW_AND_ROTATE;
    final boolean navigating = RoutingController.get().isNavigating();
    final double speedMps = hasCurrentSpeed ? mLastLocation.getSpeed() : Double.NaN;
    mSnapshot.setValue(new Snapshot(mPolicy.isEnabled(), following, navigating, mLocationHealth, hasCurrentSpeed,
                                    speedMps, mPolicy.getActivationSource()));
  }
}
