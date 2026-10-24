package app.organicmaps.incar;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import app.organicmaps.BuildConfig;
import app.organicmaps.R;
import app.organicmaps.util.ThemeUtils;

/** Measured automotive containers; no invisible/overlapping touch delegates or width changes. */
public final class InCarSettingsRows
{
  private InCarSettingsRows() {}

  public static void apply(@NonNull View row)
  {
    if (!BuildConfig.IS_IN_CAR)
      return;
    row.setMinimumHeight(Math.max(row.getMinimumHeight(),
                                  row.getResources().getDimensionPixelSize(R.dimen.in_car_runtime_row_min_height)));
    final ViewGroup.LayoutParams params = row.getLayoutParams();
    if (params != null && params.height >= 0)
    {
      params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
      row.setLayoutParams(params);
    }
    if (row instanceof TextView text)
    {
      allowMultiline(text);
      text.setGravity((text.getGravity() & ~Gravity.VERTICAL_GRAVITY_MASK) | Gravity.CENTER_VERTICAL);
    }
  }

  public static void allowMultiline(@NonNull TextView text)
  {
    text.setSingleLine(false);
    text.setMaxLines(Integer.MAX_VALUE);
    text.setEllipsize(null);
  }

  /** One semantic switch and one action for the whole labelled row, including the switch artwork. */
  public static void bindToggle(@NonNull SwitchCompat button, @NonNull CharSequence label)
  {
    if (!BuildConfig.IS_IN_CAR || !(button.getParent() instanceof View row))
      return;
    apply(row);
    button.setClickable(false);
    button.setFocusable(false);
    button.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    row.setFocusable(true);
    row.setContentDescription(label);
    row.setBackgroundResource(ThemeUtils.getResource(row.getContext(), R.attr.clickableBackground));
    row.setOnClickListener(view -> {
      if (button.isEnabled())
      {
        button.toggle();
        row.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
      }
    });
    ViewCompat.setAccessibilityDelegate(row, new AccessibilityDelegateCompat() {
      @Override
      public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfoCompat info)
      {
        super.onInitializeAccessibilityNodeInfo(host, info);
        info.setClassName("android.widget.Switch");
        info.setCheckable(true);
        info.setChecked(button.isChecked());
        info.setEnabled(button.isEnabled());
      }
    });
  }
}
