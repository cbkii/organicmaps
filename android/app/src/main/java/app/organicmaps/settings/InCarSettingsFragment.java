package app.organicmaps.settings;

import androidx.annotation.XmlRes;
import app.organicmaps.R;

public final class InCarSettingsFragment extends BaseXmlSettingsFragment
{
  @Override
  protected @XmlRes int getXmlResources()
  {
    return R.xml.prefs_in_car;
  }
}
