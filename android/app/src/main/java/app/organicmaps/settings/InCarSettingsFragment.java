package app.organicmaps.settings;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.XmlRes;
import androidx.preference.ListPreference;
import app.organicmaps.R;
import app.organicmaps.sdk.sound.OfflineNavigationVoicePack;
import app.organicmaps.sdk.sound.TtsPlayer;
import app.organicmaps.sdk.util.Config;

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
