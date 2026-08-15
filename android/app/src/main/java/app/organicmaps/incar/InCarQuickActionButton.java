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
import androidx.core.graphics.ColorUtils;
import androidx.core.widget.ImageViewCompat;

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

  void setAppearance(@DrawableRes int iconRes, @ColorInt int surfaceColor, @ColorInt int iconColor, int cornerRadiusPx,
                     int iconPaddingPx)
  {
    setImageResource(iconRes);
    ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(iconColor));
    setPadding(iconPaddingPx, iconPaddingPx, iconPaddingPx, iconPaddingPx);
    setBackground(createBackground(surfaceColor, iconColor, cornerRadiusPx));
  }

  @NonNull
  private static RippleDrawable createBackground(@ColorInt int surfaceColor, @ColorInt int feedbackColor,
                                                 int cornerRadiusPx)
  {
    final GradientDrawable surface = roundedDrawable(surfaceColor, cornerRadiusPx);
    final GradientDrawable mask = roundedDrawable(Color.WHITE, cornerRadiusPx);
    final ColorStateList feedback = new ColorStateList(
        new int[][] {
            {android.R.attr.state_pressed}, {android.R.attr.state_focused}, {android.R.attr.state_hovered}, {}},
        new int[] {ColorUtils.setAlphaComponent(feedbackColor, 82), ColorUtils.setAlphaComponent(feedbackColor, 61),
                   ColorUtils.setAlphaComponent(feedbackColor, 61), Color.TRANSPARENT});
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
