package app.organicmaps.incar;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.SharedPreferences;
import org.junit.Test;

public class InCarDrivingViewModePolicyTest
{
  // --- Migration from legacy keys ---

  @Test
  public void migrationAutoTrueYieldsAutomatic()
  {
    final SharedPreferences prefs = mock(SharedPreferences.class);
    when(prefs.getBoolean(InCarDrivingViewModePolicy.LEGACY_KEY_AUTO_DRIVING_VIEW, false)).thenReturn(true);

    assertEquals(InCarDrivingViewModePolicy.DrivingViewMode.AUTOMATIC,
                 InCarDrivingViewModePolicy.migrateFromLegacy(prefs));
  }

  @Test
  public void migrationLegacyManualButtonYieldsAutomatic()
  {
    final SharedPreferences prefs = mock(SharedPreferences.class);
    when(prefs.getBoolean(InCarDrivingViewModePolicy.LEGACY_KEY_AUTO_DRIVING_VIEW, false)).thenReturn(false);
    when(prefs.getBoolean(InCarDrivingViewModePolicy.LEGACY_KEY_SHOW_BUTTON, true)).thenReturn(true);

    assertEquals(InCarDrivingViewModePolicy.DrivingViewMode.AUTOMATIC,
                 InCarDrivingViewModePolicy.migrateFromLegacy(prefs));
  }

  @Test
  public void migrationAutoFalseShowButtonFalseYieldsOff()
  {
    final SharedPreferences prefs = mock(SharedPreferences.class);
    when(prefs.getBoolean(InCarDrivingViewModePolicy.LEGACY_KEY_AUTO_DRIVING_VIEW, false)).thenReturn(false);
    when(prefs.getBoolean(InCarDrivingViewModePolicy.LEGACY_KEY_SHOW_BUTTON, true)).thenReturn(false);

    assertEquals(InCarDrivingViewModePolicy.DrivingViewMode.OFF, InCarDrivingViewModePolicy.migrateFromLegacy(prefs));
  }

  @Test
  public void migrationDefaultsToAutomaticWhenNoLegacyKeys()
  {
    final SharedPreferences prefs = mock(SharedPreferences.class);
    when(prefs.getBoolean(InCarDrivingViewModePolicy.LEGACY_KEY_AUTO_DRIVING_VIEW, false)).thenReturn(false);
    when(prefs.getBoolean(InCarDrivingViewModePolicy.LEGACY_KEY_SHOW_BUTTON, true)).thenReturn(true);

    assertEquals(InCarDrivingViewModePolicy.DrivingViewMode.AUTOMATIC,
                 InCarDrivingViewModePolicy.migrateFromLegacy(prefs));
  }

  @Test
  public void legacyManualNormalizesToAutomatic()
  {
    assertEquals(InCarDrivingViewModePolicy.DrivingViewMode.AUTOMATIC,
                 InCarDrivingViewModePolicy.normalize(InCarDrivingViewModePolicy.DrivingViewMode.MANUAL));
  }

  // --- Preference value roundtrip ---

  @Test
  public void preferenceValueRoundtrip()
  {
    for (final InCarDrivingViewModePolicy.DrivingViewMode mode : InCarDrivingViewModePolicy.DrivingViewMode.values())
    {
      assertEquals(mode, InCarDrivingViewModePolicy.DrivingViewMode.fromPreferenceValue(mode.preferenceValue()));
    }
  }

  @Test
  public void unknownPreferenceValueDefaultsToAutomatic()
  {
    assertEquals(InCarDrivingViewModePolicy.DrivingViewMode.AUTOMATIC,
                 InCarDrivingViewModePolicy.DrivingViewMode.fromPreferenceValue("UNKNOWN_VALUE"));
  }
}
