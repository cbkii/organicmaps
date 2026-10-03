package app.organicmaps.incar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import app.organicmaps.sdk.routing.RoadSpeedLimitInfo;
import org.junit.Test;

public class InCarRoadSpeedLimitTest
{
  private static final long OBSERVED = 1_000_000_000L;
  private static final long NOW = OBSERVED + 1;
  private static final RoadSpeedLimitInfo ROAD = new RoadSpeedLimitInfo(50.0 / 3.6, OBSERVED, 42);

  @Test
  public void routeWinsAndUnrestrictedNeverBecomesFallbackNumericLimit()
  {
    assertEquals(80.0 / 3.6, InCarSpeedDisplayPolicy.selectSpeedLimit(80.0 / 3.6, ROAD, true, NOW), 0.0);
    assertEquals(0.0, InCarSpeedDisplayPolicy.selectSpeedLimit(0, ROAD, true, NOW), 0.0);
  }

  @Test
  public void validRouteLimitIsIndependentOfRoadObservationFreshness()
  {
    assertEquals(20, InCarSpeedDisplayPolicy.selectSpeedLimit(20, ROAD, false, NOW), 0.0);
    assertEquals(0, InCarSpeedDisplayPolicy.selectSpeedLimit(0, null, false, NOW), 0.0);
    assertEquals(20, InCarSpeedDisplayPolicy.selectSpeedLimit(
        20, ROAD, true, OBSERVED + RoadSpeedLimitInfo.MAX_AGE_NANOS), 0.0);
  }

  @Test
  public void unknownRouteAndNonRouteUseFreshRoadWithoutSpeedThreshold()
  {
    assertEquals(ROAD.speedLimitMps, InCarSpeedDisplayPolicy.selectSpeedLimit(-1, ROAD, true, NOW), 0.0);
    assertEquals(ROAD.speedLimitMps, InCarSpeedDisplayPolicy.selectSpeedLimit(Double.NaN, ROAD, true, NOW), 0.0);
    assertEquals(0, InCarSpeedDisplayPolicy.selectSpeedLimit(-1, new RoadSpeedLimitInfo(0, OBSERVED, 42), true, NOW),
                 0.0);
  }

  @Test
  public void missingChangedOrStaleRoadIsUnknown()
  {
    assertEquals(-1, InCarSpeedDisplayPolicy.selectSpeedLimit(-1, null, true, NOW), 0.0);
    assertEquals(-1, InCarSpeedDisplayPolicy.selectSpeedLimit(-1, ROAD, false, NOW), 0.0);
    assertEquals(
        -1, InCarSpeedDisplayPolicy.selectSpeedLimit(-1, ROAD, true, OBSERVED + RoadSpeedLimitInfo.MAX_AGE_NANOS), 0.0);
    for (RoadSpeedLimitInfo invalid : new RoadSpeedLimitInfo[] {
             new RoadSpeedLimitInfo(-1, OBSERVED, 42), new RoadSpeedLimitInfo(Double.NaN, OBSERVED, 42),
             new RoadSpeedLimitInfo(Double.POSITIVE_INFINITY, OBSERVED, 42), new RoadSpeedLimitInfo(20, OBSERVED, 0),
             new RoadSpeedLimitInfo(20, 0, 42), new RoadSpeedLimitInfo(20, NOW + 1, 42)})
      assertEquals(-1, InCarSpeedDisplayPolicy.selectSpeedLimit(-1, invalid, true, NOW), 0.0);
  }

  @Test
  public void consecutiveCompletedObservationsKeepOverspeedHysteresis()
  {
    InCarSpeedDisplayPolicy.resetSpeeding();
    InCarSpeedDisplayPolicy.updateSpeedLimit(InCarSpeedDisplayPolicy.selectSpeedLimit(-1, ROAD, true, NOW));
    assertTrue(InCarSpeedDisplayPolicy.updateCurrentSpeed(ROAD.speedLimitMps * 1.05));
    final RoadSpeedLimitInfo next = new RoadSpeedLimitInfo(ROAD.speedLimitMps, NOW, ROAD.roadToken);
    InCarSpeedDisplayPolicy.updateSpeedLimit(InCarSpeedDisplayPolicy.selectSpeedLimit(-1, next, true, NOW + 1));
    assertTrue(InCarSpeedDisplayPolicy.updateCurrentSpeed(ROAD.speedLimitMps * 1.04));
    assertFalse(InCarSpeedDisplayPolicy.updateCurrentSpeed(ROAD.speedLimitMps * 1.02));
    InCarSpeedDisplayPolicy.resetSpeeding();
  }

  @Test
  public void completedNativeResultMustBelongToSameProviderObservation()
  {
    assertTrue(ROAD.isFromObservation(OBSERVED + 20));
    assertFalse(ROAD.isFromObservation(OBSERVED + 1_000_000));
    assertFalse(ROAD.isFromObservation(0));
    assertTrue(ROAD.isFresh(OBSERVED + RoadSpeedLimitInfo.MAX_AGE_NANOS - 1));
    assertFalse(ROAD.isFresh(OBSERVED - 1));
  }
}
