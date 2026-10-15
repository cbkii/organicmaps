package app.organicmaps.sdk.routing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.organicmaps.sdk.util.log.Logger;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

public class RoutingControllerPlanningLeaseTest
{
  private static void setState(RoutingController controller, String state) throws ReflectiveOperationException
  {
    final Field field = RoutingController.class.getDeclaredField("mState");
    field.setAccessible(true);
    for (Object candidate : field.getType().getEnumConstants())
      if (candidate.toString().equals(state))
      {
        field.set(controller, candidate);
        return;
      }
    throw new AssertionError("Unknown routing state " + state);
  }

  @Test
  public void builtAndSavedPlanningRouteNotifyLeaseOwnerAndRemovalStopsNotifications()
      throws ReflectiveOperationException
  {
    final RoutingController.RouteCommands commands = mock(RoutingController.RouteCommands.class);
    final RoutingController controller = new RoutingController(commands);
    final RoutingController.NavigationStateListener listener = mock(RoutingController.NavigationStateListener.class);
    controller.addNavigationStateListener(listener);
    controller.addNavigationStateListener(listener);
    setState(controller, "PREPARE");
    final Method setBuildState =
        RoutingController.class.getDeclaredMethod("setBuildState", RoutingController.BuildState.class);
    setBuildState.setAccessible(true);
    try (MockedStatic<Logger> ignored = mockStatic(Logger.class))
    {
      setBuildState.invoke(controller, RoutingController.BuildState.BUILT);
      controller.saveRoute();
      verify(listener).onPlanningRouteReady();
      verify(listener).onPlanningRouteSaved();
      verify(commands).saveRoutePoints();
      controller.removeNavigationStateListener(listener);
      controller.saveRoute();
      verify(listener).onPlanningRouteReady();
      verify(listener).onPlanningRouteSaved();
    }
  }

  @Test
  public void incompletePlanningRouteCannotRenewLease() throws ReflectiveOperationException
  {
    final RoutingController.RouteCommands commands = mock(RoutingController.RouteCommands.class);
    final RoutingController controller = new RoutingController(commands);
    final RoutingController.NavigationStateListener listener = mock(RoutingController.NavigationStateListener.class);
    controller.addNavigationStateListener(listener);
    setState(controller, "PREPARE");
    controller.saveRoute();
    verify(listener, never()).onPlanningRouteReady();
    verify(listener, never()).onPlanningRouteSaved();
    verify(commands).saveRoutePoints();
  }

  @Test
  public void buildingPlanningRoutePersistsCurrentPointsAndNotifiesLeaseOwner() throws ReflectiveOperationException
  {
    final RoutingController.RouteCommands commands = mock(RoutingController.RouteCommands.class);
    final RoutingController controller = new RoutingController(commands);
    final RoutingController.NavigationStateListener listener = mock(RoutingController.NavigationStateListener.class);
    controller.addNavigationStateListener(listener);
    setState(controller, "PREPARE");
    final Field buildState = RoutingController.class.getDeclaredField("mBuildState");
    buildState.setAccessible(true);
    buildState.set(controller, RoutingController.BuildState.BUILDING);
    when(commands.hasCompleteRoutePoints()).thenReturn(true);
    controller.saveRoute();
    verify(commands).saveRoutePoints();
    verify(listener).onPlanningRouteSaved();
    verify(listener, never()).onPlanningRouteReady();
  }

  @Test
  public void returningToPlanningDisablesFollowingBeforeExactlyOneBuild() throws ReflectiveOperationException
  {
    final RoutingController.RouteCommands commands = mock(RoutingController.RouteCommands.class);
    final RoutingController controller = new RoutingController(commands);
    setState(controller, "NAVIGATION");
    try (MockedStatic<Logger> ignored = mockStatic(Logger.class))
    {
      assertTrue(controller.resetToPlanningStateIfNavigating());
      final InOrder order = inOrder(commands);
      order.verify(commands).disableFollowing();
      order.verify(commands).removeRoute();
      order.verify(commands).buildRoute();
      order.verifyNoMoreInteractions();
      assertEquals(RoutingController.BuildState.BUILDING, controller.getBuildState());
    }
  }
}
