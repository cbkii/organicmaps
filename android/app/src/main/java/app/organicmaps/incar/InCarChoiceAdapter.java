package app.organicmaps.incar;

import android.content.Context;
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
import app.organicmaps.R;
import java.util.ArrayList;
import java.util.List;

/** Shared large-row adapter for compact InCar choice dialogs and picker results. */
public final class InCarChoiceAdapter extends ArrayAdapter<String>
{
  private static final int HORIZONTAL_PADDING_DP = 24;
  private static final int VERTICAL_PADDING_DP = 8;
  private static final float TEXT_SIZE_SP = 18.0f;

  public InCarChoiceAdapter(@NonNull Context context, @NonNull List<String> items)
  {
    this(context, android.R.layout.simple_list_item_1, items);
  }

  @NonNull
  public static InCarChoiceAdapter singleChoice(@NonNull Context context, @NonNull List<String> items)
  {
    return new InCarChoiceAdapter(context, android.R.layout.simple_list_item_single_choice, items);
  }

  private InCarChoiceAdapter(@NonNull Context context, @LayoutRes int layoutRes, @NonNull List<String> items)
  {
    super(context, layoutRes, new ArrayList<>(items));
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
    return row;
  }

  private static int dp(@NonNull Context context, int value)
  {
    return Math.round(value * context.getResources().getDisplayMetrics().density);
  }
}
