package app.organicmaps.incar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.widget.ImageView;
import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.widget.ImageViewCompat;
import app.organicmaps.R;

/** Centred icon-only button used by the InCar Quick Destinations strip. */
final class InCarQuickActionButton extends AppCompatImageButton
{
  InCarQuickActionButton(@NonNull Context context)
  {
    super(context);
    setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    setClickable(true);
    setFocusable(true);
  }

  void setAppearance(@DrawableRes int iconRes, @ColorInt int feedbackColor, @ColorInt int iconColor, int cornerRadiusPx,
                     int iconPaddingPx)
  {
    setImageResource(iconRes);
    ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(iconColor));
    setPadding(iconPaddingPx, iconPaddingPx, iconPaddingPx, iconPaddingPx);

    final int actionSurface = ContextCompat.getColor(getContext(), R.color.in_car_quick_surface);
    final int surfaceAlpha = getResources().getInteger(R.integer.in_car_quick_surface_alpha);
    final int outlineColor = ContextCompat.getColor(getContext(), R.color.in_car_map_control_outline);
    final int outlineWidth = getResources().getDimensionPixelSize(R.dimen.in_car_map_control_outline_width);
    setBackground(createBackground(ColorUtils.setAlphaComponent(actionSurface, surfaceAlpha), feedbackColor, outlineColor,
                                   outlineWidth, cornerRadiusPx));
  }

  @NonNull
  private static RippleDrawable createBackground(@ColorInt int surfaceColor, @ColorInt int feedbackColor,
                                                 @ColorInt int outlineColor, int outlineWidthPx, int cornerRadiusPx)
  {
    final GradientDrawable surface = roundedDrawable(surfaceColor, cornerRadiusPx);
    surface.setStroke(outlineWidthPx, outlineColor);
    final GradientDrawable mask = roundedDrawable(Color.WHITE, cornerRadiusPx);
    final ColorStateList feedback = new ColorStateList(
        new int[][] {
            {android.R.attr.state_pressed}, {android.R.attr.state_focused}, {android.R.attr.state_hovered}, {}},
        new int[] {ColorUtils.setAlphaComponent(feedbackColor, 112), ColorUtils.setAlphaComponent(feedbackColor, 92),
                   ColorUtils.setAlphaComponent(feedbackColor, 92), Color.TRANSPARENT});
    return new RippleDrawable(feedback, surface, mask);
  }

  @NonNull
  private static GradientDrawable roundedDrawable(@ColorInt int color, int cornerRadiusPx)
  {
    final GradientDrawable drawable = new GradientDrawable();
    drawable.setShape(GradientDrawable.RECTANGLE);
    drawable.setColor(color);
    drawable.setCornerRadius(cornerRadiusPx);
    return drawable;
  }
}
