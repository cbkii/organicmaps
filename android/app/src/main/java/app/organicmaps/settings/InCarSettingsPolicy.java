package app.organicmaps.settings;

import android.app.Activity;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.preference.ListPreference;
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
import app.organicmaps.incar.InCarDrivingViewModePolicy;
import app.organicmaps.incar.InCarDrivingViewModePolicy.DrivingViewMode;
import app.organicmaps.incar.InCarSettingsStore;
import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.util.Config;
import app.organicmaps.sdk.util.PowerManagment;

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
    bindShowTrackRecordingButton(fragment);
    installGenericPreferenceGuardObserver(fragment);
  }

  private static void bindRootEntry(@NonNull PreferenceFragmentCompat fragment)
  {
    @Nullable
    final Preference preference = fragment.findPreference(fragment.getString(R.string.pref_in_car_settings));
    if (preference != null)
      preference.setVisible(InCarSettingsPlacementPolicy.showRootEntry(BuildConfig.IS_IN_CAR));
  }

  private static void bindAutoFollowOnLaunch(@NonNull PreferenceFragmentCompat fragment)
  {
    @Nullable
    final Preference preference =
        fragment.findPreference(fragment.getString(R.string.pref_auto_follow_location_on_launch));
    if (preference == null)
      return;

    final boolean show = showDedicatedPreference(fragment);
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

    final boolean show = showDedicatedPreference(fragment);
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
    if (!showDedicatedPreference(fragment))
      return;

    @Nullable
    final Preference modePreference =
        fragment.findPreference(fragment.getString(R.string.pref_in_car_driving_view_mode));
    if (modePreference instanceof ListPreference listPreference)
    {
      final DrivingViewMode mode = InCarDrivingViewModePolicy.getMode(fragment.requireContext());
      listPreference.setValue(mode.preferenceValue());
      listPreference.setOnPreferenceChangeListener((preference, newValue) -> {
        if (!(newValue instanceof String value))
          return false;
        final DrivingViewMode selected = DrivingViewMode.fromPreferenceValue(value);
        InCarDrivingViewModePolicy.setMode(fragment.requireContext(), selected);
        listPreference.setValue(selected.preferenceValue());
        notifyMapRuntime(fragment);
        return true;
      });
    }

    // Auto-return remains a specialist behaviour, but it is now visually separated from the
    // primary Off/Manual/Automatic mode selector rather than presented as another peer mode.
    bindBoolean(fragment, R.string.pref_in_car_auto_return_driving_view,
                ()
                    -> InCarSettingsStore.autoReturnDrivingViewEnabled(fragment.requireContext()),
                enabled -> InCarSettingsStore.setAutoReturnDrivingViewEnabled(fragment.requireContext(), enabled));
  }

  private static void bindBudgetRendering(@NonNull PreferenceFragmentCompat fragment)
  {
    @Nullable
    final Preference preference = fragment.findPreference(fragment.getString(R.string.pref_in_car_budget_rendering));
    if (preference == null)
      return;

    final boolean show = showDedicatedPreference(fragment);
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

    final boolean show = showDedicatedPreference(fragment);
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

  private static void bindShowTrackRecordingButton(@NonNull PreferenceFragmentCompat fragment)
  {
    @Nullable
    final Preference preference =
        fragment.findPreference(fragment.getString(R.string.pref_in_car_show_track_recording_button));
    if (preference == null)
      return;

    final boolean show = showDedicatedPreference(fragment);
    preference.setVisible(show);
    if (!show)
      return;

    final TwoStatePreference switchPreference = (TwoStatePreference) preference;
    switchPreference.setChecked(InCarSettingsStore.isShowTrackRecordingButton(fragment.requireContext()));
    switchPreference.setOnPreferenceChangeListener((pref, newValue) -> {
      InCarSettingsStore.setShowTrackRecordingButton(fragment.requireContext(), (boolean) newValue);
      return true;
    });
  }

  private static void installGenericPreferenceGuardObserver(@NonNull PreferenceFragmentCompat fragment)
  {
    if (!BuildConfig.IS_IN_CAR || fragment instanceof InCarSettingsFragment)
      return;

    fragment.getLifecycle().addObserver(new DefaultLifecycleObserver() {
      private boolean mListenersWrapped;

      @Override
      public void onResume(@NonNull LifecycleOwner owner)
      {
        if (!mListenersWrapped)
        {
          wrapGenericPreferenceListeners(fragment);
          mListenersWrapped = true;
        }
        applyGenericPreferenceGuards(fragment);
      }
    });
  }

  private static void wrapGenericPreferenceListeners(@NonNull PreferenceFragmentCompat fragment)
  {
    @Nullable
    final Preference buildings = fragment.findPreference(fragment.getString(R.string.pref_3d_buildings));
    if (buildings != null)
    {
      final Preference.OnPreferenceChangeListener delegate = buildings.getOnPreferenceChangeListener();
      buildings.setOnPreferenceChangeListener((preference, newValue) -> {
        if (InCarSettingsStore.budgetRenderingEnabled(fragment.requireContext()))
        {
          applyGenericPreferenceGuards(fragment);
          return false;
        }
        return delegate == null || delegate.onPreferenceChange(preference, newValue);
      });
    }

    @Nullable
    final Preference powerManagement = fragment.findPreference(fragment.getString(R.string.pref_power_management));
    if (powerManagement != null)
    {
      final Preference.OnPreferenceChangeListener delegate = powerManagement.getOnPreferenceChangeListener();
      powerManagement.setOnPreferenceChangeListener((preference, newValue) -> {
        final boolean accepted = delegate == null || delegate.onPreferenceChange(preference, newValue);
        if (accepted)
          fragment.getListView().post(() -> applyGenericPreferenceGuards(fragment));
        return accepted;
      });
    }
  }

  private static void applyGenericPreferenceGuards(@NonNull PreferenceFragmentCompat fragment)
  {
    if (!BuildConfig.IS_IN_CAR || fragment instanceof InCarSettingsFragment)
      return;

    @Nullable
    final Preference buildings = fragment.findPreference(fragment.getString(R.string.pref_3d_buildings));
    if (!(buildings instanceof TwoStatePreference buildingsSwitch))
      return;

    if (InCarSettingsStore.budgetRenderingEnabled(fragment.requireContext()))
    {
      buildingsSwitch.setShouldDisableView(true);
      buildingsSwitch.setEnabled(false);
      buildingsSwitch.setSummary(R.string.in_car_budget_rendering_summary);
      buildingsSwitch.setChecked(false);
      return;
    }

    if (!TextUtils.equals(buildingsSwitch.getSummary(), fragment.getString(R.string.in_car_budget_rendering_summary)))
      return;

    if (PowerManagment.getScheme() == PowerManagment.HIGH)
    {
      buildingsSwitch.setShouldDisableView(true);
      buildingsSwitch.setEnabled(false);
      buildingsSwitch.setSummary(R.string.pref_map_3d_buildings_disabled_summary);
      buildingsSwitch.setChecked(false);
      return;
    }

    final Framework.Params3dMode current = new Framework.Params3dMode();
    Framework.nativeGet3dMode(current);
    buildingsSwitch.setShouldDisableView(false);
    buildingsSwitch.setEnabled(true);
    buildingsSwitch.setSummary("");
    buildingsSwitch.setChecked(current.buildings);
  }

  private static boolean showDedicatedPreference(@NonNull PreferenceFragmentCompat fragment)
  {
    return InCarSettingsPlacementPolicy.showDedicatedPreference(BuildConfig.IS_IN_CAR,
                                                                fragment instanceof InCarSettingsFragment);
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
