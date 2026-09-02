package app.organicmaps.settings;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.XmlRes;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import app.organicmaps.R;
import app.organicmaps.incar.InCarChoiceAdapter;
import app.organicmaps.incar.InCarDialogSizing;
import app.organicmaps.sdk.sound.OfflineNavigationVoicePack;
import app.organicmaps.sdk.sound.TtsPlayer;
import app.organicmaps.sdk.util.Config;
import java.util.ArrayList;
import java.util.List;

public final class InCarSettingsFragment extends BaseXmlSettingsFragment
{
  @Override
  protected @XmlRes int getXmlResources()
  {
    return R.xml.prefs_in_car;
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
  {
    super.onViewCreated(view, savedInstanceState);

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
  public void onDisplayPreferenceDialog(@NonNull Preference preference)
  {
    if (!(preference instanceof ListPreference listPreference))
    {
      super.onDisplayPreferenceDialog(preference);
      return;
    }

    final CharSequence[] entries = listPreference.getEntries();
    final CharSequence[] values = listPreference.getEntryValues();
    if (entries == null || values == null || entries.length != values.length)
    {
      super.onDisplayPreferenceDialog(preference);
      return;
    }

    final List<String> labels = new ArrayList<>(entries.length);
    for (CharSequence entry : entries)
      labels.add(entry.toString());

    final InCarChoiceAdapter adapter = InCarChoiceAdapter.singleChoice(requireContext(), labels);
    final CharSequence title =
        listPreference.getDialogTitle() != null ? listPreference.getDialogTitle() : listPreference.getTitle();
    final AlertDialog dialog =
        new AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setSingleChoiceItems(adapter, listPreference.findIndexOfValue(listPreference.getValue()),
                                  (dialogInterface, which) -> {
                                    if (which < 0 || which >= values.length)
                                      return;
                                    final String value = values[which].toString();
                                    if (listPreference.callChangeListener(value))
                                      listPreference.setValue(value);
                                    dialogInterface.dismiss();
                                  })
            .setNegativeButton(R.string.cancel, null)
            .create();
    dialog.setOnShowListener(ignored -> InCarDialogSizing.applyCompactWidth(requireActivity(), dialog));
    dialog.show();
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
