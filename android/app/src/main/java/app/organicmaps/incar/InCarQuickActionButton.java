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
import app.organicmaps.R;
import com.google.android.material.color.MaterialColors;

/** Centred icon-only button used by the InCar Quick Destinations strip. */
final class InCarQuickActionButton extends AppCompatImageButton
{
  private static final int SURFACE_ALPHA = 199; // 78%, aligned with the primary InCar map controls.

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

    final int neutralSurface =
        ColorUtils.setAlphaComponent(MaterialColors.getColor(this, R.attr.menuBackground), SURFACE_ALPHA);
    setBackground(createBackground(neutralSurface, feedbackColor, cornerRadiusPx));
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
