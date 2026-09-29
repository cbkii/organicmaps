package app.organicmaps.incar;

import androidx.annotation.NonNull;

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

  /**
   * Update only the route-limit side of the warning state. Route UI may call this frequently; the
   * hysteresis is reset only when the actual limit changes or becomes invalid. Speed samples are
   * deliberately owned by {@link #isSpeeding(double, double)} from the Driving View snapshot path.
   */
  public static synchronized boolean updateSpeedLimit(double speedLimitMps)
  {
    if (!isFinite(speedLimitMps) || speedLimitMps <= 0.0)
    {
      resetSpeeding();
      return false;
    }

    if (Double.compare(sSpeedLimitMps, speedLimitMps) != 0)
    {
      sSpeeding = false;
      sSpeedLimitMps = speedLimitMps;
    }
    return sSpeeding;
  }

  /**
   * Returns the shared InCar overspeed warning state. Warning enters at 105% of a valid route
   * speed limit and remains active until speed falls below 103%, preventing GPS jitter from
   * repeatedly toggling the ribbon at the threshold. Unknown/invalid measurements clear state.
   *
   * This is the only method that feeds a speed sample into the hysteresis authority. The route
   * controller updates only the current limit via {@link #updateSpeedLimit(double)} so differently
   * timed location sources cannot advance/clear the same state machine in competing call orders.
   */
  public static synchronized boolean isSpeeding(double speedMps, double speedLimitMps)
  {
    if (!updateSpeedLimit(speedLimitMps) || !isFinite(speedMps) || speedMps < 0.0)
    {
      if (!isFinite(speedMps) || speedMps < 0.0)
        resetSpeeding();
      return false;
    }

    if (speedMps <= speedLimitMps)
    {
      sSpeeding = false;
      return false;
    }

    final double thresholdFactor = sSpeeding ? SPEED_WARNING_CLEAR_FACTOR : SPEED_WARNING_ENTER_FACTOR;
    sSpeeding = speedMps + SPEED_BOUNDARY_TOLERANCE_MPS >= speedLimitMps * thresholdFactor;
    return sSpeeding;
  }

  public static synchronized void resetSpeeding()
  {
    sSpeeding = false;
    sSpeedLimitMps = Double.NaN;
  }

  private static boolean isFinite(double value)
  {
    return !Double.isNaN(value) && !Double.isInfinite(value);
  }
}
