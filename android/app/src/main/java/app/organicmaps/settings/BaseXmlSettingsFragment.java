package app.organicmaps.settings;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.XmlRes;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.RecyclerView;
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
    installTouchTargetPolicy(recyclerView);
    ViewCompat.setOnApplyWindowInsetsListener(recyclerView, new ScrollableContentInsetsListener(recyclerView));
  }

  private void installTouchTargetPolicy(@NonNull RecyclerView recyclerView)
  {
    final int minimumHeight = getResources().getDimensionPixelSize(R.dimen.automotive_preference_row_min_height);
    for (int i = 0; i < recyclerView.getChildCount(); i++)
      recyclerView.getChildAt(i).setMinimumHeight(minimumHeight);
    recyclerView.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
      @Override
      public void onChildViewAttachedToWindow(@NonNull View view)
      {
        view.setMinimumHeight(minimumHeight);
      }

      @Override
      public void onChildViewDetachedFromWindow(@NonNull View view)
      {}
    });
  }

  protected SettingsActivity getSettingsActivity()
  {
    return (SettingsActivity) requireActivity();
  }
}
