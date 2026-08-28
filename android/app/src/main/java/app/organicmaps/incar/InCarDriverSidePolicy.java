package app.organicmaps.incar;

import android.view.Gravity;

import androidx.annotation.NonNull;

/**
 * Automotive RHD (Right-Hand Drive) physical layout policy for InCar controls.
 *
 * <p>The physical driver in InCar is seated to the <em>physical right</em> of the display.
 * All high-frequency driver-interactive controls (zoom, recenter, search, navigation End,
 * Quick actions, Track Recording) must be on the physical-right side of the screen to be
 * reachable one-handed.
 *
 * <p><strong>This must never use {@link Gravity#END} or {@code layout_gravity="end"}.</strong>
 * {@code Gravity.END} is locale/RTL-relative and would flip to the passenger side in RTL locales.
 * Always use the explicit physical constants provided by this class.
 */
public final class InCarDriverSidePolicy
{
  private InCarDriverSidePolicy() {}

  /**
   * Returns the physical-right gravity flag for use in {@link android.widget.FrameLayout.LayoutParams}
   * or any layout that accepts explicit gravity. Combines with a vertical alignment as needed,
   * e.g. {@code driverSideGravity() | Gravity.CENTER_VERTICAL}.
   */
  public static int driverSideGravity()
  {
    return Gravity.RIGHT;
  }

  /**
   * Returns the combined driver-side + center-vertical gravity suitable for a side control
   * that is vertically centred within its parent.
   */
  public static int driverSideCenterVerticalGravity()
  {
    return Gravity.RIGHT | Gravity.CENTER_VERTICAL;
  }

  /**
   * Returns the combined driver-side + bottom gravity suitable for a control anchored to the
   * bottom-right of its parent (e.g. zoom cluster, Quick actions stack).
   */
  public static int driverSideBottomGravity()
  {
    return Gravity.RIGHT | Gravity.BOTTOM;
  }

  /**
   * Returns the physical-right margin value (in pixels) that should be applied as
   * {@code rightMargin} (NOT {@code marginEnd}) on any driver-side control.
   *
   * @param baseMarginPx the desired margin in pixels, typically a dp-converted value
   */
  @NonNull
  public static android.widget.FrameLayout.LayoutParams driverSideLayoutParams(
      int widthPx, int heightPx, int rightMarginPx)
  {
    final android.widget.FrameLayout.LayoutParams lp =
        new android.widget.FrameLayout.LayoutParams(widthPx, heightPx, driverSideGravity());
    lp.rightMargin = rightMarginPx;
    return lp;
  }
}
