package app.organicmaps.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.TwoStatePreference;
import app.organicmaps.BuildConfig;
import app.organicmaps.R;
import app.organicmaps.sdk.util.Config;

/** Binds automotive-only settings without adding head-unit policy to general Settings logic. */
final class InCarSettingsPolicy
{
  private InCarSettingsPolicy() {}

  static void apply(@NonNull PreferenceFragmentCompat fragment)
  {
    bindAutoFollowOnLaunch(fragment);
    bindOptimisedVisuals(fragment);
  }

  private static void bindAutoFollowOnLaunch(@NonNull PreferenceFragmentCompat fragment)
  {
    @Nullable
    final Preference preference =
        fragment.findPreference(fragment.getString(R.string.pref_auto_follow_location_on_launch));
    if (preference == null)
      return;

    preference.setVisible(BuildConfig.IS_IN_CAR);
    if (!BuildConfig.IS_IN_CAR)
      return;

    final TwoStatePreference switchPreference = (TwoStatePreference) preference;
    switchPreference.setChecked(Config.isAutoStartLocationFollowAndRotateEnabled());
    switchPreference.setOnPreferenceChangeListener((pref, newValue) -> {
      Config.setAutoStartLocationFollowAndRotateEnabled((boolean) newValue);
      return true;
    });
  }

  private static void bindOptimisedVisuals(@NonNull PreferenceFragmentCompat fragment)
  {
    @Nullable
    final Preference preference =
        fragment.findPreference(fragment.getString(R.string.pref_in_car_optimised_visuals));
    if (preference == null)
      return;

    preference.setVisible(BuildConfig.IS_IN_CAR);
    if (!BuildConfig.IS_IN_CAR)
      return;

    final TwoStatePreference switchPreference = (TwoStatePreference) preference;
    switchPreference.setChecked(Config.isInCarOptimisedVisualsEnabled());
    switchPreference.setOnPreferenceChangeListener((pref, newValue) -> {
      Config.setInCarOptimisedVisualsEnabled((boolean) newValue);
      return true;
    });
  }
}
