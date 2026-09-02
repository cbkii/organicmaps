package app.organicmaps.incar;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import app.organicmaps.BuildConfig;
import app.organicmaps.MwmActivity;
import app.organicmaps.R;
import app.organicmaps.maplayer.MapButtonsController;
import app.organicmaps.maplayer.MapButtonsViewModel;
import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.util.log.Logger;
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
    FloatingActionButton drivingView;
    @Nullable
    FloatingActionButton zoomIn;
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

    Binding(@NonNull View overlay, @NonNull TextView speed, @Nullable TextView navigationSpeed, @Nullable View help,
            @NonNull InCarDrivingViewController controller)
    {
      this.overlay = overlay;
      this.speed = speed;
      this.navigationSpeed = navigationSpeed;
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

      TextView navigationSpeed = activity.findViewById(R.id.in_car_nav_speed);
      if (navigationSpeed == null)
      {
        final ViewStub navigationSpeedStub = activity.findViewById(R.id.in_car_nav_speed_stub);
        if (navigationSpeedStub != null)
        {
          final View inflated = navigationSpeedStub.inflate();
          if (inflated instanceof TextView textView)
            navigationSpeed = textView;
        }
      }

      final View help = activity.findViewById(R.id.help_button);
      binding = new Binding(overlay, speed, navigationSpeed, help, controller);
      BINDINGS.put(activity, new WeakReference<>(binding));
      overlay.setVisibility(View.VISIBLE);
      applyInsets(activity, binding);

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
    }
    BINDINGS.remove(activity);
  }

  private static void bindDrivingViewButton(@NonNull MwmActivity activity, @NonNull Binding binding,
                                            @Nullable View scope, @Nullable Fragment owner)
  {
    if (scope == null)
      return;

    final FloatingActionButton drivingView = scope.findViewById(R.id.in_car_driving_view_button);
    final FloatingActionButton zoomIn = scope.findViewById(R.id.nav_zoom_in);
    if (drivingView == null || zoomIn == null)
      return;

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
    if (owner != null)
      binding.mapButtonsFragment = owner;

    syncDrivingViewButtonSize(binding);
    render(activity, binding, binding.controller.getSnapshot().getValue());
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
      return;

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

    binding.speed.setVisibility(snapshot.navigating ? View.INVISIBLE : View.VISIBLE);
    if (binding.navigationSpeed != null)
      binding.navigationSpeed.setVisibility(snapshot.navigating ? View.VISIBLE : View.GONE);

    applyLocationHealth(activity, binding.speed, snapshot.locationHealth, speedText);
    if (binding.navigationSpeed != null)
      applyLocationHealth(activity, binding.navigationSpeed, snapshot.locationHealth, speedText);

    final FloatingActionButton drivingView = binding.drivingView;
    if (drivingView != null)
    {
      final boolean showButton = InCarSettingsStore.showDrivingViewButton(activity) && !snapshot.navigating;
      drivingView.setVisibility(showButton ? View.VISIBLE : View.GONE);
      drivingView.setSelected(snapshot.enabled);
      drivingView.setAlpha(snapshot.enabled && !snapshot.following ? 0.78f : 1.0f);
      drivingView.setContentDescription(activity.getString(snapshot.enabled && !snapshot.following
                                                               ? R.string.in_car_driving_view_recenter
                                                               : R.string.in_car_driving_view_button));

      final int buttonBackground =
          ContextCompat.getColor(activity, snapshot.enabled ? R.color.base_accent : R.color.bg_cards);
      final int buttonForeground = ContextCompat.getColor(
          activity, snapshot.enabled ? R.color.routing_button_activated_tint : R.color.icon_tint);
      drivingView.setBackgroundTintList(ColorStateList.valueOf(buttonBackground));
      drivingView.setImageTintList(ColorStateList.valueOf(buttonForeground));
    }

    // Promotional/help content remains available through menus/settings but is not primary driving-map chrome.
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

  private static void applyInsets(@NonNull MwmActivity activity, @NonNull Binding binding)
  {
    ViewCompat.setOnApplyWindowInsetsListener(binding.overlay, (view, windowInsets) -> {
      final Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
      final ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) binding.speed.getLayoutParams();
      final int baseMargin = activity.getResources().getDimensionPixelSize(R.dimen.margin_base);
      final int top = bars.top + baseMargin;
      if (params.topMargin != top)
      {
        params.topMargin = top;
        binding.speed.setLayoutParams(params);
      }
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
