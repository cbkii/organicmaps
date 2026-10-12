package app.organicmaps.maplayer;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
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
import app.organicmaps.incar.InCarSettingsStore;
import app.organicmaps.routing.RoutingPlanViewModel;
import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.downloader.MapManager;
import app.organicmaps.sdk.downloader.UpdateInfo;
import app.organicmaps.sdk.location.TrackRecorder;
import app.organicmaps.sdk.location.LocationState;
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
  private View mZoomMovementFrame;
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
  private final Observer<Boolean> mTrackRecorderObserver = enable ->
  {
    final boolean recording = Boolean.TRUE.equals(enable);
    updateMenuBadge(recording);
    showButton(recording, MapButtons.trackRecordingStatus);
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
    final boolean navigation = mMapButtonsViewModel.getLayoutMode().getValue() == LayoutMode.navigation;
    final int layout;
    if (BuildConfig.IS_IN_CAR)
    {
      // Distinct resource IDs prevent mobile landscape/height qualifiers from restoring removed controls.
      if (navigation)
        layout = R.layout.in_car_map_buttons_layout_navigation;
      else
        layout = R.layout.in_car_map_buttons_layout_regular;
    }
    else
    {
      if (navigation)
        layout = R.layout.map_buttons_layout_navigation;
      else
        layout = R.layout.map_buttons_layout_regular;
    }
    mFrame = inflater.inflate(layout, container, false);

    mInnerLeftButtonsFrame = mFrame.findViewById(R.id.map_buttons_inner_left);
    mInnerRightButtonsFrame = mFrame.findViewById(R.id.map_buttons_inner_right);
    mBottomButtonsFrame = mFrame.findViewById(R.id.map_buttons_bottom);

    final FloatingActionButton helpButton = mFrame.findViewById(R.id.help_button);
    final View zoomFrame = mFrame.findViewById(R.id.zoom_buttons_container);
    mZoomMovementFrame = zoomFrame;
    if (BuildConfig.IS_IN_CAR && zoomFrame.getParent() instanceof View parent)
      mZoomMovementFrame = parent;
    mFrame.findViewById(R.id.nav_zoom_in)
        .setOnClickListener(v -> mMapButtonClickListener.onMapButtonClick(MapButtons.zoomIn));
    mFrame.findViewById(R.id.nav_zoom_out)
        .setOnClickListener(v -> mMapButtonClickListener.onMapButtonClick(MapButtons.zoomOut));
    final View bookmarksButton = mFrame.findViewById(R.id.btn_bookmarks);
    bookmarksButton.setOnClickListener(v -> mMapButtonClickListener.onMapButtonClick(MapButtons.bookmarks));
    final View myPosition = mFrame.findViewById(R.id.my_position);
    mNavMyPosition =
        new MyPositionButton(myPosition, v -> mMapButtonClickListener.onMapButtonClick(MapButtons.myPosition));

    final View cameraView = mFrame.findViewById(R.id.in_car_camera_view_recenter);
    if (cameraView != null)
      cameraView.setOnClickListener(v -> {
        mMapButtonClickListener.onMapButtonClick(MapButtons.cameraViewRecenter);
        updateCameraPresentation();
      });

    // Some buttons do not exist in navigation mode.
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
      menuButton.setOnClickListener(v -> mMapButtonClickListener.onMapButtonClick(MapButtons.menu));
      // This hack is needed to show the badge on the initial startup. For some reason, updateMenuBadge does not work
      // from onResume() there.
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
      helpButton.setOnClickListener(v -> mMapButtonClickListener.onMapButtonClick(MapButtons.help));

    mSearchWheel =
        new SearchWheel(mFrame,
                        v
                        -> mMapButtonClickListener.onMapButtonClick(MapButtons.search),
                        v -> mMapButtonClickListener.onSearchCanceled(), mMapButtonsViewModel, mSearchPageViewModel);
    final View searchButton = mFrame.findViewById(R.id.btn_search);

    // Used to get the maximum height the buttons will evolve in.
    mFrame.addOnLayoutChangeListener(new ContentViewLayoutChangeListener(mFrame));
    if (BuildConfig.IS_IN_CAR)
    {
      mFrame.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> fitCameraRail());
      zoomFrame.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> fitCameraRail());
    }

    mButtonsMap = new HashMap<>();
    mButtonsMap.put(MapButtons.zoom, zoomFrame);
    mButtonsMap.put(MapButtons.myPosition, myPosition);
    if (cameraView != null)
      mButtonsMap.put(MapButtons.cameraViewRecenter, cameraView);
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
    if (BuildConfig.IS_IN_CAR)
    {
      showButton(true, MapButtons.search);
      showButton(true, MapButtons.bookmarks);
    }
    return mFrame;
  }

  private void fitCameraRail()
  {
    final View candidate = mFrame.findViewById(R.id.zoom_buttons_container);
    if (!(candidate instanceof LinearLayout rail) || mInnerRightButtonsFrame == null)
      return;
    final View cell = rail.findViewById(R.id.my_position);
    if (cell == null || cell.getHeight() == 0)
      return;
    final int available = mInnerRightButtonsFrame.getHeight() - mInnerRightButtonsFrame.getPaddingTop()
                          - mInnerRightButtonsFrame.getPaddingBottom();
    if (available <= 0)
      return;
    // Very shallow task windows use one horizontal four-cell rail; no camera/zoom action is dropped.
    final boolean horizontal = available < cell.getHeight() * 4 + 3;
    final int orientation = horizontal ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL;
    if (rail.getOrientation() != orientation)
      rail.setOrientation(orientation);
    final ViewGroup.LayoutParams frameParams = rail.getLayoutParams();
    final int targetWidth = horizontal ? ViewGroup.LayoutParams.WRAP_CONTENT : cell.getWidth();
    if (frameParams.width != targetWidth || frameParams.height != ViewGroup.LayoutParams.WRAP_CONTENT)
    {
      frameParams.width = targetWidth;
      frameParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
      rail.setLayoutParams(frameParams);
    }
    for (int i = 0; i < rail.getChildCount(); ++i)
    {
      final View child = rail.getChildAt(i);
      if (child instanceof FloatingActionButton)
        continue;
      final LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) child.getLayoutParams();
      final int thickness = Math.max(1, dpToPx(1, requireContext()));
      final int width = horizontal ? thickness : ViewGroup.LayoutParams.MATCH_PARENT;
      final int height = horizontal ? cell.getHeight() : thickness;
      final int inset = horizontal ? 0 : getResources().getDimensionPixelSize(R.dimen.in_car_camera_rail_divider_inset);
      if (params.width != width || params.height != height || params.getMarginStart() != inset
          || params.getMarginEnd() != inset)
      {
        params.width = width;
        params.height = height;
        params.setMarginStart(inset);
        params.setMarginEnd(inset);
        child.setLayoutParams(params);
      }
    }
  }

  // For disabling bottom buttons which are visible in tablets.
  private void setBottomButtonsHidden(boolean hide)
  {
    if (mBottomButtonsFrame != null)
      UiUtils.showIf(!hide, mBottomButtonsFrame);
  }

  // The quick rail owns Search/Places in active InCar navigation; planning and normal Android
  // retain their original map controls. Use the layout contract, not transient native route state.
  static boolean shouldSuppressLegacyButton(boolean inCar, @Nullable LayoutMode mode, MapButtons button)
  {
    return inCar && mode == LayoutMode.navigation && (button == MapButtons.search || button == MapButtons.bookmarks);
  }

  public void showButton(boolean show, MapButtons button)
  {
    // TODO(AB): Why do we need this check? Isn't it better to crash and fix the wrong logic ASAP?
    final View buttonView = mButtonsMap.get(button);
    if (buttonView == null)
      return;
    if (BuildConfig.IS_IN_CAR && (button == MapButtons.search || button == MapButtons.bookmarks))
    {
      final boolean suppressed =
          shouldSuppressLegacyButton(BuildConfig.IS_IN_CAR, mMapButtonsViewModel.getLayoutMode().getValue(), button);
      buttonView.setClickable(!suppressed);
      buttonView.setFocusable(!suppressed);
      buttonView.setImportantForAccessibility(suppressed ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                                                         : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
      show = show && !suppressed;
    }
    switch (button)
    {
    case zoom: UiUtils.showIf(show && (BuildConfig.IS_IN_CAR || Config.showZoomButtons()), buttonView); break;
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
    case cameraViewRecenter:
    case menu: UiUtils.showIf(show, buttonView); break;
    case trackRecordingStatus:
      UiUtils.showIf(show, buttonView);
      final boolean active = BuildConfig.IS_IN_CAR ? TrackRecorder.nativeIsTrackRecordingEnabled() : show;
      animateIconBlinking(active, (FloatingActionButton) buttonView);
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
      final Drawable drawable = button.getDrawable();
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
    final ViewGroup.MarginLayoutParams params =
        (ViewGroup.MarginLayoutParams) mTrackRecordingStatusButton.getLayoutParams();
    params.topMargin = margin;
    mTrackRecordingStatusButton.setLayoutParams(params);
  }

  @OptIn(markerClass = ExperimentalBadgeUtils.class)
  private void updateMenuBadge(Boolean enable)
  {
    final View menuButton = mButtonsMap.get(MapButtons.menu);
    final Context context = getContext();
    // Sometimes the global layout listener fires when the fragment is not attached to a context.
    if (menuButton == null || context == null)
      return;
    final UpdateInfo info = MapManager.nativeGetUpdateInfo(null);
    final int count = info == null ? 0 : info.filesCount;
    final int verticalOffset = dpToPx(8, context) + dpToPx(Integer.toString(0).length() * 5, context);

    if (count == 0)
    {
      BadgeUtils.detachBadgeDrawable(mBadgeDrawable, menuButton);
      mBadgeDrawable = BadgeDrawable.create(context);
      mBadgeDrawable.setMaxCharacterCount(0);
      mBadgeDrawable.setHorizontalOffset(verticalOffset);
      mBadgeDrawable.setVerticalOffset(dpToPx(9, context));
      mBadgeDrawable.setBackgroundColor(getResources().getColor(R.color.base_accent));
      mBadgeDrawable.setVisible(Boolean.TRUE.equals(enable));
      BadgeUtils.attachBadgeDrawable(mBadgeDrawable, menuButton);
    }
  }

  @OptIn(markerClass = com.google.android.material.badge.ExperimentalBadgeUtils.class)
  public void updateMenuBadge()
  {
    final View menuButton = mButtonsMap.get(MapButtons.menu);
    final Context context = getContext();
    // Sometimes the global layout listener fires when the fragment is not attached to a context.
    if (menuButton == null || context == null)
      return;
    final UpdateInfo info = MapManager.nativeGetUpdateInfo(null);
    final int count = info == null ? 0 : info.filesCount;
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
      // Keep this button colorful in normal theme.
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
      return !(mContentWidth / 2 > placePageWidth.floatValue() / 2.0 + v.getWidth());
    return true;
  }

  private boolean isBehindSearchSheet(View v)
  {
    if (mSearchPageViewModel == null)
      return false;
    final Integer searchPageWidth = mSearchPageViewModel.getSearchPageWidth().getValue();
    if (searchPageWidth != null)
      return !(mContentWidth / 2 > searchPageWidth.floatValue() / 2.0 + v.getWidth());
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
    // Don't apply move in landscape.
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

  private void updateButtonsVisibility(final float translation, @Nullable View parent)
  {
    if (parent == null)
      return;
    if (mZoomMovementFrame != null && mZoomMovementFrame != mButtonsMap.get(MapButtons.zoom)
        && mZoomMovementFrame.getParent() == parent)
      UiUtils.showIf(getViewTopOffset(translation, mZoomMovementFrame) >= -140, mZoomMovementFrame);
    for (Map.Entry<MapButtons, View> entry : mButtonsMap.entrySet())
    {
      final View button = entry.getValue();
      if (button.getParent() == parent)
      {
        int toleranceOffset = 0;
        // Allow offset tolerance for zoom buttons.
        switch (entry.getKey())
        {
        case zoomIn:
        case zoomOut:
        case zoom: toleranceOffset = -140; break;
        }
        showButton(getViewTopOffset(translation, button) >= toleranceOffset, entry.getKey());
      }
    }
  }

  private float getBottomButtonsHeight()
  {
    if (mBottomButtonsFrame != null && mFrame != null && UiUtils.isVisible(mFrame))
      return mBottomButtonsFrame.getMeasuredHeight();
    return 0;
  }

  public void setButtonsHidden(boolean buttonHidden)
  {
    UiUtils.showIf(!buttonHidden, mFrame);
    if (!buttonHidden)
      updateButtonsVisibility();
    mMapButtonsViewModel.setBottomButtonsHeight(getBottomButtonsHeight());
  }

  private boolean isInNavigationMode()
  {
    return RoutingController.get().isPlanning() || RoutingController.get().isNavigating();
  }

  public void updateNavMyPositionButton(int newMode)
  {
    if (mNavMyPosition != null)
      mNavMyPosition.update(newMode);
    updateCameraPresentation();
  }

  private int getViewTopOffset(float translation, View v)
  {
    return (int) (translation + v.getTop());
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
  {
    super.onViewCreated(view, savedInstanceState);
    // FragmentStateManager requests insets for the frame before onViewCreated(), but the dispatch
    // itself only happens on the next layout pass — so a listener attached here still receives it.
    // Attaching in onResume() is too late: the dispatch has already run and nothing re-requests
    // insets for an already attached view, leaving the padding at zero.
    ViewCompat.setOnApplyWindowInsetsListener(
        view, WindowInsetUtils.PaddingInsetsListener.allSides(WindowInsetsCompat.Type.systemBars()
                                                              | WindowInsetsCompat.Type.displayCutout()));
  }

  @Override
  public void onStart()
  {
    super.onStart();
    final var viewLifecycleOwner = getViewLifecycleOwner();
    mRoutingPlanViewModel.getRoutingBottomDistanceToTop().observe(viewLifecycleOwner,
                                                                  mRoutingBottomDistanceToTopObserver);
    mPlacePageViewModel.getPlacePageDistanceToTop().observe(viewLifecycleOwner, mPlacePageDistanceToTopObserver);
    mMapButtonsViewModel.getBottomButtonsHidden().observe(viewLifecycleOwner, mBottomButtonHiddenObserver);
    mMapButtonsViewModel.getButtonsHidden().observe(viewLifecycleOwner, mButtonHiddenObserver);
    mSearchPageViewModel.getSearchPageDistanceToTop().observe(viewLifecycleOwner, mSearchPageDistanceToTopObserver);
    mMapButtonsViewModel.getMyPositionMode().observe(viewLifecycleOwner, mMyPositionModeObserver);
    mMapButtonsViewModel.getSearchOption().observe(viewLifecycleOwner, mSearchOptionObserver);
    mMapButtonsViewModel.getTrackRecorderState().observe(viewLifecycleOwner, mTrackRecorderObserver);
    mMapButtonsViewModel.getTopButtonsMarginTop().observe(viewLifecycleOwner, mTopButtonMarginObserver);
    if (BuildConfig.IS_IN_CAR)
      mMapButtonsViewModel.getLayoutMode().observe(viewLifecycleOwner, ignored -> {
        showButton(true, MapButtons.search);
        showButton(true, MapButtons.bookmarks);
      });
  }

  private void updateCameraPresentation()
  {
    if (!BuildConfig.IS_IN_CAR || !app.organicmaps.sdk.Map.isEngineCreated())
      return;
    final View frame = mFrame;
    LocationState.nativeQueryCameraPresentation(perspective -> {
      if (getView() != frame || !isAdded())
        return;
      final View button = frame.findViewById(R.id.in_car_camera_view_recenter);
      if (button != null)
      {
        button.setActivated(perspective);
        button.setContentDescription(getString(perspective ? R.string.in_car_camera_driving
                                                         : R.string.in_car_camera_top_down));
      }
    });
  }

  @Override
  public void onResume()
  {
    super.onResume();
    if (mMapButtonsViewModel.getLayoutMode().getValue() == LayoutMode.navigation)
      mSearchWheel.onResume();
    updateMenuBadge();
    mTrackRecorderObserver.onChanged(TrackRecorder.nativeIsTrackRecordingEnabled());
    updateLayerButton();
    updateHelpButtonIcon();
    updateCameraPresentation();
    // A returning launcher/search window may already have focus; keep the old Android fallback.
    if (mFrame.hasWindowFocus())
      ViewCompat.requestApplyInsets(mFrame);
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

  public void onSearchOptionChange(@Nullable SearchWheel.SearchOption searchOption)
  {
    if (searchOption == null && mMapButtonsViewModel.getLayoutMode().getValue() == LayoutMode.navigation)
      mSearchWheel.reset();
  }

  public enum LayoutMode
  {
    regular,
    planning,
    navigation
  }

  public enum MapButtons
  {
    myPosition,
    cameraViewRecenter,
    toggleMapLayer,
    zoomIn,
    zoomOut,
    zoom,
    search,
    bookmarks,
    menu,
    help,
    trackRecordingStatus
  }

  public interface MapButtonClickListener
  {
    void onMapButtonClick(MapButtons button);

    void onSearchCanceled();
  }

  private class ContentViewLayoutChangeListener implements View.OnLayoutChangeListener
  {
    @NonNull
    private final View mContentView;

    ContentViewLayoutChangeListener(@NonNull View contentView)
    {
      mContentView = contentView;
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight,
                               int oldBottom)
    {
      mContentHeight = bottom - top;
      mContentWidth = right - left;
      mMapButtonsViewModel.setBottomButtonsHeight(getBottomButtonsHeight());
      mContentView.removeOnLayoutChangeListener(this);
    }
  }
}
