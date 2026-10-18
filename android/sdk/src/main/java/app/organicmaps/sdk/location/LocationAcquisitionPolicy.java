package app.organicmaps.sdk.location;

/** Monotonic, single-owner InCar recovery policy. A quiet, registered receiver is not a fault. */
public final class LocationAcquisitionPolicy
{
  public static final long FRESH_FIX_MS = 30_000;
  public static final long CACHE_MAX_AGE_MS = 120_000;
  private long mNextRecoveryMs;
  private int mRecoveries;
  private long mStoppedMs = -1;

  public void start(long nowMs)
  {
    mRecoveries = 0;
    mStoppedMs = -1;
    mNextRecoveryMs = nowMs + 30_000;
  }

  public void onGnssStopped(long nowMs)
  {
    mStoppedMs = nowMs;
  }
  public void onGnssProgress()
  {
    mStoppedMs = -1;
  }

  public boolean shouldRecover(long nowMs, boolean registered, boolean gpsExpected, boolean gpsRegistered)
  {
    return shouldRecover(nowMs, registered, gpsExpected, gpsRegistered, false);
  }

  public boolean shouldRecover(long nowMs, boolean registered, boolean gpsExpected, boolean gpsRegistered,
                               boolean deliveryStalled)
  {
    if (nowMs < mNextRecoveryMs)
      return false;
    if (!registered)
      return true;
    if (!gpsExpected)
      return false;
    return !gpsRegistered || deliveryStalled || (mStoppedMs >= 0 && nowMs - mStoppedMs >= 90_000);
  }

  /** Also gates manual requests, preventing repeated taps from becoming a restart storm. */
  public boolean canRetry(long nowMs)
  {
    return nowMs >= mNextRecoveryMs;
  }

  public void onRecovery(long nowMs)
  {
    ++mRecoveries;
    // Only observed GNSS progress clears a stopped receiver; registration success alone does not.
    mNextRecoveryMs = nowMs + switch (mRecoveries)
    {
      case 1 -> 60_000;
      case 2 -> 120_000;
      default -> 300_000;
    };
  }

  public int getRecoveries()
  {
    return mRecoveries;
  }

  public static boolean isUsableObservation(long nowNanos, long fixNanos, double latitude, double longitude,
                                            float accuracy, long maxAgeMs)
  {
    return fixNanos > 0 && fixNanos <= nowNanos && (nowNanos - fixNanos) / 1_000_000 <= maxAgeMs
 && Double.isFinite(latitude) && Math.abs(latitude) <= 90 && Double.isFinite(longitude) && Math.abs(longitude) <= 180
 && Float.isFinite(accuracy) && accuracy >= 0;
  }
}
