package app.organicmaps.maplayer;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import app.organicmaps.BuildConfig;
import app.organicmaps.MwmActivity;
import app.organicmaps.R;
import app.organicmaps.routing.RoutingPlanViewModel;
import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.downloader.MapManager;
import app.organicmaps.sdk.downloader.UpdateInfo;
import app.organicmaps.sdk.location.TrackRecorder;
import app.organicmaps.sdk.maplayer.isolines.IsolinesManager;
import app.organicmaps.sdk.maplayer.subway.SubwayManager;
import app.organicmaps.sdk.maplayer.traffic.TrafficManager;
import app.organicmaps.sdk.routing.RoutingController;
import app.organicmaps.sdk.util.Config;
import app.organicmaps.search.SearchPageViewModel;
import app.organicmaps.util.ThemeUtils;
import app.organicmaps.util.UiUtils;
import app.organicmaps.util.Utils;
import app.organicmaps.util.WindowInsetUtils;
import app.organicmaps.widget.menu.MyPositionButton;
import app.organicmaps.widget.placepage.PlacePageViewModel;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.badge.BadgeUtils;
import com.google.android.material.badge.ExperimentalBadgeUtils;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.HashMap;
import java.util.Map;

public class MapButtonsController extends Fragment
{
  Map<MapButtons, View> mButtonsMap;
  private View mFrame;
  private View mInnerLeftButtonsFrame;
  private View mInnerRightButtonsFrame;
  @Nullable
  private View mBottomButtonsFrame;
  @Nullable
  private LayersButton mToggleMapLayerButton;
  @Nullable
  FloatingActionButton mTrackRecordingStatusButton;
  @Nullable
  private MyPositionButton mNavMyPosition;
  private SearchWheel mSearchWheel;
  private BadgeDrawable mBadgeDrawable;
  @Nullable
  private ObjectAnimator mBlinkingAnimator;
  private float mContentHeight;
  private float mContentWidth;

  private MapButtonClickListener mMapButtonClickListener;
  private PlacePageViewModel mPlacePageViewModel;
  private RoutingPlanViewModel mRoutingPlanViewModel;
  private MapButtonsViewModel mMapButtonsViewModel;
  private SearchPageViewModel mSearchPageViewModel;

  private final Observer<Integer> mPlacePageDistanceToTopObserver = translationY -> move(translationY, true);
  private final Observer<Integer> mRoutingBottomDistanceToTopObserver = translationY -> move(translationY, false);
  private final Observer<Boolean> mBottomButtonHiddenObserver = this::setBottomButtonsHidden;
  private final Observer<Integer> mSearchPageDistanceToTopObserver = this::moveForSearch;
  private final Observer<Boolean> mButtonHiddenObserver = this::setButtonsHidden;
  private final Observer<Integer> mMyPositionModeObserver = this::updateNavMyPositionButton;
  private final Observer<SearchWheel.SearchOption> mSearchOptionObserver = this::onSearchOptionChange;
  private final Observer<Boolean> mTrackRecorderObserver = (enable) ->
  {
    updateMenuBadge(enable);
    showButton(enable, MapButtons.trackRecordingStatus);
  };
  private final Observer<Integer> mTopButtonMarginObserver = this::updateTopButtonsMargin;

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState)
  {
    final FragmentActivity activity = requireActivity();
    mMapButtonClickListener = (MwmActivity) activity;
    mRoutingPlanViewModel = new ViewModelProvider(activity).get(RoutingPlanViewModel.class);
    mPlacePageViewModel = new ViewModelProvider(activity).get(PlacePageViewModel.class);
    mMapButtonsViewModel = new ViewModelProvider(activity).get(MapButtonsViewModel.class);
    mSearchPageViewModel = new ViewModelProvider(activity).get(SearchPageViewModel.class);
    if (mMapButtonsViewModel.getLayoutMode().getValue() == LayoutMode.navigation)
      mFrame = inflater.inflate(R.layout.map_buttons_layout_navigation, container, false);
    else
      mFrame = inflater.inflate(R.layout.map_buttons_layout_regular, container, false);

    mInnerLeftButtonsFrame = mFrame.findViewById(R.id.map_buttons_inner_left);
    mInnerRightButtonsFrame = mFrame.findViewById(R.id.map_buttons_inner_right);
    mBottomButtonsFrame = mFrame.findViewById(R.id.map_buttons_bottom);

    final FloatingActionButton helpButton = mFrame.findViewById(R.id.help_button);
    final View zoomFrame = mFrame.findViewById(R.id.zoom_buttons_container);
    final View zoomIn = mFrame.findViewById(R.id.nav_zoom_in);
    final View zoomOut = mFrame.findViewById(R.id.nav_zoom_out);
    zoomIn.setOnClickListener((v) -> mMapButtonClickListener.onMapButtonClick(MapButtons.zoomIn));
    zoomOut.setOnClickListener((v) -> mMapButtonClickListener.onMapButtonClick(MapButtons.zoomOut));
    final View bookmarksButton = mFrame.findViewById(R.id.btn_bookmarks);
    bookmarksButton.setOnClickListener((v) -> mMapButtonClickListener.onMapButtonClick(MapButtons.bookmarks));
    final View myPosition = mFrame.findViewById(R.id.my_position);
    applyInCarPrimaryMapControls(myPosition, zoomIn, zoomOut);
    mNavMyPosition =
        new MyPositionButton(myPosition, (v) -> mMapButtonClickListener.onMapButtonClick(MapButtons.myPosition));

    mToggleMapLayerButton = mFrame.findViewById(R.id.layers_button);
    if (mToggleMapLayerButton != null)
    {
      mToggleMapLayerButton.setOnClickListener(
          view -> mMapButtonClickListener.onMapButtonClick(MapButtons.toggleMapLayer));
      mToggleMapLayerButton.setVisibility(View.VISIBLE);
    }
    mMapButtonsViewModel.setTopButtonsMarginTop(-1);
    mTrackRecordingStatusButton = mFrame.findViewById(R.id.track_recording_status);
    if (mTrackRecordingStatusButton != null)
      mTrackRecordingStatusButton.setOnClickListener(
          view -> mMapButtonClickListener.onMapButtonClick(MapButtons.trackRecordingStatus));
    final View menuButton = mFrame.findViewById(R.id.menu_button);
    if (menuButton != null)
    {
      menuButton.setOnClickListener((v) -> mMapButtonClickListener.onMapButtonClick(MapButtons.menu));
      menuButton.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
        @Override
        public void onGlobalLayout()
        {
          updateMenuBadge();
          menuButton.getViewTreeObserver().removeOnGlobalLayoutListener(this);
        }
      });
    }
    if (helpButton != null)
      helpButton.setOnClickListener((v) -> mMapButtonClickListener.onMapButtonClick(MapButtons.help));

    mSearchWheel =
        new SearchWheel(mFrame,
                        (v)
                            -> mMapButtonClickListener.onMapButtonClick(MapButtons.search),
                        (v) -> mMapButtonClickListener.onSearchCanceled(), mMapButtonsViewModel, mSearchPageViewModel);
    final View searchButton = mFrame.findViewById(R.id.btn_search);

    mFrame.addOnLayoutChangeListener(new MapButtonsController.ContentViewLayoutChangeListener(mFrame));

    mButtonsMap = new HashMap<>();
    mButtonsMap.put(MapButtons.zoom, zoomFrame);
    mButtonsMap.put(MapButtons.myPosition, myPosition);
    mButtonsMap.put(MapButtons.bookmarks, bookmarksButton);
    mButtonsMap.put(MapButtons.search, searchButton);

    if (mToggleMapLayerButton != null)
      mButtonsMap.put(MapButtons.toggleMapLayer, mToggleMapLayerButton);
    if (menuButton != null)
      mButtonsMap.put(MapButtons.menu, menuButton);
    if (helpButton != null)
      mButtonsMap.put(MapButtons.help, helpButton);
    if (mTrackRecordingStatusButton != null)
      mButtonsMap.put(MapButtons.trackRecordingStatus, mTrackRecordingStatusButton);
    showButton(false, MapButtons.trackRecordingStatus);
    return mFrame;
  }

  private void applyInCarPrimaryMapControls(@Nullable View myPosition, @Nullable View zoomIn, @Nullable View zoomOut)
  {
    if (!BuildConfig.IS_IN_CAR)
      return;
    applyInCarPrimaryMapControl(myPosition, R.dimen.in_car_map_primary_icon_size);
    applyInCarPrimaryMapControl(zoomIn, R.dimen.in_car_map_zoom_icon_size);
    applyInCarPrimaryMapControl(zoomOut, R.dimen.in_car_map_zoom_icon_size);
    if (zoomOut != null)
    {
      final ViewGroup.LayoutParams raw = zoomOut.getLayoutParams();
      if (raw instanceof ViewGroup.MarginLayoutParams params)
      {
        params.topMargin = getResources().getDimensionPixelSize(R.dimen.in_car_map_zoom_gap);
        zoomOut.setLayoutParams(params);
      }
    }
  }

  private void applyInCarPrimaryMapControl(@Nullable View view, int iconSizeRes)
  {
    if (!(view instanceof FloatingActionButton button))
      return;
    button.setCustomSize(getResources().getDimensionPixelSize(R.dimen.in_car_map_primary_button_size));
    button.setMaxImageSize(getResources().getDimensionPixelSize(iconSizeRes));
    final ColorStateList background = button.getBackgroundTintList();
    if (background != null)
      button.setBackgroundTintList(background.withAlpha(77));
  }

  private void setBottomButtonsHidden(boolean hide)
  {
    if (mBottomButtonsFrame != null)
      UiUtils.showIf(!hide, mBottomButtonsFrame);
  }

  public void showButton(boolean show, MapButtonsController.MapButtons button)
  {
    final View buttonView = mButtonsMap.get(button);
    if (buttonView == null)
      return;
    switch (button)
    {
    case zoom: UiUtils.showIf(show && Config.showZoomButtons(), buttonView); break;
    case toggleMapLayer:
      if (mToggleMapLayerButton != null)
        UiUtils.showIf(show && !isInNavigationMode(), mToggleMapLayerButton);
      break;
    case myPosition:
      if (mNavMyPosition != null)
        mNavMyPosition.showButton(show);
      break;
    case search: mSearchWheel.show(show);
    case bookmarks:
    case menu: UiUtils.showIf(show, buttonView); break;
    case trackRecordingStatus:
      UiUtils.showIf(show, buttonView);
      animateIconBlinking(show, (FloatingActionButton) buttonView);
      break;
    }
  }

  void animateIconBlinking(boolean show, @NonNull FloatingActionButton button)
  {
    if (mBlinkingAnimator != null)
    {
      mBlinkingAnimator.cancel();
      mBlinkingAnimator = null;
    }
    if (show)
    {
      Drawable drawable = button.getDrawable();
      mBlinkingAnimator = ObjectAnimator.ofArgb(drawable, "tint", 0xFF757575, 0xFFFF0000);
      mBlinkingAnimator.setDuration(2500);
      mBlinkingAnimator.setEvaluator(new ArgbEvaluator());
      mBlinkingAnimator.setRepeatCount(ObjectAnimator.INFINITE);
      mBlinkingAnimator.setRepeatMode(ObjectAnimator.REVERSE);
      mBlinkingAnimator.start();
    }
  }

  private static int dpToPx(float dp, Context context)
  {
    return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
  }

  private void updateTopButtonsMargin(int margin)
  {
    if (margin == -1 || mTrackRecordingStatusButton == null)
      return;
    ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) mTrackRecordingStatusButton.getLayoutParams();
    params.topMargin = margin;
    mTrackRecordingStatusButton.setLayoutParams(params);
  }

  @OptIn(markerClass = ExperimentalBadgeUtils.class)
  private void updateMenuBadge(Boolean enable)
  {
    final View menuButton = mButtonsMap.get(MapButtons.menu);
    final Context context = getContext();
    if (menuButton == null || context == null)
      return;
    final UpdateInfo info = MapManager.nativeGetUpdateInfo(null);
    final int count = (info == null ? 0 : info.filesCount);
    final int verticalOffset = dpToPx(8, context) + dpToPx(Integer.toString(0).length() * 5, context);

    if (count == 0)
    {
      BadgeUtils.detachBadgeDrawable(mBadgeDrawable, menuButton);
      mBadgeDrawable = BadgeDrawable.create(context);
      mBadgeDrawable.setMaxCharacterCount(0);
      mBadgeDrawable.setHorizontalOffset(verticalOffset);
      mBadgeDrawable.setVerticalOffset(dpToPx(9, context));
      mBadgeDrawable.setBackgroundColor(getResources().getColor(R.color.base_accent));
      mBadgeDrawable.setVisible(enable);
      BadgeUtils.attachBadgeDrawable(mBadgeDrawable, menuButton);
    }
  }

  @OptIn(markerClass = com.google.android.material.badge.ExperimentalBadgeUtils.class)
  public void updateMenuBadge()
  {
    final View menuButton = mButtonsMap.get(MapButtons.menu);
    final Context context = getContext();
    if (menuButton == null || context == null)
      return;
    final UpdateInfo info = MapManager.nativeGetUpdateInfo(null);
    final int count = (info == null ? 0 : info.filesCount);
    final int verticalOffset = dpToPx(8, context) + dpToPx(Integer.toString(0).length() * 5, context);
    BadgeUtils.detachBadgeDrawable(mBadgeDrawable, menuButton);
    mBadgeDrawable = BadgeDrawable.create(context);
    mBadgeDrawable.setMaxCharacterCount(3);
    mBadgeDrawable.setHorizontalOffset(verticalOffset);
    mBadgeDrawable.setVerticalOffset(dpToPx(9, context));
    mBadgeDrawable.setNumber(count);
    mBadgeDrawable.setVisible(count > 0);
    BadgeUtils.attachBadgeDrawable(mBadgeDrawable, menuButton);

    updateMenuBadge(TrackRecorder.nativeIsTrackRecordingEnabled());
  }

  public void updateHelpButtonIcon()
  {
    final View view = mButtonsMap.get(MapButtons.help);
    if (!(view instanceof FloatingActionButton helpButton))
      return;

    if (Framework.nativeCanShowCrowdfundingPromo() && !TextUtils.isEmpty(Utils.getDonateUrl(requireContext())))
    {
      helpButton.setImageResource(R.drawable.ic_crowdfunding);
      helpButton.getDrawable().setTintList(null);
    }
    else if (Config.isNY() && !TextUtils.isEmpty(Utils.getDonateUrl(requireContext())))
    {
      helpButton.setImageResource(R.drawable.ic_christmas_tree);
      helpButton.getDrawable().setTintList(null);
    }
    else
    {
      helpButton.setImageResource(app.organicmaps.branding.R.drawable.logo);
      if (!ThemeUtils.isDarkTheme(requireContext()))
        helpButton.getDrawable().setTintList(null);
    }
  }

  public void updateLayerButton()
  {
    if (mToggleMapLayerButton == null)
      return;
    final boolean buttonSelected = TrafficManager.INSTANCE.isEnabled() || IsolinesManager.isEnabled()
                                || SubwayManager.isEnabled() || Framework.nativeIsOutdoorsLayerEnabled()
                                || Framework.nativeIsHikingLayerEnabled() || Framework.nativeIsCyclingLayerEnabled()
                                || Framework.nativeIsBackgroundTilesEnabled();
    mToggleMapLayerButton.setHasActiveLayers(buttonSelected);
  }

  private boolean isBehindPlacePage(View v)
  {
    if (mPlacePageViewModel == null)
      return false;
    final Integer placePageWidth = mPlacePageViewModel.getPlacePageWidth().getValue();
    if (placePageWidth != null)
      return !(mContentWidth / 2 > (placePageWidth.floatValue() / 2.0) + v.getWidth());
    return true;
  }

  private boolean isBehindSearchSheet(View v)
  {
    if (mSearchPageViewModel == null)
      return false;
    final Integer searchPageWidth = mSearchPageViewModel.getSearchPageWidth().getValue();
    if (searchPageWidth != null)
      return !(mContentWidth / 2 > (searchPageWidth.floatValue() / 2.0) + v.getWidth());
    return true;
  }

  private boolean isMoving(View v)
  {
    return v.getTranslationY() < 0;
  }

  public void move(float translationY, boolean shouldActivate)
  {
    if (RoutingController.get().isNavigating() || mContentHeight == 0)
      return;
    final boolean pp = Boolean.TRUE.equals(mRoutingPlanViewModel.getIsPlacePageActive().getValue());
    if (!shouldActivate == pp || getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
      return;
    if (mInnerRightButtonsFrame != null)
      applyMove(mInnerRightButtonsFrame, translationY);
  }

  private void moveForSearch(float translationY)
  {
    if (mContentHeight == 0)
      return;

    if (mInnerRightButtonsFrame != null
        && (isBehindSearchSheet(mInnerRightButtonsFrame) || isMoving(mInnerRightButtonsFrame)))
      applyMove(mInnerRightButtonsFrame, translationY);
    if (mInnerLeftButtonsFrame != null
        && (isBehindSearchSheet(mInnerLeftButtonsFrame) || isMoving(mInnerLeftButtonsFrame)))
      applyMove(mInnerLeftButtonsFrame, translationY);
  }

  private void applyMove(View frame, float translationY)
  {
    final float rightTranslation = translationY - frame.getBottom();
    final float appliedTranslation = rightTranslation <= 0 ? rightTranslation : 0;
    frame.setTranslationY(appliedTranslation);
    updateButtonsVisibility(appliedTranslation, frame);
  }

  public void updateButtonsVisibility()
  {
    if (mInnerLeftButtonsFrame != null)
      updateButtonsVisibility(mInnerLeftButtonsFrame.getTranslationY(), mInnerLeftButtonsFrame);
    if (mInnerRightButtonsFrame != null)
      updateButtonsVisibility(mInnerRightButtonsFrame.getTranslationY(), mInnerRightButtonsFrame);
  }

  private void updateButtonsVisibility(float translationY, ViewGroup frame)
  {
    for (int i = 0; i < frame.getChildCount(); i++)
      frame.getChildAt(i).setEnabled(translationY == 0);
  }

  public boolean isInNavigationMode()
  {
    return mMapButtonsViewModel.getLayoutMode().getValue() == LayoutMode.navigation;
  }

  private void setButtonsHidden(Boolean hide)
  {
    if (hide == null)
      return;
    if (mInnerLeftButtonsFrame != null)
      UiUtils.showIf(!hide, mInnerLeftButtonsFrame);
    if (mInnerRightButtonsFrame != null)
      UiUtils.showIf(!hide, mInnerRightButtonsFrame);
  }

  private void updateNavMyPositionButton(Integer mode)
  {
    if (mNavMyPosition != null)
      mNavMyPosition.update(mode);
  }

  private void onSearchOptionChange(SearchWheel.SearchOption option)
  {
    if (mSearchWheel != null)
      mSearchWheel.update(option);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
  {
    super.onViewCreated(view, savedInstanceState);
  }

  @Override
  public void onStart()
  {
    super.onStart();
    mPlacePageViewModel.getPlacePageDistanceToTop().observe(getViewLifecycleOwner(), mPlacePageDistanceToTopObserver);
    mRoutingPlanViewModel.getRoutingBottomDistanceToTop().observe(getViewLifecycleOwner(), mRoutingBottomDistanceToTopObserver);
    mMapButtonsViewModel.getBottomButtonsHidden().observe(getViewLifecycleOwner(), mBottomButtonHiddenObserver);
    mSearchPageViewModel.getSearchPageDistanceToTop().observe(getViewLifecycleOwner(), mSearchPageDistanceToTopObserver);
    mMapButtonsViewModel.getButtonsHidden().observe(getViewLifecycleOwner(), mButtonHiddenObserver);
    mMapButtonsViewModel.getMyPositionMode().observe(getViewLifecycleOwner(), mMyPositionModeObserver);
    mMapButtonsViewModel.getSearchOption().observe(getViewLifecycleOwner(), mSearchOptionObserver);
    TrackRecorder.INSTANCE.getState().observe(getViewLifecycleOwner(), mTrackRecorderObserver);
    mMapButtonsViewModel.getTopButtonsMarginTop().observe(getViewLifecycleOwner(), mTopButtonMarginObserver);
  }

  @Override
  public void onStop()
  {
    super.onStop();
    if (mBlinkingAnimator != null)
    {
      mBlinkingAnimator.cancel();
      mBlinkingAnimator = null;
    }
  }

  public enum LayoutMode
  {
    regular,
    navigation
  }

  public enum MapButtons
  {
    zoom,
    myPosition,
    bookmarks,
    search,
    toggleMapLayer,
    menu,
    help,
    trackRecordingStatus,
    zoomIn,
    zoomOut
  }

  public interface MapButtonClickListener
  {
    void onMapButtonClick(MapButtons button);
    void onSearchCanceled();
  }

  private class ContentViewLayoutChangeListener implements View.OnLayoutChangeListener
  {
    private final View mContentView;

    ContentViewLayoutChangeListener(View contentView)
    {
      mContentView = contentView;
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight,
                               int oldBottom)
    {
      mContentHeight = mContentView.getHeight();
      mContentWidth = mContentView.getWidth();
    }
  }
}
