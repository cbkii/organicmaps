package app.organicmaps.incar;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
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
import app.organicmaps.search.SearchPageViewModel;
import app.organicmaps.search.SearchRequest;
import app.organicmaps.sdk.routing.RoutingController;
import app.organicmaps.sdk.search.DisplayedCategories;
import app.organicmaps.sdk.util.Language;
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

  private static final class Controller implements DefaultLifecycleObserver,
                                                   SharedPreferences.OnSharedPreferenceChangeListener
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
    private final SearchPageViewModel mSearchPageViewModel;
    @NonNull
    private final PlacePageViewModel mPlacePageViewModel;

    private boolean mRegular = true;
    private boolean mButtonsHidden;
    private boolean mSearchOpen;
    private boolean mPlacePageOpen;
    @Nullable
    private MapButtonsController.LayoutMode mLastLayoutMode;

    Controller(@NonNull MwmActivity activity, @NonNull HorizontalScrollView root, @NonNull LinearLayout container)
    {
      mActivity = activity;
      mRoot = root;
      mContainer = container;
      mPrefs = MwmApplication.prefs(activity);
      final ViewModelProvider provider = new ViewModelProvider(activity);
      mMapButtonsViewModel = provider.get(MapButtonsViewModel.class);
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
        if (mLastLayoutMode == MapButtonsController.LayoutMode.regular
            && layoutMode != MapButtonsController.LayoutMode.regular)
          InCarQuickDestinationsStore.recordRecent(mActivity, RoutingController.get().getEndPoint());
        mLastLayoutMode = layoutMode;
        mRegular = layoutMode == MapButtonsController.LayoutMode.regular;
        renderVisibility();
      });
      mMapButtonsViewModel.getButtonsHidden().observe(mActivity, hidden -> {
        mButtonsHidden = Boolean.TRUE.equals(hidden);
        renderVisibility();
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
    public void onDestroy(@NonNull LifecycleOwner owner)
    {
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

    private void rebuildButtons()
    {
      mContainer.removeAllViews();
      addFixedAction(InCarQuickDestinationsStore.Action.FUEL_CHARGING, R.string.in_car_quick_fuel_charging,
                     R.drawable.ic_in_car_quick_fuel, R.color.in_car_quick_fuel_charging,
                     () -> showFuelChargingChoice());
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
    }

    private void addFixedAction(@NonNull InCarQuickDestinationsStore.Action action, @StringRes int labelRes,
                                @DrawableRes int iconRes, @ColorRes int colorRes, @NonNull Runnable click)
    {
      if (!InCarQuickDestinationsPolicy.shouldShow(BuildConfig.IS_IN_CAR, action,
                                                    InCarQuickDestinationsStore.isActionEnabled(mActivity, action),
                                                    true))
        return;

      final MaterialButton button = createButton(colorRes);
      button.setIconResource(iconRes);
      button.setIconTint(ColorStateList.valueOf(Color.WHITE));
      button.setContentDescription(mActivity.getString(labelRes));
      button.setOnClickListener(v -> click.run());
      mContainer.addView(button);
    }

    private void addDestinationAction(@NonNull InCarQuickDestinationsStore.Action action,
                                      @Nullable InCarQuickDestination destination, @StringRes int labelRes,
                                      @DrawableRes int iconRes, @ColorRes int colorRes, boolean useGlyph)
    {
      if (!InCarQuickDestinationsPolicy.shouldShow(BuildConfig.IS_IN_CAR, action,
                                                    InCarQuickDestinationsStore.isActionEnabled(mActivity, action),
                                                    destination != null))
        return;
      if (destination == null)
        return;

      final MaterialButton button = createButton(colorRes);
      if (useGlyph)
      {
        button.setText(InCarQuickDestinationGlyphPolicy.glyph(destination.getDisplayLabel()));
        button.setTextColor(Color.WHITE);
        button.setTextSize(18.0f);
      }
      else
      {
        button.setIconResource(iconRes);
        button.setIconTint(ColorStateList.valueOf(Color.WHITE));
      }

      final String displayLabel = destination.getDisplayLabel();
      button.setContentDescription(displayLabel.isEmpty() ? mActivity.getString(labelRes) : displayLabel);
      button.setOnClickListener(v -> mActivity.startLocationToPoint(destination.toMapObject()));
      mContainer.addView(button);
    }

    @NonNull
    private MaterialButton createButton(@ColorRes int colorRes)
    {
      final MaterialButton button = new MaterialButton(mActivity, null,
                                                       com.google.android.material.R.attr.materialButtonOutlinedStyle);
      final int size = dp(56);
      final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
      params.setMarginEnd(dp(8));
      button.setLayoutParams(params);
      button.setMinWidth(0);
      button.setMinHeight(0);
      button.setMinimumWidth(size);
      button.setMinimumHeight(size);
      button.setPadding(0, 0, 0, 0);
      button.setInsetLeft(0);
      button.setInsetRight(0);
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

    private void showFuelChargingChoice()
    {
      final String[] choices = {mActivity.getString(R.string.in_car_quick_fuel),
                                mActivity.getString(R.string.in_car_quick_charging)};
      new AlertDialog.Builder(mActivity).setTitle(R.string.in_car_quick_fuel_charging).setItems(choices, (dialog, which) -> {
        openCategory(which == 0 ? InCarQuickCategoryPolicy.Category.FUEL : InCarQuickCategoryPolicy.Category.CHARGING);
      }).show();
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
      configuration.setLocale(new Locale(language));
      final Context localized = mActivity.createConfigurationContext(configuration);
      return localized.getResources();
    }

    private void renderVisibility()
    {
      final boolean visible = mContainer.getChildCount() > 0 && mRegular && !mButtonsHidden && !mSearchOpen
                              && !mPlacePageOpen;
      mRoot.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void applyInsets()
    {
      final int baseBottom = dp(72);
      ViewCompat.setOnApplyWindowInsetsListener(mRoot, (view, windowInsets) -> {
        final Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
        final ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        final int bottom = baseBottom + bars.bottom;
        if (params.bottomMargin != bottom)
        {
          params.bottomMargin = bottom;
          view.setLayoutParams(params);
        }
        return windowInsets;
      });
      ViewCompat.requestApplyInsets(mRoot);
    }

    private int dp(int value)
    {
      return Math.round(value * mActivity.getResources().getDisplayMetrics().density);
    }
  }
}
