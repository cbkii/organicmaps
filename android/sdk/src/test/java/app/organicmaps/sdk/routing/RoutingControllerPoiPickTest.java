package app.organicmaps.sdk.routing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import org.junit.Test;

public class RoutingControllerPoiPickTest
{
  @Test
  public void searchDismissalDropsEveryPickAndReplacementIdentity() throws ReflectiveOperationException
  {
    final RoutingController controller = new RoutingController();
    final Field replacementIndex = RoutingController.class.getDeclaredField("mReplaceStopIndex");
    replacementIndex.setAccessible(true);
    for (RouteMarkType type : RouteMarkType.values())
    {
      controller.waitForPoiPick(type);
      assertTrue(controller.isWaitingPoiPick());
      assertEquals(type, controller.getWaitingPoiPickType());
      controller.replaceStopPoiPick(2);
      assertTrue(controller.isPoiPickReplaceStop());
      assertEquals(2, replacementIndex.getInt(controller));
      controller.cancelPoiPick();
      assertFalse(controller.isWaitingPoiPick());
      assertFalse(controller.isPoiPickReplaceStop());
      assertEquals(-1, replacementIndex.getInt(controller));
      assertNull(controller.getWaitingPoiPickType());
      controller.cancelPoiPick();
    }
    controller.waitForPoiPick(RouteMarkType.Intermediate);
    assertFalse(controller.isPoiPickReplaceStop());
    controller.replaceStopPoiPick(1);
    assertTrue(controller.isPoiPickReplaceStop());
    assertEquals(1, replacementIndex.getInt(controller));
    controller.cancelPoiPick();
    assertEquals(-1, replacementIndex.getInt(controller));
  }
}
