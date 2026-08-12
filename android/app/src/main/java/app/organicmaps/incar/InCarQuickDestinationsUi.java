package app.organicmaps.incar;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
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
import app.organicmaps.sdk.Router;
import app.organicmaps.sdk.bookmarks.data.MapObject;
import app.organicmaps.sdk.location.LocationState;
import app.organicmaps.sdk.routing.RoutingController;
import app.organicmaps.sdk.search.DisplayedCategories;
import app.organicmaps.sdk.util.Language;
import app.organicmaps.search.SearchPageViewModel;
import app.organicmaps.search.SearchRequest;
import app.organicmaps.widget.placepage.PlacePageViewModel;
import com.google.android.material.button.MaterialButton;
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

    @Nullable
    private MaterialButton mPrimaryButton;
    @Nullable
    private InCarQuickDestination mPendingNavigation;
    private boolean mExpanded = true;
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
      final ViewModelProvider provider = new ViewModelProvider(activity);
      mMapButtonsViewModel = provider.get(MapButtonsViewModel.class);
      mRoutingPlanViewModel = provider.get(RoutingPlanViewModel.class);
      mSearchPageViewModel = provider.get(SearchPageViewModel.class);
      mPlacePageViewModel = provider.get(PlacePageViewModel.class);
    }

    void attach()
    {
      mPrefs.registerOnSharedPreferenceChangeListener(this);
      applyInsets();

      mMapButtonsViewModel.getLayoutMode().observe(mActivity, layoutMode -> {
        if (layoutMode == null)
          return;
        final boolean regular = layoutMode == MapButtonsController.LayoutMode.regular;
        if (mRegular && !regular)
          collapseForMapTransition();
        if (!mRegular && regular && mPendingNavigation != null && !RoutingController.get().isPlanning())
          clearPendingNavigation();
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
      mRoutingPlanViewModel.getMenuUpdateTrigger().observe(mActivity, ignored -> {
        handlePendingNavigation();
        recordConfirmedDestination();
      });
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
      clearPendingNavigation();
      mPrefs.unregisterOnSharedPreferenceChangeListener(this);
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

    private void handlePendingNavigation()
    {
      if (mPendingNavigation == null)
        return;

      final RoutingController routing = RoutingController.get();
      if (routing.isErrorEncountered())
      {
        clearPendingNavigation();
        return;
      }
      if (!routing.isBuilt())
        return;

      final InCarQuickDestination builtDestination = InCarQuickDestination.fromMapObject(routing.getEndPoint());
      if (!routing.isVehicleRouterType() || !mPendingNavigation.samePlace(builtDestination))
      {
        clearPendingNavigation();
        return;
      }

      // One-shot before invoking the normal start gates: a later routing update must never start stale intent.
      clearPendingNavigation();
      if (!mActivity.showStartPointNotice())
        return;
      if (!mActivity.showRoutingDisclaimer())
        return;
      mActivity.closeFloatingPanels();
      mActivity.setFullscreen(false);
      routing.start();
    }

    private void rebuildButtons()
    {
      mContainer.removeAllViews();
      addPrimaryToggleAction();
      addFixedAction(InCarQuickDestinationsStore.Action.FUEL_CHARGING, R.string.in_car_quick_fuel_charging,
                     R.drawable.ic_in_car_quick_fuel, R.color.in_car_quick_fuel_charging,
                     () -> openCategory(InCarQuickCategoryPolicy.Category.FUEL_CHARGING));
      addFixedAction(InCarQuickDestinationsStore.Action.PARKING, R.string.category_parking,
                     R.drawable.ic_in_car_quick_parking, R.color.in_car_quick_parking,
                     () -> openCategory(InCarQuickCategoryPolicy.Category.PARKING));
      addFixedAction(InCarQuickDestinationsStore.Action.TOILETS, R.string.category_toilet,
                     R.drawable.ic_in_car_quick_toilets, R.color.in_car_quick_toilets,
                     () -> openCategory(InCarQuickCategoryPolicy.Category.TOILETS));
      addFixedAction(InCarQuickDestinationsStore.Action.FOOD, R.string.in_car_quick_food,
                     R.drawable.ic_in_car_quick_food, R.color.in_car_quick_food,
                     () -> openCategory(InCarQuickCategoryPolicy.Category.FOOD));
      addFixedAction(InCarQuickDestinationsStore.Action.REST_WATER, R.string.in_car_quick_rest_water,
                     R.drawable.ic_in_car_quick_rest_water, R.color.in_car_quick_rest_water,
                     () -> openCategory(InCarQuickCategoryPolicy.Category.REST_WATER));

      addDestinationAction(InCarQuickDestinationsStore.Action.HOME, InCarQuickDestinationsStore.getHome(mActivity),
                           R.string.in_car_quick_home, R.drawable.ic_in_car_quick_home, R.color.in_car_quick_home,
                           false);
      addDestinationAction(InCarQuickDestinationsStore.Action.WORK, InCarQuickDestinationsStore.getWork(mActivity),
                           R.string.in_car_quick_work, R.drawable.ic_in_car_quick_work, R.color.in_car_quick_work,
                           false);
      addDestinationAction(InCarQuickDestinationsStore.Action.RECENT_1,
                           InCarQuickDestinationsStore.getRecent(mActivity, 1), R.string.in_car_quick_recent_1, 0,
                           R.color.in_car_quick_recent_1, true);
      addDestinationAction(InCarQuickDestinationsStore.Action.RECENT_2,
                           InCarQuickDestinationsStore.getRecent(mActivity, 2), R.string.in_car_quick_recent_2, 0,
                           R.color.in_car_quick_recent_2, true);
      renderVisibility();
      renderExpansion();
    }

    private void addPrimaryToggleAction()
    {
      final MaterialButton button =
          createButton(R.color.in_car_quick_primary, InCarQuickDestinationsLayoutPolicy.PRIMARY_ACTION_WIDTH_DP);
      button.setTextColor(quickForegroundColor());
      button.setTextSize(14.0f);
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

      final MaterialButton button = createButton(colorRes);
      button.setIconResource(iconRes);
      button.setIconTint(ColorStateList.valueOf(quickForegroundColor()));
      button.setContentDescription(mActivity.getString(labelRes));
      button.setOnClickListener(v -> click.run());
      mContainer.addView(button);
    }

    private void addDestinationAction(@NonNull InCarQuickDestinationsStore.Action action,
                                      @Nullable InCarQuickDestination destination, @StringRes int labelRes,
                                      @DrawableRes int iconRes, @ColorRes int colorRes, boolean useGlyph)
    {
      if (!InCarQuickDestinationsPolicy.shouldShow(BuildConfig.IS_IN_CAR, action, isEnabled(action),
                                                   destination != null))
        return;
      if (destination == null)
        return;

      final MaterialButton button = createButton(colorRes);
      if (useGlyph)
      {
        button.setText(InCarQuickDestinationGlyphPolicy.glyph(destination.getDisplayLabel()));
        button.setTextColor(quickForegroundColor());
        button.setTextSize(18.0f);
      }
      else
      {
        button.setIconResource(iconRes);
        button.setIconTint(ColorStateList.valueOf(quickForegroundColor()));
      }

      final String displayLabel = destination.getDisplayLabel();
      button.setContentDescription(displayLabel.isEmpty() ? mActivity.getString(labelRes) : displayLabel);
      button.setOnClickListener(v -> startExactNavigation(destination));
      mContainer.addView(button);
    }

    private void startExactNavigation(@NonNull InCarQuickDestination destination)
    {
      mPendingNavigation = destination;
      mActivity.closeFloatingPanels();
      if (LocationState.getMode() == LocationState.NOT_FOLLOW_NO_POSITION)
        LocationState.nativeSwitchToNextMode();

      final MapObject startPoint = MwmApplication.from(mActivity).getLocationHelper().getMyPosition();
      RoutingController.get().prepare(startPoint, destination.toMapObject(), Router.Vehicle);
    }

    private void clearPendingNavigation()
    {
      mPendingNavigation = null;
    }

    @NonNull
    private MaterialButton createButton(@ColorRes int colorRes)
    {
      return createButton(colorRes, InCarQuickDestinationsLayoutPolicy.ACTION_SIZE_DP);
    }

    @NonNull
    private MaterialButton createButton(@ColorRes int colorRes, int widthDp)
    {
      final MaterialButton button =
          new MaterialButton(mActivity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
      final int width = dp(widthDp);
      final int height = dp(InCarQuickDestinationsLayoutPolicy.ACTION_SIZE_DP);
      final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
      params.setMarginEnd(dp(InCarQuickDestinationsLayoutPolicy.ACTION_GAP_DP));
      button.setLayoutParams(params);
      button.setMinWidth(0);
      button.setMinHeight(0);
      button.setMinimumWidth(width);
      button.setMinimumHeight(height);
      button.setPadding(0, 0, 0, 0);
      button.setInsetTop(0);
      button.setInsetBottom(0);
      button.setCornerRadius(dp(18));
      button.setIconSize(dp(32));
      button.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(mActivity, colorRes)));
      button.setStrokeWidth(0);
      button.setAllCaps(false);
      ViewCompat.setElevation(button, dp(4));
      return button;
    }

    private void openCategory(@NonNull InCarQuickCategoryPolicy.Category category)
    {
      clearPendingNavigation();
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
      {
        mPrimaryButton.setText(mExpanded ? R.string.in_car_quick_toggle_expanded
                                         : R.string.in_car_quick_toggle_collapsed);
        mPrimaryButton.setContentDescription(
            mActivity.getString(mExpanded ? R.string.in_car_quick_collapse : R.string.in_car_quick_expand));
      }

      if (!mExpanded)
        mRoot.scrollTo(0, 0);
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
