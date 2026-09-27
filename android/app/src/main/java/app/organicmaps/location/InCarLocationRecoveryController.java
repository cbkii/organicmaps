package app.organicmaps.location;

import androidx.annotation.VisibleForTesting;

/**
 * Lifecycle-scoped settling policy for transient InCar location-provider startup failures.
 *
 * <p>This class owns no Android callbacks. The activity supplies fresh permission/provider state
 * for every evaluation and is responsible for scheduling/cancelling the returned retry action.</p>
 */
public final class InCarLocationRecoveryController
{
  public enum Action
  {
    STALE,
    RETRY,
    RESTORE_LOCATION,
    REQUEST_PERMISSION,
    SHOW_WARNING,
    SUPPRESS_WARNING
  }

  @VisibleForTesting static final long GRACE_PERIOD_MS = 10_000L;
  @VisibleForTesting static final long RETRY_INTERVAL_MS = 1_500L;

  private static final long NO_DEADLINE = -1L;

  private int mGeneration;
  private boolean mForeground;
  private long mDeadlineMs = NO_DEADLINE;
  private boolean mGraceExhausted;
  private boolean mWarningDelivered;

  /** Starts a fresh foreground generation and invalidates any work from the previous generation. */
  public int beginForeground()
  {
    ++mGeneration;
    mForeground = true;
    resetSequence();
    return mGeneration;
  }

  /** Invalidates outstanding work when the map activity is no longer started. */
  public void endForeground()
  {
    ++mGeneration;
    mForeground = false;
    resetSequence();
  }

  /**
   * Evaluates the latest Android state for a scheduled retry or provider-unavailable callback.
   */
  public Action evaluate(int generation, long nowMs, boolean permissionGranted, boolean servicesEnabled,
                         boolean warningEnabled)
  {
    if (!mForeground || generation != mGeneration)
      return Action.STALE;

    if (!permissionGranted)
    {
      resetSequence();
      return Action.REQUEST_PERMISSION;
    }

    if (servicesEnabled)
    {
      resetSequence();
      return Action.RESTORE_LOCATION;
    }

    if (mGraceExhausted)
      return warningAction(warningEnabled);

    if (mDeadlineMs == NO_DEADLINE)
      mDeadlineMs = nowMs + GRACE_PERIOD_MS;

    if (nowMs < mDeadlineMs)
      return Action.RETRY;

    mDeadlineMs = NO_DEADLINE;
    mGraceExhausted = true;
    return warningAction(warningEnabled);
  }

  /** Returns a bounded delay which reaches the grace deadline without extending it. */
  public long nextRetryDelayMs(long nowMs)
  {
    if (mDeadlineMs == NO_DEADLINE)
      return RETRY_INTERVAL_MS;
    return Math.max(1L, Math.min(RETRY_INTERVAL_MS, mDeadlineMs - nowMs));
  }

  /** A delivered fix is stronger evidence than a transient provider-enabled probe. */
  public void confirmLocationAvailable(int generation)
  {
    if (mForeground && generation == mGeneration)
      resetSequence();
  }

  @VisibleForTesting int generation()
  {
    return mGeneration;
  }

  private Action warningAction(boolean warningEnabled)
  {
    if (!warningEnabled || mWarningDelivered)
      return Action.SUPPRESS_WARNING;

    mWarningDelivered = true;
    return Action.SHOW_WARNING;
  }

  private void resetSequence()
  {
    mDeadlineMs = NO_DEADLINE;
    mGraceExhausted = false;
    mWarningDelivered = false;
  }
}
