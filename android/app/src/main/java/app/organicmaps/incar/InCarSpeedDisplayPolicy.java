package app.organicmaps.incar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.sdk.routing.RoadSpeedLimitInfo;

/** Pure formatting plus the single InCar overspeed hysteresis authority. */
public final class InCarSpeedDisplayPolicy
{
  private static final double SPEED_WARNING_ENTER_FACTOR = 1.05;
  private static final double SPEED_WARNING_CLEAR_FACTOR = 1.03;
  // Converting a displayed km/h boundary to m/s can round a few ulps below the
  // mathematically identical threshold (for example 42 / 3.6 versus 40 / 3.6 * 1.05).
  private static final double SPEED_BOUNDARY_TOLERANCE_MPS = 1.0e-9;

  private static boolean sSpeeding;
  private static double sSpeedLimitMps = Double.NaN;

  public interface Formatter
  {
    @NonNull
    String format(double speedMps);
  }

  private InCarSpeedDisplayPolicy() {}

  /** Route guidance wins, including explicit unrestricted; road fallback is posted, fresh metadata only. */
  public static double selectSpeedLimit(double routeLimitMps, @Nullable RoadSpeedLimitInfo road,
                                       boolean currentNativeObservation, long nowNanos)
  {
    if (!currentNativeObservation)
      return -1.0;
    if (isFinite(routeLimitMps) && routeLimitMps >= 0.0)
      return routeLimitMps;
    return road != null && road.isFresh(nowNanos) ? road.speedLimitMps : -1.0;
  }

  @NonNull
  public static String format(@NonNull InCarDrivingViewController.LocationHealth health, boolean hasSpeed,
                              double speedMps, @NonNull String unavailableText, @NonNull Formatter formatter)
  {
    if (health != InCarDrivingViewController.LocationHealth.CURRENT || !hasSpeed || speedMps < 0.0)
      return unavailableText;
    return formatter.format(speedMps);
  }

  /** Returns the leading numeric/value token for the compact circular navigation speed display. */
  @NonNull
  public static String compactFormattedSpeed(@NonNull CharSequence formatted)
  {
    for (int i = 0; i < formatted.length(); ++i)
    {
      final char c = formatted.charAt(i);
      if (Character.isWhitespace(c) || c == '\u00A0')
        return i == 0 ? formatted.toString() : formatted.subSequence(0, i).toString();
    }
    return formatted.toString();
  }

  private static double sCurrentSpeedMps = Double.NaN;

  /** The exact limit rendered by NavigationController; recompute using the latest speed. */
  public static synchronized boolean updateSpeedLimit(double speedLimitMps)
  {
    if (Double.compare(sSpeedLimitMps, speedLimitMps) != 0)
      sSpeeding = false;
    sSpeedLimitMps = speedLimitMps;
    return evaluate();
  }

  /** The Driving View snapshot is the sole current-speed input, including its stale/no-speed state. */
  public static synchronized boolean updateCurrentSpeed(double speedMps)
  {
    sCurrentSpeedMps = speedMps;
    return evaluate();
  }

  public static synchronized boolean isSpeeding(double speedMps, double speedLimitMps)
  {
    sCurrentSpeedMps = speedMps;
    return updateSpeedLimit(speedLimitMps);
  }

  private static boolean evaluate()
  {
    if (!isFinite(sCurrentSpeedMps) || sCurrentSpeedMps < 0.0 || !isFinite(sSpeedLimitMps) || sSpeedLimitMps <= 0.0)
    {
      sSpeeding = false;
      return false;
    }
    final double factor = sSpeeding ? SPEED_WARNING_CLEAR_FACTOR : SPEED_WARNING_ENTER_FACTOR;
    sSpeeding = sCurrentSpeedMps + SPEED_BOUNDARY_TOLERANCE_MPS >= sSpeedLimitMps * factor;
    return sSpeeding;
  }

  public static synchronized double currentLimitMps()
  {
    return sSpeedLimitMps;
  }

  public static synchronized boolean warningActive()
  {
    return sSpeeding;
  }

  /** Half-strength red tint at entry, capped at 50% by 110%; no animation or flashing. */
  public static synchronized float warningStrength()
  {
    if (!sSpeeding)
      return 0.0f;
    final double progress = Math.max(0.0, Math.min(1.0, (sCurrentSpeedMps / sSpeedLimitMps - 1.05) / 0.05));
    return (float) (0.44 + 0.06 * progress);
  }

  public static synchronized void resetSpeeding()
  {
    sSpeeding = false;
    sSpeedLimitMps = Double.NaN;
    sCurrentSpeedMps = Double.NaN;
  }

  private static boolean isFinite(double value)
  {
    return !Double.isNaN(value) && !Double.isInfinite(value);
  }
}
