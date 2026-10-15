package app.organicmaps.incar;

import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.R;

/** Common physical-space policy for Camera, Quick Destinations and Search/Menu footer. */
public final class InCarMapDockPolicy
{
  private InCarMapDockPolicy() {}

  public static int railSizePx(@NonNull Resources res)
  {
    return res.getDimensionPixelSize(R.dimen.in_car_map_rail_cell_size);
  }

  public static int edgePx(@NonNull Resources res)
  {
    return res.getDimensionPixelSize(R.dimen.in_car_map_dock_edge);
  }

  public static int columnGapPx(@NonNull Resources res)
  {
    return res.getDimensionPixelSize(R.dimen.in_car_map_dock_column_gap);
  }

  public static int footerGapPx(@NonNull Resources res)
  {
    return res.getDimensionPixelSize(R.dimen.in_car_map_dock_footer_gap);
  }

  public static int cameraBottomMarginPx(@NonNull Resources res, boolean navigation, int cellPx)
  {
    final int footer = navigation ? res.getDimensionPixelSize(R.dimen.nav_menu_height) : cellPx;
    return footer + footerGapPx(res);
  }

  public static int quickBottomMarginPx(@NonNull Resources res, boolean navigation,
                                        int cellPx, int horizontalCameraHeightPx)
  {
    return cameraBottomMarginPx(res, navigation, cellPx) + edgePx(res)
           + (horizontalCameraHeightPx > 0 ? horizontalCameraHeightPx + footerGapPx(res) : 0);
  }

  public static int quickPhysicalOffsetPx(@NonNull Resources res, int cellPx, boolean horizontalCamera)
  {
    return edgePx(res) + (horizontalCamera ? 0 : cellPx + columnGapPx(res));
  }

  public static void positionCameraFrame(@Nullable View frame, @NonNull Resources res,
                                         boolean navigation, int cellPx)
  {
    if (frame == null || !(frame.getLayoutParams() instanceof ViewGroup.MarginLayoutParams params))
      return;
    final int edge = edgePx(res);
    if (frame.getPaddingLeft() != edge || frame.getPaddingTop() != edge
        || frame.getPaddingRight() != edge || frame.getPaddingBottom() != edge)
      frame.setPadding(edge, edge, edge, edge);
    final int bottom = cameraBottomMarginPx(res, navigation, cellPx);
    if (params.bottomMargin != bottom)
    {
      params.bottomMargin = bottom;
      frame.setLayoutParams(params);
    }
  }
}
