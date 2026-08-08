package app.organicmaps.util;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

/** Applies the optional fixed-display control dimensions to the currently mounted map UI. */
public final class InCarVisuals
{
  private InCarVisuals() {}

  public static void apply(@NonNull Activity activity, boolean enabled)
  {
    applyMapButtons(activity, enabled);
    applyRoutingControls(activity, enabled);
    applyNavigationControls(activity, enabled);
  }

  private static void applyMapButtons(@NonNull Activity activity, boolean enabled)
  {
    final int buttonSize = dimen(activity, enabled ? R.dimen.in_car_map_button_size : R.dimen.map_button_size);
    final int iconSize = dimen(activity, enabled ? R.dimen.in_car_map_button_icon_size : R.dimen.map_button_icon_size);
    final int zoomIconSize =
        dimen(activity, enabled ? R.dimen.in_car_zoom_button_icon_size : R.dimen.map_button_icon_size);
    final int minTouchTarget = dimen(activity, enabled ? R.dimen.in_car_button_min_touch_target : R.dimen.map_button_size);

    for (int id : new int[] {R.id.btn_search, R.id.btn_bookmarks, R.id.my_position, R.id.layers_button,
                             R.id.menu_button, R.id.help_button, R.id.track_recording_status})
      resizeFab(activity.findViewById(id), buttonSize, iconSize, minTouchTarget);

    resizeFab(activity.findViewById(R.id.nav_zoom_in), buttonSize, zoomIconSize, minTouchTarget);
    resizeFab(activity.findViewById(R.id.nav_zoom_out), buttonSize, zoomIconSize, minTouchTarget);
  }

  private static void applyRoutingControls(@NonNull Activity activity, boolean enabled)
  {
    final int actionButtonSize =
        dimen(activity, enabled ? R.dimen.in_car_routing_action_button_size : R.dimen.routing_action_button_size);
    final int actionIconSize = dimen(
        activity, enabled ? R.dimen.in_car_routing_action_button_icon_size : R.dimen.routing_action_button_icon_size);
    final int minTouchTarget =
        dimen(activity, enabled ? R.dimen.in_car_button_min_touch_target : R.dimen.routing_action_button_size);

    for (int id : new int[] {R.id.routing_btn_search, R.id.routing_btn_bookmarks, R.id.btn__save})
      resizeFab(activity.findViewById(id), actionButtonSize, actionIconSize, minTouchTarget);

    final int routerHeight =
        dimen(activity, enabled ? R.dimen.in_car_routing_toolbar_cell_height : R.dimen.routing_toolbar_cell_height);
    for (int id : new int[] {R.id.vehicle, R.id.pedestrian, R.id.transit, R.id.bicycle, R.id.ruler})
      setViewHeight(activity.findViewById(id), routerHeight);

    final int closeSize =
        dimen(activity, enabled ? R.dimen.in_car_routing_close_button_size : R.dimen.routing_close_button_size);
    setViewSize(activity.findViewById(R.id.back), closeSize, closeSize);
  }

  private static void applyNavigationControls(@NonNull Activity activity, boolean enabled)
  {
    final int contentHeight =
        dimen(activity, enabled ? R.dimen.in_car_nav_menu_content_height : R.dimen.nav_menu_content_height);
    setViewHeight(activity.findViewById(R.id.content_frame), contentHeight);

    final int iconHeight = dimen(activity, enabled ? R.dimen.in_car_nav_icon_size : R.dimen.nav_icon_size);
    final ImageView tts = activity.findViewById(R.id.tts_volume);
    final ImageView settings = activity.findViewById(R.id.settings);
    setViewHeight(tts, iconHeight);
    setViewHeight(settings, iconHeight);
    if (tts != null)
      tts.setScaleType(enabled ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER);
    if (settings != null)
      settings.setScaleType(enabled ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER);

    final Button stop = activity.findViewById(R.id.stop);
    if (stop == null)
      return;
    final int buttonHeight = dimen(activity, enabled ? R.dimen.in_car_nav_button_height : R.dimen.nav_button_height);
    final int stopMinWidth = dimen(activity, enabled ? R.dimen.in_car_nav_stop_min_width : R.dimen.start_button_width);
    setViewHeight(stop, buttonHeight);
    stop.setMinHeight(buttonHeight);
    stop.setMinWidth(stopMinWidth);
  }

  private static int dimen(@NonNull Activity activity, int resId)
  {
    return activity.getResources().getDimensionPixelSize(resId);
  }

  private static void resizeFab(@Nullable View view, int buttonSize, int iconSize, int minTouchTarget)
  {
    if (!(view instanceof FloatingActionButton button))
      return;
    button.setCustomSize(buttonSize);
    button.setMaxImageSize(iconSize);
    button.setMinimumWidth(minTouchTarget);
    button.setMinimumHeight(minTouchTarget);
  }

  private static void setViewHeight(@Nullable View view, int height)
  {
    if (view == null)
      return;
    final ViewGroup.LayoutParams params = view.getLayoutParams();
    if (params == null || params.height == height)
      return;
    params.height = height;
    view.setLayoutParams(params);
  }

  private static void setViewSize(@Nullable View view, int width, int height)
  {
    if (view == null)
      return;
    final ViewGroup.LayoutParams params = view.getLayoutParams();
    if (params == null || (params.width == width && params.height == height))
      return;
    params.width = width;
    params.height = height;
    view.setLayoutParams(params);
  }
}
