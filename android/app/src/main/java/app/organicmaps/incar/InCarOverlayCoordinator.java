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

  /**
   * Compute the map engine's visible viewport, in SurfaceView-local pixels.
   * An active bottom sheet owns the bottom band; a genuine edge-mounted panel owns
   * its side. Invisible/off-screen panels own no map area.
   *
   * The existing PlacePageUtils callback remains the *only* writer to
   * Framework.nativeSetVisibleRect. Do not change native Follow or route state.
   */
  @NonNull
  public static Rect placePageViewport(@NonNull View map, @Nullable View activePanel,
                                       @Nullable View camera, int minimumHeightPx, int gapPx)
  {
    final int width = map.getWidth();
    final int height = map.getHeight();
    final Rect full = new Rect(0, 0, Math.max(0, width), Math.max(0, height));
    final Rect mapScreen = visibleRect(map);
    final Rect pageScreen = visibleRect(activePanel);
    if (mapScreen == null || pageScreen == null || width <= 0 || height <= 0
        || !Rect.intersects(mapScreen, pageScreen))
      return full;

    final Rect covered = new Rect(pageScreen);
    if (!covered.intersect(mapScreen))
      return full;
    final int padding = Math.max(0, gapPx);
    int left = 0;
    int right = width;
    int bottom = height;
    // Prefer the actual sheet's width and edge instead of treating every
    // narrow/side-mounted panel as a full-width bottom sheet.
    final boolean bottomSheet = covered.width() * 2 >= mapScreen.width()
                                && covered.bottom >= mapScreen.bottom - padding;
    if (bottomSheet)
      bottom = covered.top - mapScreen.top - padding;
    else if (covered.height() * 2 >= mapScreen.height())
    {
      if (covered.left <= mapScreen.left + padding)
        left = covered.right - mapScreen.left + padding;
      else if (covered.right >= mapScreen.right - padding)
        right = covered.left - mapScreen.left - padding;
    }

    // Camera controls remain actionable above the sheet in browsing mode.
    // Constrain the selected POI to their *actual* physical edge only when a
    // place page is present; active navigation is not a place-page owner.
    final Rect rail = visibleRect(camera);
    if (rail != null && Rect.intersects(rail, mapScreen))
    {
      if (rail.centerX() < mapScreen.centerX())
        left = Math.max(left, rail.right - mapScreen.left + padding);
      else
        right = Math.min(right, rail.left - mapScreen.left - padding);
    }
    final int[] bounded = boundedViewport(width, height, left, 0, right, bottom,
                                          Math.max(100, minimumHeightPx));
    return new Rect(bounded[0], bounded[1], bounded[2], bounded[3]);
  }

  /** The C++ viewport contract ignores rectangles narrower or shorter than 100px. */
  static int[] boundedViewport(int width, int height, int left, int top, int right, int bottom, int minPx)
  {
    final int w = Math.max(0, width);
    final int h = Math.max(0, height);
    final int l = Math.max(0, Math.min(w, left));
    final int t = Math.max(0, Math.min(h, top));
    final int rr = Math.max(l, Math.min(w, right));
    final int b = Math.max(t, Math.min(h, bottom));
    if (rr - l < Math.max(100, minPx) || b - t < Math.max(100, minPx))
      return new int[] {0, 0, w, h};
    return new int[] {l, t, rr, b};
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
