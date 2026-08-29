package app.organicmaps.incar;

import static org.junit.Assert.assertEquals;

import app.organicmaps.sdk.Router;
import org.junit.Test;

public class InCarRouterPolicyTest
{
  @Test
  public void normalDestinationUsesVehicle()
  {
    assertEquals(Router.Vehicle, InCarRouterPolicy.routerForNewDestination(false));
  }

  @Test
  public void explicitWalkingSessionUsesPedestrian()
  {
    assertEquals(Router.Pedestrian, InCarRouterPolicy.routerForNewDestination(true));
  }

  @Test
  public void staledLastUsedBicycleNeverExposed()
  {
    // The policy is deterministic — there is no path that returns Bicycle regardless of
    // what Router.getLastUsed() might return.  Verify both session states exhaustively.
    final Router withoutWalking = InCarRouterPolicy.routerForNewDestination(false);
    final Router withWalking = InCarRouterPolicy.routerForNewDestination(true);
    assertEquals(Router.Vehicle, withoutWalking);
    assertEquals(Router.Pedestrian, withWalking);
    // Neither is Bicycle, Transit or Ruler
    assertEquals(
        false, withoutWalking == Router.Bicycle || withoutWalking == Router.Transit || withoutWalking == Router.Ruler);
    assertEquals(false, withWalking == Router.Bicycle || withWalking == Router.Transit || withWalking == Router.Ruler);
  }

  @Test
  public void staledLastUsedTransitNeverExposed()
  {
    final Router r = InCarRouterPolicy.routerForNewDestination(false);
    assertEquals(false, r == Router.Transit);
  }

  @Test
  public void staledLastUsedRulerNeverExposed()
  {
    final Router r = InCarRouterPolicy.routerForNewDestination(false);
    assertEquals(false, r == Router.Ruler);
  }
}
