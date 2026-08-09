package app.organicmaps.util;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import app.organicmaps.R;
import app.organicmaps.sdk.util.Config;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.Map;
import java.util.WeakHashMap;

/** Applies the optional fixed-display control dimensions to the currently mounted map UI. */
public final class InCarVisuals
{
  @VisibleForTesting
  static final int COMPACT_WIDTH_DP = 720;
  @VisibleForTesting
  static final int COMPACT_HEIGHT_DP = 480;

  private static final Map<FragmentActivity, Observation> OBSERVATIONS = new WeakHashMap<>();

  @VisibleForTesting
  enum WindowProfile {
    FULL,
    COMPACT_WIDTH,
    COMPACT_HEIGHT,
    COMPACT_BOTH
  }

  private static final class Observation
  {
    @Nullable
    WindowProfile profile;
    boolean optimisedVisuals;
    int width = -1;
    int height = -1;
  }

  private InCarVisuals() {}

  @UiThread
  public static void applyAndObserve(@NonNull FragmentActivity activity)
  {
    Observation observation = OBSERVATIONS.get(activity);
    if (observation == null)
    {
      observation = new Observation();
      OBSERVATIONS.put(activity, observation);
      final Observation state = observation;

      final View content = activity.findViewById(android.R.id.content);
      if (content != null)
      {
        content.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
          if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop)
            v.post(() -> applyForCurrentBounds(activity, state, false));
        });
      }

      activity.getSupportFragmentManager().registerFragmentLifecycleCallbacks(
          new FragmentManager.FragmentLifecycleCallbacks() {
            @Override
            public void onFragmentViewCreated(@NonNull FragmentManager fm, @NonNull Fragment fragment,
                                              @NonNull View view, @Nullable Bundle savedInstanceState)
            {
              applyForCurrentBounds(activity, state, true);
            }

            @Override
            public void onFragmentStarted(@NonNull FragmentManager fm, @NonNull Fragment fragment)
            {
              if (fragment instanceof DialogFragment dialogFragment)
              {
                final Dialog dialog = dialogFragment.getDialog();
                if (dialog != null)
                  fitDialog(activity, dialog);
              }
            }
          },
          true);
    }

    applyForCurrentBounds(activity, observation, true);
  }

  @UiThread
  public static void fitDialog(@NonNull FragmentActivity activity, @NonNull Dialog dialog)
  {
    final Window window = dialog.getWindow();
    if (window == null)
      return;

    final WindowManager.LayoutParams attributes = window.getAttributes();
    if (attributes.width == ViewGroup.LayoutParams.MATCH_PARENT
        && attributes.height == ViewGroup.LayoutParams.MATCH_PARENT)
      return;

    final View content = activity.findViewById(android.R.id.content);
    if (content == null || content.getWidth() <= 0 || content.getHeight() <= 0)
      return;

    final int margin = dimen(activity, R.dimen.in_car_dialog_window_margin);
    final int availableWidth = content.getWidth() - 2 * margin;
    final int availableHeight = content.getHeight() - 2 * margin;
    if (availableWidth <= 0 || availableHeight <= 0)
      return;

    final int targetWidth = Math.min(availableWidth, dimen(activity, R.dimen.in_car_dialog_max_width));
    window.setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT);

    final View decor = window.getDecorView();
    decor.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
      @Override
      public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop,
                                 int oldRight, int oldBottom)
      {
        v.removeOnLayoutChangeListener(this);
        if (!dialog.isShowing())
          return;

        final int height = bottom - top;
        if (height > availableHeight)
          v.post(() -> window.setLayout(targetWidth, availableHeight));
      }
    });
  }

  private static void applyForCurrentBounds(@NonNull FragmentActivity activity, @NonNull Observation observation,
                                            boolean force)
  {
    final View content = activity.findViewById(android.R.id.content);
    final int width = content == null ? 0 : content.getWidth();
    final int height = content == null ? 0 : content.getHeight();
    final WindowProfile profile = resolveWindowProfile(activity, width, height);
    final boolean optimisedVisuals = Config.isInCarOptimisedVisualsEnabled();

    final boolean boundsChanged = width != observation.width || height != observation.height;
    final boolean controlsChanged =
        force || profile != observation.profile || optimisedVisuals != observation.optimisedVisuals;

    observation.width = width;
    observation.height = height;
    observation.profile = profile;
    observation.optimisedVisuals = optimisedVisuals;

    if (controlsChanged)
      apply(activity, optimisedVisuals, profile);
    if (force || boundsChanged)
      fitVisibleDialogs(activity.getSupportFragmentManager(), activity);
  }

  @NonNull
  private static WindowProfile resolveWindowProfile(@NonNull Activity activity, int width, int height)
  {
    final float density = activity.getResources().getDisplayMetrics().density;
    final int widthDp =
        width > 0 ? Math.round(width / density) : activity.getResources().getConfiguration().screenWidthDp;
    final int heightDp =
        height > 0 ? Math.round(height / density) : activity.getResources().getConfiguration().screenHeightDp;
    return classifyWindow(widthDp, heightDp);
  }

  @VisibleForTesting
  @NonNull
  static WindowProfile classifyWindow(int widthDp, int heightDp)
  {
    if (widthDp <= 0 || heightDp <= 0)
      return WindowProfile.FULL;

    final boolean compactWidth = widthDp < COMPACT_WIDTH_DP;
    final boolean compactHeight = heightDp < COMPACT_HEIGHT_DP;
    if (compactWidth && compactHeight)
      return WindowProfile.COMPACT_BOTH;
    if (compactWidth)
      return WindowProfile.COMPACT_WIDTH;
    if (compactHeight)
      return WindowProfile.COMPACT_HEIGHT;
    return WindowProfile.FULL;
  }

  private static boolean isCompact(@NonNull WindowProfile profile)
  {
    return profile != WindowProfile.FULL;
  }

  private static void apply(@NonNull Activity activity, boolean enabled, @NonNull WindowProfile profile)
  {
    applyMapButtons(activity, enabled, profile);
    applyRoutingControls(activity, enabled, profile);
    applyNavigationControls(activity, enabled, profile);
    applyPlacePageControls(activity, profile);
  }

  private static void applyMapButtons(@NonNull Activity activity, boolean enabled, @NonNull WindowProfile profile)
  {
    final View root = activity.findViewById(R.id.map_buttons);
    if (root == null)
      return;

    final boolean compact = isCompact(profile);
    final int buttonSize =
        dimen(activity, enabled ? (compact ? R.dimen.in_car_compact_map_button_size : R.dimen.in_car_map_button_size)
                                : R.dimen.map_button_size);
    final int iconSize = dimen(activity, enabled ? (compact ? R.dimen.in_car_compact_map_button_icon_size
                                                            : R.dimen.in_car_map_button_icon_size)
                                                 : R.dimen.map_button_icon_size);
    final int zoomIconSize = dimen(activity, enabled ? (compact ? R.dimen.in_car_compact_zoom_button_icon_size
                                                                : R.dimen.in_car_zoom_button_icon_size)
                                                     : R.dimen.map_button_icon_size);
    final int minTouchTarget = dimen(activity, enabled ? (compact ? R.dimen.in_car_compact_button_min_touch_target
                                                                  : R.dimen.in_car_button_min_touch_target)
                                                       : R.dimen.map_button_size);

    for (int id : new int[] {R.id.btn_search, R.id.btn_bookmarks, R.id.my_position, R.id.layers_button,
                             R.id.menu_button, R.id.help_button, R.id.track_recording_status})
      resizeFab(root.findViewById(id), buttonSize, iconSize, minTouchTarget);

    resizeFab(root.findViewById(R.id.nav_zoom_in), buttonSize, zoomIconSize, minTouchTarget);
    resizeFab(root.findViewById(R.id.nav_zoom_out), buttonSize, zoomIconSize, minTouchTarget);
  }

  private static void applyRoutingControls(@NonNull Activity activity, boolean enabled, @NonNull WindowProfile profile)
  {
    final View root = activity.findViewById(R.id.routing_root);
    if (root == null)
      return;

    final boolean compact = isCompact(profile);
    final int actionButtonSize = dimen(activity, enabled ? (compact ? R.dimen.in_car_compact_routing_action_button_size
                                                                    : R.dimen.in_car_routing_action_button_size)
                                                         : R.dimen.routing_action_button_size);
    final int actionIconSize =
        dimen(activity, enabled ? (compact ? R.dimen.in_car_compact_routing_action_button_icon_size
                                           : R.dimen.in_car_routing_action_button_icon_size)
                                : R.dimen.routing_action_button_icon_size);
    final int minTouchTarget = dimen(activity, enabled ? (compact ? R.dimen.in_car_compact_button_min_touch_target
                                                                  : R.dimen.in_car_button_min_touch_target)
                                                       : R.dimen.routing_action_button_size);

    for (int id : new int[] {R.id.routing_btn_search, R.id.routing_btn_bookmarks, R.id.btn__save})
      resizeFab(root.findViewById(id), actionButtonSize, actionIconSize, minTouchTarget);

    final int routerHeight = dimen(activity, enabled ? (compact ? R.dimen.in_car_compact_routing_toolbar_cell_height
                                                                : R.dimen.in_car_routing_toolbar_cell_height)
                                                     : R.dimen.routing_toolbar_cell_height);
    for (int id : new int[] {R.id.vehicle, R.id.pedestrian, R.id.transit, R.id.bicycle, R.id.ruler})
      setViewHeight(root.findViewById(id), routerHeight);

    final int closeSize =
        dimen(activity, compact ? R.dimen.in_car_compact_close_button_size : R.dimen.in_car_routing_close_button_size);
    setViewSize(root.findViewById(R.id.back), closeSize, closeSize);
    root.requestLayout();
  }

  private static void applyNavigationControls(@NonNull Activity activity, boolean enabled,
                                              @NonNull WindowProfile profile)
  {
    final View root = activity.findViewById(R.id.nav_bottom_frame);
    if (root == null)
      return;

    final boolean compact = isCompact(profile);
    final int contentHeight = dimen(activity, enabled ? (compact ? R.dimen.in_car_compact_nav_menu_content_height
                                                                 : R.dimen.in_car_nav_menu_content_height)
                                                      : R.dimen.nav_menu_content_height);
    setViewHeight(root.findViewById(R.id.content_frame), contentHeight);

    final int iconHeight =
        dimen(activity, enabled ? (compact ? R.dimen.in_car_compact_nav_icon_size : R.dimen.in_car_nav_icon_size)
                                : R.dimen.nav_icon_size);
    final ImageView tts = root.findViewById(R.id.tts_volume);
    final ImageView settings = root.findViewById(R.id.settings);
    setViewHeight(tts, iconHeight);
    setViewHeight(settings, iconHeight);
    if (tts != null)
      tts.setScaleType(enabled ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER);
    if (settings != null)
      settings.setScaleType(enabled ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER);

    final Button stop = root.findViewById(R.id.stop);
    if (stop == null)
      return;
    final int buttonHeight = dimen(
        activity, enabled ? (compact ? R.dimen.in_car_compact_nav_button_height : R.dimen.in_car_nav_button_height)
                          : R.dimen.nav_button_height);
    final int stopMinWidth = dimen(
        activity, enabled ? (compact ? R.dimen.in_car_compact_nav_stop_min_width : R.dimen.in_car_nav_stop_min_width)
                          : R.dimen.start_button_width);
    setViewHeight(stop, buttonHeight);
    stop.setMinHeight(buttonHeight);
    stop.setMinWidth(stopMinWidth);
    root.requestLayout();
  }

  private static void applyPlacePageControls(@NonNull Activity activity, @NonNull WindowProfile profile)
  {
    final MaterialButton close = activity.findViewById(R.id.close_button);
    if (close == null)
      return;

    final boolean compact = isCompact(profile);
    final int controlSize = dimen(
        activity, compact ? R.dimen.in_car_compact_close_button_size : R.dimen.in_car_place_page_close_button_size);
    final int iconSize =
        dimen(activity, compact ? R.dimen.in_car_compact_close_icon_size : R.dimen.in_car_close_icon_size);
    setViewSize(close, controlSize, controlSize);
    close.setMinimumWidth(controlSize);
    close.setMinimumHeight(controlSize);
    close.setIconSize(iconSize);
  }

  private static void fitVisibleDialogs(@NonNull FragmentManager fragmentManager, @NonNull FragmentActivity activity)
  {
    for (Fragment fragment : fragmentManager.getFragments())
    {
      if (fragment instanceof DialogFragment dialogFragment)
      {
        final Dialog dialog = dialogFragment.getDialog();
        if (dialog != null && dialog.isShowing())
          fitDialog(activity, dialog);
      }

      if (fragment.isAdded())
        fitVisibleDialogs(fragment.getChildFragmentManager(), activity);
    }
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
