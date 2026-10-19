package app.organicmaps.location;

import android.app.Activity;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import app.organicmaps.MwmActivity;
import app.organicmaps.MwmApplication;
import app.organicmaps.R;
import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.Map;
import app.organicmaps.sdk.location.LocationHelper;
import app.organicmaps.sdk.routing.RoutingController;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Read-only presentation of the application's one location owner; no independent subscription. */
public final class InCarLocationUi
{
  private InCarLocationUi() {}

  public static int statusText(LocationHelper helper)
  {
    return switch (helper.getAcquisitionStateSnapshot())
    {
      case STOPPED -> R.string.in_car_location_stopped;
      case WAITING_FOR_PERMISSION -> R.string.in_car_location_permission_required;
      case PROVIDER_UNAVAILABLE -> R.string.in_car_location_unavailable;
      case ACQUIRING -> R.string.in_car_location_acquiring;
      case APPROXIMATE -> R.string.in_car_location_approximate;
      case FIX_RECEIVED -> R.string.in_car_location_confirmed;
      case FIX_STALE -> R.string.in_car_location_stale;
      case RECOVERING -> R.string.in_car_location_recovering;
    };
  }

  public static void showDetails(Activity activity)
  {
    final LocationHelper helper = MwmApplication.from(activity).getLocationHelper();
    final MaterialAlertDialogBuilder builder =
        new MaterialAlertDialogBuilder(activity)
            .setTitle(statusText(helper))
            .setMessage(helper.getAcquisitionDiagnostics().replace(", ", "\n"))
            .setNegativeButton(R.string.close, null)
            .setPositiveButton(R.string.in_car_location_retry, (dialog, which) -> {
              final boolean restarted = helper.retryAcquisition();
              Toast
                  .makeText(activity,
                            restarted ? R.string.in_car_location_retry_started : R.string.in_car_location_retry_wait,
                            Toast.LENGTH_LONG)
                  .show();
            });
    final Location cached = helper.getCachedLocation();
    if (cached != null && Map.isEngineCreated() && !RoutingController.get().isNavigating()
        && !RoutingController.get().isPlanning())
      builder.setNeutralButton(R.string.in_car_location_show_cache, (dialog, which) -> {
        // Explicit map preview only: never fed to nativeLocationUpdated or used as a route origin.
        if (helper.getCachedLocation() != null && Map.isEngineCreated() && !RoutingController.get().isNavigating()
            && !RoutingController.get().isPlanning())
          Framework.nativeSetViewportCenter(cached.getLatitude(), cached.getLongitude(), 14);
      });
    builder.show();
  }

  public static void install(MwmActivity activity)
  {
    final LocationHelper helper = MwmApplication.from(activity).getLocationHelper();
    final RelativeLayout container = activity.findViewById(R.id.map_container);
    final TextView status = new TextView(activity);
    status.setTextSize(12);
    status.setSingleLine(true);
    status.setEllipsize(android.text.TextUtils.TruncateAt.END);
    status.setGravity(Gravity.CENTER);
    final int padding = Math.round(8 * activity.getResources().getDisplayMetrics().density);
    status.setPadding(padding, padding / 2, padding, padding / 2);
    final android.content.res.TypedArray colours =
        activity.obtainStyledAttributes(new int[] {android.R.attr.textColorPrimary});
    status.setTextColor(colours.getColor(0, android.graphics.Color.BLACK));
    colours.recycle();
    status.setBackgroundColor(ContextCompat.getColor(activity, R.color.bg_cards));
    status.setOnClickListener(view -> showDetails(activity));
    status.setMinHeight(Math.round(48 * activity.getResources().getDisplayMetrics().density));
    final RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
        RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
    params.addRule(RelativeLayout.CENTER_HORIZONTAL);
    params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
    container.addView(status, params);
    final Handler handler = new Handler(Looper.getMainLooper());
    final int[] origin = new int[2];
    final int[] hudIds = {R.id.in_car_driving_speed_cluster, R.id.nav_next_turn_container};
    final android.graphics.Rect hudBounds = new android.graphics.Rect();
    final Runnable update = new Runnable() {
      private int lastText;
      private int lastWidth = -1;
      private int lastTop = -1;

      @Override
      public void run()
      {
        final int text = statusText(helper);
        if (text != lastText)
        {
          status.setText(text);
          lastText = text;
        }
        // Follow the real top HUD bounds in normal/compact windows, without taking its ownership.
        final androidx.core.view.WindowInsetsCompat insets =
            androidx.core.view.ViewCompat.getRootWindowInsets(container);
        final androidx.core.graphics.Insets bars =
            insets == null ? androidx.core.graphics.Insets.NONE
                           : insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
        int top = bars.top;
        container.getLocationOnScreen(origin);
        for (int id : hudIds)
        {
          final android.view.View hud = activity.findViewById(id);
          if (hud != null && hud.isShown() && hud.getGlobalVisibleRect(hudBounds))
            top = Math.max(top, hudBounds.bottom - origin[1]);
        }
        final int rails = 2 * activity.getResources().getDimensionPixelSize(R.dimen.in_car_map_primary_button_size);
        final int width = Math.max(0, container.getWidth() - bars.left - bars.right - rails);
        final float translation = (bars.left - bars.right) / 2.0f;
        if (status.getTranslationX() != translation)
          status.setTranslationX(translation);
        if (width != lastWidth)
        {
          status.setMaxWidth(width);
          lastWidth = width;
        }
        if (top != lastTop)
        {
          params.topMargin = top;
          status.setLayoutParams(params);
          lastTop = top;
        }
        final int visibility = width > 0 && top + status.getMinimumHeight() < container.getHeight()
                                   ? android.view.View.VISIBLE
                                   : android.view.View.GONE;
        if (status.getVisibility() != visibility)
          status.setVisibility(visibility);
        handler.postDelayed(this, 2_000);
      }
    };
    activity.getLifecycle().addObserver(new DefaultLifecycleObserver() {
      @Override
      public void onStart(@NonNull LifecycleOwner owner)
      {
        // Refresh after permission/settings changes while stopped, then use owner snapshots.
        helper.getAcquisitionState();
        handler.post(update);
      }
      @Override
      public void onStop(@NonNull LifecycleOwner owner)
      {
        handler.removeCallbacks(update);
      }
    });
  }
}
