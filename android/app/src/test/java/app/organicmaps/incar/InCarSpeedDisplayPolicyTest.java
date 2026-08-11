package app.organicmaps.incar;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class InCarSpeedDisplayPolicyTest
{
  @Test
  public void currentSpeedDelegatesToExistingFormatter()
  {
    final AtomicReference<Double> received = new AtomicReference<>();
    final String result =
        InCarSpeedDisplayPolicy.format(InCarDrivingViewController.LocationHealth.CURRENT, true, 12.5, speedMps -> {
          received.set(speedMps);
          return "45 km/h";
        });

    assertEquals("45 km/h", result);
    assertEquals(12.5, received.get(), 0.0);
  }

  @Test
  public void zeroSpeedStillUsesExistingFormatter()
  {
    final String result =
        InCarSpeedDisplayPolicy.format(InCarDrivingViewController.LocationHealth.CURRENT, true, 0.0,
                                       speedMps -> speedMps == 0.0 ? "0 km/h" : "unexpected");
    assertEquals("0 km/h", result);
  }

  @Test
  public void staleSpeedNeverFormatsOldNumericValue()
  {
    final String result =
        InCarSpeedDisplayPolicy.format(InCarDrivingViewController.LocationHealth.STALE, true, 27.0,
                                       speedMps -> {
                                         throw new AssertionError("stale speed must not be formatted");
                                       });
    assertEquals("--", result);
  }

  @Test
  public void unavailableAndMissingSpeedRemainUnavailable()
  {
    assertEquals("--", InCarSpeedDisplayPolicy.format(InCarDrivingViewController.LocationHealth.UNAVAILABLE, true,
                                                       27.0, speedMps -> "unexpected"));
    assertEquals("--", InCarSpeedDisplayPolicy.format(InCarDrivingViewController.LocationHealth.CURRENT, false,
                                                       Double.NaN, speedMps -> "unexpected"));
  }
}
