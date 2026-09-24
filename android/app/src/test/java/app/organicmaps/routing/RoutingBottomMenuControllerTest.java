package app.organicmaps.routing;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RoutingBottomMenuControllerTest
{
  @Test
  public void startGateRequiresEnabledBuiltPlanningRoute()
  {
    final RoutingBottomMenuController.StartGate gate = new RoutingBottomMenuController.StartGate();

    assertFalse(gate.canDispatch(true, true));
    gate.setState(RoutingBottomMenuController.StartState.BUILDING);
    assertFalse(gate.canDispatch(true, true));
    gate.setState(RoutingBottomMenuController.StartState.ENABLED);
    assertFalse(gate.canDispatch(false, true));
    assertFalse(gate.canDispatch(true, false));
    assertTrue(gate.canDispatch(true, true));
  }

  @Test
  public void repeatedBuildStartEndCyclesDoNotRetainAStaleStartState()
  {
    final RoutingBottomMenuController.StartGate gate = new RoutingBottomMenuController.StartGate();

    for (int cycle = 0; cycle < 3; ++cycle)
    {
      gate.setState(RoutingBottomMenuController.StartState.BUILDING);
      assertFalse(gate.canDispatch(true, false));

      gate.setState(RoutingBottomMenuController.StartState.ENABLED);
      assertTrue(gate.canDispatch(true, true));

      gate.setState(RoutingBottomMenuController.StartState.DISABLED);
      assertFalse(gate.canDispatch(false, false));
    }
  }
}
