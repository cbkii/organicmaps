package app.organicmaps.incar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InCarDrivingViewLifecycleTest
{
  @Test
  public void coldStartupDoesNotAttachOrAccessNativeState()
  {
    final InCarDrivingViewLifecycle lifecycle = new InCarDrivingViewLifecycle();
    assertFalse(lifecycle.isAttached());
    assertFalse(lifecycle.canAccessNativeState());
  }

  @Test
  public void frameworkAndActivityAttachExactlyOnce()
  {
    final InCarDrivingViewLifecycle lifecycle = new InCarDrivingViewLifecycle();
    assertEquals(InCarDrivingViewLifecycle.Transition.NONE, lifecycle.onFrameworkReady());
    assertEquals(InCarDrivingViewLifecycle.Transition.NONE, lifecycle.onFrameworkReady());
    assertEquals(InCarDrivingViewLifecycle.Transition.ATTACH, lifecycle.onMapActivityStarted());
    assertEquals(InCarDrivingViewLifecycle.Transition.NONE, lifecycle.onMapActivityStarted());
    assertTrue(lifecycle.isAttached());
  }

  @Test
  public void activityStartedBeforeFrameworkAttachesWhenFrameworkBecomesReady()
  {
    final InCarDrivingViewLifecycle lifecycle = new InCarDrivingViewLifecycle();
    assertEquals(InCarDrivingViewLifecycle.Transition.NONE, lifecycle.onMapActivityStarted());
    assertFalse(lifecycle.isAttached());
    assertEquals(InCarDrivingViewLifecycle.Transition.ATTACH, lifecycle.onFrameworkReady());
    assertTrue(lifecycle.isAttached());
    assertFalse(lifecycle.canAccessNativeState());
    lifecycle.onRenderingCreated();
    assertTrue(lifecycle.canAccessNativeState());
  }

  @Test
  public void multipleStartedActivitiesDetachOnlyAfterLastStop()
  {
    final InCarDrivingViewLifecycle lifecycle = new InCarDrivingViewLifecycle();
    lifecycle.onFrameworkReady();
    assertEquals(InCarDrivingViewLifecycle.Transition.ATTACH, lifecycle.onMapActivityStarted());
    assertEquals(InCarDrivingViewLifecycle.Transition.NONE, lifecycle.onMapActivityStarted());
    assertEquals(InCarDrivingViewLifecycle.Transition.NONE, lifecycle.onMapActivityStopped());
    assertTrue(lifecycle.isAttached());
    assertEquals(InCarDrivingViewLifecycle.Transition.DETACH, lifecycle.onMapActivityStopped());
    assertFalse(lifecycle.isAttached());
  }

  @Test
  public void renderingAttachAndDetachGateNativeAccess()
  {
    final InCarDrivingViewLifecycle lifecycle = new InCarDrivingViewLifecycle();
    lifecycle.onFrameworkReady();
    lifecycle.onMapActivityStarted();
    assertFalse(lifecycle.canAccessNativeState());
    lifecycle.onRenderingCreated();
    assertTrue(lifecycle.canAccessNativeState());
    lifecycle.onRenderingDetached();
    assertFalse(lifecycle.canAccessNativeState());
  }

  @Test
  public void frameworkDetachAndReattachAreIdempotent()
  {
    final InCarDrivingViewLifecycle lifecycle = new InCarDrivingViewLifecycle();
    lifecycle.onFrameworkReady();
    lifecycle.onMapActivityStarted();
    lifecycle.onRenderingCreated();
    assertEquals(InCarDrivingViewLifecycle.Transition.DETACH, lifecycle.onFrameworkDetached());
    assertEquals(InCarDrivingViewLifecycle.Transition.NONE, lifecycle.onFrameworkDetached());
    assertFalse(lifecycle.canAccessNativeState());
    assertEquals(InCarDrivingViewLifecycle.Transition.ATTACH, lifecycle.onFrameworkReady());
    lifecycle.onRenderingCreated();
    assertTrue(lifecycle.canAccessNativeState());
  }

  @Test
  public void lastActivityStopDetachesAndFreshProcessStartsCold()
  {
    final InCarDrivingViewLifecycle lifecycle = new InCarDrivingViewLifecycle();
    lifecycle.onFrameworkReady();
    lifecycle.onMapActivityStarted();
    lifecycle.onRenderingCreated();
    assertEquals(InCarDrivingViewLifecycle.Transition.DETACH, lifecycle.onMapActivityStopped());
    assertFalse(lifecycle.isAttached());
    assertFalse(lifecycle.canAccessNativeState());
    final InCarDrivingViewLifecycle relaunched = new InCarDrivingViewLifecycle();
    assertFalse(relaunched.isAttached());
    assertFalse(relaunched.canAccessNativeState());
  }
}
