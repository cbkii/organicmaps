package app.organicmaps.sdk.routing;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RouteStartGateTest
{
  @Test
  public void delayedDisclaimerCannotStartCancelledOrReplacedRoute()
  {
    final RoutingController.RouteStartGate gate = new RoutingController.RouteStartGate();
    gate.invalidate(); // First native build.
    final long firstRoute = gate.getRevision();
    assertTrue(gate.canStart(true, true, firstRoute));

    gate.invalidate(); // END or cancellation.
    assertFalse(gate.canStart(false, false, firstRoute));

    gate.invalidate(); // Another route builds while the first disclaimer is open.
    final long secondRoute = gate.getRevision();
    assertFalse(gate.canStart(true, true, firstRoute));
    assertTrue(gate.canStart(true, true, secondRoute));
    assertFalse(gate.canStart(false, true, secondRoute)); // Already navigating.
    assertFalse(gate.canStart(true, false, secondRoute)); // Rebuilding.
  }
}
