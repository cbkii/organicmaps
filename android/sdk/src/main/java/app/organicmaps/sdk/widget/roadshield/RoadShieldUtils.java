package app.organicmaps.sdk.widget.roadshield;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.CharacterStyle;
import android.text.style.ImageSpan;
import android.text.style.ReplacementSpan;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.sdk.routing.roadshield.RoadShield;
import app.organicmaps.sdk.routing.roadshield.RoadShieldInfo;
import java.util.Objects;
import java.util.function.Function;

public class RoadShieldUtils
{
  public interface RoadShieldSpanCreator
  {
    @NonNull
    CharacterStyle create(@NonNull RoadShieldDrawable roadShieldDrawable);
  }

  @NonNull
  public static CharSequence createStreetTextWithShields(@NonNull String street,
                                                         @Nullable RoadShieldInfo roadShieldInfo, float textSize)
  {
    return createStreetTextWithShields(RoadShieldUtils::createRoadShieldSpan, street, roadShieldInfo, textSize, true);
  }

  @NonNull
  public static CharSequence createStreetTextWithShields(@NonNull RoadShieldSpanCreator roadShieldSpanCreator,
                                                         @NonNull String street,
                                                         @Nullable RoadShieldInfo roadShieldInfo, float textSize,
                                                         boolean drawOutline)
  {
    return applyStreetShields(
        street, roadShieldInfo,
        shield -> roadShieldSpanCreator.create(new RoadShieldDrawable(shield, textSize, drawOutline)));
  }

  @NonNull
  public static CharSequence createAdaptiveStreetTextWithShields(@NonNull String street,
                                                                 @Nullable RoadShieldInfo roadShieldInfo)
  {
    return applyStreetShields(street, roadShieldInfo, AdaptiveShieldSpan::new);
  }

  @NonNull
  private static CharSequence applyStreetShields(@NonNull String street, @Nullable RoadShieldInfo roadShieldInfo,
                                                 @NonNull Function<RoadShield, CharacterStyle> spanCreator)
  {
    if (roadShieldInfo == null)
      return street;

    final SpannableString text = new SpannableString(street);
    if (roadShieldInfo.hasTargetRoadShields())
      applyRoadShieldsSpannables(spanCreator, text, Objects.requireNonNull(roadShieldInfo.targetRoadShields),
                                 roadShieldInfo.targetRoadShieldsIndexStart, roadShieldInfo.targetRoadShieldsIndexEnd);
    return text;
  }

  private static void applyRoadShieldsSpannables(@NonNull Function<RoadShield, CharacterStyle> spanCreator,
                                                 @NonNull SpannableString text, @NonNull RoadShield[] shields,
                                                 int indexStart, int indexEnd)
  {
    int offset = 0;
    for (int i = 0; i < shields.length; i++)
    {
      final CharacterStyle shieldSpan = spanCreator.apply(shields[i]);
      final int start = indexStart + offset;
      final int end = i < shields.length - 1 ? start + 1 : indexEnd;
      text.setSpan(shieldSpan, start, end, Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
      offset += 1;
    }
  }

  @NonNull
  private static CharacterStyle createRoadShieldSpan(@NonNull RoadShieldDrawable roadShieldDrawable)
  {
    return new ImageSpan(roadShieldDrawable, ImageSpan.ALIGN_BOTTOM);
  }

  /** Measure and draw at the TextView's chosen size, including during autosize trial layouts. */
  private static final class AdaptiveShieldSpan extends ReplacementSpan
  {
    private final RoadShield mShield;
    private RoadShieldDrawable mDrawable;
    private float mTextSize;

    AdaptiveShieldSpan(RoadShield shield)
    {
      mShield = shield;
    }

    private RoadShieldDrawable drawable(Paint paint)
    {
      if (mDrawable == null || mTextSize != paint.getTextSize())
      {
        mTextSize = paint.getTextSize();
        mDrawable = new RoadShieldDrawable(mShield, mTextSize, true);
      }
      return mDrawable;
    }

    private static float fitHeight(Paint paint, RoadShieldDrawable drawable)
    {
      return Math.min(1.0f, (paint.descent() - paint.ascent()) / drawable.getIntrinsicHeight());
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm)
    {
      final RoadShieldDrawable drawable = drawable(paint);
      return (int) Math.ceil(drawable.getIntrinsicWidth() * fitHeight(paint, drawable));
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom,
                     @NonNull Paint paint)
    {
      final RoadShieldDrawable drawable = drawable(paint);
      final float scale = fitHeight(paint, drawable);
      final float offset = (paint.ascent() + paint.descent() - drawable.getIntrinsicHeight() * scale) / 2.0f;
      final int save = canvas.save();
      canvas.translate(x, y + offset);
      canvas.scale(scale, scale);
      drawable.draw(canvas);
      canvas.restoreToCount(save);
    }
  }
}
