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
import app.organicmaps.maplayer.MapButtonsViewModel;
import app.organicmaps.routing.RoutingPlanViewModel;
import app.organicmaps.sdk.routing.RoutingController;
import app.organicmaps.sdk.search.DisplayedCategories;
import app.organicmaps.sdk.util.Language;
import app.organicmaps.search.SearchFragmentController;
import app.organicmaps.search.SearchPageViewModel;
import app.organicmaps.search.SearchRequest;
import app.organicmaps.util.InCarVisuals;
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
    private final List<QuickActionBinding> mDirectActions = new ArrayList<>();
    @NonNull
    private final List<QuickActionBinding> mOverflowActions = new ArrayList<>();
    @NonNull
    private final List<QuickActionBinding> mRenderedOverflowActions = new ArrayList<>();

    @Nullable
    private InCarQuickActionButton mMoreButton;
    @Nullable
    private ViewGroup mAnchorParent;
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
      mLayoutListener = (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
      {
        if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop)
          scheduleActionLayout();
      };
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

      mMapButtonsViewModel.getLayoutMode().observe(mActivity, layoutMode -> renderVisibility());
      mMapButtonsViewModel.getButtonsHidden().observe(mActivity, hidden -> renderVisibility());
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
      mDirectActions.clear();
      mOverflowActions.clear();
      mRenderedOverflowActions.clear();
      mMoreButton = null;

      // Semantic priority is bottom-up on screen: More -> Home -> Work -> Parking -> Fuel/Charging.
      collectDestinationAction(InCarQuickDestinationsStore.Action.HOME, InCarQuickDestinationsStore.getHome(mActivity),
                               R.string.in_car_quick_home, R.drawable.ic_in_car_quick_home,
                               R.color.in_car_quick_home);
      collectDestinationAction(InCarQuickDestinationsStore.Action.WORK, InCarQuickDestinationsStore.getWork(mActivity),
                               R.string.in_car_quick_work, R.drawable.ic_in_car_quick_work,
                               R.color.in_car_quick_work);
      collectFixedAction(InCarQuickDestinationsStore.Action.PARKING, R.string.category_parking,
                         R.drawable.ic_in_car_quick_parking, R.color.in_car_quick_parking,
                         () -> openCategory(InCarQuickCategoryPolicy.Category.PARKING));
      collectFuelChargingAction();

      collectOverflowAction(InCarQuickDestinationsStore.Action.TOILETS, R.string.category_toilet,
                            R.drawable.ic_in_car_quick_toilets, R.color.in_car_quick_toilets,
                            () -> openCategory(InCarQuickCategoryPolicy.Category.TOILETS));
      collectOverflowAction(InCarQuickDestinationsStore.Action.FOOD, R.string.in_car_quick_food,
                            R.drawable.ic_in_car_quick_food, R.color.in_car_quick_food,
                            () -> openCategory(InCarQuickCategoryPolicy.Category.FOOD));
      collectOverflowDestination(InCarQuickDestinationsStore.Action.RECENT_1,
                                 InCarQuickDestinationsStore.getRecent(mActivity, 1), R.string.in_car_quick_recent_1,
                                 R.drawable.ic_in_car_quick_recent, R.color.in_car_quick_recent_1);
      collectOverflowDestination(InCarQuickDestinationsStore.Action.RECENT_2,
                                 InCarQuickDestinationsStore.getRecent(mActivity, 2), R.string.in_car_quick_recent_2,
                                 R.drawable.ic_in_car_quick_recent, R.color.in_car_quick_recent_2);

      renderActionLayout();
      renderVisibility();
      updateRootBounds();
    }

    private void collectFixedAction(@NonNull InCarQuickDestinationsStore.Action action, @StringRes int labelRes,
                                    @DrawableRes int iconRes, @ColorRes int colorRes, @NonNull Runnable click)
    {
      if (!InCarQuickDestinationsPolicy.shouldShow(BuildConfig.IS_IN_CAR, action, isEnabled(action), true))
        return;
      final String label = mActivity.getString(labelRes);
      final InCarQuickActionButton button = createButton(colorRes, iconRes);
      button.setContentDescription(label);
      button.setOnClickListener(v -> click.run());
      mDirectActions.add(new QuickActionBinding(button, label, click));
    }

    private void collectDestinationAction(@NonNull InCarQuickDestinationsStore.Action action,
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
      final Runnable actionRun = () -> mActivity.startLocationToPoint(destination.toMapObject());
      button.setContentDescription(description);
      button.setOnClickListener(v -> actionRun.run());
      mDirectActions.add(new QuickActionBinding(button, description, actionRun));
    }

    private void collectFuelChargingAction()
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

      final String label = mActivity.getString(labelRes);
      final InCarQuickActionButton button = createButton(R.color.in_car_quick_fuel_charging, iconRes);
      button.setContentDescription(label);
      button.setOnClickListener(v -> click.run());
      mDirectActions.add(new QuickActionBinding(button, label, click));
    }

    private void collectOverflowAction(@NonNull InCarQuickDestinationsStore.Action action, @StringRes int labelRes,
                                       @DrawableRes int iconRes, @ColorRes int colorRes, @NonNull Runnable click)
    {
      if (!InCarQuickDestinationsPolicy.shouldShow(BuildConfig.IS_IN_CAR, action, isEnabled(action), true))
        return;
      final String label = mActivity.getString(labelRes);
      final InCarQuickActionButton button = createButton(colorRes, iconRes);
      mOverflowActions.add(new QuickActionBinding(button, label, click));
    }

    private void collectOverflowDestination(@NonNull InCarQuickDestinationsStore.Action action,
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
      mOverflowActions.add(
          new QuickActionBinding(button, description, () -> mActivity.startLocationToPoint(destination.toMapObject())));
    }

    private void renderActionLayout()
    {
      applyResponsiveButtonSizing();

      final int actionSizeDp = pxToDp(InCarVisuals.currentQuickActionSizePx(mActivity));
      final int availableHeightDp = availableHeightDp();
      final int capacity = InCarQuickDestinationsLayoutPolicy.maxVisibleActions(
          availableHeightDp, actionSizeDp, InCarQuickDestinationsLayoutPolicy.MIN_ACTION_GAP_DP);
      final int visibleDirectCount = InCarQuickDestinationsLayoutPolicy.directActionCountForCapacity(
          capacity, mDirectActions.size(), mOverflowActions.size());

      mRenderedOverflowActions.clear();
      mRenderedOverflowActions.addAll(mOverflowActions);
      for (int i = visibleDirectCount; i < mDirectActions.size(); ++i)
        mRenderedOverflowActions.add(mDirectActions.get(i));

      final boolean showMore = InCarQuickDestinationsLayoutPolicy.shouldShowMore(
          capacity, mDirectActions.size(), visibleDirectCount, mRenderedOverflowActions.size());
      final int visibleCount = visibleDirectCount + (showMore ? 1 : 0);
      final int gapDp = InCarQuickDestinationsLayoutPolicy.resolvedGapDp(availableHeightDp, actionSizeDp, visibleCount);

      final List<InCarQuickActionButton> rendered = new ArrayList<>(visibleCount);
      // The container is bottom-anchored. Reverse direct priority so Home/Work sit immediately above More.
      for (int i = visibleDirectCount - 1; i >= 0; --i)
        rendered.add(mDirectActions.get(i).button);
      if (showMore)
        rendered.add(ensureMoreButton());

      mContainer.removeAllViews();
      for (int i = 0; i < rendered.size(); ++i)
      {
        final InCarQuickActionButton button = rendered.get(i);
        setBottomGap(button, i + 1 < rendered.size() ? gapDp : 0);
        mContainer.addView(button);
      }
      mRoot.bringToFront();
      updateRootBounds();
    }

    @NonNull
    private InCarQuickActionButton ensureMoreButton()
    {
      if (mMoreButton == null)
      {
        mMoreButton = createButton(R.color.in_car_quick_primary, R.drawable.ic_in_car_quick_more);
        mMoreButton.setContentDescription(mActivity.getString(R.string.in_car_quick_more));
        mMoreButton.setOnClickListener(v -> showOverflowChoice(mRenderedOverflowActions));
      }
      resizeButton(mMoreButton);
      return mMoreButton;
    }

    private void scheduleActionLayout()
    {
      if (mLayoutPassScheduled)
        return;
      mLayoutPassScheduled = true;
      mRoot.post(() -> {
        mLayoutPassScheduled = false;
        renderActionLayout();
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
      final int minimum = InCarVisuals.currentQuickActionSizePx(mActivity);
      final int availablePx = Math.max(
          minimum,
          parentHeight - mSystemTopInset - dp(InCarQuickDestinationsLayoutPolicy.SAFE_TOP_GAP_DP) - bottomMargin);
      return Math.max(1, pxToDp(availablePx));
    }

    @NonNull
    private InCarQuickActionButton createButton(@ColorRes int colorRes, @DrawableRes int iconRes)
    {
      final InCarQuickActionButton button = new InCarQuickActionButton(mActivity);
      resizeButton(button);
      final int actionSize = InCarVisuals.currentQuickActionSizePx(mActivity);
      final int iconSize = InCarVisuals.currentQuickActionIconSizePx(mActivity);
      final int iconPadding = Math.max(0, (actionSize - iconSize) / 2);
      button.setAppearance(iconRes, ContextCompat.getColor(mActivity, colorRes), quickForegroundColor(),
                           dp(InCarQuickDestinationsLayoutPolicy.ACTION_CORNER_RADIUS_DP), iconPadding);
      ViewCompat.setElevation(button, mActivity.getResources().getDimension(R.dimen.in_car_quick_elevation));
      return button;
    }

    private void applyResponsiveButtonSizing()
    {
      for (QuickActionBinding binding : mDirectActions)
        resizeButton(binding.button);
      for (QuickActionBinding binding : mOverflowActions)
        resizeButton(binding.button);
      if (mMoreButton != null)
        resizeButton(mMoreButton);
    }

    private void resizeButton(@NonNull InCarQuickActionButton button)
    {
      final int size = InCarVisuals.currentQuickActionSizePx(mActivity);
      final int iconSize = InCarVisuals.currentQuickActionIconSizePx(mActivity);
      final int iconPadding = Math.max(0, (size - iconSize) / 2);
      final ViewGroup.LayoutParams raw = button.getLayoutParams();
      final LinearLayout.LayoutParams params =
          raw instanceof LinearLayout.LayoutParams layoutParams ? layoutParams : new LinearLayout.LayoutParams(size, size);
      params.width = size;
      params.height = size;
      button.setLayoutParams(params);
      button.setMinimumWidth(size);
      button.setMinimumHeight(size);
      button.setPadding(iconPadding, iconPadding, iconPadding, iconPadding);
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

    private void setBottomGap(@NonNull View button, int gapDp)
    {
      final ViewGroup.LayoutParams raw = button.getLayoutParams();
      final int size = InCarVisuals.currentQuickActionSizePx(mActivity);
      final LinearLayout.LayoutParams params =
          raw instanceof LinearLayout.LayoutParams layoutParams ? layoutParams : new LinearLayout.LayoutParams(size, size);
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
        final Insets bars =
            windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
        mSystemTopInset = bars.top;
        mSystemBottomInset = bars.bottom;
        updateBottomMargin();
        scheduleActionLayout();
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
        scheduleActionLayout();
        return;
      }
      params.bottomMargin = bottom;
      mRoot.setLayoutParams(params);
      scheduleActionLayout();
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

    private int pxToDp(int value)
    {
      return Math.round(value / mActivity.getResources().getDisplayMetrics().density);
    }
  }
}
