package app.organicmaps.location;

import static org.junit.Assert.assertEquals;

import app.organicmaps.location.InCarLocationRecoveryController.Action;
import org.junit.Test;

public class InCarLocationRecoveryControllerTest
{
  @Test
  public void transientDisabledStateRetriesThenRestoresWithoutWarning()
  {
    final InCarLocationRecoveryController controller = new InCarLocationRecoveryController();
    final int generation = controller.beginForeground();

    assertEquals(Action.RETRY, controller.evaluate(generation, 1_000L, true, false, false));
    assertEquals(1_500L, controller.nextRetryDelayMs(1_000L));
    assertEquals(Action.RETRY, controller.evaluate(generation, 2_500L, true, false, false));
    assertEquals(Action.RESTORE_LOCATION, controller.evaluate(generation, 4_000L, true, true, false));
  }

  @Test
  public void persistentDisabledStateIsSilentWhenWarningDisabled()
  {
    final InCarLocationRecoveryController controller = new InCarLocationRecoveryController();
    final int generation = controller.beginForeground();

    assertEquals(Action.RETRY, controller.evaluate(generation, 5_000L, true, false, false));
    assertEquals(Action.SUPPRESS_WARNING,
                 controller.evaluate(generation, 5_000L + InCarLocationRecoveryController.GRACE_PERIOD_MS, true, false,
                                     false));
    assertEquals(Action.SUPPRESS_WARNING,
                 controller.evaluate(generation, 20_000L, true, false, false));
  }

  @Test
  public void persistentDisabledStateWarnsAtMostOnceWhenEnabled()
  {
    final InCarLocationRecoveryController controller = new InCarLocationRecoveryController();
    final int generation = controller.beginForeground();

    assertEquals(Action.RETRY, controller.evaluate(generation, 0L, true, false, true));
    assertEquals(Action.SHOW_WARNING,
                 controller.evaluate(generation, InCarLocationRecoveryController.GRACE_PERIOD_MS, true, false, true));
    assertEquals(Action.SUPPRESS_WARNING,
                 controller.evaluate(generation, InCarLocationRecoveryController.GRACE_PERIOD_MS + 1L, true, false,
                                     true));
  }

  @Test
  public void missingPermissionNeverWaitsForProviderGrace()
  {
    final InCarLocationRecoveryController controller = new InCarLocationRecoveryController();
    final int generation = controller.beginForeground();

    assertEquals(Action.REQUEST_PERMISSION, controller.evaluate(generation, 0L, false, false, false));
  }

  @Test
  public void retryDelayIsClampedToRemainingGracePeriod()
  {
    final InCarLocationRecoveryController controller = new InCarLocationRecoveryController();
    final int generation = controller.beginForeground();

    assertEquals(Action.RETRY, controller.evaluate(generation, 100L, true, false, false));
    assertEquals(500L, controller.nextRetryDelayMs(9_600L));
  }

  @Test
  public void stoppedLifecycleInvalidatesScheduledGeneration()
  {
    final InCarLocationRecoveryController controller = new InCarLocationRecoveryController();
    final int generation = controller.beginForeground();
    assertEquals(Action.RETRY, controller.evaluate(generation, 0L, true, false, false));

    controller.endForeground();

    assertEquals(Action.STALE, controller.evaluate(generation, 1_500L, true, true, false));
  }

  @Test
  public void newForegroundGenerationRejectsOldScheduledWork()
  {
    final InCarLocationRecoveryController controller = new InCarLocationRecoveryController();
    final int oldGeneration = controller.beginForeground();
    assertEquals(Action.RETRY, controller.evaluate(oldGeneration, 0L, true, false, false));

    final int newGeneration = controller.beginForeground();

    assertEquals(Action.STALE, controller.evaluate(oldGeneration, 1_500L, true, true, false));
    assertEquals(Action.RETRY, controller.evaluate(newGeneration, 1_500L, true, false, false));
  }

  @Test
  public void deliveredLocationFixResetsPendingFalsePositiveSequence()
  {
    final InCarLocationRecoveryController controller = new InCarLocationRecoveryController();
    final int generation = controller.beginForeground();
    assertEquals(Action.RETRY, controller.evaluate(generation, 0L, true, false, true));

    controller.confirmLocationAvailable(generation);

    assertEquals(Action.RETRY,
                 controller.evaluate(generation, InCarLocationRecoveryController.GRACE_PERIOD_MS, true, false, true));
  }
}
