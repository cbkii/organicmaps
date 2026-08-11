package app.organicmaps.incar;

import androidx.annotation.NonNull;

/** Pure adapter that deliberately delegates unit conversion/formatting to Organic Maps. */
public final class InCarSpeedDisplayPolicy
{
  public interface Formatter
  {
    @NonNull
    String format(double speedMps);
  }

  private InCarSpeedDisplayPolicy() {}

  @NonNull
  public static String format(@NonNull InCarDrivingViewController.LocationHealth health, boolean hasSpeed,
                              double speedMps, @NonNull Formatter formatter)
  {
    if (health != InCarDrivingViewController.LocationHealth.CURRENT || !hasSpeed || speedMps < 0.0)
      return "--";
    return formatter.format(speedMps);
  }
}
