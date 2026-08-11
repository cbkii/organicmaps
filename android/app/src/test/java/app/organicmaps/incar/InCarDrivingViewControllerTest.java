package app.organicmaps.incar;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InCarDrivingViewControllerTest
{
  @Test
  public void freshDisabledStateDoesNotOverwriteNativeStartupMode()
  {
    assertFalse(
        InCarDrivingViewController.shouldApplyNativeState(false, false, false, false, true, true, false, false));
  }

  @Test
  public void firstEnabledStateIsApplied()
  {
    assertTrue(InCarDrivingViewController.shouldApplyNativeState(true, false, false, false, true, true, false, false));
  }

  @Test
  public void routingOwnershipChangeForcesReapplication()
  {
    assertTrue(InCarDrivingViewController.shouldApplyNativeState(true, false, true, true, true, true, false, true));
  }

  @Test
  public void unchangedAppliedStateIsIdempotent()
  {
    assertFalse(InCarDrivingViewController.shouldApplyNativeState(true, false, true, true, true, true, false, false));
  }
}
