package app.organicmaps.widget.menu;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.SparseArray;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import androidx.annotation.AttrRes;
import androidx.annotation.DimenRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.widget.ImageViewCompat;
import app.organicmaps.BuildConfig;
import app.organicmaps.R;
import app.organicmaps.sdk.Map;
import app.organicmaps.sdk.location.LocationState;
import app.organicmaps.util.ThemeUtils;
import app.organicmaps.util.UiUtils;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class MyPositionButton
{
  private static final int FOLLOW_SHIFT = 1;

  @NonNull
  private final FloatingActionButton mButton;
  private static final SparseArray<Drawable> mIcons = new SparseArray<>(); // Location mode -> Button icon

  private final int mFollowPaddingShift;

  public MyPositionButton(@NonNull View button, @NonNull View.OnClickListener listener)
  {
    mButton = (FloatingActionButton) button;
    mButton.setOnClickListener(listener);
    mIcons.clear();
    mFollowPaddingShift = (int) (FOLLOW_SHIFT * button.getResources().getDisplayMetrics().density);
    final int locationMode = Map.isEngineCreated() ? LocationState.getMode() : LocationState.NOT_FOLLOW_NO_POSITION;
    update(locationMode);
  }

  public void update(int mode)
  {
    Drawable image = mIcons.get(mode);
    @AttrRes
    int colorAttr = R.attr.iconTint;
    @DimenRes
    int sizeDimen = R.dimen.map_button_icon_size;
    if (mode == LocationState.FOLLOW || mode == LocationState.FOLLOW_AND_ROTATE
        || mode == LocationState.PENDING_POSITION)
    {
      colorAttr = androidx.appcompat.R.attr.colorAccent;
      if (mode == LocationState.PENDING_POSITION)
        sizeDimen = R.dimen.map_button_size;
      else
        sizeDimen = R.dimen.map_button_arrow_icon_size;
    }
    Resources resources = mButton.getResources();
    Context context = mButton.getContext();
    if (image == null)
    {
      @DrawableRes
      int drawableRes = switch (mode)
      {
        case LocationState.PENDING_POSITION -> R.drawable.ic_menu_location_pending;
        case LocationState.NOT_FOLLOW_NO_POSITION -> R.drawable.ic_location_off;
        case LocationState.NOT_FOLLOW -> R.drawable.ic_not_follow;
        case LocationState.FOLLOW -> R.drawable.ic_follow;
        case LocationState.FOLLOW_AND_ROTATE -> R.drawable.ic_follow_and_rotate;
        default -> throw new IllegalArgumentException("Invalid button mode: " + mode);
      };
      image = ResourcesCompat.getDrawable(resources, drawableRes, context.getTheme());
      mIcons.put(mode, image);
    }

    final boolean following = mode == LocationState.FOLLOW || mode == LocationState.FOLLOW_AND_ROTATE;
    mButton.setSelected(BuildConfig.IS_IN_CAR && following);
    mButton.setImageDrawable(image);
    mButton.setMaxImageSize((int) resources.getDimension(sizeDimen));
    if (BuildConfig.IS_IN_CAR && following)
      ImageViewCompat.setImageTintList(
          mButton, ColorStateList.valueOf(ContextCompat.getColor(context, R.color.in_car_selection_foreground)));
    else
      ImageViewCompat.setImageTintList(mButton, ColorStateList.valueOf(ThemeUtils.getColor(context, colorAttr)));
    if (BuildConfig.IS_IN_CAR)
      mButton.setContentDescription(context.getString(inCarContentDescription(mode)));
    updatePadding(mode);

    if (mode == LocationState.PENDING_POSITION)
    {
      final RotateAnimation rotate =
          new RotateAnimation(0, 360, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);

      rotate.setDuration(1000);
      rotate.setRepeatCount(Animation.INFINITE);
      rotate.setInterpolator(new LinearInterpolator());

      mButton.startAnimation(rotate);
    }
    else
      mButton.clearAnimation();
  }

  @StringRes
  private static int inCarContentDescription(int mode)
  {
    return switch (mode)
    {
      case LocationState.PENDING_POSITION -> R.string.in_car_location_mode_finding;
      case LocationState.NOT_FOLLOW_NO_POSITION -> R.string.in_car_location_mode_unavailable;
      case LocationState.NOT_FOLLOW -> R.string.in_car_location_mode_free;
      case LocationState.FOLLOW -> R.string.in_car_location_mode_centred;
      case LocationState.FOLLOW_AND_ROTATE -> R.string.in_car_location_mode_heading;
      default -> throw new IllegalArgumentException("Invalid button mode: " + mode);
    };
  }

  private void updatePadding(int mode)
  {
    if (mode == LocationState.FOLLOW)
      mButton.setPadding(0, mFollowPaddingShift, mFollowPaddingShift, 0);
    else
      mButton.setPadding(0, 0, 0, 0);
  }

  public void showButton(boolean show)
  {
    UiUtils.showIf(show, mButton);
  }
}
