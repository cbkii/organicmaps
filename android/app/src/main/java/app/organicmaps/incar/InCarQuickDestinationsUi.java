package app.organicmaps.incar;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.FrameLayout;
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
import androidx.fragment.app.Fragment;
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
import app.organicmaps.search.SearchFragmentController;
import app.organicmaps.search.SearchPageViewModel;
import app.organicmaps.search.SearchRequest;
import app.organicmaps.widget.placepage.PlacePageViewModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Lifecycle-owned presentation/controller adapter for the InCar Quick Destinations vertical stack. */
public final class InCarQuickDestinationsUi
{
  private InCarQuickDestinationsUi() {}

  public static void attach(@NonNull MwmActivity activity)
  {
    if (!BuildConfig.IS_IN_CAR)
      return;

    FrameLayout root = activity.findViewById(R.id.in_car_quick_destinations);
    if (root == null)
    {
      final ViewStub stub = activity.findViewById(R.id.in_car_quick_destinations_stub);
      if (stub == null)
        return;
      final View inflated = stub.inflate();
      if (!(inflated instanceof FrameLayout frameLayout))
        return;
      root = frameLayout;
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
    private static final class QuickActionBinding
    {
      @NonNull
      final InCarQuickActionButton button;
      @NonNull
      final String label;
      @NonNull
      final Runnable action;

      QuickActionBinding(@NonNull InCarQuickActionButton button, @NonNull String label, @NonNull Runnable action)
      {
        this.button = button;
        this.label = label;
        this.action = action;
      }
    }

    @NonNull
    private final MwmActivity mActivity;
    @NonNull
    private final FrameLayout mRoot;
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
    private final View.OnLayoutChangeListener mLayoutListener;
    @NonNull
    private final List<QuickActionBinding> mQuickActions = new ArrayList<>();

    @Nullable
    private InCarQuickActionButton mPrimaryButton;
    @Nullable
    private InCarQuickActionButton mOverflowButton;
    @Nullable
    private ViewGroup mAnchorParent;
    private boolean mExpanded;
    private boolean mRegular = true;
    private boolean mButtonsHidden;
    private boolean mSearchOpen;
    private boolean mPlacePageOpen;
    private boolean mLayoutPassScheduled;
    private int mBottomButtonsHeight;
    private int mSystemTopInset;
    private int mSystemBottomInset;

    Controller(@NonNull MwmActivity activity, @NonNull FrameLayout root, @NonNull LinearLayout container)
    {
      mActivity = activity;
      mRoot = root;
      mContainer = container;
      mPrefs = MwmApplication.prefs(activity);
      mLayoutListener = (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
        if (mExpanded && (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop))
          scheduleExpandedLayout();
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
      mRoot.addOnLayoutChangeListener(mLayoutListener);
      if (mRoot.getParent() instanceof ViewGroup parent)
      {
        mAnchorParent = parent;
        parent.addOnLayoutChangeListener(mLayoutListener);
      }
      ViewCompat.setElevation(mRoot, dp(8));
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
      mRoot.removeOnLayoutChangeListener(mLayoutListener);
      if (mAnchorParent != null)
        mAnchorParent.removeOnLayoutChangeListener(mLayoutListener);
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
      mQuickActions.clear();
      mOverflowButton = null;
      addPrimaryToggleAction();
      addFuelChargingAction();
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
    }

    private void addFuelChargingAction()
    {
      final InCarQuickDestinationsPolicy.FuelChargingMode mode = InCarQuickDestinationsPolicy.resolveFuelChargingMode(
          isEnabled(InCarQuickDestinationsStore.Action.FUEL), isEnabled(InCarQuickDestinationsStore.Action.CHARGING));
      if (mode == InCarQuickDestinationsPolicy.FuelChargingMode.HIDDEN)
        return;

      final int labelRes;
      final int iconRes;
      final Runnable click;
      switch (mode)
      {
      case FUEL:
        labelRes = R.string.in_car_quick_fuel;
        iconRes = R.drawable.ic_in_car_quick_fuel;
        click = () -> openCategory(InCarQuickCategoryPolicy.Category.FUEL);
        break;
      case CHARGING:
        labelRes = R.string.in_car_quick_charging;
        iconRes = R.drawable.ic_in_car_quick_charging;
        click = () -> openCategory(InCarQuickCategoryPolicy.Category.CHARGING);
        break;
      case CHOOSER:
        labelRes = R.string.in_car_quick_fuel_charging;
        iconRes = R.drawable.ic_in_car_quick_fuel;
        click = this::showFuelChargingChoice;
        break;
      case HIDDEN:
      default: return;
      }

      final InCarQuickActionButton button = createButton(R.color.in_car_quick_fuel_charging, iconRes);
      addQuickAction(button, mActivity.getString(labelRes), mActivity.getString(labelRes), click);
    }

    private void addFixedAction(@NonNull InCarQuickDestinationsStore.Action action, @StringRes int labelRes,
                                @DrawableRes int iconRes, @ColorRes int colorRes, @NonNull Runnable click)
    {
      if (!InCarQuickDestinationsPolicy.shouldShow(BuildConfig.IS_IN_CAR, action, isEnabled(action), true))
        return;

      final InCarQuickActionButton button = createButton(colorRes, iconRes);
      final String label = mActivity.getString(labelRes);
      addQuickAction(button, label, label, click);
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
      final String description =
          displayLabel.isEmpty()
              ? actionLabel
              : mActivity.getString(R.string.in_car_quick_destination_description, actionLabel, displayLabel);
      addQuickAction(button, description, description, () -> mActivity.startLocationToPoint(destination.toMapObject()));
    }

    private void addQuickAction(@NonNull InCarQuickActionButton button, @NonNull String menuLabel,
                                @NonNull String accessibilityLabel, @NonNull Runnable action)
    {
      button.setContentDescription(accessibilityLabel);
      button.setOnClickListener(v -> action.run());
      mQuickActions.add(new QuickActionBinding(button, menuLabel, action));
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
      button.setLayoutParams(new LinearLayout.LayoutParams(width, height));
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

    private void renderExpandedLayout()
    {
      if (!mExpanded || mPrimaryButton == null)
        return;

      final int availableHeightDp = availableHeightDp();
      final int capacity = InCarQuickDestinationsLayoutPolicy.maxVisibleDestinationActions(availableHeightDp);
      final boolean needsOverflow =
          InCarQuickDestinationsLayoutPolicy.requiresOverflow(availableHeightDp, mQuickActions.size());
      final int directCount = needsOverflow ? Math.max(0, capacity - 1) : Math.min(capacity, mQuickActions.size());
      final List<QuickActionBinding> overflowActions = new ArrayList<>();
      if (needsOverflow)
      {
        for (int i = directCount; i < mQuickActions.size(); ++i)
          overflowActions.add(mQuickActions.get(i));
      }

      mContainer.removeAllViews();
      mOverflowButton = null;
      final boolean showOverflow = needsOverflow && capacity > 0 && !overflowActions.isEmpty();
      final int visibleActionSlots = directCount + (showOverflow ? 1 : 0);
      final int gapDp = InCarQuickDestinationsLayoutPolicy.resolvedGapDp(availableHeightDp, visibleActionSlots);

      if (showOverflow)
      {
        final InCarQuickActionButton overflow =
            createButton(R.color.in_car_quick_primary, R.drawable.ic_in_car_quick_more);
        overflow.setContentDescription(mActivity.getString(R.string.in_car_quick_more));
        overflow.setOnClickListener(v -> showOverflowChoice(overflowActions));
        setBottomGap(overflow, gapDp);
        mOverflowButton = overflow;
        mContainer.addView(overflow);
      }

      // Highest-priority actions stay closest to the Quick anchor at the bottom of the stack.
      for (int i = directCount - 1; i >= 0; --i)
      {
        final InCarQuickActionButton button = mQuickActions.get(i).button;
        setBottomGap(button, gapDp);
        mContainer.addView(button);
      }

      setBottomGap(mPrimaryButton, 0);
      mContainer.addView(mPrimaryButton);
      mRoot.bringToFront();
      updateRootBounds();
    }

    private void showOverflowChoice(@NonNull List<QuickActionBinding> actions)
    {
      final List<String> choices = new ArrayList<>(actions.size());
      for (QuickActionBinding action : actions)
        choices.add(action.label);

      final InCarChoiceAdapter adapter = new InCarChoiceAdapter(mActivity, choices);
      final AlertDialog dialog = new AlertDialog.Builder(mActivity)
                                     .setTitle(R.string.in_car_quick_more)
                                     .setAdapter(adapter, (ignored, which) -> actions.get(which).action.run())
                                     .create();
      dialog.setOnShowListener(ignored -> InCarDialogSizing.applyCompactWidth(mActivity, dialog));
      dialog.show();
    }

    private void showFuelChargingChoice()
    {
      final List<String> choices = new ArrayList<>(2);
      choices.add(mActivity.getString(R.string.in_car_quick_fuel));
      choices.add(mActivity.getString(R.string.in_car_quick_charging));
      final InCarChoiceAdapter adapter = new InCarChoiceAdapter(mActivity, choices);
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

      final Fragment searchController =
          mActivity.getSupportFragmentManager().findFragmentById(R.id.search_container_fragment);
      if (searchController instanceof SearchFragmentController controller)
        controller.beginInCarQuickDestinationsSearch();
      mSearchOpen = true;
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
      if (mPrimaryButton == null)
        return;

      mPrimaryButton.setContentDescription(
          mActivity.getString(mExpanded ? R.string.in_car_quick_collapse : R.string.in_car_quick_expand));

      if (!mExpanded)
      {
        mContainer.removeAllViews();
        mOverflowButton = null;
        setBottomGap(mPrimaryButton, 0);
        mContainer.addView(mPrimaryButton);
        updateRootBounds();
        return;
      }

      scheduleExpandedLayout();
    }

    private void scheduleExpandedLayout()
    {
      if (!mExpanded || mLayoutPassScheduled)
        return;
      mLayoutPassScheduled = true;
      mRoot.post(() -> {
        mLayoutPassScheduled = false;
        renderExpandedLayout();
      });
    }

    private int availableHeightDp()
    {
      int parentHeight = mAnchorParent == null ? 0 : mAnchorParent.getHeight();
      if (parentHeight <= 0)
        parentHeight = mActivity.getResources().getDisplayMetrics().heightPixels;

      int bottomMargin = 0;
      if (mRoot.getLayoutParams() instanceof ViewGroup.MarginLayoutParams params)
        bottomMargin = Math.max(0, params.bottomMargin);
      final int availablePx = Math.max(
          dp(InCarQuickDestinationsLayoutPolicy.ACTION_SIZE_DP),
          parentHeight - mSystemTopInset - dp(InCarQuickDestinationsLayoutPolicy.SAFE_TOP_GAP_DP) - bottomMargin);
      return Math.max(InCarQuickDestinationsLayoutPolicy.ACTION_SIZE_DP,
                      Math.round(availablePx / mActivity.getResources().getDisplayMetrics().density));
    }

    private void setBottomGap(@NonNull View button, int gapDp)
    {
      final ViewGroup.LayoutParams raw = button.getLayoutParams();
      final LinearLayout.LayoutParams params =
          raw instanceof LinearLayout.LayoutParams layoutParams
              ? layoutParams
              : new LinearLayout.LayoutParams(dp(InCarQuickDestinationsLayoutPolicy.ACTION_SIZE_DP),
                                              dp(InCarQuickDestinationsLayoutPolicy.ACTION_SIZE_DP));
      params.setMargins(0, 0, 0, dp(Math.max(0, gapDp)));
      button.setLayoutParams(params);
    }

    private void updateRootBounds()
    {
      final ViewGroup.LayoutParams params = mRoot.getLayoutParams();
      boolean changed = false;
      if (params.width != ViewGroup.LayoutParams.WRAP_CONTENT)
      {
        params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        changed = true;
      }
      if (params.height != ViewGroup.LayoutParams.WRAP_CONTENT)
      {
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        changed = true;
      }
      if (changed)
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
        final Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                                                   | WindowInsetsCompat.Type.displayCutout());
        mSystemTopInset = bars.top;
        mSystemBottomInset = bars.bottom;
        updateBottomMargin();
        scheduleExpandedLayout();
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
      {
        scheduleExpandedLayout();
        return;
      }
      params.bottomMargin = bottom;
      mRoot.setLayoutParams(params);
      scheduleExpandedLayout();
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
