package app.organicmaps;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LocationFeatureStopPolicyTest
{
  @Test
  public void backgroundStopRechecksOtherActiveLocationConsumers()
  {
    assertTrue(LocationFeatureStopPolicy.shouldReassessBackground(false));
    assertFalse(LocationFeatureStopPolicy.shouldReassessBackground(true));
    assertFalse(LocationFeatureStopPolicy.shouldAdjustForegroundRate(false, false, true, true, true, false));
  }

  @Test
  public void foregroundStopOnlyAdjustsRunningProviderInResumedActivity()
  {
    assertTrue(LocationFeatureStopPolicy.shouldAdjustForegroundRate(true, true, true, true, true, false));
    assertFalse(LocationFeatureStopPolicy.shouldAdjustForegroundRate(true, false, true, true, true, false));
    assertFalse(LocationFeatureStopPolicy.shouldAdjustForegroundRate(true, true, false, true, true, false));
    assertFalse(LocationFeatureStopPolicy.shouldAdjustForegroundRate(true, true, true, false, true, false));
    assertFalse(LocationFeatureStopPolicy.shouldAdjustForegroundRate(true, true, true, true, false, false));
    assertFalse(LocationFeatureStopPolicy.shouldAdjustForegroundRate(true, true, true, true, true, true));
  }
}
