package app.organicmaps.settings;

import android.app.Activity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.TwoStatePreference;
import app.organicmaps.BuildConfig;
import app.organicmaps.MwmActivity;
import app.organicmaps.MwmApplication;
import app.organicmaps.R;
import app.organicmaps.incar.InCarBudgetRendering;
import app.organicmaps.incar.InCarDrivingUi;
import app.organicmaps.incar.InCarDrivingViewController;
import app.organicmaps.incar.InCarSettingsStore;
import app.organicmaps.sdk.util.Config;

/** Binds automotive-only settings without adding head-unit policy to general Settings logic. */
final class InCarSettingsPolicy
{
  private InCarSettingsPolicy() {}

  static void apply(@NonNull PreferenceFragmentCompat fragment)
  {
    bindRootEntry(fragment);
    bindAutoFollowOnLaunch(fragment);
    bindOptimisedVisuals(fragment);
    bindDrivingViewSettings(fragment);
    bindBudgetRendering(fragment);
    bindMapAgeWarning(fragment);
    applyGenericPreferenceGuards(fragment);
  }

  private static void bindRootEntry(@NonNull PreferenceFragmentCompat fragment)
  {
    @Nullable
    final Preference preference = fragment.findPreference(fragment.getString(R.string.pref_in_car_settings));
    if (preference != null)
      preference.setVisible(BuildConfig.IS_IN_CAR);
  }

  private static void bindAutoFollowOnLaunch(@NonNull PreferenceFragmentCompat fragment)
  {
    @Nullable
    final Preference preference =
        fragment.findPreference(fragment.getString(R.string.pref_auto_follow_location_on_launch));
    if (preference == null)
      return;

    final boolean show = BuildConfig.IS_IN_CAR && fragment instanceof InCarSettingsFragment;
    preference.setVisible(show);
    if (!show)
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
    final Preference preference = fragment.findPreference(fragment.getString(R.string.pref_in_car_optimised_visuals));
    if (preference == null)
      return;

    final boolean show = BuildConfig.IS_IN_CAR && fragment instanceof InCarSettingsFragment;
    preference.setVisible(show);
    if (!show)
      return;

    final TwoStatePreference switchPreference = (TwoStatePreference) preference;
    switchPreference.setChecked(Config.isInCarOptimisedVisualsEnabled());
    switchPreference.setOnPreferenceChangeListener((pref, newValue) -> {
      Config.setInCarOptimisedVisualsEnabled((boolean) newValue);
      notifyMapRuntime(fragment);
      return true;
    });
  }

  private static void bindDrivingViewSettings(@NonNull PreferenceFragmentCompat fragment)
  {
    if (!BuildConfig.IS_IN_CAR || !(fragment instanceof InCarSettingsFragment))
      return;

    bindBoolean(fragment, R.string.pref_in_car_show_driving_view_button,
                () -> InCarSettingsStore.showDrivingViewButton(fragment.requireContext()),
                enabled -> InCarSettingsStore.setShowDrivingViewButton(fragment.requireContext(), enabled));
    bindBoolean(fragment, R.string.pref_in_car_start_driving_view,
                () -> InCarSettingsStore.startDrivingViewOnLaunch(fragment.requireContext()),
                enabled -> InCarSettingsStore.setStartDrivingViewOnLaunch(fragment.requireContext(), enabled));
    bindBoolean(fragment, R.string.pref_in_car_automatic_driving_view,
                () -> InCarSettingsStore.automaticDrivingViewEnabled(fragment.requireContext()),
                enabled -> InCarSettingsStore.setAutomaticDrivingViewEnabled(fragment.requireContext(), enabled));
    bindBoolean(fragment, R.string.pref_in_car_auto_return_driving_view,
                () -> InCarSettingsStore.autoReturnDrivingViewEnabled(fragment.requireContext()),
                enabled -> InCarSettingsStore.setAutoReturnDrivingViewEnabled(fragment.requireContext(), enabled));
  }

  private static void bindBudgetRendering(@NonNull PreferenceFragmentCompat fragment)
  {
    @Nullable
    final Preference preference = fragment.findPreference(fragment.getString(R.string.pref_in_car_budget_rendering));
    if (preference == null)
      return;

    final boolean show = BuildConfig.IS_IN_CAR && fragment instanceof InCarSettingsFragment;
    preference.setVisible(show);
    if (!show)
      return;

    final TwoStatePreference switchPreference = (TwoStatePreference) preference;
    switchPreference.setChecked(InCarSettingsStore.budgetRenderingEnabled(fragment.requireContext()));
    switchPreference.setOnPreferenceChangeListener((pref, newValue) -> {
      final boolean enabled = (boolean) newValue;
      InCarSettingsStore.setBudgetRenderingEnabled(fragment.requireContext(), enabled);
      InCarBudgetRendering.apply(fragment.requireContext(), enabled);
      return true;
    });
  }

  private static void bindMapAgeWarning(@NonNull PreferenceFragmentCompat fragment)
  {
    @Nullable
    final Preference preference = fragment.findPreference(fragment.getString(R.string.pref_in_car_map_age_warning));
    if (preference == null)
      return;

    final boolean show = BuildConfig.IS_IN_CAR && fragment instanceof InCarSettingsFragment;
    preference.setVisible(show);
    if (!show)
      return;

    final TwoStatePreference switchPreference = (TwoStatePreference) preference;
    switchPreference.setChecked(InCarSettingsStore.mapAgeWarningEnabled(fragment.requireContext()));
    switchPreference.setOnPreferenceChangeListener((pref, newValue) -> {
      InCarSettingsStore.setMapAgeWarningEnabled(fragment.requireContext(), (boolean) newValue);
      return true;
    });
  }

  private static void applyGenericPreferenceGuards(@NonNull PreferenceFragmentCompat fragment)
  {
    if (!BuildConfig.IS_IN_CAR || fragment instanceof InCarSettingsFragment)
      return;

    @Nullable
    final Preference buildings = fragment.findPreference(fragment.getString(R.string.pref_3d_buildings));
    if (buildings != null && InCarSettingsStore.budgetRenderingEnabled(fragment.requireContext()))
    {
      buildings.setEnabled(false);
      buildings.setSummary(R.string.in_car_budget_rendering_summary);
    }
  }

  private interface BooleanReader
  {
    boolean get();
  }

  private interface BooleanWriter
  {
    void set(boolean value);
  }

  private static void bindBoolean(@NonNull PreferenceFragmentCompat fragment, int keyRes, @NonNull BooleanReader reader,
                                  @NonNull BooleanWriter writer)
  {
    @Nullable
    final Preference preference = fragment.findPreference(fragment.getString(keyRes));
    if (preference == null)
      return;

    final TwoStatePreference switchPreference = (TwoStatePreference) preference;
    switchPreference.setChecked(reader.get());
    switchPreference.setOnPreferenceChangeListener((pref, newValue) -> {
      writer.set((boolean) newValue);
      notifyMapRuntime(fragment);
      return true;
    });
  }

  private static void notifyMapRuntime(@NonNull PreferenceFragmentCompat fragment)
  {
    final MwmApplication app = MwmApplication.from(fragment.requireContext());
    final InCarDrivingViewController controller = app.getInCarDrivingViewController();
    if (controller != null)
      controller.onSettingsChanged();

    final Activity activity = app.getTopActivity();
    if (activity instanceof MwmActivity mapActivity)
      InCarDrivingUi.refresh(mapActivity);
  }
}
