package app.organicmaps.incar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InCarDrivingViewPolicyTest
{
  private static final long T0 = 10_000L;

  @Test
  public void manualEnableDisableWinsImmediately()
  {
    final InCarDrivingViewPolicy policy = offPolicy();
    assertEquals(InCarDrivingViewPolicy.Transition.ENABLE, policy.enableManually());
    assertTrue(policy.isEnabled());
    assertEquals(InCarDrivingViewPolicy.ActivationSource.MANUAL, policy.getActivationSource());

    assertEquals(InCarDrivingViewPolicy.Transition.DISABLE, policy.disableManually());
    assertFalse(policy.isEnabled());
    assertTrue(policy.isAutomaticRearmSuppressed());
  }

  @Test
  public void automaticEntryRequiresTwoSamplesStrictlyAboveThirtyKmh()
  {
    final InCarDrivingViewPolicy policy = offPolicy();
    final double exactlyThirty = InCarDrivingViewPolicy.AUTO_ENTER_SPEED_MPS;

    assertEquals(InCarDrivingViewPolicy.Transition.NONE, policy.onSpeedSample(true, true, exactlyThirty, T0, true));
    assertFalse(policy.isEnabled());

    assertEquals(InCarDrivingViewPolicy.Transition.NONE,
                 policy.onSpeedSample(true, true, exactlyThirty + 0.1, T0 + 200, true));
    assertEquals(InCarDrivingViewPolicy.Transition.ENABLE,
                 policy.onSpeedSample(true, true, exactlyThirty + 0.1, T0 + 400, true));
    assertTrue(policy.isEnabled());
    assertEquals(InCarDrivingViewPolicy.ActivationSource.AUTOMATIC, policy.getActivationSource());
  }

  @Test
  public void isolatedHighSpeedOutlierDoesNotEnable()
  {
    final InCarDrivingViewPolicy policy = offPolicy();
    final double high = InCarDrivingViewPolicy.AUTO_ENTER_SPEED_MPS + 1.0;
    assertEquals(InCarDrivingViewPolicy.Transition.NONE, policy.onSpeedSample(true, true, high, T0, true));
    assertEquals(InCarDrivingViewPolicy.Transition.NONE,
                 policy.onSpeedSample(true, true, InCarDrivingViewPolicy.AUTO_ENTER_SPEED_MPS, T0 + 200, true));
    assertEquals(InCarDrivingViewPolicy.Transition.NONE, policy.onSpeedSample(true, true, high, T0 + 400, true));
    assertFalse(policy.isEnabled());
  }

  @Test
  public void automaticSessionExitsOnlyAfterFiveContinuousMinutesBelowFiveKmh()
  {
    final InCarDrivingViewPolicy policy = automaticPolicy();
    final double low = InCarDrivingViewPolicy.AUTO_EXIT_SPEED_MPS - 0.1;

    assertEquals(InCarDrivingViewPolicy.Transition.NONE, policy.onSpeedSample(true, true, low, T0, true));
    assertEquals(InCarDrivingViewPolicy.Transition.NONE,
                 policy.onSpeedSample(true, true, low, T0 + InCarDrivingViewPolicy.LOW_SPEED_EXIT_MS - 1, true));
    assertTrue(policy.isEnabled());
    assertEquals(InCarDrivingViewPolicy.Transition.DISABLE,
                 policy.onSpeedSample(true, true, low, T0 + InCarDrivingViewPolicy.LOW_SPEED_EXIT_MS, true));
    assertFalse(policy.isEnabled());
  }

  @Test
  public void exactlyFiveKmhDoesNotStartOrCompleteExitTimer()
  {
    final InCarDrivingViewPolicy policy = automaticPolicy();
    final double low = InCarDrivingViewPolicy.AUTO_EXIT_SPEED_MPS - 0.1;

    policy.onSpeedSample(true, true, low, T0, true);
    policy.onSpeedSample(true, true, InCarDrivingViewPolicy.AUTO_EXIT_SPEED_MPS,
                         T0 + InCarDrivingViewPolicy.LOW_SPEED_EXIT_MS, true);
    assertEquals(InCarDrivingViewPolicy.Transition.NONE,
                 policy.onSpeedSample(true, true, low, T0 + InCarDrivingViewPolicy.LOW_SPEED_EXIT_MS + 1, true));
    assertTrue(policy.isEnabled());
  }

  @Test
  public void movingSampleResetsLowSpeedTimer()
  {
    final InCarDrivingViewPolicy policy = automaticPolicy();
    final double low = InCarDrivingViewPolicy.AUTO_EXIT_SPEED_MPS - 0.1;

    policy.onSpeedSample(true, true, low, T0, true);
    policy.onSpeedSample(true, true, InCarDrivingViewPolicy.AUTO_EXIT_SPEED_MPS + 0.1,
                         T0 + InCarDrivingViewPolicy.LOW_SPEED_EXIT_MS - 1, true);
    assertEquals(InCarDrivingViewPolicy.Transition.NONE,
                 policy.onSpeedSample(true, true, low, T0 + InCarDrivingViewPolicy.LOW_SPEED_EXIT_MS, true));
    assertTrue(policy.isEnabled());
  }

  @Test
  public void staleOrMissingSpeedCannotCompleteExit()
  {
    final InCarDrivingViewPolicy policy = automaticPolicy();
    final double low = InCarDrivingViewPolicy.AUTO_EXIT_SPEED_MPS - 0.1;

    policy.onSpeedSample(true, true, low, T0, true);
    policy.onSpeedSample(false, true, low, T0 + InCarDrivingViewPolicy.LOW_SPEED_EXIT_MS, true);
    assertEquals(InCarDrivingViewPolicy.Transition.NONE,
                 policy.onSpeedSample(true, true, low, T0 + InCarDrivingViewPolicy.LOW_SPEED_EXIT_MS + 1, true));
    assertTrue(policy.isEnabled());

    policy.onSpeedSample(true, false, -1.0, T0 + 2 * InCarDrivingViewPolicy.LOW_SPEED_EXIT_MS, true);
    assertTrue(policy.isEnabled());
  }

  @Test
  public void manualEnableIsNotAutoExitedAtLowSpeed()
  {
    final InCarDrivingViewPolicy policy = offPolicy();
    policy.enableManually();
    final double low = InCarDrivingViewPolicy.AUTO_EXIT_SPEED_MPS - 0.1;

    policy.onSpeedSample(true, true, low, T0, true);
    assertEquals(InCarDrivingViewPolicy.Transition.NONE,
                 policy.onSpeedSample(true, true, low, T0 + InCarDrivingViewPolicy.LOW_SPEED_EXIT_MS, true));
    assertTrue(policy.isEnabled());
  }

  @Test
  public void manualDisableRequiresLowSpeedRearmBeforeAutomaticEntry()
  {
    final InCarDrivingViewPolicy policy = offPolicy();
    policy.enableManually();
    policy.disableManually();
    final double high = InCarDrivingViewPolicy.AUTO_ENTER_SPEED_MPS + 1.0;
    final double low = InCarDrivingViewPolicy.AUTO_EXIT_SPEED_MPS - 0.1;

    policy.onSpeedSample(true, true, high, T0, true);
    policy.onSpeedSample(true, true, high, T0 + 200, true);
    assertFalse(policy.isEnabled());

    policy.onSpeedSample(true, true, low, T0 + 400, true);
    policy.onSpeedSample(true, true, low, T0 + 400 + InCarDrivingViewPolicy.LOW_SPEED_EXIT_MS, true);
    assertFalse(policy.isAutomaticRearmSuppressed());

    policy.onSpeedSample(true, true, high, T0 + 401 + InCarDrivingViewPolicy.LOW_SPEED_EXIT_MS, true);
    assertEquals(InCarDrivingViewPolicy.Transition.ENABLE,
                 policy.onSpeedSample(true, true, high, T0 + 601 + InCarDrivingViewPolicy.LOW_SPEED_EXIT_MS, true));
  }

  @Test
  public void newSessionClearsManualRearmSuppression()
  {
    final InCarDrivingViewPolicy policy = offPolicy();
    policy.disableManually();
    assertTrue(policy.isAutomaticRearmSuppressed());
    policy.beginNewSession();
    assertFalse(policy.isAutomaticRearmSuppressed());
  }

  @Test
  public void launchAndRestoredSessionsPreserveExplicitEnabledState()
  {
    final InCarDrivingViewPolicy policy = offPolicy();
    assertEquals(InCarDrivingViewPolicy.Transition.ENABLE, policy.enableFromLaunch());
    assertEquals(InCarDrivingViewPolicy.ActivationSource.LAUNCH, policy.getActivationSource());

    final InCarDrivingViewPolicy restored =
        new InCarDrivingViewPolicy(true, InCarDrivingViewPolicy.ActivationSource.RESTORED);
    assertTrue(restored.isEnabled());
    assertEquals(InCarDrivingViewPolicy.ActivationSource.RESTORED, restored.getActivationSource());
  }

  private static InCarDrivingViewPolicy offPolicy()
  {
    return new InCarDrivingViewPolicy(false, InCarDrivingViewPolicy.ActivationSource.OFF);
  }

  private static InCarDrivingViewPolicy automaticPolicy()
  {
    final InCarDrivingViewPolicy policy = offPolicy();
    final double high = InCarDrivingViewPolicy.AUTO_ENTER_SPEED_MPS + 1.0;
    policy.onSpeedSample(true, true, high, T0, true);
    policy.onSpeedSample(true, true, high, T0 + 200, true);
    return policy;
  }
}
