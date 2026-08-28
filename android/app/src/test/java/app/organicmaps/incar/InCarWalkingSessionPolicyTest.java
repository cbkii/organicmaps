package app.organicmaps.incar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import app.organicmaps.sdk.Router;

import org.junit.Test;

public class InCarWalkingSessionPolicyTest
{
  @Test
  public void newPolicyIsNotActive()
  {
    assertFalse(new InCarWalkingSessionPolicy().isActive());
  }

  @Test
  public void restoredActivePolicyIsActive()
  {
    assertTrue(new InCarWalkingSessionPolicy(true).isActive());
  }

  @Test
  public void restoredInactivePolicyIsNotActive()
  {
    assertFalse(new InCarWalkingSessionPolicy(false).isActive());
  }

  @Test
  public void startWalkingActivatesSession()
  {
    final InCarWalkingSessionPolicy policy = new InCarWalkingSessionPolicy();
    policy.startWalking();
    assertTrue(policy.isActive());
  }

  @Test
  public void endWalkingDeactivatesSession()
  {
    final InCarWalkingSessionPolicy policy = new InCarWalkingSessionPolicy(true);
    policy.endWalking();
    assertFalse(policy.isActive());
  }

  @Test
  public void routerForSessionReturnsPedestrianWhenActive()
  {
    final InCarWalkingSessionPolicy policy = new InCarWalkingSessionPolicy(true);
    assertEquals(Router.Pedestrian, policy.routerForSession());
  }

  @Test
  public void routerForSessionReturnsVehicleWhenInactive()
  {
    final InCarWalkingSessionPolicy policy = new InCarWalkingSessionPolicy(false);
    assertEquals(Router.Vehicle, policy.routerForSession());
  }

  @Test
  public void startThenEndReturnsVehicle()
  {
    final InCarWalkingSessionPolicy policy = new InCarWalkingSessionPolicy();
    policy.startWalking();
    policy.endWalking();
    assertEquals(Router.Vehicle, policy.routerForSession());
    assertFalse(policy.isActive());
  }

  @Test
  public void multipleStartCallsKeepSessionActive()
  {
    final InCarWalkingSessionPolicy policy = new InCarWalkingSessionPolicy();
    policy.startWalking();
    policy.startWalking();
    assertTrue(policy.isActive());
    assertEquals(Router.Pedestrian, policy.routerForSession());
  }
}
