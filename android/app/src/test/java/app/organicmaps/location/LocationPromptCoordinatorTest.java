package app.organicmaps.location;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import app.organicmaps.location.LocationPromptCoordinator.PermissionAction;
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
  public void permanentlyDeniedPermissionUsesAppSettingsInsteadOfAnotherRequest()
  {
    final LocationPromptCoordinator coordinator = new LocationPromptCoordinator();

    assertEquals(PermissionAction.REQUEST_PERMISSION, coordinator.onPermissionRequired(false, false));
    coordinator.finishPermissionRequest(false, false);

    assertTrue(coordinator.isPermissionPermanentlyDenied());
    assertEquals(PermissionAction.SHOW_APP_SETTINGS, coordinator.onPermissionRequired(false, false));
    assertEquals(ProviderAction.SHOW_APP_SETTINGS,
                 coordinator.onProviderUnavailable(false, false, false));
  }

  @Test
  public void repeatedCallbacksWhilePermissionRequestOutstandingDoNothing()
  {
    final LocationPromptCoordinator coordinator = new LocationPromptCoordinator();

    assertEquals(PermissionAction.REQUEST_PERMISSION, coordinator.onPermissionRequired(false, false));
    assertEquals(PermissionAction.NONE, coordinator.onPermissionRequired(false, false));
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

    assertEquals(PermissionAction.NONE, coordinator.onPermissionRequired(false, true));
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
  public void externallyGrantedPermissionClearsRememberedDenial()
  {
    final LocationPromptCoordinator coordinator = new LocationPromptCoordinator();

    assertEquals(PermissionAction.REQUEST_PERMISSION, coordinator.onPermissionRequired(false, false));
    coordinator.finishPermissionRequest(false, false);
    assertTrue(coordinator.isPermissionPermanentlyDenied());

    assertEquals(PermissionAction.NONE, coordinator.onPermissionRequired(true, false));
    assertFalse(coordinator.isPermissionPermanentlyDenied());
    assertFalse(coordinator.isPermissionRequestPending());
  }
}
