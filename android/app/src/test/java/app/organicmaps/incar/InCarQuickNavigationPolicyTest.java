package app.organicmaps.incar;

import static org.junit.Assert.assertEquals;

import app.organicmaps.sdk.Router;
import org.junit.Test;

public class InCarQuickNavigationPolicyTest
{
  private static final InCarQuickDestination A = new InCarQuickDestination("A", "", -35.0, 149.0);
  private static final InCarQuickDestination B = new InCarQuickDestination("B", "", -35.1, 149.1);

  @Test
  public void waitsWhileRouteIsStillBuilding()
  {
    assertEquals(InCarQuickNavigationPolicy.Decision.WAIT,
                 InCarQuickNavigationPolicy.evaluate(true, false, false, false, false, true, A, null));
  }

  @Test
  public void matchingSuccessfulVehicleBuildStarts()
  {
    assertEquals(InCarQuickNavigationPolicy.Decision.START,
                 InCarQuickNavigationPolicy.evaluate(true, false, false, true, true, true, A, A));
  }

  @Test
  public void mismatchedEndpointDoesNotStart()
  {
    assertEquals(InCarQuickNavigationPolicy.Decision.CLEAR,
                 InCarQuickNavigationPolicy.evaluate(true, false, false, true, true, true, A, B));
  }

  @Test
  public void replacingPendingDestinationRejectsOldBuild()
  {
    assertEquals(InCarQuickNavigationPolicy.Decision.CLEAR,
                 InCarQuickNavigationPolicy.evaluate(true, false, false, true, true, true, B, A));
  }

  @Test
  public void buildErrorClearsPending()
  {
    assertEquals(InCarQuickNavigationPolicy.Decision.CLEAR,
                 InCarQuickNavigationPolicy.evaluate(true, false, true, false, false, true, A, null));
  }

  @Test
  public void cancelledPlanningClearsPending()
  {
    assertEquals(InCarQuickNavigationPolicy.Decision.CLEAR,
                 InCarQuickNavigationPolicy.evaluate(false, false, false, false, false, true, A, null));
  }

  @Test
  public void partialBuiltResultDoesNotStart()
  {
    assertEquals(InCarQuickNavigationPolicy.Decision.CLEAR,
                 InCarQuickNavigationPolicy.evaluate(true, false, false, true, false, true, A, A));
  }

  @Test
  public void nonVehicleBuildDoesNotStart()
  {
    assertEquals(InCarQuickNavigationPolicy.Decision.CLEAR,
                 InCarQuickNavigationPolicy.evaluate(true, false, false, true, true, false, A, A));
  }

  @Test
  public void alreadyNavigatingClearsPending()
  {
    assertEquals(InCarQuickNavigationPolicy.Decision.CLEAR,
                 InCarQuickNavigationPolicy.evaluate(false, true, false, true, true, true, A, A));
  }

  @Test
  public void exactDestinationsAlwaysRequestVehicleRouter()
  {
    assertEquals(Router.Vehicle, InCarQuickNavigationPolicy.exactRouter());
  }
}
