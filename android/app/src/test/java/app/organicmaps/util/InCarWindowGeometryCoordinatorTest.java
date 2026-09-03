package app.organicmaps.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InCarWindowGeometryCoordinatorTest
{
  @Test
  public void zeroSizedStartupDoesNotAttemptRecovery()
  {
    final InCarWindowGeometryCoordinator.GeometryStatus status =
        InCarWindowGeometryCoordinator.evaluateGeometry(0, 0, 0, 0, 0, 0, 0, 0);
    assertFalse(status.expectedValid);
    assertEquals(InCarWindowGeometryCoordinator.RecoveryAction.NONE,
                 InCarWindowGeometryCoordinator.chooseRecoveryAction(status, false, false, false, false, true));
  }

  @Test
  public void fullyConvergedGeometryNeedsNoRecovery()
  {
    final InCarWindowGeometryCoordinator.GeometryStatus status =
        InCarWindowGeometryCoordinator.evaluateGeometry(542, 313, 542, 313, 542, 313, 542, 313);
    assertTrue(status.isConverged());
    assertEquals(InCarWindowGeometryCoordinator.RecoveryAction.NONE,
                 InCarWindowGeometryCoordinator.chooseRecoveryAction(status, false, false, false, false, true));
  }

  @Test
  public void mapMismatchRepairsLayoutFirst()
  {
    final InCarWindowGeometryCoordinator.GeometryStatus status =
        InCarWindowGeometryCoordinator.evaluateGeometry(1280, 720, 735, 387, 735, 387, 735, 387);
    assertTrue(status.mapMismatch);
    assertEquals(InCarWindowGeometryCoordinator.RecoveryAction.REQUEST_LAYOUT,
                 InCarWindowGeometryCoordinator.chooseRecoveryAction(status, false, false, false, false, true));
    assertEquals(InCarWindowGeometryCoordinator.RecoveryAction.NONE,
                 InCarWindowGeometryCoordinator.chooseRecoveryAction(status, true, false, false, false, true));
  }

  @Test
  public void staleSurfaceUsesLayoutDerivedSurfaceRepair()
  {
    final InCarWindowGeometryCoordinator.GeometryStatus status =
        InCarWindowGeometryCoordinator.evaluateGeometry(735, 387, 735, 387, 1280, 720, 1280, 720);
    assertTrue(status.surfaceMismatch);
    assertEquals(InCarWindowGeometryCoordinator.RecoveryAction.SURFACE_SIZE_FROM_LAYOUT,
                 InCarWindowGeometryCoordinator.chooseRecoveryAction(status, true, false, false, false, true));
  }

  @Test
  public void staleNativeViewportIsReappliedBeforeReattach()
  {
    final InCarWindowGeometryCoordinator.GeometryStatus status =
        InCarWindowGeometryCoordinator.evaluateGeometry(735, 387, 735, 387, 735, 387, 1280, 720);
    assertTrue(status.nativeMismatch);
    assertEquals(InCarWindowGeometryCoordinator.RecoveryAction.NATIVE_REAPPLY,
                 InCarWindowGeometryCoordinator.chooseRecoveryAction(status, true, true, false, false, true));
    assertEquals(InCarWindowGeometryCoordinator.RecoveryAction.SURFACE_REATTACH,
                 InCarWindowGeometryCoordinator.chooseRecoveryAction(status, true, true, true, false, true));
  }

  @Test
  public void surfaceReattachIsBoundedAndRequiresSafeHostState()
  {
    final InCarWindowGeometryCoordinator.GeometryStatus status =
        InCarWindowGeometryCoordinator.evaluateGeometry(735, 387, 735, 387, 735, 387, 1280, 720);
    assertEquals(InCarWindowGeometryCoordinator.RecoveryAction.NONE,
                 InCarWindowGeometryCoordinator.chooseRecoveryAction(status, true, true, true, false, false));
    assertEquals(InCarWindowGeometryCoordinator.RecoveryAction.NONE,
                 InCarWindowGeometryCoordinator.chooseRecoveryAction(status, true, true, true, true, true));
  }

  @Test
  public void vendorCompactGeometryDoesNotDependOnAndroidWindowModeFlags()
  {
    // Window-mode flags are intentionally absent from the epoch key. A DoFun-style compact task is real geometry
    // even when Android reports neither standard multi-window nor PiP.
    assertFalse(InCarWindowGeometryCoordinator.shouldStartNewGeometryEpoch(542, 313, 7, 11, 542, 313, 7, 11));
    assertTrue(InCarWindowGeometryCoordinator.shouldStartNewGeometryEpoch(542, 313, 7, 11, 1280, 720, 7, 11));
  }

  @Test
  public void taskAndActivityIdentityStartNewEpochs()
  {
    assertTrue(InCarWindowGeometryCoordinator.shouldStartNewGeometryEpoch(1280, 720, 7, 11, 1280, 720, 8, 11));
    assertTrue(InCarWindowGeometryCoordinator.shouldStartNewGeometryEpoch(1280, 720, 7, 11, 1280, 720, 7, 12));
  }

  @Test
  public void sameTaskNewIntentCanReuseStableGeometryEpoch()
  {
    assertFalse(InCarWindowGeometryCoordinator.shouldStartNewGeometryEpoch(1280, 720, 7, 11, 1280, 720, 7, 11));
  }

  @Test
  public void staleScheduledTransitionIsRejected()
  {
    assertTrue(InCarWindowGeometryCoordinator.isGenerationCurrent(12, 12));
    assertFalse(InCarWindowGeometryCoordinator.isGenerationCurrent(11, 12));
  }
}
