package app.organicmaps.settings;

import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import app.organicmaps.incar.InCarSettingsRows;

/** Applied by the InCar adapter during binding, before RecyclerView measures the real hit bounds. */
final class InCarPreferenceRowPolicy
{
  private InCarPreferenceRowPolicy() {}

  static void bind(@NonNull View row, @NonNull Preference preference)
  {
    if (preference instanceof PreferenceCategory)
      row.setMinimumHeight(0);
    else if (preference.isSelectable())
      InCarSettingsRows.apply(row); // Disabled action rows retain their geometry.
    final TextView title = row.findViewById(android.R.id.title);
    if (title != null)
      InCarSettingsRows.allowMultiline(title);
    final TextView summary = row.findViewById(android.R.id.summary);
    if (summary != null)
      InCarSettingsRows.allowMultiline(summary);
  }
}
