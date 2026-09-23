package app.organicmaps.location;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import app.organicmaps.location.LocationPromptCoordinator.ProviderAction;
import org.junit.Test;

public class LocationPromptCoordinatorTest
{
  @Test
  public void grantedAndEnabledIgnoresStaleProviderCallback()
  {
    final LocationPromptCoordinator coordinator = new LocationPromptCoordinator();

    assertEquals(ProviderAction.IGNORE_STALE_CALLBACK,
                 coordinator.onProviderUnavailable(true, true, false));
    assertFalse(coordinator.isPermissionRequestPending());
    assertFalse(coordinator.isLocationSettingsTransitionPending());
  }

  @Test
  public void grantedAndDisabledUsesProviderSettingsOnly()
  {
    final LocationPromptCoordinator coordinator = new LocationPromptCoordinator();

    assertEquals(ProviderAction.SHOW_LOCATION_SETTINGS,
                 coordinator.onProviderUnavailable(true, false, false));
    assertFalse(coordinator.isPermissionRequestPending());
  }

  @Test
  public void missingPermissionUsesRuntimePermissionPath()
  {
    final LocationPromptCoordinator coordinator = new LocationPromptCoordinator();

    assertEquals(ProviderAction.REQUEST_PERMISSION,
                 coordinator.onProviderUnavailable(false, false, false));
    assertTrue(coordinator.isPermissionRequestPending());
  }

  @Test
  public void repeatedCallbacksWhilePermissionRequestOutstandingDoNothing()
  {
    final LocationPromptCoordinator coordinator = new LocationPromptCoordinator();

    assertTrue(coordinator.beginPermissionRequest(false, false));
    assertFalse(coordinator.beginPermissionRequest(false, false));
    assertEquals(ProviderAction.NONE,
                 coordinator.onProviderUnavailable(false, false, false));
  }

  @Test
  public void repeatedCallbacksWhileSettingsOutstandingDoNothing()
  {
    final LocationPromptCoordinator coordinator = new LocationPromptCoordinator();

    assertTrue(coordinator.beginLocationSettingsTransition());
    assertFalse(coordinator.beginLocationSettingsTransition());
    assertEquals(ProviderAction.NONE,
                 coordinator.onProviderUnavailable(true, false, false));
  }

  @Test
  public void visibleLocationDialogSuppressesDuplicatePermissionOrSettingsUi()
  {
    final LocationPromptCoordinator coordinator = new LocationPromptCoordinator();

    assertFalse(coordinator.beginPermissionRequest(false, true));
    assertEquals(ProviderAction.NONE,
                 coordinator.onProviderUnavailable(false, false, true));
    assertEquals(ProviderAction.NONE,
                 coordinator.onProviderUnavailable(true, false, true));
  }

  @Test
  public void returningFromSettingsReevaluatesCurrentProviderState()
  {
    final LocationPromptCoordinator coordinator = new LocationPromptCoordinator();

    assertTrue(coordinator.beginLocationSettingsTransition());
    coordinator.finishLocationSettingsTransition();

    assertEquals(ProviderAction.IGNORE_STALE_CALLBACK,
                 coordinator.onProviderUnavailable(true, true, false));
  }

  @Test
  public void permissionResultAllowsFreshCurrentStateOnLaterOperation()
  {
    final LocationPromptCoordinator coordinator = new LocationPromptCoordinator();

    assertTrue(coordinator.beginPermissionRequest(false, false));
    coordinator.finishPermissionRequest();

    assertFalse(coordinator.beginPermissionRequest(true, false));
    assertFalse(coordinator.isPermissionRequestPending());
  }
}
