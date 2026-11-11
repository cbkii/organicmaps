package app.organicmaps.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.XmlRes;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;
import androidx.recyclerview.widget.RecyclerView;
import app.organicmaps.BuildConfig;
import app.organicmaps.R;
import app.organicmaps.util.Utils;
import app.organicmaps.util.WindowInsetUtils.ScrollableContentInsetsListener;

abstract class BaseXmlSettingsFragment extends PreferenceFragmentCompat
{
  protected abstract @XmlRes int getXmlResources();

  @NonNull
  public <T extends Preference> T getPreference(@NonNull CharSequence key)
  {
    final T pref = findPreference(key);
    if (pref == null)
      throw new RuntimeException("Can't get preference by key: " + key);
    return pref;
  }

  @Override
  public void onCreatePreferences(Bundle bundle, String root)
  {
    setPreferencesFromResource(getXmlResources(), root);
    InCarSettingsPolicy.apply(this);
  }

  @Override
  public void onAttach(@NonNull Context context)
  {
    super.onAttach(context);
    Utils.detachFragmentIfCoreNotInitialized(context, this);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
  {
    super.onViewCreated(view, savedInstanceState);
    view.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.bg_cards));
    final RecyclerView recyclerView = getListView();
    recyclerView.setClipToPadding(false);
    ViewCompat.setOnApplyWindowInsetsListener(recyclerView, new ScrollableContentInsetsListener(recyclerView));
  }

  @Override
  @SuppressLint("RestrictedApi") // AndroidX's adapter retains preference listeners, stable IDs and view types.
  protected RecyclerView.Adapter onCreateAdapter(@NonNull PreferenceScreen screen)
  {
    if (!BuildConfig.IS_IN_CAR)
      return super.onCreateAdapter(screen);
    return new PreferenceGroupAdapter(screen) {
      @Override
      public void onBindViewHolder(@NonNull PreferenceViewHolder holder, int position)
      {
        super.onBindViewHolder(holder, position);
        final Preference preference = getItem(position);
        if (preference != null)
          InCarPreferenceRowPolicy.bind(holder.itemView, preference);
      }
    };
  }

  @Override
  @SuppressWarnings("deprecation") // PreferenceDialogFragmentCompat resolves its owner through the target fragment.
  public void onDisplayPreferenceDialog(@NonNull Preference preference)
  {
    if (!BuildConfig.IS_IN_CAR || !(preference instanceof ListPreference))
    {
      super.onDisplayPreferenceDialog(preference);
      return;
    }
    final String tag = "androidx.preference.PreferenceFragment.DIALOG";
    if (getParentFragmentManager().findFragmentByTag(tag) != null || getParentFragmentManager().isStateSaved())
      return;
    final InCarListPreferenceDialogFragment dialog = InCarListPreferenceDialogFragment.newInstance(preference.getKey());
    dialog.setTargetFragment(this, 0);
    dialog.show(getParentFragmentManager(), tag);
  }

  protected SettingsActivity getSettingsActivity()
  {
    return (SettingsActivity) requireActivity();
  }
}
