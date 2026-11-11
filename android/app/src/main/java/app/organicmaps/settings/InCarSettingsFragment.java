package app.organicmaps.settings;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.XmlRes;
import androidx.core.content.ContextCompat;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.TwoStatePreference;
import app.organicmaps.R;
import app.organicmaps.incar.InCarSettingsStore;
import app.organicmaps.location.InCarLocationPermissionPolicy;
import app.organicmaps.location.InCarLocationUi;
import app.organicmaps.sdk.location.LocationUtils;
import app.organicmaps.sdk.sound.OfflineNavigationVoicePack;
import app.organicmaps.sdk.sound.TtsPlayer;
import app.organicmaps.sdk.util.Config;
import app.organicmaps.util.Utils;
import java.util.ArrayList;
import java.util.List;

public final class InCarSettingsFragment extends BaseXmlSettingsFragment
{
  private int mCompactCheckGeneration;
  private boolean mLocationAccessPending;
  private boolean mLocationExplanationShowing;
  private final ActivityResultLauncher<String> mBackgroundLocationRequest =
      registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
        mLocationAccessPending = false;
        updateLocationAccess();
      });
  private final ActivityResultLauncher<Intent> mAppPermissionSettings =
      registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        mLocationAccessPending = false;
        updateLocationAccess();
      });

  @Override
  protected @XmlRes int getXmlResources()
  {
    return R.xml.prefs_in_car;
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
  {
    super.onViewCreated(view, savedInstanceState);
    mLocationAccessPending = savedInstanceState != null && savedInstanceState.getBoolean("location_access_pending");
    getPreference("in_car_location_access").setOnPreferenceClickListener(preference -> {
      if (mLocationAccessPending || mLocationExplanationShowing)
        return true;
      mLocationExplanationShowing = true;
      new AlertDialog.Builder(requireContext())
          .setTitle(R.string.in_car_location_access)
          .setMessage(R.string.in_car_location_background_explanation)
          .setNegativeButton(R.string.close, null)
          .setOnDismissListener(dialog -> mLocationExplanationShowing = false)
          .setPositiveButton(R.string.settings, (dialog, which) -> requestLocationAccess())
          .show();
      return true;
    });
    getPreference("in_car_location_diagnostics").setOnPreferenceClickListener(preference -> {
      InCarLocationUi.showDetails(requireActivity());
      return true;
    });
    updateLocationAccess();
    getViewLifecycleOwner().getLifecycle().addObserver(new androidx.lifecycle.DefaultLifecycleObserver() {
      @Override
      public void onResume(@NonNull androidx.lifecycle.LifecycleOwner owner)
      {
        updateLocationAccess();
      }
    });

    final TwoStatePreference compact = getPreference("in_car_compact_startup");
    compact.setVisible(app.organicmaps.BuildConfig.IS_IN_CAR);
    compact.setChecked(InCarSettingsStore.compactStartupEnabled(requireContext()));
    compact.setSummary(R.string.in_car_compact_startup_checking);
    compact.setEnabled(false);
    compact.setOnPreferenceChangeListener((preference, value) -> {
      InCarSettingsStore.setCompactStartupEnabled(requireContext(), (boolean) value);
      return true;
    });

    final TwoStatePreference edgeWarning = getPreference("in_car_speed_edge_warning");
    edgeWarning.setChecked(InCarSettingsStore.speedEdgeWarningEnabled(requireContext()));
    edgeWarning.setOnPreferenceChangeListener((preference, value) -> {
      InCarSettingsStore.setSpeedEdgeWarningEnabled(requireContext(), (boolean) value);
      return true;
    });

    final ListPreference position = getPreference("in_car_control_position");
    position.setValue(InCarSettingsStore.controlsOnLeft(requireContext()) ? "RHD" : "LHD");
    position.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
    position.setOnPreferenceChangeListener((preference, value) -> {
      InCarSettingsStore.setControlPosition(requireContext(), (String) value);
      return true;
    });
    final ListPreference delay = getPreference("in_car_navigation_action_delay");
    delay.setValue(Integer.toString(InCarSettingsStore.getAutomaticNavigationActionDelaySeconds(requireContext())));
    delay.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
    delay.setOnPreferenceChangeListener((preference, value) -> {
      InCarSettingsStore.setAutomaticNavigationActionDelaySeconds(requireContext(), Integer.parseInt((String) value));
      return true;
    });

    final TwoStatePreference warning = getPreference(getString(R.string.pref_in_car_location_disabled_warning));
    warning.setChecked(InCarSettingsStore.showLocationDisabledWarning(requireContext()));
    warning.setOnPreferenceChangeListener((preference, value) -> {
      InCarSettingsStore.setShowLocationDisabledWarning(requireContext(), (boolean) value);
      return true;
    });
    getPreference(getString(R.string.pref_in_car_open_location_settings)).setOnPreferenceClickListener(preference -> {
      final Intent intent = Utils.makeSystemLocationSettingIntent(requireContext());
      if (intent == null)
      {
        Toast.makeText(requireContext(), R.string.in_car_location_settings_unavailable, Toast.LENGTH_LONG).show();
        return false;
      }
      try
      {
        startActivity(intent);
        return true;
      }
      catch (ActivityNotFoundException e)
      {
        Toast.makeText(requireContext(), R.string.in_car_location_settings_unavailable, Toast.LENGTH_LONG).show();
        return false;
      }
    });

    // The old MANUAL option depended on a second map-facing camera button. Keep its resource entry
    // for migration/backward compatibility, but do not expose an impossible mode after that button
    // has been removed. The runtime policy normalises legacy MANUAL installs to AUTOMATIC.
    final ListPreference drivingView = getPreference(getString(R.string.pref_in_car_driving_view_mode));
    filterLegacyManualDrivingView(drivingView);

    final ListPreference expiry = getPreference("in_car_route_resume_minutes");
    final List<CharSequence> expiryLabels = new ArrayList<>();
    final List<CharSequence> expiryValues = new ArrayList<>();
    for (int minutes = 5; minutes <= 90; minutes += 5)
    {
      expiryLabels.add(getString(R.string.in_car_route_resume_minutes, minutes, getString(R.string.minute)));
      expiryValues.add(Integer.toString(minutes));
    }
    expiry.setEntries(expiryLabels.toArray(new CharSequence[0]));
    expiry.setEntryValues(expiryValues.toArray(new CharSequence[0]));
    expiry.setValue(Integer.toString(InCarSettingsStore.routeResumeMinutes(requireContext())));
    expiry.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
    expiry.setOnPreferenceChangeListener((preference, value) -> {
      InCarSettingsStore.setRouteResumeMinutes(requireContext(), Integer.parseInt((String) value));
      return true;
    });

    final ListPreference fallback = getPreference(getString(R.string.pref_in_car_navigation_fallback_mode));
    final OfflineNavigationVoicePack.Mode mode = OfflineNavigationVoicePack.getMode(requireContext());
    fallback.setValue(mode.getPreferenceValue());
    updateFallbackSummary(fallback, mode);
    fallback.setOnPreferenceChangeListener((preference, newValue) -> {
      final OfflineNavigationVoicePack.Mode newMode =
          OfflineNavigationVoicePack.Mode.fromPreferenceValue((String) newValue);
      OfflineNavigationVoicePack.setMode(requireContext(), newMode);
      updateFallbackSummary(fallback, newMode);
      TtsPlayer.setEnabled(Config.TTS.isEnabled());
      return true;
    });
  }

  @Override
  public void onResume()
  {
    super.onResume();
    final int generation = ++mCompactCheckGeneration;
    final View view = getView();
    if (view == null || !app.organicmaps.BuildConfig.IS_IN_CAR)
      return;
    final TwoStatePreference compact = getPreference("in_car_compact_startup");
    compact.setChecked(InCarSettingsStore.compactStartupEnabled(requireContext()));
    compact.setEnabled(false);
    compact.setSummary(R.string.in_car_compact_startup_checking);
    app.organicmaps.incar.InCarCompactStartupProvider.checkController(requireContext(), available -> {
      if (!isAdded() || getView() != view || generation != mCompactCheckGeneration)
        return;
      compact.setEnabled(available);
      compact.setSummary(available ? R.string.in_car_compact_startup_summary
                                   : R.string.in_car_compact_startup_unavailable);
    });
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState)
  {
    outState.putBoolean("location_access_pending", mLocationAccessPending);
    super.onSaveInstanceState(outState);
  }

  private void updateLocationAccess()
  {
    if (getContext() == null || findPreference("in_car_location_access") == null)
      return;
    final boolean foreground = LocationUtils.checkLocationPermission(requireContext());
    final boolean background =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
        || ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION)
               == PackageManager.PERMISSION_GRANTED;
    getPreference("in_car_location_access").setSummary(switch (
        InCarLocationPermissionPolicy.access(Build.VERSION.SDK_INT, foreground, background))
    {
      case DENIED -> R.string.in_car_location_denied;
      case WHILE_USING -> R.string.in_car_location_while_using;
      case ALWAYS -> R.string.in_car_location_always;
    });
    getPreference("in_car_location_access").setEnabled(!mLocationAccessPending);
  }

  private void requestLocationAccess()
  {
    if (mLocationAccessPending)
      return;
    mLocationAccessPending = true;
    updateLocationAccess();
    try
    {
      if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && LocationUtils.checkLocationPermission(requireContext())
          && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                 != PackageManager.PERMISSION_GRANTED
          && shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
        mBackgroundLocationRequest.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
      else
        mAppPermissionSettings.launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                 Uri.parse("package:" + requireContext().getPackageName())));
    }
    catch (ActivityNotFoundException | SecurityException e)
    {
      mLocationAccessPending = false;
      updateLocationAccess();
      Toast.makeText(requireContext(), R.string.in_car_location_settings_unavailable, Toast.LENGTH_LONG).show();
    }
  }

  private static void filterLegacyManualDrivingView(@NonNull ListPreference preference)
  {
    final CharSequence[] entries = preference.getEntries();
    final CharSequence[] values = preference.getEntryValues();
    if (entries == null || values == null || entries.length != values.length)
      return;

    final List<CharSequence> filteredEntries = new ArrayList<>(entries.length);
    final List<CharSequence> filteredValues = new ArrayList<>(values.length);
    for (int i = 0; i < values.length; ++i)
    {
      if ("MANUAL".contentEquals(values[i]))
        continue;
      filteredEntries.add(entries[i]);
      filteredValues.add(values[i]);
    }
    preference.setEntries(filteredEntries.toArray(new CharSequence[0]));
    preference.setEntryValues(filteredValues.toArray(new CharSequence[0]));
  }

  private void updateFallbackSummary(@NonNull ListPreference preference, @NonNull OfflineNavigationVoicePack.Mode mode)
  {
    preference.setSummary(switch (mode)
    {
      case OFF -> R.string.in_car_navigation_fallback_off_summary;
      case VOICE -> R.string.in_car_navigation_fallback_voice_summary;
      case TONE_ALERTS -> R.string.in_car_navigation_fallback_tone_alerts_summary;
      case TONE_ALL -> R.string.in_car_navigation_fallback_tone_all_summary;
    });
  }
}
