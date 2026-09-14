package app.organicmaps.incar;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.location.Location;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.fragment.app.FragmentActivity;
import app.organicmaps.BuildConfig;
import app.organicmaps.MwmApplication;
import app.organicmaps.R;
import app.organicmaps.sdk.routing.RoutingController;
import app.organicmaps.sdk.routing.RoutingInfo;
import app.organicmaps.util.InCarVisuals;

/**
 * Compact current-speed presentation for the InCar navigation glance cluster.
 *
 * <p>The route speed limit remains owned by Organic Maps routing. This view only projects the
 * already-available current speed and cached route limit into a high-contrast visual warning.
 */
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
    refreshWarningState();
  }

  @Override
  public void setVisibility(int visibility)
  {
    super.setVisibility(visibility);
    if (visibility == View.VISIBLE)
      refreshWarningState();
  }

  @Override
  protected void onAttachedToWindow()
  {
    super.onAttachedToWindow();
    captureDefaultTextColors();
    refreshWarningState();
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

  private void refreshWarningState()
  {
    if (!BuildConfig.IS_IN_CAR || !isAttachedToWindow())
      return;

    boolean warning = false;
    try
    {
      final RoutingInfo info = RoutingController.get().getCachedRoutingInfo();
      final Location location = MwmApplication.from(getContext()).getLocationHelper().getSavedLocation();
      warning = info != null && location != null && location.hasSpeed()
             && InCarSpeedDisplayPolicy.isSpeeding(location.getSpeed(), info.speedLimitMps);
    }
    catch (RuntimeException ignored)
    {
      // Fail open while the Activity/native routing graph is being recreated (including PiP transitions).
      warning = false;
    }

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
