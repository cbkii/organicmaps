package app.organicmaps.settings;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.XmlRes;
import androidx.preference.SwitchPreferenceCompat;
import app.organicmaps.R;
import app.organicmaps.maplayer.LayersUtils;
import app.organicmaps.sdk.maplayer.Mode;
import app.organicmaps.sdk.util.SharedPropertiesUtils;
import app.organicmaps.util.ThemeSwitcher;

/** InCar layer controls live in Settings instead of competing with map/navigation chrome. */
public final class InCarMapLayersSettingsFragment extends BaseXmlSettingsFragment
{
  private static final String KEY_OUTDOORS = "in_car_layer_outdoors";
  private static final String KEY_ISOLINES = "in_car_layer_isolines";
  private static final String KEY_HIKING = "in_car_layer_hiking";
  private static final String KEY_CYCLING = "in_car_layer_cycling";
  private static final String KEY_SATELLITE = "in_car_layer_satellite";

  @Override
  protected @XmlRes int getXmlResources()
  {
    return R.xml.prefs_in_car_layers;
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
  {
    super.onViewCreated(view, savedInstanceState);
    bindLayer(KEY_OUTDOORS, Mode.OUTDOORS);
    bindLayer(KEY_ISOLINES, Mode.ISOLINES);
    bindLayer(KEY_HIKING, Mode.HIKING);
    bindLayer(KEY_CYCLING, Mode.CYCLING);
    bindLayer(KEY_SATELLITE, Mode.SATELLITE);
  }

  private void bindLayer(@NonNull String key, @NonNull Mode mode)
  {
    final SwitchPreferenceCompat preference = getPreference(key);
    final Context context = requireContext();
    final boolean available = LayersUtils.getAvailableLayers().contains(mode);
    // A missing XYZ source makes Satellite unavailable, not a user choice. Explain the
    // prerequisite instead of silently removing the option from the settings screen.
    if (!available && mode == Mode.SATELLITE)
    {
      preference.setVisible(true);
      preference.setEnabled(false);
      preference.setSummary(R.string.in_car_satellite_source_required);
      return;
    }
    preference.setVisible(available);
    if (!available)
      return;
    preference.setEnabled(true);
    preference.setSummary(null);

    preference.setChecked(mode.isEnabled(context));
    preference.setOnPreferenceChangeListener((ignored, newValue) -> {
      if (!(newValue instanceof Boolean enabled))
        return false;

      SharedPropertiesUtils.setLayerMarkerShownForLayerMode(mode);
      mode.setEnabled(context, enabled);
      if (mode == Mode.OUTDOORS)
        ThemeSwitcher.INSTANCE.synchronizeMapStyle(context, true);
      return true;
    });
  }
}
