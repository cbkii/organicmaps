package app.organicmaps.incar;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.TypedValue;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.fragment.app.FragmentActivity;
import app.organicmaps.BuildConfig;
import app.organicmaps.R;
import app.organicmaps.util.InCarVisuals;

/** Compact current-speed presentation for the InCar navigation glance cluster. */
public final class InCarNavigationSpeedView extends AppCompatTextView
{
  @Nullable
  private ColorStateList mDefaultTextColors;
  private boolean mHasWarningState;
  private boolean mWarningState;

  public InCarNavigationSpeedView(@NonNull Context context)
  {
    super(context);
    captureDefaultTextColors();
  }

  public InCarNavigationSpeedView(@NonNull Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
    captureDefaultTextColors();
  }

  public InCarNavigationSpeedView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr)
  {
    super(context, attrs, defStyleAttr);
    captureDefaultTextColors();
  }

  @Override
  public void setText(@Nullable CharSequence text, BufferType type)
  {
    final CharSequence value = text == null ? "" : InCarSpeedDisplayPolicy.compactFormattedSpeed(text);
    super.setText(value, type);
  }

  public void setSpeeding(boolean warning)
  {
    if (mHasWarningState && mWarningState == warning)
      return;

    mHasWarningState = true;
    mWarningState = warning;
    setBackgroundResource(warning ? R.drawable.in_car_speed_warning_background
                                  : R.drawable.in_car_speed_circle_background);
    if (warning)
      setTextColor(Color.WHITE);
    else if (mDefaultTextColors != null)
      setTextColor(mDefaultTextColors);
  }

  @Override
  protected void onAttachedToWindow()
  {
    super.onAttachedToWindow();
    captureDefaultTextColors();
    if (mHasWarningState)
      setSpeeding(mWarningState);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
  {
    final FragmentActivity activity = findActivity(getContext());
    if (BuildConfig.IS_IN_CAR && activity != null)
    {
      final int size = InCarVisuals.currentQuickActionSizePx(activity);
      final int compact = getResources().getDimensionPixelSize(R.dimen.in_car_touch_target_min);
      final int extraCompact = getResources().getDimensionPixelSize(R.dimen.in_car_touch_target_extra_compact);
      final float textSp = size <= extraCompact ? 20.0f : size <= compact ? 24.0f : 28.0f;
      setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp);
      final int exact = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
      super.onMeasure(exact, exact);
      return;
    }
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }

  private void captureDefaultTextColors()
  {
    if (!mHasWarningState || !mWarningState)
      mDefaultTextColors = getTextColors();
  }

  @Nullable
  private static FragmentActivity findActivity(@NonNull Context context)
  {
    Context current = context;
    while (current instanceof ContextWrapper)
    {
      if (current instanceof FragmentActivity activity)
        return activity;
      final Context base = ((ContextWrapper) current).getBaseContext();
      if (base == current)
        break;
      current = base;
    }
    return current instanceof FragmentActivity activity ? activity : null;
  }
}
