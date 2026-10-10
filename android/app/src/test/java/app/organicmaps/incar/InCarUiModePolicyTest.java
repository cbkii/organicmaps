package app.organicmaps.incar;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.res.Configuration;
import org.junit.Test;

public class InCarUiModePolicyTest
{
  private static final int DAY = Configuration.UI_MODE_TYPE_NORMAL | Configuration.UI_MODE_NIGHT_NO;
  private static final int NIGHT = Configuration.UI_MODE_TYPE_NORMAL | Configuration.UI_MODE_NIGHT_YES;
  private static final int CAR_DAY = Configuration.UI_MODE_TYPE_CAR | Configuration.UI_MODE_NIGHT_NO;

  @Test
  public void unchangedUiModeLeavesGeometryRecoveryInCharge()
  {
    assertFalse(InCarUiModePolicy.shouldRecreate(DAY, DAY));
    assertFalse(InCarUiModePolicy.shouldRecreate(NIGHT, NIGHT));
    assertFalse(InCarUiModePolicy.shouldRecreate(CAR_DAY, CAR_DAY));
  }

  @Test
  public void themeChangesStillRecreate()
  {
    assertTrue(InCarUiModePolicy.shouldRecreate(DAY, NIGHT));
    assertTrue(InCarUiModePolicy.shouldRecreate(NIGHT, DAY));
    assertTrue(
        InCarUiModePolicy.shouldRecreate(CAR_DAY, Configuration.UI_MODE_TYPE_CAR | Configuration.UI_MODE_NIGHT_YES));
    assertTrue(InCarUiModePolicy.shouldRecreate(CAR_DAY, NIGHT));
  }

  @Test
  public void carModeOnlyTransitionPreservesExistingMap()
  {
    assertFalse(InCarUiModePolicy.shouldRecreate(DAY, CAR_DAY));
    assertFalse(InCarUiModePolicy.shouldRecreate(CAR_DAY, DAY));
  }

  @Test
  public void otherUiModeTypeChangesStillRecreate()
  {
    assertTrue(InCarUiModePolicy.shouldRecreate(DAY, Configuration.UI_MODE_TYPE_DESK | Configuration.UI_MODE_NIGHT_NO));
  }
}
