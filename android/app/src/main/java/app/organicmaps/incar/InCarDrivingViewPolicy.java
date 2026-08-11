package app.organicmaps.incar;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

/** Pure state machine for route-independent InCar Driving View activation. */
public final class InCarDrivingViewPolicy
{
  @VisibleForTesting
  static final double AUTO_ENTER_SPEED_MPS = 30.0 / 3.6;
  @VisibleForTesting
  static final double AUTO_EXIT_SPEED_MPS = 5.0 / 3.6;
  @VisibleForTesting
  static final long LOW_SPEED_EXIT_MS = 5L * 60L * 1000L;
  @VisibleForTesting
  static final int REQUIRED_HIGH_SPEED_SAMPLES = 2;
  private static final long NO_LOW_SPEED_TIMER = -1L;

  public enum ActivationSource
  {
    OFF,
    MANUAL,
    AUTOMATIC,
    LAUNCH,
    RESTORED
  }

  public enum Transition
  {
    NONE,
    ENABLE,
    DISABLE
  }

  private boolean mEnabled;
  @NonNull
  private ActivationSource mSource;
  private boolean mAutomaticRearmSuppressed;
  private int mConsecutiveHighSpeedSamples;
  private long mLowSpeedSinceMs = NO_LOW_SPEED_TIMER;

  public InCarDrivingViewPolicy(boolean enabled, @NonNull ActivationSource source)
  {
    mEnabled = enabled;
    mSource = enabled ? source : ActivationSource.OFF;
  }

  public boolean isEnabled()
  {
    return mEnabled;
  }

  @NonNull
  public ActivationSource getActivationSource()
  {
    return mSource;
  }

  public boolean isAutomaticRearmSuppressed()
  {
    return mAutomaticRearmSuppressed;
  }

  public void beginNewSession()
  {
    mAutomaticRearmSuppressed = false;
    resetSpeedEvidence();
  }

  @NonNull
  public Transition enableManually()
  {
    mAutomaticRearmSuppressed = false;
    resetSpeedEvidence();
    return setEnabled(true, ActivationSource.MANUAL);
  }

  @NonNull
  public Transition enableFromLaunch()
  {
    resetSpeedEvidence();
    return setEnabled(true, ActivationSource.LAUNCH);
  }

  @NonNull
  public Transition disableManually()
  {
    // A manual OFF must not be undone by the next >30 km/h fix. Re-arm only after the
    // same sustained low-speed condition that ends an automatically started session.
    mAutomaticRearmSuppressed = true;
    resetSpeedEvidence();
    return setEnabled(false, ActivationSource.OFF);
  }

  @NonNull
  public Transition onSpeedSample(boolean locationCurrent, boolean hasSpeed, double speedMps, long elapsedRealtimeMs,
                                  boolean automaticEnabled)
  {
    if (!locationCurrent || !hasSpeed || speedMps < 0.0)
    {
      resetSpeedEvidence();
      return Transition.NONE;
    }

    if (speedMps < AUTO_EXIT_SPEED_MPS)
    {
      mConsecutiveHighSpeedSamples = 0;
      if (mLowSpeedSinceMs == NO_LOW_SPEED_TIMER)
        mLowSpeedSinceMs = elapsedRealtimeMs;

      if (elapsedRealtimeMs - mLowSpeedSinceMs < LOW_SPEED_EXIT_MS)
        return Transition.NONE;

      if (mAutomaticRearmSuppressed)
        mAutomaticRearmSuppressed = false;

      if (mEnabled && mSource == ActivationSource.AUTOMATIC)
      {
        resetSpeedEvidence();
        return setEnabled(false, ActivationSource.OFF);
      }
      return Transition.NONE;
    }

    mLowSpeedSinceMs = NO_LOW_SPEED_TIMER;

    if (speedMps <= AUTO_ENTER_SPEED_MPS)
    {
      mConsecutiveHighSpeedSamples = 0;
      return Transition.NONE;
    }

    if (!automaticEnabled || mEnabled || mAutomaticRearmSuppressed)
    {
      mConsecutiveHighSpeedSamples = 0;
      return Transition.NONE;
    }

    ++mConsecutiveHighSpeedSamples;
    if (mConsecutiveHighSpeedSamples < REQUIRED_HIGH_SPEED_SAMPLES)
      return Transition.NONE;

    resetSpeedEvidence();
    return setEnabled(true, ActivationSource.AUTOMATIC);
  }

  private void resetSpeedEvidence()
  {
    mConsecutiveHighSpeedSamples = 0;
    mLowSpeedSinceMs = NO_LOW_SPEED_TIMER;
  }

  @NonNull
  private Transition setEnabled(boolean enabled, @NonNull ActivationSource source)
  {
    if (mEnabled == enabled)
    {
      if (enabled)
        mSource = source;
      return Transition.NONE;
    }

    mEnabled = enabled;
    mSource = enabled ? source : ActivationSource.OFF;
    return enabled ? Transition.ENABLE : Transition.DISABLE;
  }
}
