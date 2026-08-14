package app.organicmaps.sdk.routing;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NavigationNotificationTest
{
  @Test
  public void mapsNativeRouteRecalculationEventWithoutInspectingText()
  {
    final String[] texts = {"Any localised text"};
    final NavigationNotification notification = new NavigationNotification(texts, 2, 0);

    assertArrayEquals(texts, notification.getTexts());
    assertEquals(NavigationNotification.Event.ROUTE_RECALCULATION, notification.getEvent());
    assertEquals(NavigationNotification.Stage.NONE, notification.getStage());
  }

  @Test
  public void preservesNativeManeuverStage()
  {
    final NavigationNotification advance = new NavigationNotification(new String[] {"Advance"}, 1, 1);
    final NavigationNotification immediate = new NavigationNotification(new String[] {"Immediate"}, 1, 2);

    assertEquals(NavigationNotification.Stage.ADVANCE, advance.getStage());
    assertEquals(NavigationNotification.Stage.IMMEDIATE, immediate.getStage());
  }

  @Test
  public void mapsTypedSpeedCameraEvent()
  {
    final NavigationNotification notification = new NavigationNotification(new String[0], 3, 0);

    assertEquals(NavigationNotification.Event.SPEED_CAMERA, notification.getEvent());
    assertEquals(NavigationNotification.Stage.NONE, notification.getStage());
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsUnknownNativeEvent()
  {
    new NavigationNotification(new String[] {"Unknown"}, 99, 0);
  }
}
