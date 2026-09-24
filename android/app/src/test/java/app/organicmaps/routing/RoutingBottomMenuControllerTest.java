package app.organicmaps.routing;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RoutingBottomMenuControllerTest
{
  @Test
  public void startDispatchRequiresEnabledBuiltPlanningRoute()
  {
    assertFalse(RoutingBottomMenuController.canDispatchStart(
        RoutingBottomMenuController.StartState.DISABLED, true, true));
    assertFalse(RoutingBottomMenuController.canDispatchStart(
        RoutingBottomMenuController.StartState.BUILDING, true, true));
    assertFalse(RoutingBottomMenuController.canDispatchStart(
        RoutingBottomMenuController.StartState.ENABLED, false, true));
    assertFalse(RoutingBottomMenuController.canDispatchStart(
        RoutingBottomMenuController.StartState.ENABLED, true, false));
    assertTrue(RoutingBottomMenuController.canDispatchStart(
        RoutingBottomMenuController.StartState.ENABLED, true, true));
  }

  @Test
  public void repeatedBuildStartEndCyclesDoNotRetainAStaleStartState()
  {
    for (int cycle = 0; cycle < 3; ++cycle)
    {
      assertFalse(RoutingBottomMenuController.canDispatchStart(
          RoutingBottomMenuController.StartState.BUILDING, true, false));
      assertTrue(RoutingBottomMenuController.canDispatchStart(
          RoutingBottomMenuController.StartState.ENABLED, true, true));
      assertFalse(RoutingBottomMenuController.canDispatchStart(
          RoutingBottomMenuController.StartState.DISABLED, false, false));
    }
  }
}
