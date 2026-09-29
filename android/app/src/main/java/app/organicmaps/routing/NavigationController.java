package app.organicmaps.routing;

import static app.organicmaps.sdk.util.Utils.dimen;

import android.content.res.Configuration;
import android.location.Location;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;
import app.organicmaps.BuildConfig;
import app.organicmaps.MwmActivity;
import app.organicmaps.MwmApplication;
import app.organicmaps.R;
import app.organicmaps.incar.InCarDrivingUi;
import app.organicmaps.incar.InCarSpeedDisplayPolicy;
import app.organicmaps.maplayer.MapButtonsViewModel;
import app.organicmaps.sdk.Router;
import app.organicmaps.sdk.maplayer.traffic.TrafficManager;
import app.organicmaps.sdk.routing.RoutingController;
import app.organicmaps.sdk.routing.RoutingInfo;
import app.organicmaps.sdk.util.StringUtils;
import app.organicmaps.sdk.widget.roadshield.RoadShieldUtils;
import app.organicmaps.sdk.widgets.lanes.LanesView;
import app.organicmaps.sdk.widgets.speedlimit.SpeedLimitView;
import app.organicmaps.util.UiUtils;
import app.organicmaps.util.Utils;
import app.organicmaps.util.WindowInsetUtils;
import app.organicmaps.util.WindowInsetUtils.BaselinePaddingInsetsListener;
import app.organicmaps.widget.menu.NavMenu;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

public class NavigationController implements TrafficManager.TrafficCallback, NavMenu.NavMenuListener
{
  private final View mFrame;
  private final AppCompatActivity mActivity;

  private final ImageView mNextTurnImage;
  private final TextView mNextTurnDistance;

  private final View mNextNextTurnFrame;
  private final ImageView mNextNextTurnImage;

  private final View mStreetFrame;
  private final TextView mNextStreet;

  @NonNull
  private final LanesView mLanesView;
  @NonNull
  private final SpeedLimitView mSpeedLimit;

  private final MapButtonsViewModel mMapButtonsViewModel;
  private final View mTopFrame;
  private final View mNextTurnContainer;

  private final NavMenu mNavMenu;
  View.OnClickListener mOnSettingsClickListener;
  View.OnClickListener mOnVoiceSettingsClickListener;

  public NavigationController(AppCompatActivity activity, View.OnClickListener onSettingsClickListener,
                              View.OnClickListener onVoiceSettingsClickListener,
                              NavMenu.OnMenuSizeChangedListener onMenuSizeChangedListener)
  {
    mActivity = activity;
    mMapButtonsViewModel = new ViewModelProvider(activity).get(MapButtonsViewModel.class);

    mFrame = activity.findViewById(R.id.navigation_frame);
    mNavMenu = new NavMenu(activity, this, onMenuSizeChangedListener);
    mOnSettingsClickListener = onSettingsClickListener;
    mOnVoiceSettingsClickListener = onVoiceSettingsClickListener;

    // Top frame.
    mTopFrame = mFrame.findViewById(R.id.nav_top_frame);
    mTopFrame.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> updateNavigationHeaderMetrics());
    final View turnFrame = mTopFrame.findViewById(R.id.nav_next_turn_frame);
    mNextTurnImage = turnFrame.findViewById(R.id.turn);
    mNextTurnDistance = turnFrame.findViewById(R.id.distance);

    mNextNextTurnFrame = mTopFrame.findViewById(R.id.nav_next_next_turn_frame);
    mNextNextTurnImage = mNextNextTurnFrame.findViewById(R.id.turn);

    mStreetFrame = mTopFrame.findViewById(R.id.street_frame);
    mNextStreet = mStreetFrame.findViewById(R.id.street);

    mLanesView = mTopFrame.findViewById(R.id.lanes);
    mSpeedLimit = mTopFrame.findViewById(R.id.nav_speed_limit);

    // Blank rectangle below the navbar that hides menu content behind it.
    final View navigationBarBackground = mFrame.findViewById(R.id.nav_bottom_sheet_nav_bar);
    final View navBottomSheet = mFrame.findViewById(R.id.nav_bottom_sheet);
    mNextTurnContainer = mFrame.findViewById(R.id.nav_next_turn_container);

    if (isInCarLandscape())
    {
      // The landscape InCar resource is one fixed-height full-width ribbon. Apply safe drawing
      // insets once to that owning surface so turn, street, lanes and the physical-right speed
      // cluster move as one without conditional children changing map clearance.
      final int left = mNextTurnContainer.getPaddingLeft();
      final int right = mNextTurnContainer.getPaddingRight();
      ViewCompat.setOnApplyWindowInsetsListener(mNextTurnContainer, (view, insets) -> {
        final Insets safe = insets.getInsets(WindowInsetUtils.TYPE_SAFE_DRAWING);
        final ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        if (params.topMargin != safe.top)
        {
          params.topMargin = safe.top;
          view.setLayoutParams(params);
        }
        view.setPadding(left + safe.left, view.getPaddingTop(), right + safe.right, view.getPaddingBottom());
        return insets;
      });
    }
    else
      ViewCompat.setOnApplyWindowInsetsListener(mStreetFrame, BaselinePaddingInsetsListener.excludeBottom());

    ViewCompat.setOnApplyWindowInsetsListener(mTopFrame, (v, windowInsets) -> {
      final Insets safeDrawing = windowInsets.getInsets(WindowInsetUtils.TYPE_SAFE_DRAWING);
      if (BuildConfig.IS_IN_CAR)
      {
        if (isInCarLandscape())
          return windowInsets;

        // Portrait InCar retains the established physical-right glance cluster. Protect that edge
        // from SystemUI without allowing locale direction to mirror it.
        mNextTurnContainer.setPadding(mNextTurnContainer.getPaddingLeft(), mNextTurnContainer.getPaddingTop(),
                                      safeDrawing.right, mNextTurnContainer.getPaddingBottom());
      }
      else
      {
        // Normal Android keeps locale-relative start-edge behaviour.
        final boolean isRtl = v.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        final int startInset = isRtl ? safeDrawing.right : safeDrawing.left;
        mNextTurnContainer.setPaddingRelative(startInset, mNextTurnContainer.getPaddingTop(),
                                              mNextTurnContainer.getPaddingEnd(),
                                              mNextTurnContainer.getPaddingBottom());
      }
      return windowInsets;
    });

    ViewCompat.setOnApplyWindowInsetsListener(navigationBarBackground, (v, windowInsets) -> {
      final ViewGroup.LayoutParams lp = v.getLayoutParams();
      lp.height = windowInsets.getInsets(WindowInsetUtils.TYPE_SAFE_DRAWING).bottom;
      v.setLayoutParams(lp);
      return windowInsets;
    });

    // navBottomSheet.getWidth() is 0 on the first inset dispatch (layout hasn't run yet),
    // so mirror the width through a layout listener instead of reading it inline.
    navBottomSheet.addOnLayoutChangeListener((v, l, t, r, b, oL, oT, oR, oB) -> {
      final int width = r - l;
      final ViewGroup.LayoutParams lp = navigationBarBackground.getLayoutParams();
      if (lp.width != width)
      {
        lp.width = width;
        navigationBarBackground.setLayoutParams(lp);
      }
    });
  }

  private boolean isInCarLandscape()
  {
    return BuildConfig.IS_IN_CAR
 && mFrame.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
  }

  private void updateNavigationHeaderMetrics()
  {
    final int contentHeight = computeNavContentHeight();
    mMapButtonsViewModel.setTopHeaderHeight(contentHeight);
    if (isInCarLandscape())
    {
      // The active InCar footer is owned by NavMenu, not MapButtonsController's legacy bottom frame.
      // Publish its fixed resource-backed height so Quick Destinations and its More dialog always
      // reserve the real END/progress row even when the legacy frame reports zero.
      mMapButtonsViewModel.setBottomButtonsHeight(dimen(mFrame.getContext(), R.dimen.nav_menu_height));
      mMapButtonsViewModel.setTopButtonsMarginTop(dimen(mFrame.getContext(), R.dimen.nav_frame_padding)
                                                  + contentHeight);
    }
  }

  // Height the search sheet and map controls must clear. InCar landscape owns all driver guidance
  // inside one fixed ribbon envelope; conditional lanes/next-next content must never move the map.
  private int computeNavContentHeight()
  {
    if (isInCarLandscape())
    {
      final ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) mNextTurnContainer.getLayoutParams();
      return UiUtils.isVisible(mNextTurnContainer)
        ? dimen(mFrame.getContext(), R.dimen.in_car_nav_ribbon_height) + params.topMargin : 0;
    }

    int turnAndSpeedHeight = 0;
    if (mFrame.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT)
    {
      if (UiUtils.isVisible(mNextTurnContainer))
        turnAndSpeedHeight += mNextTurnContainer.getHeight();
      if (UiUtils.isVisible(mSpeedLimit))
        turnAndSpeedHeight += mSpeedLimit.getHeight();
    }
    final int lanesHeight = UiUtils.isVisible(mLanesView) ? mLanesView.getHeight() : 0;
    return mStreetFrame.getHeight() + Math.max(turnAndSpeedHeight, lanesHeight);
  }

  private void updateVehicle(@NonNull RoutingInfo info)
  {
    mNextTurnDistance.setText(Utils.formatDistance(mFrame.getContext(), info.distToTurn));
    mNextTurnImage.setImageResource(info.carDirection.getTurnRes(info.exitNum));

    final boolean hasLanes = info.lanes != null && info.lanes.length > 0;
    final boolean showNextNextTurn = info.hasNextNextTurn() && (!isInCarLandscape() || !hasLanes);
    UiUtils.showIf(showNextNextTurn, mNextNextTurnFrame);
    if (showNextNextTurn)
      mNextNextTurnImage.setImageResource(info.nextCarDirection.getTurnRes());

    mLanesView.setLanes(info.lanes);

    updateSpeedLimit(info);
  }

  private void updatePedestrian(@NonNull RoutingInfo info)
  {
    mNextTurnDistance.setText(Utils.formatDistance(mFrame.getContext(), info.distToTurn));
    mNextTurnImage.setImageResource(info.pedestrianDirection.getTurnRes());
  }

  public void update(@Nullable RoutingInfo info)
  {
    if (info == null)
      return;

    if (Router.get() == Router.Pedestrian)
    {
      updatePedestrian(info);
      if (isInCarLandscape())
      {
        mLanesView.setLanes(null);
        UiUtils.hide(mNextNextTurnFrame);
        updateSpeedLimit(info);
      }
    }
    else
      updateVehicle(info);

    updateStreetView(info);
    updateNavigationHeaderMetrics();
    mNavMenu.update(info);
  }

  private void updateStreetView(@NonNull RoutingInfo info)
  {
    final boolean hasStreet = !TextUtils.isEmpty(info.nextStreet);
    // Sic: don't use UiUtils.showIf() here because View.GONE breaks layout
    // https://github.com/organicmaps/organicmaps/issues/3732
    UiUtils.visibleIf(hasStreet, mStreetFrame);
    if (!TextUtils.isEmpty(info.nextStreet))
      mNextStreet.setText(RoadShieldUtils.createStreetTextWithShields(info.nextStreet, info.nextStreetRoadShields,
                                                                      mNextStreet.getTextSize()));
    int margin = dimen(mFrame.getContext(), R.dimen.nav_frame_padding);
    if (isInCarLandscape())
      margin += computeNavContentHeight();
    else if (hasStreet)
      margin += mStreetFrame.getHeight();
    mMapButtonsViewModel.setTopButtonsMarginTop(margin);
  }

  public void show(boolean show)
  {
    if (show && !UiUtils.isVisible(mFrame))
    {
      collapseNavMenu();
      if (BuildConfig.IS_IN_CAR)
        InCarSpeedDisplayPolicy.resetSpeeding();
      // Seed the panel from the already-built route so it isn't empty until the first GPS fix arrives.
      update(RoutingController.get().getCachedRoutingInfo());
    }
    UiUtils.showIf(show, mFrame);
    if (show)
      updateNavigationHeaderMetrics();
    else
    {
      mMapButtonsViewModel.setTopHeaderHeight(0);
      if (BuildConfig.IS_IN_CAR)
        InCarSpeedDisplayPolicy.resetSpeeding();
    }
  }

  public boolean isNavMenuCollapsed()
  {
    return mNavMenu.getBottomSheetState() == BottomSheetBehavior.STATE_COLLAPSED;
  }

  public boolean isNavMenuHidden()
  {
    return mNavMenu.getBottomSheetState() == BottomSheetBehavior.STATE_HIDDEN;
  }

  public void collapseNavMenu()
  {
    mNavMenu.collapseNavBottomSheet();
  }

  public void refresh()
  {
    mNavMenu.refreshTts();
  }

  @Override
  public void onEnabled()
  {
    // mNavMenu.refreshTraffic();
  }

  @Override
  public void onDisabled()
  {
    // no op
  }

  @Override
  public void onWaitingData()
  {
    // no op
  }

  @Override
  public void onOutdated()
  {
    // no op
  }

  @Override
  public void onNoData()
  {
    // no op
  }

  @Override
  public void onNetworkError()
  {
    // no op
  }

  @Override
  public void onExpiredData()
  {
    // no op
  }

  @Override
  public void onExpiredApp()
  {
    // no op
  }

  @Override
  public void onSettingsClicked()
  {
    mOnSettingsClickListener.onClick(null);
  }

  @Override
  public void onTtsVoiceSettingsClicked()
  {
    mOnVoiceSettingsClickListener.onClick(null);
  }

  @Override
  public void onStopClicked()
  {
    // Reuse the established navigation cancellation authority for both normal Android and the
    // fixed InCar End Navigation button supplied by the resource overlay.
    RoutingController.get().cancel();
  }

  private void updateSpeedLimit(@NonNull RoutingInfo info)
  {
    final boolean speedLimitExceeded;
    if (BuildConfig.IS_IN_CAR)
    {
      // Route updates own only the current posted limit. The Driving View snapshot is the single
      // authority that feeds speed samples into the hysteresis, avoiding competing saved-location
      // and snapshot call order around the 103-105% band.
      InCarSpeedDisplayPolicy.updateSpeedLimit(info.speedLimitMps);
      if (mActivity instanceof MwmActivity mapActivity)
        InCarDrivingUi.updateNavigationSpeedLimit(mapActivity, info.speedLimitMps);
      speedLimitExceeded = InCarSpeedDisplayPolicy.warningActive();
    }
    else
    {
      final Location location = MwmApplication.from(mFrame.getContext()).getLocationHelper().getSavedLocation();
      speedLimitExceeded = location != null && info.speedLimitMps < location.getSpeed();
    }
    mSpeedLimit.setSpeedLimit(StringUtils.nativeFormatSpeed(info.speedLimitMps), speedLimitExceeded);
  }
}
