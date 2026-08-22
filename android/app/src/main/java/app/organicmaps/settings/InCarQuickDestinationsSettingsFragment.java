package app.organicmaps.settings;

import android.app.AlertDialog;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.TwoStatePreference;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import app.organicmaps.MwmApplication;
import app.organicmaps.R;
import app.organicmaps.incar.InCarChoiceAdapter;
import app.organicmaps.incar.InCarDialogSizing;
import app.organicmaps.incar.InCarQuickDestination;
import app.organicmaps.incar.InCarQuickDestinationsStore;
import app.organicmaps.sdk.bookmarks.data.BookmarkCategory;
import app.organicmaps.sdk.bookmarks.data.BookmarkInfo;
import app.organicmaps.sdk.bookmarks.data.BookmarkManager;
import app.organicmaps.sdk.search.SearchEngine;
import app.organicmaps.sdk.search.SearchListener;
import app.organicmaps.sdk.search.SearchResult;
import app.organicmaps.sdk.util.Language;
import app.organicmaps.search.SearchAdapter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Dedicated InCar configuration for Quick Destinations. */
public final class InCarQuickDestinationsSettingsFragment extends BaseXmlSettingsFragment
{
  private static final long SEARCH_DEBOUNCE_MS = 200L;
  private static final long NO_ACTIVE_SEARCH = Long.MIN_VALUE;

  private enum DestinationAction
  {
    SEARCH,
    SAVED_PLACE,
    CURRENT_LOCATION,
    CLEAR
  }

  private static final class SavedPlaceChoice
  {
    @NonNull
    final String label;
    @NonNull
    final InCarQuickDestination destination;

    SavedPlaceChoice(@NonNull String label, @NonNull InCarQuickDestination destination)
    {
      this.label = label;
      this.destination = destination;
    }
  }

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
    final List<String> labels = new ArrayList<>();
    final List<DestinationAction> actions = new ArrayList<>();
    labels.add(getString(R.string.in_car_quick_search_destination));
    actions.add(DestinationAction.SEARCH);
    labels.add(getString(R.string.in_car_quick_saved_places));
    actions.add(DestinationAction.SAVED_PLACE);
    labels.add(getString(R.string.in_car_quick_use_current_location));
    actions.add(DestinationAction.CURRENT_LOCATION);
    if (current != null)
    {
      labels.add(getString(R.string.in_car_quick_clear_destination));
      actions.add(DestinationAction.CLEAR);
    }

    final InCarChoiceAdapter adapter = new InCarChoiceAdapter(requireContext(), labels);
    final AlertDialog dialog =
        new AlertDialog.Builder(requireContext())
            .setTitle(labelRes)
            .setAdapter(adapter,
                        (ignored, which) -> {
                          if (which < 0 || which >= actions.size())
                            return;
                          switch (actions.get(which))
                          {
                          case SEARCH -> showDestinationSearchDialog(preference, home);
                          case SAVED_PLACE -> showSavedPlacesDialog(preference, home);
                          case CURRENT_LOCATION -> saveCurrentLocation(preference, labelRes, home);
                          case CLEAR -> {
                            saveDestination(home, null);
                            updateDestinationSummary(preference, null);
                          }
                          }
                        })
            .create();
    dialog.setOnShowListener(ignored -> InCarDialogSizing.applyCompactWidth(requireActivity(), dialog));
    dialog.show();
  }

  private void saveCurrentLocation(@NonNull Preference preference, int labelRes, boolean home)
  {
    final Location location = MwmApplication.from(requireContext()).getLocationHelper().getSavedLocation();
    final InCarQuickDestination destination = InCarQuickDestination.fromLocation(getString(labelRes), location);
    if (destination == null)
    {
      Toast.makeText(requireContext(), R.string.in_car_quick_current_location_unavailable, Toast.LENGTH_SHORT).show();
      return;
    }
    saveDestination(home, destination);
    updateDestinationSummary(preference, destination);
  }

  /**
   * Uses the normal Organic Maps SearchEngine call shape and the normal SearchAdapter rows/suggestions.
   * The only InCar-specific behaviour here is that selecting a result persists it as Home or Work.
   */
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

    final RecyclerView results = new RecyclerView(requireContext());
    results.setLayoutManager(new LinearLayoutManager(requireContext()));
    results.setClipToPadding(false);
    content.addView(results, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                          ViewGroup.LayoutParams.MATCH_PARENT));

    final LinearLayout statusRow = new LinearLayout(requireContext());
    statusRow.setOrientation(LinearLayout.HORIZONTAL);
    statusRow.setGravity(android.view.Gravity.CENTER);
    statusRow.setPadding(dp(16), dp(16), dp(16), dp(16));
    final ProgressBar progress = new ProgressBar(requireContext(), null, android.R.attr.progressBarStyleSmall);
    statusRow.addView(progress, new LinearLayout.LayoutParams(dp(32), dp(32)));
    final TextView statusText = new TextView(requireContext());
    statusText.setGravity(android.view.Gravity.CENTER_VERTICAL);
    statusText.setTextSize(18.0f);
    statusText.setPadding(dp(12), 0, 0, 0);
    statusRow.addView(statusText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                                                                ViewGroup.LayoutParams.WRAP_CONTENT));
    content.addView(statusRow, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                            ViewGroup.LayoutParams.MATCH_PARENT));

    final AlertDialog dialog = new AlertDialog.Builder(requireContext()).setView(root).create();
    final boolean[] suggestionChange = {false};
    final boolean[] categoryQuery = {false};
    final SearchAdapter adapter = new SearchAdapter(this, new SearchAdapter.Listener() {
      @Override
      public void onSuggestionSelected(@NonNull SearchResult result)
      {
        suggestionChange[0] = true;
        categoryQuery[0] = result.type == SearchResult.TYPE_PURE_SUGGEST;
        query.setText(result.suggestion);
        query.setSelection(query.length());
        suggestionChange[0] = false;
      }

      @Override
      public void onResultSelected(@NonNull SearchResult result, int order)
      {
        saveSearchResult(preference, home, result);
        dialog.dismiss();
      }
    });
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
        adapter.refreshData(searchResults);
        if (adapter.getItemCount() > 0)
          showSearchResults(statusRow, results);
      }

      @Override
      public void onResultsEnd(long timestamp)
      {
        if (!isAdded() || timestamp != activeTimestamp[0])
          return;
        if (adapter.getItemCount() == 0)
          showSearchStatus(statusRow, progress, statusText, results, R.string.in_car_quick_search_no_results, false);
        else
          showSearchResults(statusRow, results);
      }
    };

    SearchEngine.INSTANCE.addListener(listener);
    dialog.setOnDismissListener(ignored -> {
      activeTimestamp[0] = NO_ACTIVE_SEARCH;
      handler.removeCallbacksAndMessages(null);
      SearchEngine.INSTANCE.removeListener(listener);
      SearchEngine.INSTANCE.cancel();
    });
    dialog.setOnShowListener(ignored -> InCarDialogSizing.applyPickerSize(requireActivity(), dialog));

    showSearchStatus(statusRow, progress, statusText, results, R.string.in_car_quick_search_prompt, false);
    query.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after)
      {}

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count)
      {
        if (!suggestionChange[0])
          categoryQuery[0] = false;
      }

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
        adapter.clear();

        final String text = editable.toString().trim();
        if (text.isEmpty())
        {
          showSearchStatus(statusRow, progress, statusText, results, R.string.in_car_quick_search_prompt, false);
          return;
        }

        showSearchStatus(statusRow, progress, statusText, results, R.string.in_car_quick_searching, true);
        final boolean isCategory = categoryQuery[0];
        pending[0] = () -> runStandardInteractiveSearch(text, isCategory, activeTimestamp, statusRow, progress,
                                                        statusText, results);
        handler.postDelayed(pending[0], SEARCH_DEBOUNCE_MS);
      }
    });

    dialog.show();
  }

  private void runStandardInteractiveSearch(@NonNull String text, boolean isCategory, @NonNull long[] activeTimestamp,
                                            @NonNull View statusRow, @NonNull ProgressBar progress,
                                            @NonNull TextView statusText, @NonNull RecyclerView results)
  {
    final long timestamp = System.nanoTime();
    activeTimestamp[0] = timestamp;
    final Location location = MwmApplication.from(requireContext()).getLocationHelper().getSavedLocation();
    final boolean hasLocation = location != null;
    final double lat = hasLocation ? location.getLatitude() : 0.0;
    final double lon = hasLocation ? location.getLongitude() : 0.0;
    final String locale = Language.getKeyboardLocale(requireContext());

    SearchEngine.INSTANCE.cancel();
    SearchEngine.INSTANCE.setQuery(text);
    final boolean started = SearchEngine.INSTANCE.searchInteractive(text, isCategory, locale, timestamp,
                                                                    true /* isMapAndTable */, hasLocation, lat, lon);
    if (!started)
    {
      activeTimestamp[0] = NO_ACTIVE_SEARCH;
      showSearchStatus(statusRow, progress, statusText, results, R.string.in_car_quick_search_no_results, false);
    }
  }

  private void saveSearchResult(@NonNull Preference preference, boolean home, @NonNull SearchResult result)
  {
    if (result.type != SearchResult.TYPE_RESULT)
      return;
    final CharSequence region = result.getFormattedAddress(requireContext());
    final InCarQuickDestination destination =
        new InCarQuickDestination(result.getTitle(requireContext()), region.toString(), result.lat, result.lon);
    if (!destination.isValid())
      return;
    saveDestination(home, destination);
    updateDestinationSummary(preference, destination);
  }

  private void showSearchResults(@NonNull View statusRow, @NonNull RecyclerView results)
  {
    statusRow.setVisibility(View.GONE);
    results.setVisibility(View.VISIBLE);
  }

  private void showSearchStatus(@NonNull View statusRow, @NonNull ProgressBar progress, @NonNull TextView statusText,
                                @NonNull RecyclerView results, int messageRes, boolean showProgress)
  {
    results.setVisibility(View.GONE);
    statusRow.setVisibility(View.VISIBLE);
    progress.setVisibility(showProgress ? View.VISIBLE : View.GONE);
    statusText.setText(messageRes);
  }

  private void showSavedPlacesDialog(@NonNull Preference preference, boolean home)
  {
    if (BookmarkManager.INSTANCE.isAsyncBookmarksLoadingInProgress())
    {
      Toast.makeText(requireContext(), R.string.in_car_quick_saved_places_loading, Toast.LENGTH_SHORT).show();
      return;
    }

    final List<SavedPlaceChoice> choices = new ArrayList<>();
    final Set<Long> visitedCategories = new HashSet<>();
    for (BookmarkCategory category : BookmarkManager.INSTANCE.getCategories())
      collectSavedPlaces(category, visitedCategories, choices);

    if (choices.isEmpty())
    {
      Toast.makeText(requireContext(), R.string.in_car_quick_saved_places_empty, Toast.LENGTH_SHORT).show();
      return;
    }

    choices.sort(Comparator.comparing(choice -> choice.label, String.CASE_INSENSITIVE_ORDER));
    final List<String> labels = new ArrayList<>(choices.size());
    for (SavedPlaceChoice choice : choices)
      labels.add(choice.label);

    final InCarChoiceAdapter adapter = new InCarChoiceAdapter(requireContext(), labels);
    final AlertDialog dialog =
        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.in_car_quick_saved_places)
            .setAdapter(adapter, (ignored, which) -> {
              if (which < 0 || which >= choices.size())
                return;
              final InCarQuickDestination destination = choices.get(which).destination;
              saveDestination(home, destination);
              updateDestinationSummary(preference, destination);
            })
            .create();
    dialog.setOnShowListener(ignored -> InCarDialogSizing.applyPickerSize(requireActivity(), dialog));
    dialog.show();
  }

  private void collectSavedPlaces(@NonNull BookmarkCategory category, @NonNull Set<Long> visitedCategories,
                                  @NonNull List<SavedPlaceChoice> choices)
  {
    if (!visitedCategories.add(category.getId()))
      return;

    for (int position = 0; position < category.getBookmarksCount(); ++position)
    {
      final long bookmarkId = category.getBookmarkIdByPosition(position);
      final BookmarkInfo bookmark = BookmarkManager.INSTANCE.getBookmarkInfo(bookmarkId);
      if (bookmark == null)
        continue;
      final InCarQuickDestination destination =
          new InCarQuickDestination(bookmark.getName(), bookmark.getAddress(), bookmark.getLat(), bookmark.getLon());
      if (!destination.isValid())
        continue;
      final String title = bookmark.getName().trim();
      final String address = bookmark.getAddress().trim();
      final String primary = !title.isEmpty() ? title : (!address.isEmpty() ? address : category.getName());
      final String label = !address.isEmpty() && !address.equals(primary) ? primary + "\n" + address : primary;
      choices.add(new SavedPlaceChoice(label, destination));
    }

    for (BookmarkCategory child : BookmarkManager.INSTANCE.getChildrenCategories(category.getId()))
      collectSavedPlaces(child, visitedCategories, choices);
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
