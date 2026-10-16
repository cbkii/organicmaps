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

  /**
   * Measure both the preferred and extra-compact column footprints. Navigation
   * can choose a 55dp cell after capacity calculation; checking both footprints
   * avoids a circular sizing dependency and misses no narrow intersecting UI.
   */
  @NonNull
  public static Snapshot measureQuickColumn(@NonNull MwmActivity activity, boolean left,
                                            boolean horizontal, int profileSizePx)
  {
    final int mainOffset = InCarMapDockPolicy.quickPhysicalOffsetPx(
        activity.getResources(), profileSizePx, horizontal);
    final Snapshot primary = measure(activity, left, mainOffset, profileSizePx);
    final int compact = Math.min(profileSizePx,
        activity.getResources().getDimensionPixelSize(R.dimen.in_car_touch_target_extra_compact));
    if (compact == profileSizePx)
      return primary;
    final int compactOffset = InCarMapDockPolicy.quickPhysicalOffsetPx(
        activity.getResources(), compact, horizontal);
    final Snapshot fallback = measure(activity, left, compactOffset, compact);
    return new Snapshot(primary.mMeasured || fallback.mMeasured,
                        Math.max(primary.mTop, fallback.mTop),
                        Math.max(primary.mBottom, fallback.mBottom));
  }

  /** Fail-open snapshot for fragment creation before the map is measured. */
  @NonNull
  public static Snapshot unmeasured()
  {
    return new Snapshot(false, 0, 0);
  }

  /** Smallest available vertical band in the frame and real uncluttered map column. */
  public static int usableColumnHeight(int frameAvailable, int mapHeight, int topClearance, int bottomClearance)
  {
    return Math.max(0, Math.min(Math.max(0, frameAvailable),
        Math.max(0, mapHeight - Math.max(0, topClearance) - Math.max(0, bottomClearance))));
  }

  /** Maximal top and bottom occupied bands in the Quick Destinations physical column. */
  @NonNull
  public static Snapshot measure(@NonNull MwmActivity activity, boolean controlsLeft, int columnOffsetPx,
                                 int cellPx)
  {
    final View map = activity.findViewById(R.id.map_container);
    final Rect bounds = visibleRect(map);
    if (bounds == null || bounds.isEmpty())
      return new Snapshot(false, 0, 0);

    final int columnLeft = controlsLeft ? bounds.left + columnOffsetPx
                                        : bounds.right - columnOffsetPx - cellPx;
    final int columnRight = columnLeft + cellPx;

    int top = 0;
    for (int id : new int[] {R.id.street_frame, R.id.nav_next_turn_container, R.id.lanes,
                             R.id.in_car_speed, R.id.in_car_road_speed_limit,
                             R.id.nav_speed_limit, R.id.in_car_nav_speed})
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
