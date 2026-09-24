package app.organicmaps.incar;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.ListPopupWindow;
import app.organicmaps.R;
import java.util.List;

/** Automotive-sized anchored action list for driver-facing InCar overflow controls. */
public final class InCarActionMenu
{
  @FunctionalInterface
  public interface OnItemClickListener {
    void onItemClick(int position);
  }

  private InCarActionMenu() {}

  public static void show(@NonNull View anchor, @NonNull List<String> labels, @NonNull OnItemClickListener listener)
  {
    final Context context = anchor.getContext();
    final int minHeight = context.getResources().getDimensionPixelSize(R.dimen.in_car_runtime_row_min_height);
    final ArrayAdapter<String> adapter =
        new ArrayAdapter<>(context, R.layout.in_car_action_menu_item, android.R.id.text1, labels) {
          @NonNull
          @Override
          public View getView(int position, View convertView, @NonNull ViewGroup parent)
          {
            final View view = super.getView(position, convertView, parent);
            view.setMinimumHeight(minHeight);
            return view;
          }
        };

    final ListPopupWindow popup = new ListPopupWindow(context);
    popup.setAnchorView(anchor);
    popup.setAdapter(adapter);
    popup.setModal(true);
    popup.setWidth(context.getResources().getDimensionPixelSize(R.dimen.in_car_action_menu_width));
    popup.setOnItemClickListener((parent, view, position, id) -> {
      popup.dismiss();
      listener.onItemClick(position);
    });
    popup.show();
  }
}
