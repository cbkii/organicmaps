package app.organicmaps.incar;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import app.organicmaps.BuildConfig;
import app.organicmaps.MwmActivity;
import app.organicmaps.R;
import app.organicmaps.maplayer.MapButtonsController;
import app.organicmaps.maplayer.MapButtonsViewModel;
import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.routing.RoutingController;
import app.organicmaps.sdk.routing.RoutingInfo;
import app.organicmaps.sdk.util.StringUtils;
import app.organicmaps.sdk.util.log.Logger;
import app.organicmaps.sdk.widgets.lanes.LanesView;
import app.organicmaps.sdk.widgets.speedlimit.SpeedLimitView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.Map;
import java.util.WeakHashMap;

/** InCar-only presentation for Driving View and current GPS speed. */
public final class InCarDrivingUi
{
  private static final String TAG = InCarDrivingUi.class.getSimpleName();
  private static final Map<MwmActivity, WeakReference<Binding>> BINDINGS = new WeakHashMap<>();
  private static boolean sMapAgeNoticeEvaluated;

  private static final class Binding
  {
    @NonNull
    final View overlay;
    @NonNull
    final TextView speed;
    @Nullable
    final TextView navigationSpeed;
    @Nullable
    final SpeedLimitView navigationSpeedLimit;
    @Nullable
    FloatingActionButton drivingView;
    @Nullable
    View zoomIn;
    @Nullable
    View.OnLayoutChangeListener zoomSizeListener;
    @Nullable
    Fragment mapButtonsFragment;
    @Nullable
    FragmentManager.FragmentLifecycleCallbacks mapButtonsCallbacks;
    @Nullable
    final View help;
    @NonNull
    final InCarDrivingViewController controller;
    String lastSpeedText;
    double currentRouteSpeedLimitMps = -1.0;
    final View speedCluster;
    final InCarSpeedWarningView speedEdgeWarning;
    Insets safeInsets = Insets.NONE;
    final SpeedLimitView roadSpeedLimit;

    Binding(@NonNull View overlay, @NonNull TextView speed, @Nullable TextView navigationSpeed,
            @Nullable SpeedLimitView navigationSpeedLimit, @Nullable View help,
            @NonNull InCarDrivingViewController controller)
    {
      this.overlay = overlay;
      this.speed = speed;
      this.speedEdgeWarning = overlay.findViewById(R.id.in_car_speed_edge_warning);
      this.speedCluster = overlay.findViewById(R.id.in_car_driving_speed_cluster);
      this.roadSpeedLimit = overlay.findViewById(R.id.in_car_road_speed_limit);
      this.navigationSpeed = navigationSpeed;
      this.navigationSpeedLimit = navigationSpeedLimit;
      this.help = help;
      this.controller = controller;
    }
  }

  private InCarDrivingUi() {}

  @Nullable
  private static Binding getBinding(@NonNull MwmActivity activity)
  {
    final WeakReference<Binding> reference = BINDINGS.get(activity);
    return reference == null ? null : reference.get();
  }

  public static void attach(@NonNull MwmActivity activity, @NonNull InCarDrivingViewController controller)
  {
    if (!BuildConfig.IS_IN_CAR)
      return;

    Binding binding = getBinding(activity);
    if (binding == null)
    {
      View overlay = activity.findViewById(R.id.in_car_driving_overlay);
      if (overlay == null)
      {
        final ViewStub overlayStub = activity.findViewById(R.id.in_car_driving_overlay_stub);
        if (overlayStub != null)
          overlay = overlayStub.inflate();
      }

      if (overlay == null)
      {
        Logger.w(TAG, "Driving overlay stub is not mounted");
        return;
      }

      final TextView speed = overlay.findViewById(R.id.in_car_speed);
      if (speed == null)
      {
        Logger.w(TAG, "Driving overlay speed control is unavailable");
        return;
      }

      final TextView navigationSpeed = activity.findViewById(R.id.in_car_nav_speed);
      final SpeedLimitView navigationSpeedLimit = activity.findViewById(R.id.nav_speed_limit);
      final View help = activity.findViewById(R.id.help_button);
      binding = new Binding(overlay, speed, navigationSpeed, navigationSpeedLimit, help, controller);
      final InCarDrivingViewController.Snapshot initial = controller.getSnapshot().getValue();
      if (initial != null && initial.routeLimitAvailable && app.organicmaps.sdk.Map.isEngineCreated()
          && RoutingController.get().isNavigating())
      {
        final RoutingInfo route = Framework.nativeGetRouteFollowingInfo();
        if (route != null)
          binding.currentRouteSpeedLimitMps = route.speedLimitMps;
      }
      BINDINGS.put(activity, new WeakReference<>(binding));
      overlay.setVisibility(View.VISIBLE);
      applyInsets(activity, binding);
      final Binding geometryBinding = binding;
      overlay.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
          updateSpeedEdgeGeometry(activity, geometryBinding));
      binding.speedCluster.addOnLayoutChangeListener(
          (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom)
              activity.refreshInCarCompassOffset();
          });
      final View navRibbon = activity.findViewById(R.id.nav_next_turn_container);
      if (navRibbon != null)
        navRibbon.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
          if (bottom != oldBottom || top != oldTop)
            activity.refreshInCarCompassOffset();
        });

      final Binding observed = binding;
      final FragmentManager fragmentManager = activity.getSupportFragmentManager();
      observed.mapButtonsCallbacks = new FragmentManager.FragmentLifecycleCallbacks() {
        @Override
        public void onFragmentViewCreated(@NonNull FragmentManager fm, @NonNull Fragment fragment, @NonNull View view,
                                          @Nullable Bundle savedInstanceState)
        {
          if (fragment instanceof MapButtonsController)
            bindDrivingViewButton(activity, observed, view, fragment);
        }

        @Override
        public void onFragmentViewDestroyed(@NonNull FragmentManager fm, @NonNull Fragment fragment)
        {
          if (fragment instanceof MapButtonsController && observed.mapButtonsFragment == fragment)
            clearDrivingViewButton(observed);
        }
      };
      fragmentManager.registerFragmentLifecycleCallbacks(observed.mapButtonsCallbacks, true);

      bindDrivingViewButton(activity, observed, activity.findViewById(android.R.id.content), null);
      controller.getSnapshot().observe(activity, snapshot -> render(activity, observed, snapshot));
      new ViewModelProvider(activity).get(MapButtonsViewModel.class).getLayoutMode().observe(activity, layoutMode -> {
        controller.onRoutingPresentationChanged();
        final View content = activity.findViewById(android.R.id.content);
        if (content != null)
          content.post(() -> bindDrivingViewButton(activity, observed, content, null));
      });
    }

    bindDrivingViewButton(activity, binding, activity.findViewById(android.R.id.content), null);
    render(activity, binding, controller.getSnapshot().getValue());
    maybeShowMapAgeNotice(activity);
  }

  public static void updateNavigationSpeedLimit(@NonNull MwmActivity activity, double speedLimitMps)
  {
    final Binding binding = getBinding(activity);
    if (binding == null)
    {
      InCarSpeedDisplayPolicy.updateSpeedLimit(speedLimitMps);
      return;
    }
    binding.currentRouteSpeedLimitMps = speedLimitMps;
    render(activity, binding, binding.controller.getSnapshot().getValue());
  }

  private static void renderRibbon(@NonNull MwmActivity activity, boolean warning, float strength)
  {
    final int red =
        ContextCompat.getColor(activity, InCarSpeedDisplayPolicy.severity() == InCarSpeedDisplayPolicy.Severity.SEVERE
                                             ? R.color.in_car_nav_warning
                                             : R.color.in_car_nav_warning_orange);
    final View underline = activity.findViewById(R.id.in_car_nav_warning_underline);
    if (underline != null)
    {
      underline.setBackgroundColor(red);
      underline.setVisibility(warning ? View.VISIBLE : View.INVISIBLE);
    }
    final View ribbon = activity.findViewById(R.id.nav_next_turn_container);
    if (ribbon == null)
      return;
    final View group = activity.findViewById(R.id.in_car_nav_guidance_group);
    final View immediate = activity.findViewById(R.id.nav_next_turn_frame);
    final View then = activity.findViewById(R.id.nav_next_next_turn_frame);
    final View immediateGroup = activity.findViewById(R.id.in_car_nav_immediate_group);
    final LanesView lanes = activity.findViewById(R.id.lanes);
    final boolean hasLanes = lanes != null && lanes.getVisibility() == View.VISIBLE;
    final int normal = ContextCompat.getColor(activity, R.color.bg_cards);
    final int immediateBase = ContextCompat.getColor(activity, R.color.in_car_nav_immediate_background);
    final int afterBase = ContextCompat.getColor(
        activity, hasLanes ? R.color.in_car_nav_lanes_background : R.color.in_car_nav_after_background);
    final int immediateBackground = warning ? ColorUtils.blendARGB(immediateBase, red, strength) : immediateBase;
    final int afterBackground = warning ? ColorUtils.blendARGB(afterBase, red, strength) : afterBase;
    final int immediateForeground = warning ? InCarContrast.foreground(immediateBackground, normal)
                                            : ContextCompat.getColor(activity, R.color.in_car_nav_immediate_foreground);
    final int foreground = warning ? InCarContrast.foreground(afterBackground, normal)
                                   : ContextCompat.getColor(activity, hasLanes ? R.color.in_car_nav_lanes_foreground
                                                                               : R.color.in_car_nav_after_foreground);
    // Paint every section itself: child surfaces must not cover the parent's warning wash.
    // The speed circles retain their own separate severity/limit presentation.
    ribbon.setBackgroundColor(warning ? ColorUtils.blendARGB(normal, red, strength) : normal);
    if (immediateGroup != null)
      immediateGroup.setBackgroundColor(immediateBackground);
    if (immediate != null)
    {
      immediate.setBackgroundColor(immediateBackground);
      ((TextView) immediate.findViewById(R.id.distance)).setTextColor(immediateForeground);
      ImageViewCompat.setImageTintList((ImageView) immediate.findViewById(R.id.turn),
                                       ColorStateList.valueOf(immediateForeground));
    }
    final View streetFrame = activity.findViewById(R.id.street_frame);
    if (streetFrame != null)
    {
      streetFrame.setBackgroundColor(immediateBackground);
      ((TextView) streetFrame.findViewById(R.id.street)).setTextColor(immediateForeground);
    }
    if (group != null)
      group.setBackgroundColor(afterBackground);
    if (then != null)
    {
      then.setBackgroundColor(afterBackground);
      ImageViewCompat.setImageTintList((ImageView) then.findViewById(R.id.turn), ColorStateList.valueOf(foreground));
    }
    final TextView label = activity.findViewById(R.id.in_car_nav_guidance_label);
    if (label != null)
      label.setTextColor(foreground);
    if (lanes != null)
    {
      lanes.setBackgroundColor(afterBackground);
      final int active = warning ? foreground : ContextCompat.getColor(activity, R.color.in_car_nav_lanes_foreground);
      final int secondary = warning ? ColorUtils.blendARGB(afterBackground, active, 0.75f)
                                    : ContextCompat.getColor(activity, R.color.in_car_nav_secondary);
      lanes.setLaneColors(active, secondary);
    }
  }

  public static void refresh(@NonNull MwmActivity activity)
  {
    final Binding binding = getBinding(activity);
    if (binding == null)
      return;
    binding.controller.onSettingsChanged();
    render(activity, binding, binding.controller.getSnapshot().getValue());
  }

  public static void release(@NonNull MwmActivity activity)
  {
    final Binding binding = getBinding(activity);
    if (binding != null)
    {
      if (binding.mapButtonsCallbacks != null)
        activity.getSupportFragmentManager().unregisterFragmentLifecycleCallbacks(binding.mapButtonsCallbacks);
      clearDrivingViewButton(binding);
      if (binding.speedEdgeWarning != null)
        binding.speedEdgeWarning.setVisibility(View.GONE);
    }
    BINDINGS.remove(activity);
  }

  private static void bindDrivingViewButton(@NonNull MwmActivity activity, @NonNull Binding binding,
                                            @Nullable View scope, @Nullable Fragment owner)
  {
    if (scope == null)
      return;

    final FloatingActionButton drivingView = scope.findViewById(R.id.in_car_driving_view_button);
    final View zoomIn = scope.findViewById(R.id.nav_zoom_in);
    if (drivingView == null || zoomIn == null)
      return;

    final Fragment resolvedOwner =
        owner != null ? owner : findMapButtonsOwner(activity.getSupportFragmentManager(), drivingView);
    if (binding.drivingView != drivingView || binding.zoomIn != zoomIn)
    {
      clearDrivingViewButton(binding);
      binding.drivingView = drivingView;
      binding.zoomIn = zoomIn;
      drivingView.setOnClickListener(v -> binding.controller.onDrivingViewButtonPressed());
      binding.zoomSizeListener =
          (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> syncDrivingViewButtonSize(binding);
      zoomIn.addOnLayoutChangeListener(binding.zoomSizeListener);
    }
    binding.mapButtonsFragment = resolvedOwner;

    syncDrivingViewButtonSize(binding);
    render(activity, binding, binding.controller.getSnapshot().getValue());
  }

  @Nullable
  private static Fragment findMapButtonsOwner(@NonNull FragmentManager fragmentManager, @NonNull View drivingView)
  {
    for (Fragment fragment : fragmentManager.getFragments())
    {
      final View fragmentView = fragment.getView();
      if (fragment instanceof MapButtonsController && fragmentView != null
          && fragmentView.findViewById(R.id.in_car_driving_view_button) == drivingView)
        return fragment;

      if (fragment.isAdded())
      {
        final Fragment childOwner = findMapButtonsOwner(fragment.getChildFragmentManager(), drivingView);
        if (childOwner != null)
          return childOwner;
      }
    }
    return null;
  }

  private static void clearDrivingViewButton(@NonNull Binding binding)
  {
    if (binding.zoomIn != null && binding.zoomSizeListener != null)
      binding.zoomIn.removeOnLayoutChangeListener(binding.zoomSizeListener);
    if (binding.drivingView != null)
      binding.drivingView.setOnClickListener(null);
    binding.drivingView = null;
    binding.zoomIn = null;
    binding.zoomSizeListener = null;
    binding.mapButtonsFragment = null;
  }

  private static void syncDrivingViewButtonSize(@NonNull Binding binding)
  {
    if (binding.drivingView == null || binding.zoomIn == null)
      return;

    final int width = binding.zoomIn.getWidth();
    final int height = binding.zoomIn.getHeight();
    final int size = Math.min(width, height);
    if (size > 0 && (binding.drivingView.getWidth() != size || binding.drivingView.getHeight() != size))
      binding.drivingView.setCustomSize(size);

    binding.drivingView.setMinimumWidth(binding.zoomIn.getMinimumWidth());
    binding.drivingView.setMinimumHeight(binding.zoomIn.getMinimumHeight());
  }

  private static void render(@NonNull MwmActivity activity, @NonNull Binding binding,
                             @Nullable InCarDrivingViewController.Snapshot snapshot)
  {
    if (snapshot == null)
    {
      if (binding.speedEdgeWarning != null)
        binding.speedEdgeWarning.setVisibility(View.GONE);
      return;
    }

    final String speedText = InCarSpeedDisplayPolicy.format(
        snapshot.locationHealth, snapshot.hasSpeed, snapshot.speedMps,
        activity.getString(R.string.in_car_speed_value_unavailable), Framework::nativeFormatSpeed);
    if (!speedText.equals(binding.lastSpeedText))
    {
      binding.speed.setText(speedText);
      if (binding.navigationSpeed != null)
        binding.navigationSpeed.setText(speedText);
      binding.lastSpeedText = speedText;
    }

    final boolean modeChanged =
        binding.speedCluster.getVisibility() != (snapshot.navigating ? View.GONE : View.VISIBLE);
    binding.speedCluster.setVisibility(snapshot.navigating ? View.GONE : View.VISIBLE);
    if (binding.navigationSpeed != null)
      binding.navigationSpeed.setVisibility(snapshot.navigating ? View.VISIBLE : View.GONE);

    if (!snapshot.navigating)
      binding.currentRouteSpeedLimitMps = -1.0;
    if (snapshot.navigating && snapshot.routeLimitAvailable && snapshot.currentNativeObservation
        && app.organicmaps.sdk.Map.isEngineCreated())
    {
      final RoutingInfo route = Framework.nativeGetRouteFollowingInfo();
      if (route != null)
        binding.currentRouteSpeedLimitMps = route.speedLimitMps;
    }
    final double routeLimit = snapshot.routeLimitAvailable ? binding.currentRouteSpeedLimitMps : -1.0;
    final double displayedLimit = InCarSpeedDisplayPolicy.selectSpeedLimit(
        routeLimit, snapshot.roadSpeedLimit, snapshot.currentNativeObservation, SystemClock.elapsedRealtimeNanos());
    InCarSpeedDisplayPolicy.updateSpeedLimit(displayedLimit);
    final boolean current = snapshot.currentNativeObservation
                         && snapshot.locationHealth == InCarDrivingViewController.LocationHealth.CURRENT
                         && snapshot.hasSpeed;
    final boolean warning = InCarSpeedDisplayPolicy.updateCurrentSpeed(current ? snapshot.speedMps : Double.NaN);
    if (binding.navigationSpeed instanceof InCarNavigationSpeedView navigationSpeedView)
      navigationSpeedView.setSpeeding(warning);
    if (binding.speed instanceof InCarNavigationSpeedView drivingSpeedView)
      drivingSpeedView.setSpeeding(warning);
    final String limitDescription = displayedLimit > 0
                                      ? activity.getString(R.string.in_car_road_speed_limit_content_description,
                                                           Framework.nativeFormatSpeed(displayedLimit))
                                      : null;
    if (binding.navigationSpeedLimit != null)
    {
      binding.navigationSpeedLimit.setSpeedLimit(StringUtils.nativeFormatSpeed(displayedLimit), warning);
      binding.navigationSpeedLimit.setContentDescription(limitDescription);
    }
    final boolean showRoadLimit = !snapshot.navigating && displayedLimit > 0.0
                               && ((snapshot.enabled && snapshot.following) || (current && snapshot.speedMps > 0.0));
    binding.roadSpeedLimit.setVisibility(showRoadLimit ? View.VISIBLE : View.GONE);
    binding.roadSpeedLimit.setContentDescription(showRoadLimit ? limitDescription : null);
    if (showRoadLimit)
      binding.roadSpeedLimit.setSpeedLimit(StringUtils.nativeFormatSpeed(displayedLimit), warning);
    if (modeChanged)
      binding.speedCluster.post(activity::refreshInCarCompassOffset);
    renderRibbon(activity, snapshot.navigating && warning, InCarSpeedDisplayPolicy.warningStrength());
    if (binding.speedEdgeWarning != null)
    {
      updateSpeedEdgeGeometry(activity, binding);
      final boolean showEdge = warning && (snapshot.navigating || snapshot.enabled)
                            && InCarSettingsStore.speedEdgeWarningEnabled(activity);
      binding.speedEdgeWarning.render(showEdge, InCarSpeedDisplayPolicy.severity());
    }

    applyLocationHealth(activity, binding.speed, snapshot.locationHealth, speedText);
    if (binding.navigationSpeed != null)
      applyLocationHealth(activity, binding.navigationSpeed, snapshot.locationHealth, speedText);

    final FloatingActionButton drivingView = binding.drivingView;
    if (drivingView != null)
    {
      final boolean showButton = InCarSettingsStore.showDrivingViewButton(activity) && !snapshot.navigating;
      drivingView.setVisibility(showButton ? View.VISIBLE : View.GONE);
      drivingView.setSelected(snapshot.enabled);
      // Keep the existing recenter-state cue without making selected chrome less legible than neutral controls.
      drivingView.setAlpha(snapshot.enabled && !snapshot.following ? 0.88f : 1.0f);
      drivingView.setContentDescription(activity.getString(snapshot.enabled && !snapshot.following
                                                               ? R.string.in_car_driving_view_recenter
                                                               : R.string.in_car_driving_view_button));

      final int buttonBackground =
          ContextCompat.getColor(activity, snapshot.enabled ? R.color.base_accent : R.color.bg_cards);
      final int backgroundAlpha = activity.getResources().getInteger(
          snapshot.enabled ? R.integer.in_car_map_button_selected_alpha : R.integer.in_car_map_button_surface_alpha);
      final int buttonForeground = ContextCompat.getColor(
          activity, snapshot.enabled ? R.color.routing_button_activated_tint : R.color.icon_tint);
      drivingView.setBackgroundTintList(
          ColorStateList.valueOf(ColorUtils.setAlphaComponent(buttonBackground, backgroundAlpha)));
      drivingView.setImageTintList(ColorStateList.valueOf(buttonForeground));
    }

    if (binding.help != null)
      binding.help.setVisibility(View.GONE);
  }

  private static void applyLocationHealth(@NonNull MwmActivity activity, @NonNull TextView view,
                                          @NonNull InCarDrivingViewController.LocationHealth health,
                                          @NonNull String speedText)
  {
    switch (health)
    {
    case CURRENT:
      view.setAlpha(1.0f);
      view.setContentDescription(activity.getString(R.string.in_car_speed_current, speedText));
      break;
    case STALE:
      view.setAlpha(0.72f);
      view.setContentDescription(activity.getString(R.string.in_car_speed_stale));
      break;
    case UNAVAILABLE:
      view.setAlpha(0.55f);
      view.setContentDescription(activity.getString(R.string.in_car_speed_unavailable));
      break;
    }
  }

  private static void updateSpeedEdgeGeometry(@NonNull MwmActivity activity, @NonNull Binding binding)
  {
    if (binding.speedEdgeWarning == null)
      return;
    binding.speedEdgeWarning.updateGeometry(app.organicmaps.util.InCarVisuals.currentSpeedCircleSizePx(activity),
                                            InCarSettingsStore.controlsOnLeft(activity), binding.safeInsets);
  }

  private static void applyInsets(@NonNull MwmActivity activity, @NonNull Binding binding)
  {
    ViewCompat.setOnApplyWindowInsetsListener(binding.overlay, (view, windowInsets) -> {
      final Insets bars = windowInsets.getInsets(
          WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
      binding.safeInsets = bars;
      updateSpeedEdgeGeometry(activity, binding);
      final ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) binding.speedCluster.getLayoutParams();
      final int baseMargin = activity.getResources().getDimensionPixelSize(R.dimen.margin_base);
      final int top = bars.top + baseMargin;
      final int right = bars.right + baseMargin;
      if (params.topMargin != top || params.rightMargin != right)
      {
        params.topMargin = top;
        params.rightMargin = right;
        binding.speedCluster.setLayoutParams(params);
      }
      binding.speedCluster.post(activity::refreshInCarCompassOffset);
      return windowInsets;
    });
    ViewCompat.requestApplyInsets(binding.overlay);
  }

  private static void maybeShowMapAgeNotice(@NonNull MwmActivity activity)
  {
    if (sMapAgeNoticeEvaluated || !InCarSettingsStore.mapAgeWarningEnabled(activity))
      return;

    final Date dataVersion;
    try
    {
      dataVersion = Framework.getDataVersion();
    }
    catch (RuntimeException | AssertionError e)
    {
      Logger.w(TAG, "Unable to evaluate global map-data version: " + e.getMessage());
      return;
    }

    sMapAgeNoticeEvaluated = true;

    if (!InCarMapAgePolicy.isOutdated(dataVersion, new Date()))
      return;

    final View coordinator = activity.findViewById(R.id.coordinator);
    if (coordinator != null)
      Snackbar.make(coordinator, R.string.in_car_maps_may_be_outdated, Snackbar.LENGTH_LONG).show();
  }
}
