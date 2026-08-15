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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.TwoStatePreference;
import app.organicmaps.MwmApplication;
import app.organicmaps.R;
import app.organicmaps.incar.InCarChoiceAdapter;
import app.organicmaps.incar.InCarDestinationSearchPolicy;
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
  private static final long NO_ACTIVE_SEARCH = Long.MIN_VALUE;

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
    bindAction(InCarQuickDestinationsStore.Action.FUEL);
    bindAction(InCarQuickDestinationsStore.Action.CHARGING);
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

    final InCarChoiceAdapter adapter = new InCarChoiceAdapter(requireContext(), items);
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

    final FrameLayout content = new FrameLayout(requireContext());
    final LinearLayout.LayoutParams contentParams =
        new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
    contentParams.topMargin = dp(8);
    root.addView(content, contentParams);

    final ListView results = new ListView(requireContext());
    results.setDividerHeight(dp(4));
    content.addView(results, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                          ViewGroup.LayoutParams.MATCH_PARENT));

    final LinearLayout statusRow = new LinearLayout(requireContext());
    statusRow.setOrientation(LinearLayout.HORIZONTAL);
    statusRow.setGravity(Gravity.CENTER);
    statusRow.setPadding(dp(16), dp(16), dp(16), dp(16));
    final ProgressBar progress = new ProgressBar(requireContext(), null, android.R.attr.progressBarStyleSmall);
    statusRow.addView(progress, new LinearLayout.LayoutParams(dp(32), dp(32)));
    final TextView statusText = new TextView(requireContext());
    statusText.setGravity(Gravity.CENTER_VERTICAL);
    statusText.setTextSize(18.0f);
    statusText.setPadding(dp(12), 0, 0, 0);
    statusRow.addView(statusText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                                                                ViewGroup.LayoutParams.WRAP_CONTENT));
    content.addView(statusRow, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                            ViewGroup.LayoutParams.MATCH_PARENT));

    final List<SearchResult> currentResults = new ArrayList<>();
    final InCarChoiceAdapter adapter = new InCarChoiceAdapter(requireContext(), new ArrayList<>());
    results.setAdapter(adapter);

    final Handler handler = new Handler(Looper.getMainLooper());
    final Runnable[] pending = new Runnable[1];
    final long[] activeTimestamp = {NO_ACTIVE_SEARCH};
    final SearchListener listener = new SearchListener() {
      @Override
      public void onResultsUpdate(@NonNull SearchResult[] searchResults, long timestamp)
      {
        if (!isAdded() || timestamp != activeTimestamp[0])
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
        if (!currentResults.isEmpty())
          renderSearchState(InCarDestinationSearchPolicy.UiState.RESULTS, statusRow, progress, statusText, results);
      }

      @Override
      public void onResultsEnd(long timestamp)
      {
        if (!isAdded() || timestamp != activeTimestamp[0])
          return;
        renderSearchState(InCarDestinationSearchPolicy.stateForCompletedResults(currentResults.size()), statusRow,
                          progress, statusText, results);
      }
    };

    SearchEngine.INSTANCE.addListener(listener);
    final AlertDialog dialog = new AlertDialog.Builder(requireContext()).setView(root).create();
    dialog.setOnDismissListener(ignored -> {
      activeTimestamp[0] = NO_ACTIVE_SEARCH;
      handler.removeCallbacksAndMessages(null);
      SearchEngine.INSTANCE.removeListener(listener);
      SearchEngine.INSTANCE.cancel();
    });
    dialog.setOnShowListener(ignored -> InCarDialogSizing.applyPickerSize(requireActivity(), dialog));

    renderSearchState(InCarDestinationSearchPolicy.UiState.IDLE, statusRow, progress, statusText, results);
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
        {
          handler.removeCallbacks(pending[0]);
          pending[0] = null;
        }
        activeTimestamp[0] = NO_ACTIVE_SEARCH;
        SearchEngine.INSTANCE.cancel();
        currentResults.clear();
        adapter.clear();
        adapter.notifyDataSetChanged();

        final String text = editable.toString().trim();
        final InCarDestinationSearchPolicy.UiState state = InCarDestinationSearchPolicy.stateForQuery(text);
        renderSearchState(state, statusRow, progress, statusText, results);
        if (state != InCarDestinationSearchPolicy.UiState.SEARCHING)
          return;

        pending[0] = () -> {
          final long timestamp = System.nanoTime();
          activeTimestamp[0] = timestamp;
          renderSearchState(InCarDestinationSearchPolicy.UiState.SEARCHING, statusRow, progress, statusText, results);
          SearchEngine.INSTANCE.searchInteractive(text, false, Language.getKeyboardLocale(requireContext()), timestamp,
                                                  false);
        };
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

  private void renderSearchState(@NonNull InCarDestinationSearchPolicy.UiState state, @NonNull View statusRow,
                                 @NonNull ProgressBar progress, @NonNull TextView statusText,
                                 @NonNull ListView results)
  {
    if (state == InCarDestinationSearchPolicy.UiState.RESULTS)
    {
      statusRow.setVisibility(View.GONE);
      results.setVisibility(View.VISIBLE);
      return;
    }

    statusRow.setVisibility(View.VISIBLE);
    results.setVisibility(View.GONE);
    progress.setVisibility(state == InCarDestinationSearchPolicy.UiState.SEARCHING ? View.VISIBLE : View.GONE);
    final int messageRes = switch (state)
    {
      case IDLE -> R.string.in_car_quick_search_prompt;
      case QUERY_TOO_SHORT -> R.string.in_car_quick_search_more_characters;
      case SEARCHING -> R.string.in_car_quick_searching;
      case EMPTY -> R.string.in_car_quick_search_no_results;
      case RESULTS -> throw new IllegalStateException("Results state is handled above");
    };
    statusText.setText(messageRes);
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
