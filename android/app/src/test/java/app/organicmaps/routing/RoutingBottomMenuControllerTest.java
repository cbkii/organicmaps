package app.organicmaps.routing;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RoutingBottomMenuControllerTest
{
  @Test
  public void automaticExpiryUsesTheSameGuardedStartListenerAndNeverRetries()
  {
    final StartGateFixture fixture = new StartGateFixture();
    fixture.gate.setState(RoutingBottomMenuController.StartState.ENABLED);
    var countdown = new app.organicmaps.incar.InCarNavigationCountdownPolicy();
    countdown.arm("START", 1, 2, 0);
    if (countdown.dispatch(2000, true))
      RoutingBottomMenuController.dispatchStart(fixture.gate, true, true, () -> fixture.calls++);
    if (countdown.dispatch(3000, true))
      RoutingBottomMenuController.dispatchStart(fixture.gate, true, true, () -> fixture.calls++);
    org.junit.Assert.assertEquals(1, fixture.calls);
    RoutingBottomMenuController.dispatchStart(fixture.gate, false, true, () -> fixture.calls++);
    org.junit.Assert.assertEquals(1, fixture.calls);
  }

  private static final class StartGateFixture
  {
    final RoutingBottomMenuController.StartGate gate = new RoutingBottomMenuController.StartGate();
    int calls;
  }

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
