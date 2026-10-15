package app.organicmaps.incar;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import app.organicmaps.R;
import java.util.ArrayList;
import java.util.List;

/** Shared large-row adapter for compact InCar choice dialogs and picker results. */
public final class InCarChoiceAdapter extends ArrayAdapter<String>
{
  private static final int HORIZONTAL_PADDING_DP = 24;
  private static final int VERTICAL_PADDING_DP = 8;
  private static final int ICON_SIZE_DP = 28;
  private static final int ICON_TEXT_GAP_DP = 16;
  private static final float TEXT_SIZE_SP = 18.0f;

  @Nullable
  private final List<Integer> mIconResIds;

  public InCarChoiceAdapter(@NonNull Context context, @NonNull List<String> items)
  {
    this(context, android.R.layout.simple_list_item_1, items, null);
  }

  @NonNull
  public static InCarChoiceAdapter singleChoice(@NonNull Context context, @NonNull List<String> items)
  {
    return new InCarChoiceAdapter(context, android.R.layout.simple_list_item_single_choice, items, null);
  }

  @NonNull
  public static InCarChoiceAdapter withIcons(@NonNull Context context, @NonNull List<String> items,
                                             @NonNull List<Integer> iconResIds)
  {
    if (items.size() != iconResIds.size())
      throw new IllegalArgumentException("Each InCar choice row must have exactly one icon");
    return new InCarChoiceAdapter(context, android.R.layout.simple_list_item_1, items, iconResIds);
  }

  private InCarChoiceAdapter(@NonNull Context context, @LayoutRes int layoutRes, @NonNull List<String> items,
                             @Nullable List<Integer> iconResIds)
  {
    super(context, layoutRes, new ArrayList<>(items));
    mIconResIds = iconResIds == null ? null : new ArrayList<>(iconResIds);
  }

  @NonNull
  @Override
  public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent)
  {
    final TextView row = (TextView) super.getView(position, convertView, parent);
    row.setMinHeight(getContext().getResources().getDimensionPixelSize(R.dimen.in_car_runtime_row_min_height));
    row.setMaxHeight(getContext().getResources().getDimensionPixelSize(R.dimen.in_car_runtime_row_max_height));
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(dp(getContext(), HORIZONTAL_PADDING_DP), dp(getContext(), VERTICAL_PADDING_DP),
                   dp(getContext(), HORIZONTAL_PADDING_DP), dp(getContext(), VERTICAL_PADDING_DP));
    row.setSingleLine(false);
    row.setMaxLines(2);
    row.setEllipsize(TextUtils.TruncateAt.END);
    row.setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE_SP);
    applyIcon(row, position);
    return row;
  }

  private void applyIcon(@NonNull TextView row, int position)
  {
    if (mIconResIds == null)
    {
      row.setCompoundDrawablesRelative(null, null, null, null);
      row.setCompoundDrawablePadding(0);
      return;
    }

    Drawable icon = ContextCompat.getDrawable(getContext(), mIconResIds.get(position));
    if (icon == null)
      return;
    icon = DrawableCompat.wrap(icon.mutate());
    DrawableCompat.setTint(icon, row.getCurrentTextColor());
    final int iconSize = dp(getContext(), ICON_SIZE_DP);
    icon.setBounds(0, 0, iconSize, iconSize);
    row.setCompoundDrawablesRelative(icon, null, null, null);
    row.setCompoundDrawablePadding(dp(getContext(), ICON_TEXT_GAP_DP));
  }

  private static int dp(@NonNull Context context, int value)
  {
    return Math.round(value * context.getResources().getDisplayMetrics().density);
  }
}
