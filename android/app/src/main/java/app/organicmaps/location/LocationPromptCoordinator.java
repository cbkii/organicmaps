package app.organicmaps.location;

import android.app.Activity;
import android.location.Location;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModel;
import app.organicmaps.BuildConfig;
import app.organicmaps.MwmActivity;
import app.organicmaps.MwmApplication;
import app.organicmaps.incar.InCarSettingsStore;
import java.lang.ref.WeakReference;

/**
 * Keeps Android location permission and settings UI single-flight while callers re-check the
 * current platform state on every callback.
 *
 * <p>The coordinator deliberately does not cache permission or provider state. Those values can be
 * changed outside Organic Maps while the activity is backgrounded, so callers must supply fresh
 * Android state for every decision.</p>
 */
public final class LocationPromptCoordinator extends ViewModel
{
  public enum PermissionAction
  {
    NONE,
    REQUEST_PERMISSION,
    SHOW_APP_SETTINGS
  }

  public enum ProviderAction
  {
    NONE,
    REQUEST_PERMISSION,
    SHOW_APP_SETTINGS,
    SHOW_LOCATION_SETTINGS,
    RESTORE_LOCATION
  }

  private boolean mPermissionRequestPending;
  private boolean mLocationSettingsTransitionPending;
  private boolean mProviderRecoveryPending;
  private boolean mPermissionPermanentlyDenied;
  private boolean mTrackRecordingRequested;

  private final InCarLocationRecoveryController mInCarRecoveryController = new InCarLocationRecoveryController();
  @Nullable
  private WeakReference<MwmActivity> mInCarRecoveryOwner;
  @Nullable
  private Runnable mInCarRetryRunnable;
  @Nullable
  private DefaultLifecycleObserver mInCarLifecycleObserver;
  private int mInCarRecoveryGeneration;
  private long mInCarRecoveryStartedElapsedNanos;
  private long mInCarRecoveryStartedWallTimeMs;

  /**
   * Classifies a location operation which currently requires Android runtime permission.
   */
  public PermissionAction onPermissionRequired(boolean requiredPermissionGranted, boolean locationUiShowing)
  {
    if (requiredPermissionGranted)
    {
      mPermissionPermanentlyDenied = false;
      return PermissionAction.NONE;
    }

    if (locationUiShowing || mPermissionRequestPending || mLocationSettingsTransitionPending)
      return PermissionAction.NONE;

    if (mPermissionPermanentlyDenied)
      return PermissionAction.SHOW_APP_SETTINGS;

    mPermissionRequestPending = true;
    return PermissionAction.REQUEST_PERMISSION;
  }

  /**
   * Completes one Android permission request using the current permission and rationale state.
   */
  public void finishPermissionRequest(boolean permissionGranted, boolean canShowRationale)
  {
    mPermissionRequestPending = false;
    mPermissionPermanentlyDenied = !permissionGranted && !canShowRationale;
  }

  /**
   * Classifies a provider-unavailable callback using current Android permission/provider state.
   */
  public ProviderAction onProviderUnavailable(boolean locationPermissionGranted, boolean locationServicesEnabled,
                                              boolean locationUiShowing)
  {
    if (!locationPermissionGranted)
    {
      cancelInCarRecovery();
      mProviderRecoveryPending = false;
      return switch (onPermissionRequired(false, locationUiShowing))
      {
        case REQUEST_PERMISSION -> ProviderAction.REQUEST_PERMISSION;
        case SHOW_APP_SETTINGS -> ProviderAction.SHOW_APP_SETTINGS;
        case NONE -> ProviderAction.NONE;
      };
    }

    mPermissionPermanentlyDenied = false;
    if (locationServicesEnabled)
    {
      cancelInCarRecovery();
      if (mProviderRecoveryPending)
        return ProviderAction.NONE;

      mProviderRecoveryPending = true;
      return ProviderAction.RESTORE_LOCATION;
    }

    mProviderRecoveryPending = false;

    if (locationUiShowing || mLocationSettingsTransitionPending)
      return ProviderAction.NONE;

    if (BuildConfig.IS_IN_CAR)
    {
      final ProviderAction inCarAction = evaluateInCarProviderUnavailable();
      if (inCarAction != null)
        return inCarAction;
    }

    return ProviderAction.SHOW_LOCATION_SETTINGS;
  }

  /**
   * Gives the direct-display InCar build a bounded settling window before any blocking provider
   * warning is allowed. Normal Organic Maps builds retain the immediate upstream decision path.
   */
  @Nullable
  private ProviderAction evaluateInCarProviderUnavailable()
  {
    final MwmApplication app = MwmApplication.sInstance;
    if (app == null)
      return null;

    final Activity topActivity = app.getTopActivity();
    if (!(topActivity instanceof MwmActivity owner)
        || !owner.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED))
    {
      // MwmApplication only publishes getTopActivity() from onActivityResumed(). A provider failure
      // can arrive between onStart() and onResume(), so never let that ownership race fall through
      // to the immediate blocking settings path. MwmActivity.onResume() already retries location.
      return ProviderAction.NONE;
    }

    final MwmActivity previousOwner = mInCarRecoveryOwner == null ? null : mInCarRecoveryOwner.get();
    if (previousOwner != owner)
      beginInCarRecovery(owner);

    final boolean warningEnabled = InCarSettingsStore.locationDisabledWarningEnabled(owner);
    final InCarLocationRecoveryController.Action action =
        mInCarRecoveryController.evaluate(mInCarRecoveryGeneration, SystemClock.elapsedRealtime(), true, false,
                                          warningEnabled);
    return switch (action)
    {
      case RETRY -> scheduleInCarRetry(owner) ? ProviderAction.NONE
                                              : warningEnabled ? ProviderAction.SHOW_LOCATION_SETTINGS
                                                               : ProviderAction.NONE;
      case SHOW_WARNING ->
      {
        clearScheduledInCarRetry();
        yield ProviderAction.SHOW_LOCATION_SETTINGS;
      }
      case SUPPRESS_WARNING, STALE ->
      {
        clearScheduledInCarRetry();
        yield ProviderAction.NONE;
      }
      case RESTORE_LOCATION -> ProviderAction.RESTORE_LOCATION;
      case REQUEST_PERMISSION -> ProviderAction.REQUEST_PERMISSION;
    };
  }

  private void beginInCarRecovery(@NonNull MwmActivity owner)
  {
    cancelInCarRecovery();
    mInCarRecoveryOwner = new WeakReference<>(owner);
    mInCarRecoveryGeneration = mInCarRecoveryController.beginForeground();
    mInCarRecoveryStartedElapsedNanos = SystemClock.elapsedRealtimeNanos();
    mInCarRecoveryStartedWallTimeMs = System.currentTimeMillis();
    mInCarLifecycleObserver = new DefaultLifecycleObserver() {
      @Override
      public void onStop(@NonNull LifecycleOwner lifecycleOwner)
      {
        cancelInCarRecoveryIfOwnedBy(owner);
      }

      @Override
      public void onDestroy(@NonNull LifecycleOwner lifecycleOwner)
      {
        cancelInCarRecoveryIfOwnedBy(owner);
      }
    };
    owner.getLifecycle().addObserver(mInCarLifecycleObserver);
  }

  private boolean scheduleInCarRetry(@NonNull MwmActivity owner)
  {
    clearScheduledInCarRetry();
    final int generation = mInCarRecoveryGeneration;
    final Runnable retry = () -> {
      mInCarRetryRunnable = null;
      final MwmActivity currentOwner = mInCarRecoveryOwner == null ? null : mInCarRecoveryOwner.get();
      if (currentOwner != owner || generation != mInCarRecoveryGeneration
          || !owner.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED))
      {
        cancelInCarRecoveryIfOwnedBy(owner);
        return;
      }

      if (hasFreshLocationSinceRecoveryStarted())
      {
        cancelInCarRecovery();
        return;
      }

      // Re-enter the Activity's normal #53 decision path so permission, provider state and provider
      // restart are all freshly re-evaluated instead of being cached by this settling policy.
      owner.onLocationDisabled();
    };
    mInCarRetryRunnable = retry;
    final long delayMs = mInCarRecoveryController.nextRetryDelayMs(SystemClock.elapsedRealtime());
    if (owner.getWindow().getDecorView().postDelayed(retry, delayMs))
      return true;

    mInCarRetryRunnable = null;
    return false;
  }

  private boolean hasFreshLocationSinceRecoveryStarted()
  {
    final MwmApplication app = MwmApplication.sInstance;
    if (app == null)
      return false;
    final Location location = app.getLocationHelper().getSavedLocation();
    if (location == null)
      return false;

    final long elapsedNanos = location.getElapsedRealtimeNanos();
    if (elapsedNanos > 0L)
      return elapsedNanos >= mInCarRecoveryStartedElapsedNanos;
    return location.getTime() >= mInCarRecoveryStartedWallTimeMs;
  }

  private void cancelInCarRecoveryIfOwnedBy(@NonNull MwmActivity owner)
  {
    final MwmActivity currentOwner = mInCarRecoveryOwner == null ? null : mInCarRecoveryOwner.get();
    if (currentOwner == owner)
      cancelInCarRecovery();
  }

  private void clearScheduledInCarRetry()
  {
    final MwmActivity owner = mInCarRecoveryOwner == null ? null : mInCarRecoveryOwner.get();
    if (owner != null && mInCarRetryRunnable != null)
      owner.getWindow().getDecorView().removeCallbacks(mInCarRetryRunnable);
    mInCarRetryRunnable = null;
  }

  private void cancelInCarRecovery()
  {
    clearScheduledInCarRetry();
    final MwmActivity owner = mInCarRecoveryOwner == null ? null : mInCarRecoveryOwner.get();
    if (owner != null && mInCarLifecycleObserver != null)
      owner.getLifecycle().removeObserver(mInCarLifecycleObserver);
    mInCarLifecycleObserver = null;
    mInCarRecoveryOwner = null;
    mInCarRecoveryController.endForeground();
    mInCarRecoveryGeneration = mInCarRecoveryController.generation();
    mInCarRecoveryStartedElapsedNanos = 0L;
    mInCarRecoveryStartedWallTimeMs = 0L;
  }

  /**
   * Marks a location-related Android settings activity as outstanding.
   *
   * @return true only for the first launch until {@link #finishLocationSettingsTransition()}.
   */
  public boolean beginLocationSettingsTransition()
  {
    if (mLocationSettingsTransitionPending || mPermissionRequestPending)
      return false;

    mLocationSettingsTransitionPending = true;
    return true;
  }

  /** Clears the in-flight marker; callers must re-read Android permission and provider state afterwards. */
  public void finishLocationSettingsTransition()
  {
    mLocationSettingsTransitionPending = false;
  }

  /** Allows another provider restart after the current attempt has left the Android callback queue. */
  public void finishProviderRecoveryAttempt()
  {
    mProviderRecoveryPending = false;
  }

  public boolean isPermissionRequestPending()
  {
    return mPermissionRequestPending;
  }

  public boolean isLocationSettingsTransitionPending()
  {
    return mLocationSettingsTransitionPending;
  }

  public boolean isProviderRecoveryPending()
  {
    return mProviderRecoveryPending;
  }

  public boolean isPermissionPermanentlyDenied()
  {
    return mPermissionPermanentlyDenied;
  }

  /** Retains a user track-recording request until precise location permission becomes valid. */
  public void requestTrackRecording()
  {
    mTrackRecordingRequested = true;
  }

  /**
   * Completes a retained track-recording request only when precise location permission is valid.
   */
  public boolean consumeTrackRecordingRequest(boolean fineLocationPermissionGranted)
  {
    if (!mTrackRecordingRequested || !fineLocationPermissionGranted)
      return false;

    mTrackRecordingRequested = false;
    return true;
  }

  public void onTrackRecordingStarted()
  {
    mTrackRecordingRequested = false;
  }

  public boolean isTrackRecordingRequested()
  {
    return mTrackRecordingRequested;
  }

  @Override
  protected void onCleared()
  {
    cancelInCarRecovery();
  }
}