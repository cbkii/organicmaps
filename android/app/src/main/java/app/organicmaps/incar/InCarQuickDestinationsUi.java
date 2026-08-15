package app.organicmaps.incar;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.ArrayAdapter;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import app.organicmaps.BuildConfig;
import app.organicmaps.MwmActivity;
import app.organicmaps.MwmApplication;
import app.organicmaps.R;
import app.organicmaps.maplayer.MapButtonsController;
import app.organicmaps.maplayer.MapButtonsViewModel;
import app.organicmaps.routing.RoutingPlanViewModel;
import app.organicmaps.sdk.routing.RoutingController;
import app.organicmaps.sdk.search.DisplayedCategories;
import app.organicmaps.sdk.util.Language;
import app.organicmaps.search.SearchPageViewModel;
import app.organicmaps.search.SearchRequest;
import app.organicmaps.widget.placepage.PlacePageViewModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Lifecycle-owned presentation/controller adapter for the InCar Quick Destinations strip. */
public final class InCarQuickDestinationsUi
{
  private InCarQuickDestinationsUi() {}

  public static void attach(@NonNull MwmActivity activity)
  {
    if (!BuildConfig.IS_IN_CAR)
      return;

    HorizontalScrollView root = activity.findViewById(R.id.in_car_quick_destinations);
    if (root == null)
    {
      final ViewStub stub = activity.findViewById(R.id.in_car_quick_destinations_stub);
      if (stub == null)
        return;
      final View inflated = stub.inflate();
      if (!(inflated instanceof HorizontalScrollView scrollView))
        return;
      root = scrollView;
    }

    if (root.getTag() instanceof Controller controller)
    {
      controller.refresh();
      return;
    }

    final LinearLayout container = root.findViewById(R.id.in_car_quick_destinations_container);
    if (container == null)
      return;

    final Controller controller = new Controller(activity, root, container);
    root.setTag(controller);
    activity.getLifecycle().addObserver(controller);
    controller.attach();
  }

  private static final class Controller
      implements DefaultLifecycleObserver, SharedPreferences.OnSharedPreferenceChangeListener
  {
    @NonNull
    private final MwmActivity mActivity;
    @NonNull
    private final HorizontalScrollView mRoot;
    @NonNull
    private final LinearLayout mContainer;
    @NonNull
    private final SharedPreferences mPrefs;
    @NonNull
    private final MapButtonsViewModel mMapButtonsViewModel;
    @NonNull
    private final RoutingPlanViewModel mRoutingPlanViewModel;
    @NonNull
    private final SearchPageViewModel mSearchPageViewModel;
    @NonNull
    private final PlacePageViewModel mPlacePageViewModel;
    @NonNull
    private final View.OnLayoutChangeListener mRootLayoutListener;

    @Nullable
    private InCarQuickActionButton mPrimaryButton;
    @Nullable
    private InCarQuickActionButton mOverflowButton;
    private boolean mExpanded;
    private boolean mRegular = true;
    private boolean mButtonsHidden;
    private boolean mSearchOpen;
    private boolean mPlacePageOpen;
    private int mBottomButtonsHeight;
    private int mSystemBottomInset;

    Controller(@NonNull MwmActivity activity, @NonNull HorizontalScrollView root, @NonNull LinearLayout container)
    {
      mActivity = activity;
      mRoot = root;
      mContainer = container;
      mPrefs = MwmApplication.prefs(activity);
      mRootLayoutListener = (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
      {
        if (right - left != oldRight - oldLeft)
          mRoot.post(this::applyOverflowPolicy);
      };
      mExpanded = !InCarQuickDestinationsStore.startCollapsed(activity);
      final ViewModelProvider provider = new ViewModelProvider(activity);
      mMapButtonsViewModel = provider.get(MapButtonsViewModel.class);
      mRoutingPlanViewModel = provider.get(RoutingPlanViewModel.class);
      mSearchPageViewModel = provider.get(SearchPageViewModel.class);
      mPlacePageViewModel = provider.get(PlacePageViewModel.class);
    }

    void attach()
    {
      mPrefs.registerOnSharedPreferenceChangeListener(this);
      mRoot.addOnLayoutChangeListener(mRootLayoutListener);
      applyInsets();

      mMapButtonsViewModel.getLayoutMode().observe(mActivity, layoutMode -> {
        if (layoutMode == null)
          return;
        final boolean regular = layoutMode == MapButtonsController.LayoutMode.regular;
        if (mRegular && !regular)
          collapseForMapTransition();
        mRegular = regular;
        renderVisibility();
      });
      mMapButtonsViewModel.getButtonsHidden().observe(mActivity, hidden -> {
        final boolean buttonsHidden = Boolean.TRUE.equals(hidden);
        if (!mButtonsHidden && buttonsHidden)
          collapseForMapTransition();
        mButtonsHidden = buttonsHidden;
        renderVisibility();
      });
      mMapButtonsViewModel.getBottomButtonsHeight().observe(mActivity, height -> {
        mBottomButtonsHeight = height == null ? 0 : Math.max(0, Math.round(height));
        updateBottomMargin();
      });
      mRoutingPlanViewModel.getMenuUpdateTrigger().observe(mActivity, ignored -> recordConfirmedDestination());
      mSearchPageViewModel.getSearchEnabled().observe(mActivity, enabled -> {
        mSearchOpen = Boolean.TRUE.equals(enabled);
        renderVisibility();
      });
      mPlacePageViewModel.getMapObject().observe(mActivity, mapObject -> {
        mPlacePageOpen = mapObject != null;
        renderVisibility();
      });

      rebuildButtons();
    }

    void refresh()
    {
      rebuildButtons();
    }

    @Override
    public void onResume(@NonNull LifecycleOwner owner)
    {
      rebuildButtons();
      updateBottomMargin();
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner)
    {
      mPrefs.unregisterOnSharedPreferenceChangeListener(this);
      mRoot.removeOnLayoutChangeListener(mRootLayoutListener);
      ViewCompat.setOnApplyWindowInsetsListener(mRoot, null);
      mRoot.setTag(null);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String key)
    {
      if (InCarQuickDestinationsStore.isQuickPreferenceKey(key))
        rebuildButtons();
    }

    private void recordConfirmedDestination()
    {
      final RoutingController routing = RoutingController.get();
      if (!routing.isBuilt() && !routing.isNavigating())
        return;
      InCarQuickDestinationsStore.recordRecent(mActivity, routing.getEndPoint());
    }

    private void rebuildButtons()
    {
      mContainer.removeAllViews();
      mOverflowButton = null;
      addPrimaryToggleAction();
      addFixedAction(InCarQuickDestinationsStore.Action.FUEL_CHARGING, R.string.in_car_quick_fuel_charging,
                     R.drawable.ic_in_car_quick_fuel, R.color.in_car_quick_fuel_charging, this::showFuelChargingChoice);
      addFixedAction(InCarQuickDestinationsStore.Action.PARKING, R.string.category_parking,
                     R.drawable.ic_in_car_quick_parking, R.color.in_car_quick_parking,
                     () -> openCategory(InCarQuickCategoryPolicy.Category.PARKING));
      addFixedAction(InCarQuickDestinationsStore.Action.TOILETS, R.string.category_toilet,
                     R.drawable.ic_in_car_quick_toilets, R.color.in_car_quick_toilets,
                     () -> openCategory(InCarQuickCategoryPolicy.Category.TOILETS));
      addFixedAction(InCarQuickDestinationsStore.Action.FOOD, R.string.in_car_quick_food,
                     R.drawable.ic_in_car_quick_food, R.color.in_car_quick_food,
                     () -> openCategory(InCarQuickCategoryPolicy.Category.FOOD));

      addDestinationAction(InCarQuickDestinationsStore.Action.HOME, InCarQuickDestinationsStore.getHome(mActivity),
                           R.string.in_car_quick_home, R.drawable.ic_in_car_quick_home, R.color.in_car_quick_home);
      addDestinationAction(InCarQuickDestinationsStore.Action.WORK, InCarQuickDestinationsStore.getWork(mActivity),
                           R.string.in_car_quick_work, R.drawable.ic_in_car_quick_work, R.color.in_car_quick_work);
      addDestinationAction(InCarQuickDestinationsStore.Action.RECENT_1,
                           InCarQuickDestinationsStore.getRecent(mActivity, 1), R.string.in_car_quick_recent_1,
                           R.drawable.ic_in_car_quick_recent, R.color.in_car_quick_recent_1);
      addDestinationAction(InCarQuickDestinationsStore.Action.RECENT_2,
                           InCarQuickDestinationsStore.getRecent(mActivity, 2), R.string.in_car_quick_recent_2,
                           R.drawable.ic_in_car_quick_recent, R.color.in_car_quick_recent_2);
      renderVisibility();
      renderExpansion();
    }

    private void addPrimaryToggleAction()
    {
      final InCarQuickActionButton button =
          createButton(R.color.in_car_quick_primary, InCarQuickDestinationsLayoutPolicy.PRIMARY_ACTION_WIDTH_DP,
                       R.drawable.ic_in_car_quick_toggle);
      button.setOnClickListener(v -> {
        mExpanded = !mExpanded;
        renderExpansion();
      });
      mPrimaryButton = button;
      mContainer.addView(button);
    }

    private void addFixedAction(@NonNull InCarQuickDestinationsStore.Action action, @StringRes int labelRes,
                                @DrawableRes int iconRes, @ColorRes int colorRes, @NonNull Runnable click)
    {
      if (!InCarQuickDestinationsPolicy.shouldShow(BuildConfig.IS_IN_CAR, action, isEnabled(action), true))
        return;

      final InCarQuickActionButton button = createButton(colorRes, iconRes);
      button.setContentDescription(mActivity.getString(labelRes));
      button.setOnClickListener(v -> click.run());
      mContainer.addView(button);
    }

    private void addDestinationAction(@NonNull InCarQuickDestinationsStore.Action action,
                                      @Nullable InCarQuickDestination destination, @StringRes int labelRes,
                                      @DrawableRes int iconRes, @ColorRes int colorRes)
    {
      if (!InCarQuickDestinationsPolicy.shouldShow(BuildConfig.IS_IN_CAR, action, isEnabled(action),
                                                   destination != null))
        return;
      if (destination == null)
        return;

      final InCarQuickActionButton button = createButton(colorRes, iconRes);
      final String actionLabel = mActivity.getString(labelRes);
      final String displayLabel = destination.getDisplayLabel();
      button.setContentDescription(
          displayLabel.isEmpty()
              ? actionLabel
              : mActivity.getString(R.string.in_car_quick_destination_description, actionLabel, displayLabel));
      button.setOnClickListener(v -> mActivity.startLocationToPoint(destination.toMapObject()));
      mContainer.addView(button);
    }

    @NonNull
    private InCarQuickActionButton createButton(@ColorRes int colorRes, @DrawableRes int iconRes)
    {
      return createButton(colorRes, InCarQuickDestinationsLayoutPolicy.ACTION_SIZE_DP, iconRes);
    }

    @NonNull
    private InCarQuickActionButton createButton(@ColorRes int colorRes, int widthDp, @DrawableRes int iconRes)
    {
      final InCarQuickActionButton button = new InCarQuickActionButton(mActivity);
      final int width = dp(widthDp);
      final int height = dp(InCarQuickDestinationsLayoutPolicy.ACTION_SIZE_DP);
      final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
      params.setMarginEnd(dp(InCarQuickDestinationsLayoutPolicy.ACTION_GAP_DP));
      button.setLayoutParams(params);
      button.setMinimumWidth(width);
      button.setMinimumHeight(height);
      final int iconPaddingDp = Math.max(0, (InCarQuickDestinationsLayoutPolicy.ACTION_SIZE_DP
                                             - InCarQuickDestinationsLayoutPolicy.ACTION_ICON_SIZE_DP)
                                                / 2);
      button.setAppearance(iconRes, ContextCompat.getColor(mActivity, colorRes), quickForegroundColor(),
                           dp(InCarQuickDestinationsLayoutPolicy.ACTION_CORNER_RADIUS_DP), dp(iconPaddingDp));
      ViewCompat.setElevation(button, dp(4));
      return button;
    }

    private void applyOverflowPolicy()
    {
      if (!mExpanded || mRoot.getWidth() <= 0)
        return;

      if (mOverflowButton != null)
      {
        mContainer.removeView(mOverflowButton);
        mOverflowButton = null;
      }

      final int actionCount = Math.max(0, mContainer.getChildCount() - 1);
      for (int index = 1; index < mContainer.getChildCount(); index++)
        mContainer.getChildAt(index).setVisibility(View.VISIBLE);

      final int availableWidthDp = Math.round(mRoot.getWidth() / mActivity.getResources().getDisplayMetrics().density);
      if (!InCarQuickDestinationsLayoutPolicy.requiresOverflow(availableWidthDp, actionCount))
        return;

      final int capacity = InCarQuickDestinationsLayoutPolicy.maxVisibleDestinationActions(availableWidthDp);
      if (capacity <= 0)
        return;

      final int visibleActions = Math.max(0, capacity - 1);
      final List<InCarQuickActionButton> overflowActions = new ArrayList<>();
      for (int index = 1; index <= actionCount; index++)
      {
        final View child = mContainer.getChildAt(index);
        if (!(child instanceof InCarQuickActionButton button) || index <= visibleActions)
          continue;
        button.setVisibility(View.GONE);
        overflowActions.add(button);
      }

      if (overflowActions.isEmpty())
        return;

      final InCarQuickActionButton overflow =
          createButton(R.color.in_car_quick_primary, R.drawable.ic_in_car_quick_more);
      overflow.setContentDescription(mActivity.getString(R.string.in_car_quick_more));
      overflow.setOnClickListener(v -> showOverflowChoice(overflowActions));
      mOverflowButton = overflow;
      mContainer.addView(overflow);
    }

    private void showOverflowChoice(@NonNull List<InCarQuickActionButton> actions)
    {
      final List<InCarQuickActionButton> availableActions = new ArrayList<>(actions);
      final String[] choices = new String[availableActions.size()];
      for (int index = 0; index < availableActions.size(); index++)
      {
        final CharSequence description = availableActions.get(index).getContentDescription();
        choices[index] = description == null ? mActivity.getString(R.string.in_car_quick_more) : description.toString();
      }

      final ArrayAdapter<String> adapter = new ArrayAdapter<>(mActivity, android.R.layout.simple_list_item_1, choices) {
        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent)
        {
          final TextView row = (TextView) super.getView(position, convertView, parent);
          row.setMinHeight(dp(64));
          row.setGravity(Gravity.CENTER_VERTICAL);
          row.setPadding(dp(24), 0, dp(24), 0);
          return row;
        }
      };
      final AlertDialog dialog =
          new AlertDialog.Builder(mActivity)
              .setTitle(R.string.in_car_quick_more)
              .setAdapter(adapter, (ignored, which) -> availableActions.get(which).performClick())
              .create();
      dialog.setOnShowListener(ignored -> InCarDialogSizing.applyCompactWidth(mActivity, dialog));
      dialog.show();
    }

    private void showFuelChargingChoice()
    {
      final String[] choices = {mActivity.getString(R.string.in_car_quick_fuel),
                                mActivity.getString(R.string.in_car_quick_charging)};
      final ArrayAdapter<String> adapter = new ArrayAdapter<>(mActivity, android.R.layout.simple_list_item_1, choices) {
        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent)
        {
          final TextView row = (TextView) super.getView(position, convertView, parent);
          row.setMinHeight(dp(72));
          row.setGravity(Gravity.CENTER_VERTICAL);
          row.setPadding(dp(24), 0, dp(24), 0);
          row.setTextSize(18.0f);
          return row;
        }
      };
      final AlertDialog dialog =
          new AlertDialog.Builder(mActivity)
              .setAdapter(adapter,
                          (ignored, which)
                              -> openCategory(which == 0 ? InCarQuickCategoryPolicy.Category.FUEL
                                                         : InCarQuickCategoryPolicy.Category.CHARGING))
              .create();
      dialog.setOnShowListener(ignored -> InCarDialogSizing.applyCompactWidth(mActivity, dialog));
      dialog.show();
    }

    private void openCategory(@NonNull InCarQuickCategoryPolicy.Category category)
    {
      final int stringRes = InCarQuickCategoryPolicy.searchTermRes(category);
      final String locale;
      final String term;
      if (InCarQuickCategoryPolicy.usesEnglishCanonicalQuery(category))
      {
        locale = "en";
        term = getResourcesForLocale("en").getString(stringRes);
      }
      else
      {
        final String defaultLocale = Language.getDefaultLocale();
        if (DisplayedCategories.nativeIsLangSupported(defaultLocale))
        {
          locale = defaultLocale;
          term = mActivity.getString(stringRes);
        }
        else
        {
          locale = "en";
          term = getResourcesForLocale("en").getString(stringRes);
        }
      }

      mSearchPageViewModel.setSearchEnabled(true, new SearchRequest(term + " ", locale, true));
    }

    @NonNull
    private Resources getResourcesForLocale(@NonNull String language)
    {
      final Configuration configuration = new Configuration(mActivity.getResources().getConfiguration());
      configuration.setLocale(Locale.forLanguageTag(language));
      final Context localized = mActivity.createConfigurationContext(configuration);
      return localized.getResources();
    }

    private void collapseForMapTransition()
    {
      if (!mExpanded)
        return;
      mExpanded = false;
      renderExpansion();
    }

    private void renderExpansion()
    {
      updateRootWidth();
      for (int index = 1; index < mContainer.getChildCount(); index++)
        mContainer.getChildAt(index).setVisibility(mExpanded ? View.VISIBLE : View.GONE);

      if (mPrimaryButton != null)
        mPrimaryButton.setContentDescription(
            mActivity.getString(mExpanded ? R.string.in_car_quick_collapse : R.string.in_car_quick_expand));

      if (!mExpanded)
        mRoot.scrollTo(0, 0);
      else
        mRoot.post(this::applyOverflowPolicy);
    }

    private void updateRootWidth()
    {
      final ViewGroup.LayoutParams params = mRoot.getLayoutParams();
      final int width = mExpanded ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT;
      if (params.width == width)
        return;
      params.width = width;
      mRoot.setLayoutParams(params);
    }

    private void renderVisibility()
    {
      final boolean visible =
          InCarQuickDestinationsPolicy.shouldShowSurface(BuildConfig.IS_IN_CAR, mSearchOpen, mPlacePageOpen);
      mRoot.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void applyInsets()
    {
      ViewCompat.setOnApplyWindowInsetsListener(mRoot, (view, windowInsets) -> {
        final Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
        mSystemBottomInset = bars.bottom;
        updateBottomMargin();
        mRoot.post(this::applyOverflowPolicy);
        return windowInsets;
      });
      ViewCompat.requestApplyInsets(mRoot);
    }

    private void updateBottomMargin()
    {
      final ViewGroup.LayoutParams rawParams = mRoot.getLayoutParams();
      if (!(rawParams instanceof ViewGroup.MarginLayoutParams params))
        return;
      final int bottom = mBottomButtonsHeight + mSystemBottomInset + dp(12);
      if (params.bottomMargin == bottom)
        return;
      params.bottomMargin = bottom;
      mRoot.setLayoutParams(params);
    }

    private boolean isEnabled(@NonNull InCarQuickDestinationsStore.Action action)
    {
      return InCarQuickDestinationsStore.isActionEnabled(mActivity, action);
    }

    private int quickForegroundColor()
    {
      return ContextCompat.getColor(mActivity, R.color.in_car_quick_foreground);
    }

    private int dp(int value)
    {
      return Math.round(value * mActivity.getResources().getDisplayMetrics().density);
    }
  }
}
