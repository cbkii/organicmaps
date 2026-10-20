package app.organicmaps.incar;

import java.util.HashMap;
import java.util.Map;

/** One-shot generations survive cancellation: a redraw cannot re-arm the same action. */
public final class InCarNavigationCountdownPolicy
{
  public static final int DEFAULT_SECONDS = 10;
  private final Map<String, Long> mSeen = new HashMap<>();
  private long mDeadline;
  private boolean mPending;

  public static int normaliseDelaySeconds(int seconds)
  {
    return switch (seconds)
    {
      case 2, 3, 5, 10, 15, 20, 30 -> seconds;
      default -> DEFAULT_SECONDS;
    };
  }

  public boolean arm(String stage, long generation, int seconds, long now)
  {
    if (generation <= 0 || mSeen.getOrDefault(stage, 0L) >= generation)
      return false;
    mSeen.put(stage, generation);
    mDeadline = now + normaliseDelaySeconds(seconds) * 1000L;
    mPending = true;
    return true;
  }

  public void beginCountdown(int seconds, long now)
  {
    mDeadline = now + normaliseDelaySeconds(seconds) * 1000L;
  }

  public void cancel()
  {
    mPending = false;
  }
  public int remainingSeconds(long now)
  {
    return (int) Math.max(0, (mDeadline - now + 999) / 1000);
  }
  public boolean dispatch(long now, boolean eligible)
  {
    if (!eligible)
    {
      cancel();
      return false;
    }
    if (!mPending || now < mDeadline)
      return false;
    mPending = false;
    return true;
  }
}
