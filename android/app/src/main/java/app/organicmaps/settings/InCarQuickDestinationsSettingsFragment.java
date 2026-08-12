package app.organicmaps.settings;

import android.app.AlertDialog;
import android.location.Location;
import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.Preference;
import androidx.preference.TwoStatePreference;
import app.organicmaps.MwmApplication;
import app.organicmaps.R;
import app.organicmaps.incar.InCarQuickDestination;
import app.organicmaps.incar.InCarQuickDestinationsStore;

/** Dedicated InCar configuration for Quick Destinations. */
public final class InCarQuickDestinationsSettingsFragment extends BaseXmlSettingsFragment
{
  @Override
  protected int getXmlResources()
  {
    return R.xml.prefs_in_car_quick_destinations;
  }

  @Override
  public void onCreatePreferences(@Nullable Bundle bundle, @Nullable String rootKey)
  {
    super.onCreatePreferences(bundle, rootKey);

    bindAction(InCarQuickDestinationsStore.Action.FUEL_CHARGING);
    bindAction(InCarQuickDestinationsStore.Action.PARKING);
    bindAction(InCarQuickDestinationsStore.Action.TOILETS);
    bindAction(InCarQuickDestinationsStore.Action.FOOD);
    bindAction(InCarQuickDestinationsStore.Action.REST_WATER);
    bindAction(InCarQuickDestinationsStore.Action.HOME);
    bindAction(InCarQuickDestinationsStore.Action.WORK);
    bindAction(InCarQuickDestinationsStore.Action.RECENT_1);
    bindAction(InCarQuickDestinationsStore.Action.RECENT_2);

    bindDestinationConfig(R.string.pref_in_car_quick_home_config, R.string.in_car_quick_home,
                          InCarQuickDestinationsStore.getHome(requireContext()), true);
    bindDestinationConfig(R.string.pref_in_car_quick_work_config, R.string.in_car_quick_work,
                          InCarQuickDestinationsStore.getWork(requireContext()), false);
    refreshDynamicToggles();
  }

  @Override
  public void onResume()
  {
    super.onResume();
    refreshDynamicToggles();
  }

  private void bindAction(@NonNull InCarQuickDestinationsStore.Action action)
  {
    final Preference preference = findPreference(InCarQuickDestinationsStore.preferenceKey(action));
    if (!(preference instanceof TwoStatePreference toggle))
      return;

    toggle.setChecked(InCarQuickDestinationsStore.isActionEnabled(requireContext(), action));
    toggle.setOnPreferenceChangeListener((pref, newValue) -> {
      InCarQuickDestinationsStore.setActionEnabled(requireContext(), action, (boolean) newValue);
      return true;
    });
  }

  private void bindDestinationConfig(@StringRes int keyRes, @StringRes int labelRes,
                                     @Nullable InCarQuickDestination initial, boolean home)
  {
    final Preference preference = findPreference(getString(keyRes));
    if (preference == null)
      return;

    updateDestinationSummary(preference, initial);
    preference.setOnPreferenceClickListener(pref -> {
      showDestinationDialog(pref, labelRes, home);
      return true;
    });
  }

  private void showDestinationDialog(@NonNull Preference preference, @StringRes int labelRes, boolean home)
  {
    final InCarQuickDestination current = home ? InCarQuickDestinationsStore.getHome(requireContext())
                                               : InCarQuickDestinationsStore.getWork(requireContext());
    final String[] items = current == null ? new String[] {getString(R.string.in_car_quick_use_current_location)}
                                           : new String[] {getString(R.string.in_car_quick_use_current_location),
                                                           getString(R.string.in_car_quick_clear_destination)};

    new AlertDialog.Builder(requireContext())
        .setTitle(labelRes)
        .setItems(
            items,
            (dialog, which) -> {
              if (which == 1 && current != null)
              {
                saveDestination(home, null);
                updateDestinationSummary(preference, null);
                refreshDynamicToggles();
                return;
              }

              final Location location = MwmApplication.from(requireContext()).getLocationHelper().getSavedLocation();
              final InCarQuickDestination destination =
                  InCarQuickDestination.fromLocation(getString(labelRes), location);
              if (destination == null)
              {
                Toast.makeText(requireContext(), R.string.in_car_quick_current_location_unavailable, Toast.LENGTH_SHORT)
                    .show();
                return;
              }
              saveDestination(home, destination);
              updateDestinationSummary(preference, destination);
              refreshDynamicToggles();
            })
        .show();
  }

  private void saveDestination(boolean home, @Nullable InCarQuickDestination destination)
  {
    if (home)
      InCarQuickDestinationsStore.setHome(requireContext(), destination);
    else
      InCarQuickDestinationsStore.setWork(requireContext(), destination);
  }

  private void updateDestinationSummary(@NonNull Preference preference, @Nullable InCarQuickDestination destination)
  {
    preference.setSummary(destination == null ? R.string.in_car_quick_not_configured
                                              : R.string.in_car_quick_configured);
  }

  private void refreshDynamicToggles()
  {
    refreshDynamicToggle(InCarQuickDestinationsStore.Action.HOME,
                         InCarQuickDestinationsStore.getHome(requireContext()) != null);
    refreshDynamicToggle(InCarQuickDestinationsStore.Action.WORK,
                         InCarQuickDestinationsStore.getWork(requireContext()) != null);
    refreshDynamicToggle(InCarQuickDestinationsStore.Action.RECENT_1,
                         InCarQuickDestinationsStore.getRecent(requireContext(), 1) != null);
    refreshDynamicToggle(InCarQuickDestinationsStore.Action.RECENT_2,
                         InCarQuickDestinationsStore.getRecent(requireContext(), 2) != null);
  }

  private void refreshDynamicToggle(@NonNull InCarQuickDestinationsStore.Action action, boolean available)
  {
    final Preference preference = findPreference(InCarQuickDestinationsStore.preferenceKey(action));
    if (!(preference instanceof TwoStatePreference toggle))
      return;
    toggle.setEnabled(available);
    toggle.setChecked(available && InCarQuickDestinationsStore.isActionEnabled(requireContext(), action));
  }
}
