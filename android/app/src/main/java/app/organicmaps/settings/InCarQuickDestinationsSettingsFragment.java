package app.organicmaps.settings;

import android.app.AlertDialog;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.TwoStatePreference;
import app.organicmaps.MwmApplication;
import app.organicmaps.R;
import app.organicmaps.incar.InCarDialogSizing;
import app.organicmaps.incar.InCarQuickDestination;
import app.organicmaps.incar.InCarQuickDestinationsStore;
import app.organicmaps.sdk.search.SearchEngine;
import app.organicmaps.sdk.search.SearchListener;
import app.organicmaps.sdk.search.SearchResult;
import app.organicmaps.sdk.util.Language;
import java.util.ArrayList;
import java.util.List;

/** Dedicated InCar configuration for Quick Destinations. */
public final class InCarQuickDestinationsSettingsFragment extends BaseXmlSettingsFragment
{
  private static final long SEARCH_DEBOUNCE_MS = 250L;

  @Override
  protected int getXmlResources()
  {
    return R.xml.prefs_in_car_quick_destinations;
  }

  @Override
  public void onCreatePreferences(@Nullable Bundle bundle, @Nullable String rootKey)
  {
    super.onCreatePreferences(bundle, rootKey);

    bindStartCollapsed();
    bindAction(InCarQuickDestinationsStore.Action.FUEL_CHARGING);
    bindAction(InCarQuickDestinationsStore.Action.PARKING);
    bindAction(InCarQuickDestinationsStore.Action.TOILETS);
    bindAction(InCarQuickDestinationsStore.Action.FOOD);
    bindAction(InCarQuickDestinationsStore.Action.HOME);
    bindAction(InCarQuickDestinationsStore.Action.WORK);
    bindAction(InCarQuickDestinationsStore.Action.RECENT_1);
    bindAction(InCarQuickDestinationsStore.Action.RECENT_2);

    bindDestinationConfig(R.string.pref_in_car_quick_home_config, R.string.in_car_quick_home, true);
    bindDestinationConfig(R.string.pref_in_car_quick_work_config, R.string.in_car_quick_work, false);
  }

  private void bindStartCollapsed()
  {
    final Preference preference = findPreference(InCarQuickDestinationsStore.startCollapsedPreferenceKey());
    if (!(preference instanceof TwoStatePreference toggle))
      return;
    toggle.setChecked(InCarQuickDestinationsStore.startCollapsed(requireContext()));
    toggle.setOnPreferenceChangeListener((ignored, newValue) -> {
      InCarQuickDestinationsStore.setStartCollapsed(requireContext(), (boolean) newValue);
      return true;
    });
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

  private void bindDestinationConfig(int keyRes, int labelRes, boolean home)
  {
    final Preference preference = findPreference(getString(keyRes));
    if (preference == null)
      return;

    updateDestinationSummary(preference, home ? InCarQuickDestinationsStore.getHome(requireContext())
                                              : InCarQuickDestinationsStore.getWork(requireContext()));
    preference.setOnPreferenceClickListener(pref -> {
      showDestinationDialog(pref, labelRes, home);
      return true;
    });
  }

  private void showDestinationDialog(@NonNull Preference preference, int labelRes, boolean home)
  {
    final InCarQuickDestination current = home ? InCarQuickDestinationsStore.getHome(requireContext())
                                               : InCarQuickDestinationsStore.getWork(requireContext());
    final List<String> items = new ArrayList<>();
    items.add(getString(R.string.in_car_quick_search_destination));
    items.add(getString(R.string.in_car_quick_use_current_location));
    if (current != null)
      items.add(getString(R.string.in_car_quick_clear_destination));

    final ArrayAdapter<String> adapter = createTouchChoiceAdapter(items, 64);
    final AlertDialog dialog =
        new AlertDialog.Builder(requireContext())
            .setTitle(labelRes)
            .setAdapter(adapter,
                        (ignored, which) -> {
                          if (which == 0)
                          {
                            showDestinationSearchDialog(preference, home);
                            return;
                          }
                          if (which == 1)
                          {
                            final Location location =
                                MwmApplication.from(requireContext()).getLocationHelper().getSavedLocation();
                            final InCarQuickDestination destination =
                                InCarQuickDestination.fromLocation(getString(labelRes), location);
                            if (destination == null)
                            {
                              Toast
                                  .makeText(requireContext(), R.string.in_car_quick_current_location_unavailable,
                                            Toast.LENGTH_SHORT)
                                  .show();
                              return;
                            }
                            saveDestination(home, destination);
                            updateDestinationSummary(preference, destination);
                            return;
                          }

                          saveDestination(home, null);
                          updateDestinationSummary(preference, null);
                        })
            .create();
    dialog.setOnShowListener(ignored -> InCarDialogSizing.applyCompactWidth(requireActivity(), dialog));
    dialog.show();
  }

  private void showDestinationSearchDialog(@NonNull Preference preference, boolean home)
  {
    final LinearLayout root = new LinearLayout(requireContext());
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(dp(16), dp(8), dp(16), dp(8));

    final EditText query = new EditText(requireContext());
    query.setSingleLine(true);
    query.setHint(R.string.in_car_quick_search_hint);
    query.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
    query.setMinHeight(dp(56));
    root.addView(
        query, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    final ListView results = new ListView(requireContext());
    results.setDividerHeight(dp(4));
    final LinearLayout.LayoutParams resultParams =
        new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
    resultParams.topMargin = dp(8);
    root.addView(results, resultParams);

    final List<SearchResult> currentResults = new ArrayList<>();
    final ArrayAdapter<String> adapter =
        new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, new ArrayList<>()) {
          @NonNull
          @Override
          public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent)
          {
            final TextView row = (TextView) super.getView(position, convertView, parent);
            row.setMinHeight(dp(64));
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(8), dp(16), dp(8));
            row.setSingleLine(false);
            return row;
          }
        };
    results.setAdapter(adapter);

    final Handler handler = new Handler(Looper.getMainLooper());
    final Runnable[] pending = new Runnable[1];
    final SearchListener listener = new SearchListener() {
      @Override
      public void onResultsUpdate(@NonNull SearchResult[] searchResults, long timestamp)
      {
        if (!isAdded())
          return;
        currentResults.clear();
        adapter.clear();
        for (SearchResult result : searchResults)
        {
          if (result.type != SearchResult.TYPE_RESULT)
            continue;
          currentResults.add(result);
          final String title = result.getTitle(requireContext());
          final CharSequence region = result.getFormattedAddress(requireContext());
          adapter.add(region.length() == 0 ? title : title + "\n" + region);
        }
        adapter.notifyDataSetChanged();
      }

      @Override
      public void onResultsEnd(long timestamp)
      {}
    };

    SearchEngine.INSTANCE.addListener(listener);
    final AlertDialog dialog = new AlertDialog.Builder(requireContext()).setView(root).create();
    dialog.setOnDismissListener(ignored -> {
      handler.removeCallbacksAndMessages(null);
      SearchEngine.INSTANCE.removeListener(listener);
      SearchEngine.INSTANCE.cancel();
    });
    dialog.setOnShowListener(ignored -> InCarDialogSizing.applyPickerSize(requireActivity(), dialog));

    query.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after)
      {}
      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count)
      {}
      @Override
      public void afterTextChanged(Editable editable)
      {
        if (pending[0] != null)
          handler.removeCallbacks(pending[0]);
        final String text = editable.toString().trim();
        if (text.isEmpty())
        {
          currentResults.clear();
          adapter.clear();
          SearchEngine.INSTANCE.cancel();
          return;
        }
        pending[0] = ()
            -> SearchEngine.INSTANCE.searchInteractive(text, false, Language.getKeyboardLocale(requireContext()),
                                                       System.nanoTime(), false);
        handler.postDelayed(pending[0], SEARCH_DEBOUNCE_MS);
      }
    });

    results.setOnItemClickListener((parent, view, position, id) -> {
      if (position < 0 || position >= currentResults.size())
        return;
      final SearchResult result = currentResults.get(position);
      final CharSequence region = result.getFormattedAddress(requireContext());
      final InCarQuickDestination destination =
          new InCarQuickDestination(result.getTitle(requireContext()), region.toString(), result.lat, result.lon);
      if (!destination.isValid())
        return;
      saveDestination(home, destination);
      updateDestinationSummary(preference, destination);
      dialog.dismiss();
    });
    dialog.show();
  }

  @NonNull
  private ArrayAdapter<String> createTouchChoiceAdapter(@NonNull List<String> items, int minHeightDp)
  {
    return new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, new ArrayList<>(items)) {
      @NonNull
      @Override
      public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent)
      {
        final TextView row = (TextView) super.getView(position, convertView, parent);
        row.setMinHeight(dp(minHeightDp));
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), 0, dp(16), 0);
        return row;
      }
    };
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
    if (destination == null)
    {
      preference.setSummary(R.string.in_car_quick_not_configured);
      return;
    }
    final String label = destination.getDisplayLabel();
    preference.setSummary(label.isEmpty() ? getString(R.string.in_car_quick_configured) : label);
  }

  private int dp(int value)
  {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }
}
