package app.organicmaps.incar;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import app.organicmaps.R;

/** Passive viewport paint; speed validity and hysteresis belong exclusively to the existing controller. */
public final class InCarSpeedWarningView extends View
{
  public InCarSpeedWarningView(@NonNull Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
    setClickable(false);
    setFocusable(false);
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    setAlpha(0.5f);
    setVisibility(GONE);
  }

  public void render(boolean active, @NonNull InCarSpeedDisplayPolicy.Severity severity)
  {
    setBackgroundColor(ContextCompat.getColor(getContext(), severity == InCarSpeedDisplayPolicy.Severity.SEVERE
                                                                ? R.color.in_car_speed_edge_warning_red
                                                                : R.color.in_car_speed_edge_warning_orange));
    setVisibility(active && severity != InCarSpeedDisplayPolicy.Severity.NORMAL ? VISIBLE : GONE);
  }

  public void updateGeometry(int circleSizePx, boolean controlsOnLeft, @NonNull Insets safe)
  {
    final FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) getLayoutParams();
    final int width =
        circleSizePx + getResources().getDimensionPixelSize(R.dimen.in_car_speed_edge_warning_extra_width);
    // The physical driver side must not be mirrored by an RTL locale.
    final int gravity = (controlsOnLeft ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP;
    if (params.width == width && params.gravity == gravity && params.leftMargin == safe.left
        && params.topMargin == safe.top && params.rightMargin == safe.right && params.bottomMargin == safe.bottom)
      return;
    params.width = width;
    params.gravity = gravity;
    params.setMargins(safe.left, safe.top, safe.right, safe.bottom);
    setLayoutParams(params);
  }
}
