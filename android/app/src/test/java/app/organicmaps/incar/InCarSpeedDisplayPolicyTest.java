package app.organicmaps.incar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;

public class InCarSpeedDisplayPolicyTest
{
  private static final String UNAVAILABLE = "unavailable";

  @Before
  public void resetWarningState()
  {
    InCarSpeedDisplayPolicy.resetSpeeding();
  }

  @Test
  public void currentSpeedDelegatesToExistingFormatter()
  {
    final AtomicReference<Double> received = new AtomicReference<>();
    final String result = InCarSpeedDisplayPolicy.format(InCarDrivingViewController.LocationHealth.CURRENT, true, 12.5,
                                                         UNAVAILABLE, speedMps -> {
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
        InCarSpeedDisplayPolicy.format(InCarDrivingViewController.LocationHealth.CURRENT, true, 0.0, UNAVAILABLE,
                                       speedMps -> speedMps == 0.0 ? "0 km/h" : "unexpected");
    assertEquals("0 km/h", result);
  }

  @Test
  public void staleSpeedNeverFormatsOldNumericValue()
  {
    final String result =
        InCarSpeedDisplayPolicy.format(InCarDrivingViewController.LocationHealth.STALE, true, 27.0, UNAVAILABLE,
                                       speedMps -> { throw new AssertionError("stale speed must not be formatted"); });
    assertEquals(UNAVAILABLE, result);
  }

  @Test
  public void unavailableAndMissingSpeedRemainUnavailable()
  {
    assertEquals(UNAVAILABLE, InCarSpeedDisplayPolicy.format(InCarDrivingViewController.LocationHealth.UNAVAILABLE,
                                                             true, 27.0, UNAVAILABLE, speedMps -> "unexpected"));
    assertEquals(UNAVAILABLE, InCarSpeedDisplayPolicy.format(InCarDrivingViewController.LocationHealth.CURRENT, false,
                                                             Double.NaN, UNAVAILABLE, speedMps -> "unexpected"));
  }

  @Test
  public void compactSpeedKeepsValueAndDropsUnit()
  {
    assertEquals("45", InCarSpeedDisplayPolicy.compactFormattedSpeed("45 km/h"));
    assertEquals("100", InCarSpeedDisplayPolicy.compactFormattedSpeed("100\u00A0km/h"));
    assertEquals("--", InCarSpeedDisplayPolicy.compactFormattedSpeed("--"));
  }

  @Test
  public void speedWarningEntersAtOneHundredAndFivePercent()
  {
    assertFalse(InCarSpeedDisplayPolicy.isSpeeding(kph(41.9), kph(40.0)));
    assertTrue(InCarSpeedDisplayPolicy.isSpeeding(kph(42.0), kph(40.0)));

    InCarSpeedDisplayPolicy.resetSpeeding();
    assertFalse(InCarSpeedDisplayPolicy.isSpeeding(kph(62.9), kph(60.0)));
    assertTrue(InCarSpeedDisplayPolicy.isSpeeding(kph(63.0), kph(60.0)));

    InCarSpeedDisplayPolicy.resetSpeeding();
    assertTrue(InCarSpeedDisplayPolicy.isSpeeding(kph(84.0), kph(80.0)));

    InCarSpeedDisplayPolicy.resetSpeeding();
    assertTrue(InCarSpeedDisplayPolicy.isSpeeding(kph(105.0), kph(100.0)));
  }

  @Test
  public void speedWarningUsesOneHundredAndThreePercentClearHysteresis()
  {
    assertTrue(InCarSpeedDisplayPolicy.isSpeeding(kph(105.0), kph(100.0)));
    assertTrue(InCarSpeedDisplayPolicy.isSpeeding(kph(104.0), kph(100.0)));
    assertTrue(InCarSpeedDisplayPolicy.isSpeeding(kph(103.0), kph(100.0)));
    assertFalse(InCarSpeedDisplayPolicy.isSpeeding(kph(102.9), kph(100.0)));

    InCarSpeedDisplayPolicy.resetSpeeding();
    assertFalse(InCarSpeedDisplayPolicy.isSpeeding(kph(104.0), kph(100.0)));
  }

  @Test
  public void routeLimitRefreshCannotCompeteWithSpeedHysteresis()
  {
    assertFalse(InCarSpeedDisplayPolicy.updateSpeedLimit(kph(100.0)));
    assertTrue(InCarSpeedDisplayPolicy.isSpeeding(kph(105.0), kph(100.0)));

    // Interleaved route-info refreshes for the same limit read the established state but do not
    // feed a second speed sample or reset the 103-105% hysteresis band.
    assertTrue(InCarSpeedDisplayPolicy.updateSpeedLimit(kph(100.0)));
    assertTrue(InCarSpeedDisplayPolicy.isSpeeding(kph(104.0), kph(100.0)));
    assertTrue(InCarSpeedDisplayPolicy.updateSpeedLimit(kph(100.0)));
    assertFalse(InCarSpeedDisplayPolicy.isSpeeding(kph(102.9), kph(100.0)));
    assertFalse(InCarSpeedDisplayPolicy.updateSpeedLimit(kph(100.0)));
  }

  @Test
  public void routeLimitChangeRecomputesUsingLatestSpeedSample()
  {
    assertTrue(InCarSpeedDisplayPolicy.isSpeeding(kph(105.0), kph(100.0)));
    assertTrue(InCarSpeedDisplayPolicy.updateSpeedLimit(kph(80.0)));
    assertTrue(InCarSpeedDisplayPolicy.isSpeeding(kph(83.9), kph(80.0)));
    assertFalse(InCarSpeedDisplayPolicy.isSpeeding(kph(82.3), kph(80.0)));
    assertTrue(InCarSpeedDisplayPolicy.isSpeeding(kph(84.0), kph(80.0)));
  }

  @Test
  public void invalidMeasurementClearsExistingWarning()
  {
    assertTrue(InCarSpeedDisplayPolicy.isSpeeding(kph(105.0), kph(100.0)));
    assertFalse(InCarSpeedDisplayPolicy.isSpeeding(Double.NaN, kph(100.0)));
    assertFalse(InCarSpeedDisplayPolicy.isSpeeding(kph(104.0), kph(100.0)));
  }

  @Test
  public void speedWarningRequiresValidMeasurements()
  {
    assertFalse(InCarSpeedDisplayPolicy.isSpeeding(21.0, -1.0));
    assertFalse(InCarSpeedDisplayPolicy.isSpeeding(-1.0, 20.0));
    assertFalse(InCarSpeedDisplayPolicy.isSpeeding(Double.NaN, 20.0));
    assertFalse(InCarSpeedDisplayPolicy.isSpeeding(21.0, Double.NaN));
  }

  @Test
  public void zeroLimitIsUnknownAndClearsWarning()
  {
    assertTrue(InCarSpeedDisplayPolicy.isSpeeding(kph(105.0), kph(100.0)));
    assertFalse(InCarSpeedDisplayPolicy.isSpeeding(0.0, 0.0));
    assertFalse(InCarSpeedDisplayPolicy.isSpeeding(0.1, 0.0));
    assertFalse(InCarSpeedDisplayPolicy.isSpeeding(kph(104.0), kph(100.0)));
  }

  @Test
  public void aChangedRoadLimitDoesNotInheritThePreviousWarning()
  {
    assertTrue(InCarSpeedDisplayPolicy.isSpeeding(kph(105.0), kph(100.0)));
    assertFalse(InCarSpeedDisplayPolicy.isSpeeding(kph(83.5), kph(80.0)));
    assertTrue(InCarSpeedDisplayPolicy.isSpeeding(kph(84.0), kph(80.0)));
  }

  @Test
  public void liveDisplayedLimitWorksRegardlessOfWhichInputArrivesFirst()
  {
    assertFalse(InCarSpeedDisplayPolicy.updateCurrentSpeed(kph(92)));
    assertTrue(InCarSpeedDisplayPolicy.updateSpeedLimit(kph(80)));
    assertTrue(InCarSpeedDisplayPolicy.warningActive());
    assertEquals(0.50f, InCarSpeedDisplayPolicy.warningStrength(), 0.0f);
    InCarSpeedDisplayPolicy.resetSpeeding();
    assertFalse(InCarSpeedDisplayPolicy.updateSpeedLimit(kph(80)));
    assertTrue(InCarSpeedDisplayPolicy.updateCurrentSpeed(kph(92)));
    assertEquals(0.50f, InCarSpeedDisplayPolicy.warningStrength(), 0.0f);
    // The same speed with a newly displayed 100 km/h limit immediately clears the surface.
    assertFalse(InCarSpeedDisplayPolicy.updateSpeedLimit(kph(100)));
  }

  @Test
  public void tintProgressionAndStaleSamplesUseOneState()
  {
    InCarSpeedDisplayPolicy.updateSpeedLimit(kph(100));
    assertFalse(InCarSpeedDisplayPolicy.updateCurrentSpeed(kph(104.9)));
    assertEquals(0.0f, InCarSpeedDisplayPolicy.warningStrength(), 0.0f);
    assertTrue(InCarSpeedDisplayPolicy.updateCurrentSpeed(kph(105)));
    assertEquals(0.44f, InCarSpeedDisplayPolicy.warningStrength(), 0.00001f);
    InCarSpeedDisplayPolicy.updateCurrentSpeed(kph(107.5));
    assertEquals(0.47f, InCarSpeedDisplayPolicy.warningStrength(), 0.00001f);
    InCarSpeedDisplayPolicy.updateCurrentSpeed(kph(110));
    assertEquals(0.50f, InCarSpeedDisplayPolicy.warningStrength(), 0.00001f);
    assertTrue(InCarSpeedDisplayPolicy.updateCurrentSpeed(kph(103)));
    assertFalse(InCarSpeedDisplayPolicy.updateCurrentSpeed(kph(102.9)));
    InCarSpeedDisplayPolicy.updateCurrentSpeed(kph(110));
    assertFalse(InCarSpeedDisplayPolicy.updateCurrentSpeed(Double.NaN));
    assertEquals(0.0f, InCarSpeedDisplayPolicy.warningStrength(), 0.0f);
  }

  @Test
  public void halfStrengthProgressionIsMonotonicAndBounded()
  {
    InCarSpeedDisplayPolicy.updateSpeedLimit(kph(100));
    float previous = 0.44f;
    for (double speed = 105; speed <= 120; speed += 0.25)
    {
      assertTrue(InCarSpeedDisplayPolicy.updateCurrentSpeed(kph(speed)));
      final float strength = InCarSpeedDisplayPolicy.warningStrength();
      assertTrue(strength + 0.00001f >= previous);
      assertTrue(strength <= 0.50f);
      previous = strength;
    }
    assertFalse(InCarSpeedDisplayPolicy.updateSpeedLimit(-1));
    assertEquals(0.0f, InCarSpeedDisplayPolicy.warningStrength(), 0.0f);
  }

  private static double kph(double value)
  {
    return value / 3.6;
  }
}
