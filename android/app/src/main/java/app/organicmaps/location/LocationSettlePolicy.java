package app.organicmaps.location;

/** Bounded retry schedule for an InCar Android Location master-switch reading. */
public final class LocationSettlePolicy
{
  public static final long WINDOW_MS = 10_000L;
  public static final long INTERVAL_MS = 1_500L;

  private LocationSettlePolicy() {}

  /** Zero means the settling window has expired; a positive value is the next main-loop delay. */
  public static long nextDelay(long deadlineUptimeMs, long nowUptimeMs)
  {
    return Math.min(INTERVAL_MS, Math.max(0L, deadlineUptimeMs - nowUptimeMs));
  }
}
