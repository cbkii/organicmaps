package app.organicmaps.sdk.routing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.util.log.Logger;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
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
    final RoutingController controller = new RoutingController();
    final RoutingController.NavigationStateListener listener = mock(RoutingController.NavigationStateListener.class);
    controller.addNavigationStateListener(listener);
    controller.addNavigationStateListener(listener);
    setState(controller, "PREPARE");
    final Method setBuildState =
        RoutingController.class.getDeclaredMethod("setBuildState", RoutingController.BuildState.class);
    setBuildState.setAccessible(true);
    try (MockedStatic<Framework> framework = mockStatic(Framework.class);
         MockedStatic<Logger> ignored = mockStatic(Logger.class))
    {
      setBuildState.invoke(controller, RoutingController.BuildState.BUILT);
      controller.saveRoute();
      verify(listener).onPlanningRouteReady();
      verify(listener).onPlanningRouteSaved();
      framework.verify(Framework::nativeSaveRoutePoints);
      controller.removeNavigationStateListener(listener);
      controller.saveRoute();
      verify(listener).onPlanningRouteReady();
      verify(listener).onPlanningRouteSaved();
    }
  }

  @Test
  public void incompletePlanningRouteCannotRenewLease() throws ReflectiveOperationException
  {
    final RoutingController controller = new RoutingController();
    final RoutingController.NavigationStateListener listener = mock(RoutingController.NavigationStateListener.class);
    controller.addNavigationStateListener(listener);
    setState(controller, "PREPARE");
    try (MockedStatic<Framework> framework = mockStatic(Framework.class))
    {
      controller.saveRoute();
      verify(listener, never()).onPlanningRouteReady();
      framework.verify(Framework::nativeSaveRoutePoints);
    }
  }

  @Test
  public void buildingPlanningRoutePersistsCurrentPoints() throws ReflectiveOperationException
  {
    final RoutingController controller = new RoutingController();
    setState(controller, "PREPARE");
    final Field buildState = RoutingController.class.getDeclaredField("mBuildState");
    buildState.setAccessible(true);
    buildState.set(controller, RoutingController.BuildState.BUILDING);
    try (MockedStatic<Framework> framework = mockStatic(Framework.class))
    {
      controller.saveRoute();
      framework.verify(Framework::nativeSaveRoutePoints);
      framework.verify(Framework::nativeDeleteSavedRoutePoints, never());
    }
  }

  @Test
  public void returningToPlanningDisablesFollowingBeforeExactlyOneBuild() throws ReflectiveOperationException
  {
    final RoutingController controller = new RoutingController();
    setState(controller, "NAVIGATION");
    final List<String> events = new ArrayList<>();
    try (MockedStatic<Framework> framework = mockStatic(Framework.class);
         MockedStatic<Logger> ignored = mockStatic(Logger.class))
    {
      framework.when(Framework::nativeDisableFollowing).thenAnswer(invocation -> {
        events.add("disable");
        return null;
      });
      framework.when(Framework::nativeBuildRoute).thenAnswer(invocation -> {
        events.add("build");
        return null;
      });
      assertTrue(controller.resetToPlanningStateIfNavigating());
      assertEquals(List.of("disable", "build"), events);
      framework.verify(Framework::nativeBuildRoute);
    }
  }
}
