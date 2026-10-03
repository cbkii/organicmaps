package app.organicmaps.incar;

/** Inactivity, not journey duration, determines whether an InCar route may resume. */
public final class InCarRouteResumePolicy
{
  public static final int DEFAULT_MINUTES = 30;
  public static final long HEARTBEAT_MS = 15_000L;

  private InCarRouteResumePolicy() {}

  public static int normaliseMinutes(int minutes)
  {
    return minutes >= 5 && minutes <= 90 && minutes % 5 == 0 ? minutes : DEFAULT_MINUTES;
  }

  public static boolean isExpired(long lastWallMs, long lastElapsedMs, int lastBoot, long wallMs, long elapsedMs,
                                  int boot, int minutes)
  {
    // Legacy routes have no activity evidence. Do not give them a new lease at launch.
    if (lastElapsedMs < 0)
      return true;
    final long age;
    if (boot >= 0 && boot == lastBoot)
      age = elapsedMs - lastElapsedMs;
    else
    {
      if (lastWallMs <= 0)
        return true;
      age = wallMs - lastWallMs;
    }
    // A backwards clock/reboot without a reliable time cannot prove a recent journey.
    return age < 0 || age >= normaliseMinutes(minutes) * 60_000L;
  }
}
