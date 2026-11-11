package app.organicmaps.incar;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
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
  private boolean mHasWarningState;
  private boolean mWarningState;
  private boolean mNight;
  private InCarSpeedDisplayPolicy.Severity mSeverity = InCarSpeedDisplayPolicy.Severity.NORMAL;

  public InCarNavigationSpeedView(@NonNull Context context)
  {
    super(context);
  }

  public InCarNavigationSpeedView(@NonNull Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
  }

  public InCarNavigationSpeedView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr)
  {
    super(context, attrs, defStyleAttr);
  }

  @Override
  public void setText(@Nullable CharSequence text, BufferType type)
  {
    final CharSequence value = text == null ? "" : InCarSpeedDisplayPolicy.compactFormattedSpeed(text);
    super.setText(value, type);
  }

  public void setSpeeding(boolean warning)
  {
    final InCarSpeedDisplayPolicy.Severity severity = InCarSpeedDisplayPolicy.severity();
    final boolean night = app.organicmaps.util.ThemeUtils.isDarkTheme(getContext());
    if (mHasWarningState && mWarningState == warning && mSeverity == severity && mNight == night)
      return;

    mHasWarningState = true;
    mWarningState = warning;
    mSeverity = severity;
    mNight = night;
    androidx.core.view.ViewCompat.setBackgroundTintList(this, null);
    setBackgroundResource(!warning ? R.drawable.in_car_speed_circle_background
                          : severity == InCarSpeedDisplayPolicy.Severity.WARNING
                              ? R.drawable.in_car_speed_warning_orange_background
                              : R.drawable.in_car_speed_warning_background);
    if (warning)
    {
      // Use a coloured solid instead of tinting the whole drawable: preserve the dark outline.
      // Severe red is bright with black text in daylight, dark with white text at night.
      setTextColor(!night || severity == InCarSpeedDisplayPolicy.Severity.WARNING ? Color.BLACK : Color.WHITE);
    }
    else
    {
      androidx.core.view.ViewCompat.setBackgroundTintList(this, null);
      setTextColor(app.organicmaps.util.ThemeUtils.getColor(getContext(), R.attr.iconTint));
    }
  }

  @Override
  protected void onAttachedToWindow()
  {
    super.onAttachedToWindow();
    if (mHasWarningState)
      setSpeeding(mWarningState);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
  {
    final FragmentActivity activity = findActivity(getContext());
    if (BuildConfig.IS_IN_CAR && activity != null)
    {
      int size = InCarVisuals.currentSpeedCircleSizePx(activity);
      if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED)
        size = Math.min(size, MeasureSpec.getSize(widthMeasureSpec));
      if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED)
        size = Math.min(size, MeasureSpec.getSize(heightMeasureSpec));
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

  @Override
  protected void onConfigurationChanged(@NonNull Configuration newConfig)
  {
    super.onConfigurationChanged(newConfig);
    // The same warning severity can survive a scheduled or manual theme change.
    mHasWarningState = false;
    setSpeeding(mWarningState);
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
