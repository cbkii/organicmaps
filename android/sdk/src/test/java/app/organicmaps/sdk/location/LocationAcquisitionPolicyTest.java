package app.organicmaps.sdk.location;

import static org.junit.Assert.*;
import org.junit.Test;

public class LocationAcquisitionPolicyTest
{
  @Test public void poorReceptionNeverRestartsRegisteredReceiverEvenAfterTenMinutes()
  {
    final LocationAcquisitionPolicy policy = new LocationAcquisitionPolicy();
    policy.start(0);
    for (long time = 0; time <= 1_200_000; time += 5_000)
      assertFalse(policy.shouldRecover(time, true, true, true));
    assertEquals(0, policy.getRecoveries());
  }

  @Test public void failedGpsRegistrationRetriesEvenIfNetworkRegisteredWithBoundedBackoff()
  {
    final LocationAcquisitionPolicy policy = new LocationAcquisitionPolicy();
    policy.start(0);
    assertFalse(policy.shouldRecover(29_999, true, true, false));
    assertTrue(policy.shouldRecover(30_000, true, true, false));
    policy.onRecovery(30_000);
    assertFalse(policy.shouldRecover(89_999, false, true, false));
    assertTrue(policy.shouldRecover(90_000, false, true, false));
    policy.onRecovery(90_000);
    assertFalse(policy.canRetry(209_999));
    assertTrue(policy.canRetry(210_000));
    policy.onRecovery(210_000);
    assertFalse(policy.canRetry(509_999));
    assertTrue(policy.canRetry(510_000));
    policy.onRecovery(510_000);
    assertFalse(policy.canRetry(809_999));
    assertTrue(policy.canRetry(810_000));
  }

  @Test public void coarseOnlyDoesNotExpectGpsAndLiveProgressCancelsStoppedEvidence()
  {
    final LocationAcquisitionPolicy policy = new LocationAcquisitionPolicy();
    policy.start(0);
    assertFalse(policy.shouldRecover(60_000, true, false, false));
    policy.onGnssStopped(40_000);
    assertFalse(policy.shouldRecover(129_999, true, true, true));
    assertTrue(policy.shouldRecover(130_000, true, true, true));
    policy.onGnssProgress();
    assertFalse(policy.shouldRecover(200_000, true, true, true));
  }

  @Test public void resumedSessionResetsCooldownAndStoppedEvidence()
  {
    final LocationAcquisitionPolicy policy = new LocationAcquisitionPolicy();
    policy.start(0);
    policy.onRecovery(30_000);
    policy.onGnssStopped(31_000);
    policy.start(1_000_000);
    assertEquals(0, policy.getRecoveries());
    assertFalse(policy.shouldRecover(1_030_000, true, true, true));
    assertTrue(policy.shouldRecover(1_030_000, false, true, false));
  }

  @Test public void cacheAndLiveAgeValidityCannotConfuseStaleOrInvalidPositionWithCurrentFix()
  {
    final long now = 200_000_000_000L;
    assertTrue(LocationAcquisitionPolicy.isUsableObservation(now, now - 30_000_000_000L, -35, 149, 0, 30_000));
    assertFalse(LocationAcquisitionPolicy.isUsableObservation(now, now - 30_001_000_000L, -35, 149, 5, 30_000));
    assertTrue(LocationAcquisitionPolicy.isUsableObservation(now, now - 60_000_000_000L, -35, 149, 5, 120_000));
    assertFalse(LocationAcquisitionPolicy.isUsableObservation(now, 0, -35, 149, 5, 120_000));
    assertFalse(LocationAcquisitionPolicy.isUsableObservation(now, now + 1, -35, 149, 5, 30_000));
    assertFalse(LocationAcquisitionPolicy.isUsableObservation(now, now, Double.NaN, 149, 5, 30_000));
    assertFalse(LocationAcquisitionPolicy.isUsableObservation(now, now, 91, 149, 5, 30_000));
    assertFalse(LocationAcquisitionPolicy.isUsableObservation(now, now, -35, 181, 5, 30_000));
    assertFalse(LocationAcquisitionPolicy.isUsableObservation(now, now, -35, 149, Float.NaN, 30_000));
    assertFalse(LocationAcquisitionPolicy.isUsableObservation(now, now, -35, 149, -1, 30_000));
  }
}
