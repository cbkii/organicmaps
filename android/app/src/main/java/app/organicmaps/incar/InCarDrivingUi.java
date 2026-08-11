package app.organicmaps.incar;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import app.organicmaps.BuildConfig;
import app.organicmaps.MwmActivity;
import app.organicmaps.R;
import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.util.log.Logger;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import java.util.Date;
import java.util.Map;
import java.util.WeakHashMap;

/** InCar-only presentation for Driving View and current GPS speed. */
public final class InCarDrivingUi
{
  private static final String TAG = InCarDrivingUi.class.getSimpleName();
  private static final Map<MwmActivity, Binding> BINDINGS = new WeakHashMap<>();
  private static boolean sMapAgeNoticeEvaluated;

  private static final class Binding
  {
    @NonNull
    final View overlay;
    @NonNull
    final TextView speed;
    @Nullable
    final TextView navigationSpeed;
    @NonNull
    final FloatingActionButton drivingView;
    @NonNull
    final InCarDrivingViewController controller;
    String lastSpeedText;

    Binding(@NonNull View overlay, @NonNull TextView speed, @Nullable TextView navigationSpeed,
            @NonNull FloatingActionButton drivingView, @NonNull InCarDrivingViewController controller)
    {
      this.overlay = overlay;
      this.speed = speed;
      this.navigationSpeed = navigationSpeed;
      this.drivingView = drivingView;
      this.controller = controller;
    }
  }

  private InCarDrivingUi() {}

  public static void attach(@NonNull MwmActivity activity, @NonNull InCarDrivingViewController controller)
  {
    if (!BuildConfig.IS_IN_CAR)
      return;

    Binding binding = BINDINGS.get(activity);
    if (binding == null)
    {
      final View overlay = activity.findViewById(R.id.in_car_driving_overlay);
      final TextView speed = activity.findViewById(R.id.in_car_speed);
      final TextView navigationSpeed = activity.findViewById(R.id.in_car_nav_speed);
      final FloatingActionButton drivingView = activity.findViewById(R.id.in_car_driving_view_button);
      if (overlay == null || speed == null || drivingView == null)
      {
        Logger.w(TAG, "Driving overlay is not mounted");
        return;
      }

      binding = new Binding(overlay, speed, navigationSpeed, drivingView, controller);
      BINDINGS.put(activity, binding);
      overlay.setVisibility(View.VISIBLE);
      drivingView.setOnClickListener(v -> controller.onDrivingViewButtonPressed());
      applyInsets(activity, binding);

      final Binding observed = binding;
      controller.getSnapshot().observe(activity, snapshot -> render(activity, observed, snapshot));
    }

    render(activity, binding, controller.getSnapshot().getValue());
    maybeShowMapAgeNotice(activity);
  }

  public static void refresh(@NonNull MwmActivity activity)
  {
    final Binding binding = BINDINGS.get(activity);
    if (binding == null)
      return;
    binding.controller.onSettingsChanged();
    render(activity, binding, binding.controller.getSnapshot().getValue());
  }

  public static void release(@NonNull MwmActivity activity)
  {
    BINDINGS.remove(activity);
  }

  private static void render(@NonNull MwmActivity activity, @NonNull Binding binding,
                             @Nullable InCarDrivingViewController.Snapshot snapshot)
  {
    if (snapshot == null)
      return;

    final String speedText = InCarSpeedDisplayPolicy.format(snapshot.locationHealth, snapshot.hasSpeed,
                                                            snapshot.speedMps, Framework::nativeFormatSpeed);
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

    final boolean showButton = InCarSettingsStore.showDrivingViewButton(activity) && !snapshot.navigating;
    binding.drivingView.setVisibility(showButton ? View.VISIBLE : View.GONE);
    binding.drivingView.setSelected(snapshot.enabled);
    binding.drivingView.setAlpha(snapshot.enabled && !snapshot.following ? 0.78f : 1.0f);
    binding.drivingView.setContentDescription(activity.getString(snapshot.enabled && !snapshot.following
                                                                    ? R.string.in_car_driving_view_recenter
                                                                    : R.string.in_car_driving_view_button));

    final int buttonBackground =
        ContextCompat.getColor(activity, snapshot.enabled ? R.color.base_accent : R.color.bg_cards);
    final int buttonForeground =
        snapshot.enabled ? Color.WHITE : ContextCompat.getColor(activity, R.color.icon_tint);
    binding.drivingView.setBackgroundTintList(ColorStateList.valueOf(buttonBackground));
    binding.drivingView.setImageTintList(ColorStateList.valueOf(buttonForeground));

    // Promotional/help content remains available through menus/settings but is not primary driving-map chrome.
    final View help = activity.findViewById(R.id.help_button);
    if (help != null)
      help.setVisibility(View.GONE);
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
    sMapAgeNoticeEvaluated = true;

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

    if (!InCarMapAgePolicy.isOutdated(dataVersion, new Date()))
      return;

    final View coordinator = activity.findViewById(R.id.coordinator);
    if (coordinator != null)
      Snackbar.make(coordinator, R.string.in_car_maps_may_be_outdated, Snackbar.LENGTH_LONG).show();
  }
}
