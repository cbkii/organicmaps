package app.organicmaps.incar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.Resources;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
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

  @VisibleForTesting
  static int boundedSizePx(int availablePx, int minPx, int maxPx, float fraction)
  {
    final int available = Math.max(1, availablePx);
    final int proportional = Math.round(available * fraction);
    return Math.min(available, Math.max(minPx, Math.min(maxPx, proportional)));
  }

  @NonNull
  private static int[] usableWindowSize(@NonNull Activity activity)
  {
    int width = activity.getResources().getDisplayMetrics().widthPixels;
    int height = activity.getResources().getDisplayMetrics().heightPixels;
    final WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(activity.getWindow().getDecorView());
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
