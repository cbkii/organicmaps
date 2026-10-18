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

    // A single background belongs to the containing rail; cells own only transient feedback.
    final int pressedFeedbackAlpha = getResources().getInteger(R.integer.in_car_quick_pressed_feedback_alpha);
    final int focusedFeedbackAlpha = getResources().getInteger(R.integer.in_car_quick_focused_feedback_alpha);
    setBackground(createBackground(Color.TRANSPARENT, feedbackColor,
                                   Color.TRANSPARENT, 0, cornerRadiusPx, pressedFeedbackAlpha,
                                   focusedFeedbackAlpha));
  }

  @NonNull
  private static RippleDrawable createBackground(@ColorInt int surfaceColor, @ColorInt int feedbackColor,
                                                 @ColorInt int outlineColor, int outlineWidthPx, int cornerRadiusPx,
                                                 int pressedFeedbackAlpha, int focusedFeedbackAlpha)
  {
    final GradientDrawable surface = roundedDrawable(surfaceColor, cornerRadiusPx);
    surface.setStroke(outlineWidthPx, outlineColor);
    final GradientDrawable mask = roundedDrawable(Color.WHITE, cornerRadiusPx);
    final ColorStateList feedback = new ColorStateList(
        new int[][] {
            {android.R.attr.state_pressed}, {android.R.attr.state_focused}, {android.R.attr.state_hovered}, {}},
        new int[] {ColorUtils.setAlphaComponent(feedbackColor, pressedFeedbackAlpha),
                   ColorUtils.setAlphaComponent(feedbackColor, focusedFeedbackAlpha),
                   ColorUtils.setAlphaComponent(feedbackColor, focusedFeedbackAlpha), Color.TRANSPARENT});
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
