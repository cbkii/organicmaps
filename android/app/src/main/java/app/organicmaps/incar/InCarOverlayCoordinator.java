package app.organicmaps.incar;

import android.graphics.Rect;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.MwmActivity;
import app.organicmaps.R;

/**
 * Read-only measurement of occupied map regions. All rectangles are screen-coordinate
 * results of getGlobalVisibleRect(), so Android 10 vendor window insets are not
 * subtracted a second time. This class never re-parents or takes over map/native UI.
 */
public final class InCarOverlayCoordinator
{
  private InCarOverlayCoordinator() {}

  public static final class Snapshot
  {
    private final boolean mMeasured;
    private final int mTop;
    private final int mBottom;

    private Snapshot(boolean measured, int top, int bottom)
    {
      mMeasured = measured;
      mTop = top;
      mBottom = bottom;
    }

    public boolean hasMapBounds() { return mMeasured; }
    public int topClearancePx() { return mTop; }
    public int bottomClearancePx() { return mBottom; }
  }

  /** Maximal top and bottom occupied bands in the Quick Destinations physical column. */
  @NonNull
  public static Snapshot measure(@NonNull MwmActivity activity, boolean controlsLeft, int cellPx)
  {
    final View map = activity.findViewById(R.id.map_container);
    final Rect bounds = visibleRect(map);
    if (bounds == null || bounds.isEmpty())
      return new Snapshot(false, 0, 0);

    final int edge = activity.getResources().getDimensionPixelSize(R.dimen.in_car_map_dock_edge);
    final int gap = activity.getResources().getDimensionPixelSize(R.dimen.in_car_map_dock_column_gap);
    final int offset = edge + cellPx + gap;
    final int columnLeft = controlsLeft ? bounds.left + offset : bounds.right - offset - cellPx;
    final int columnRight = columnLeft + cellPx;

    int top = 0;
    for (int id : new int[] {R.id.street_frame, R.id.nav_next_turn_container, R.id.lanes,
                             R.id.in_car_driving_speed_cluster})
    {
      final Rect rect = visibleRect(activity.findViewById(id));
      if (rect != null && overlapsColumn(rect, columnLeft, columnRight)
          && rect.top < bounds.centerY())
        top = Math.max(top, Math.min(bounds.height(), rect.bottom - bounds.top));
    }

    int bottom = 0;
    for (int id : new int[] {R.id.nav_bottom_frame, R.id.routing_bottom_buttons, R.id.map_buttons_bottom})
    {
      final Rect rect = visibleRect(activity.findViewById(id));
      if (rect != null && overlapsColumn(rect, columnLeft, columnRight)
          && rect.bottom > bounds.centerY())
        bottom = Math.max(bottom, Math.min(bounds.height(), bounds.bottom - rect.top));
    }

    return new Snapshot(true, Math.max(0, top), Math.max(0, bottom));
  }

  /** Interval intersection only; touching edges are not an overlap. */
  public static boolean overlapsColumn(@NonNull Rect rect, int left, int right)
  {
    return rect.left < right && rect.right > left;
  }

  @Nullable
  private static Rect visibleRect(@Nullable View view)
  {
    if (view == null || view.getVisibility() != View.VISIBLE || !view.isShown())
      return null;
    final Rect rect = new Rect();
    return view.getGlobalVisibleRect(rect) && !rect.isEmpty() ? rect : null;
  }
}
