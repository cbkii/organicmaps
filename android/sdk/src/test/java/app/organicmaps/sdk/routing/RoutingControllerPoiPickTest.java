package app.organicmaps.sdk.routing;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RoutingControllerPoiPickTest
{
  @Test
  public void searchDismissalDropsEveryPickAndReplacementIdentity()
  {
    final RoutingController controller = new RoutingController();
    for (RouteMarkType type : RouteMarkType.values())
    {
      controller.waitForPoiPick(type);
      assertTrue(controller.isWaitingPoiPick());
      controller.replaceStopPoiPick(2);
      controller.cancelPoiPick();
      assertFalse(controller.isWaitingPoiPick());
      assertFalse(controller.isPoiPickReplaceStop());
      assertNull(controller.getWaitingPoiPickType());
      controller.cancelPoiPick();
    }
    controller.waitForPoiPick(RouteMarkType.Intermediate);
    assertFalse(controller.isPoiPickReplaceStop());
  }
}
