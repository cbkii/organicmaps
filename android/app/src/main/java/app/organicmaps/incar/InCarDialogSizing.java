package app.organicmaps.incar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.Resources;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import app.organicmaps.R;

/** Inset-aware sizing policy for the compact dialogs introduced by the InCar UI. */
public final class InCarDialogSizing
{
  private InCarDialogSizing() {}

  public static void applyCompactWidth(@NonNull Activity activity, @NonNull AlertDialog dialog)
  {
    final Resources resources = activity.getResources();
    applyWidth(activity, dialog, resources.getFraction(R.fraction.in_car_compact_dialog_width_fraction, 1, 1),
               resources.getDimensionPixelSize(R.dimen.in_car_compact_dialog_min_width),
               resources.getDimensionPixelSize(R.dimen.in_car_compact_dialog_max_width));
    enforceTouchTargets(activity, dialog);
  }

  /** Place the active-navigation overflow below the measured ribbon and above route controls. */
  public static void applyNavigationOverflowBounds(@NonNull Activity activity, @NonNull AlertDialog dialog,
                                                   int headerHeightPx, int bottomControlsHeightPx)
  {
    applyCompactWidth(activity, dialog);
    final Window window = dialog.getWindow();
    if (window == null)
      return;

    final int gap = activity.getResources().getDimensionPixelSize(R.dimen.margin_half);
    final int bottom = Math.max(0, bottomControlsHeightPx) + gap;
    // The two gaps reserve space below the ribbon and above the footer.
    final int maximumHeight = Math.max(1, usableWindowSize(activity)[1] - Math.max(0, headerHeightPx) - bottom - gap);
    final View decor = window.getDecorView();
    if (decor.getHeight() > 0)
    {
      if (decor.getHeight() > maximumHeight)
        window.setLayout(window.getAttributes().width, maximumHeight);
    }
    else
    {
      decor.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
        @Override
        public void onLayoutChange(View view, int left, int top, int right, int bottom, int oldLeft, int oldTop,
                                   int oldRight, int oldBottom)
        {
          final int height = bottom - top;
          if (height <= 0)
            return;
          view.removeOnLayoutChangeListener(this);
          if (height > maximumHeight)
            window.setLayout(window.getAttributes().width, maximumHeight);
        }
      });
    }

    final WindowManager.LayoutParams attributes = window.getAttributes();
    attributes.gravity = Gravity.RIGHT | Gravity.BOTTOM;
    attributes.y = bottom;
    window.setAttributes(attributes);
  }

  public static void applyPickerSize(@NonNull Activity activity, @NonNull AlertDialog dialog)
  {
    final Window window = dialog.getWindow();
    if (window == null)
      return;

    final Resources resources = activity.getResources();
    final int[] usable = usableWindowSize(activity);
    final int width = boundedSizePx(usable[0], resources.getDimensionPixelSize(R.dimen.in_car_picker_dialog_min_width),
                                    resources.getDimensionPixelSize(R.dimen.in_car_picker_dialog_max_width),
                                    resources.getFraction(R.fraction.in_car_picker_dialog_width_fraction, 1, 1));
    final int height =
        boundedSizePx(usable[1], resources.getDimensionPixelSize(R.dimen.in_car_picker_dialog_min_height),
                      resources.getDimensionPixelSize(R.dimen.in_car_picker_dialog_max_height),
                      resources.getFraction(R.fraction.in_car_picker_dialog_height_fraction, 1, 1));
    window.setLayout(width, height);
    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    enforceTouchTargets(activity, dialog);
  }

  private static void applyWidth(@NonNull Activity activity, @NonNull AlertDialog dialog, float fraction,
                                 int minWidthPx, int maxWidthPx)
  {
    final Window window = dialog.getWindow();
    if (window == null)
      return;
    final int width = boundedSizePx(usableWindowSize(activity)[0], minWidthPx, maxWidthPx, fraction);
    window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
  }

  /**
   * Dialog chrome otherwise falls back to phone defaults (commonly 48dp). Apply the InCar preferred
   * interaction height after the dialog is shown, while leaving layout width/height free to wrap or scroll.
   */
  private static void enforceTouchTargets(@NonNull Activity activity, @NonNull AlertDialog dialog)
  {
    final Window window = dialog.getWindow();
    if (window == null)
      return;
    final int target = activity.getResources().getDimensionPixelSize(R.dimen.in_car_touch_target_preferred);
    enforceTouchTargets(window.getDecorView(), target);
  }

  private static void enforceTouchTargets(@NonNull View view, int target)
  {
    if (view.isClickable() || view.isLongClickable() || view instanceof EditText)
    {
      view.setMinimumWidth(Math.max(view.getMinimumWidth(), target));
      view.setMinimumHeight(Math.max(view.getMinimumHeight(), target));
    }

    if (!(view instanceof ViewGroup group))
      return;
    for (int index = 0; index < group.getChildCount(); ++index)
      enforceTouchTargets(group.getChildAt(index), target);
  }

  @VisibleForTesting
  static int boundedSizePx(int availablePx, int minPx, int maxPx, float fraction)
  {
    final int available = Math.max(1, availablePx);
    final int proportional = Math.round(available * fraction);
    return Math.min(available, Math.max(minPx, Math.min(maxPx, proportional)));
  }

  @VisibleForTesting
  static int measuredOrFallback(int measuredPx, int fallbackPx)
  {
    return measuredPx > 0 ? measuredPx : fallbackPx;
  }

  @NonNull
  private static int[] usableWindowSize(@NonNull Activity activity)
  {
    final View decor = activity.getWindow().getDecorView();
    final Resources resources = activity.getResources();
    int width = measuredOrFallback(decor.getWidth(), resources.getDisplayMetrics().widthPixels);
    int height = measuredOrFallback(decor.getHeight(), resources.getDisplayMetrics().heightPixels);
    final WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(decor);
    if (insets != null)
    {
      final Insets safe =
          insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
      width -= safe.left + safe.right;
      height -= safe.top + safe.bottom;
    }
    return new int[] {Math.max(1, width), Math.max(1, height)};
  }
}
