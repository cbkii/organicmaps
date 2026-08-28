package app.organicmaps.incar;

import static org.junit.Assert.assertEquals;

import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class InCarDrivingViewModePolicyTest
{
  private SharedPreferences mPrefs;

  @Before
  public void setUp()
  {
    mPrefs = RuntimeEnvironment.getApplication().getSharedPreferences("test_prefs",
                                                                      android.content.Context.MODE_PRIVATE);
    mPrefs.edit().clear().apply();
  }

  // --- Migration from legacy keys ---

  @Test
  public void migrationAutoTrueYieldsAutomatic()
  {
    mPrefs.edit().putBoolean(InCarDrivingViewModePolicy.LEGACY_KEY_AUTO_DRIVING_VIEW, true).apply();
    assertEquals(InCarDrivingViewModePolicy.DrivingViewMode.AUTOMATIC,
                 InCarDrivingViewModePolicy.migrateFromLegacy(mPrefs));
  }

  @Test
  public void migrationAutoFalseShowButtonTrueYieldsManual()
  {
    mPrefs.edit()
          .putBoolean(InCarDrivingViewModePolicy.LEGACY_KEY_AUTO_DRIVING_VIEW, false)
          .putBoolean(InCarDrivingViewModePolicy.LEGACY_KEY_SHOW_BUTTON, true)
          .apply();
    assertEquals(InCarDrivingViewModePolicy.DrivingViewMode.MANUAL,
                 InCarDrivingViewModePolicy.migrateFromLegacy(mPrefs));
  }

  @Test
  public void migrationAutoFalseShowButtonFalseYieldsOff()
  {
    mPrefs.edit()
          .putBoolean(InCarDrivingViewModePolicy.LEGACY_KEY_AUTO_DRIVING_VIEW, false)
          .putBoolean(InCarDrivingViewModePolicy.LEGACY_KEY_SHOW_BUTTON, false)
          .apply();
    assertEquals(InCarDrivingViewModePolicy.DrivingViewMode.OFF,
                 InCarDrivingViewModePolicy.migrateFromLegacy(mPrefs));
  }

  @Test
  public void migrationDefaultsToManualWhenNoLegacyKeys()
  {
    // No legacy keys set — default show_button=true, auto=false → MANUAL
    assertEquals(InCarDrivingViewModePolicy.DrivingViewMode.MANUAL,
                 InCarDrivingViewModePolicy.migrateFromLegacy(mPrefs));
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
  public void unknownPreferenceValueDefaultsToManual()
  {
    assertEquals(InCarDrivingViewModePolicy.DrivingViewMode.MANUAL,
                 InCarDrivingViewModePolicy.DrivingViewMode.fromPreferenceValue("UNKNOWN_VALUE"));
  }
}
