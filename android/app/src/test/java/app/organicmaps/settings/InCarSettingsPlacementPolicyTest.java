package app.organicmaps.settings;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InCarSettingsPlacementPolicyTest
{
  @Test
  public void rootEntryOnlyAppearsForInCar()
  {
    assertTrue(InCarSettingsPlacementPolicy.showRootEntry(true));
    assertFalse(InCarSettingsPlacementPolicy.showRootEntry(false));
  }

  @Test
  public void headUnitPreferencesOnlyAppearOnDedicatedPage()
  {
    assertTrue(InCarSettingsPlacementPolicy.showDedicatedPreference(true, true));
    assertFalse(InCarSettingsPlacementPolicy.showDedicatedPreference(true, false));
    assertFalse(InCarSettingsPlacementPolicy.showDedicatedPreference(false, true));
    assertFalse(InCarSettingsPlacementPolicy.showDedicatedPreference(false, false));
  }
}
