package app.organicmaps.incar;

import androidx.annotation.NonNull;

/** Pure adapter that deliberately delegates unit conversion/formatting to Organic Maps. */
public final class InCarSpeedDisplayPolicy
{
  private static final double SPEED_WARNING_FACTOR = 1.05;

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

  /**
   * True only when both measurements are valid and current speed is at least five percent above
   * the route-provided speed limit. A zero limit still requires actual positive movement; unknown
   * limits are represented by a negative value by RoutingInfo and must never create a warning.
   */
  public static boolean isSpeeding(double speedMps, double speedLimitMps)
  {
    if (!Double.isFinite(speedMps) || speedMps < 0.0 || !Double.isFinite(speedLimitMps) || speedLimitMps < 0.0)
      return false;
    return speedMps > speedLimitMps && speedMps >= speedLimitMps * SPEED_WARNING_FACTOR;
  }
}
