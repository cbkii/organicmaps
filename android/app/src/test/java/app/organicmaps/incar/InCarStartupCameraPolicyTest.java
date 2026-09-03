package app.organicmaps.incar;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import app.organicmaps.incar.InCarStartupCameraStore.StartupMapView;
import org.junit.Test;

public class InCarStartupCameraPolicyTest
{
  @Test
  public void plainLauncherRequiresMainLauncherWithoutPayloadOrHistory()
  {
    assertTrue(InCarStartupCameraPolicy.isPlainLauncherIntent(true, true, false, false, false, false));
    assertFalse(InCarStartupCameraPolicy.isPlainLauncherIntent(false, true, false, false, false, false));
    assertFalse(InCarStartupCameraPolicy.isPlainLauncherIntent(true, false, false, false, false, false));
    assertFalse(InCarStartupCameraPolicy.isPlainLauncherIntent(true, true, true, false, false, false));
    assertFalse(InCarStartupCameraPolicy.isPlainLauncherIntent(true, true, false, true, false, false));
    assertFalse(InCarStartupCameraPolicy.isPlainLauncherIntent(true, true, false, false, true, false));
    assertFalse(InCarStartupCameraPolicy.isPlainLauncherIntent(true, true, false, false, false, true));
  }

  @Test
  public void anyRoutingStateKeepsCameraAuthority()
  {
    assertFalse(InCarStartupCameraPolicy.hasRoutingCameraAuthority(false, false, false, false, false));
    assertTrue(InCarStartupCameraPolicy.hasRoutingCameraAuthority(true, false, false, false, false));
    assertTrue(InCarStartupCameraPolicy.hasRoutingCameraAuthority(false, true, false, false, false));
    assertTrue(InCarStartupCameraPolicy.hasRoutingCameraAuthority(false, false, true, false, false));
    assertTrue(InCarStartupCameraPolicy.hasRoutingCameraAuthority(false, false, false, true, false));
    assertTrue(InCarStartupCameraPolicy.hasRoutingCameraAuthority(false, false, false, false, true));
  }

  @Test
  public void drivingAreaNeedsAutoFollowAnchorAndNoRouteAuthority()
  {
    assertTrue(InCarStartupCameraPolicy.shouldShowDrivingArea(true, StartupMapView.DRIVING_AREA, true, false));
    assertFalse(InCarStartupCameraPolicy.shouldShowDrivingArea(false, StartupMapView.DRIVING_AREA, true, false));
    assertFalse(InCarStartupCameraPolicy.shouldShowDrivingArea(true, StartupMapView.LAST_MAP_VIEW, true, false));
    assertFalse(InCarStartupCameraPolicy.shouldShowDrivingArea(true, StartupMapView.DRIVING_AREA, false, false));
    assertFalse(InCarStartupCameraPolicy.shouldShowDrivingArea(true, StartupMapView.DRIVING_AREA, true, true));
  }

  @Test
  public void followRequestRespectsSettingAndRoutingAuthority()
  {
    assertTrue(InCarStartupCameraPolicy.shouldRequestFollowAndRotate(true, false));
    assertFalse(InCarStartupCameraPolicy.shouldRequestFollowAndRotate(false, false));
    assertFalse(InCarStartupCameraPolicy.shouldRequestFollowAndRotate(true, true));
  }
}
