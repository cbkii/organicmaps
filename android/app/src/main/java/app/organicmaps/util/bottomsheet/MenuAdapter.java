package app.organicmaps.util.bottomsheet;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;
import app.organicmaps.R;
import app.organicmaps.sdk.location.TrackRecorder;
import app.organicmaps.sdk.util.Config;
import java.util.ArrayList;

public class MenuAdapter extends RecyclerView.Adapter<MenuAdapter.ViewHolder>
{
  private final ArrayList<MenuBottomSheetItem> dataSet;
  @Nullable
  private final MenuBottomSheetItem.OnClickListener onClickListener;

  public MenuAdapter(ArrayList<MenuBottomSheetItem> dataSet,
                     @Nullable MenuBottomSheetItem.OnClickListener onClickListener)
  {
    this.dataSet = dataSet;
    this.onClickListener = onClickListener;
  }

  private void onMenuItemClick(MenuBottomSheetItem item)
  {
    if (onClickListener != null)
      onClickListener.onClick();
    item.onClickListener.onClick();
  }

  @NonNull
  @Override
  public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType)
  {
    View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.bottom_sheet_menu_item, viewGroup, false);
    return new ViewHolder(view);
  }

  @Override
  public void onBindViewHolder(ViewHolder viewHolder, final int position)
  {
    final MenuBottomSheetItem item = dataSet.get(position);
    final ImageView iv = viewHolder.getIconImageView();
    if (item.iconRes == R.drawable.ic_donate && Config.isNY())
    {
      iv.setImageResource(R.drawable.ic_christmas_tree);
      iv.setImageTintMode(null);
    }
    else
      iv.setImageResource(item.iconRes);

    viewHolder.getTitleTextView().setText(item.titleRes);
    TextView badge = viewHolder.getBadgeTextView();
    if (item.badgeCount > 0)
    {
      badge.setText(String.valueOf(item.badgeCount));
      badge.setVisibility(View.VISIBLE);
    }
    else
      badge.setVisibility(View.GONE);

    final SwitchCompat toggle = viewHolder.getToggle();
    toggle.setOnCheckedChangeListener(null);
    if (item.checkable)
    {
      toggle.setVisibility(View.VISIBLE);
      toggle.setChecked(item.checked);
      toggle.setOnCheckedChangeListener((button, checked) -> {
        if (checked != item.checked)
          onMenuItemClick(item);
      });
      viewHolder.getContainer().setOnClickListener(v -> toggle.setChecked(!toggle.isChecked()));
    }
    else
    {
      toggle.setVisibility(View.GONE);
      viewHolder.getContainer().setOnClickListener(v -> onMenuItemClick(item));
    }

    // Preserve the established mobile menu presentation. InCar checkable rows keep a stable title/icon
    // and expose actual recorder state through their trailing ON/OFF switch instead.
    if (!item.checkable && item.iconRes == R.drawable.ic_track_recording_off
        && TrackRecorder.nativeIsTrackRecordingEnabled())
    {
      iv.setImageResource(R.drawable.ic_track_recording_on);
      iv.setImageTintMode(null);
      viewHolder.getTitleTextView().setText(R.string.stop_track_recording);
      badge.setBackgroundResource(R.drawable.track_recorder_badge);
      badge.setVisibility(View.VISIBLE);
    }
  }

  @Override
  public int getItemCount()
  {
    return dataSet.size();
  }

  public static class ViewHolder extends RecyclerView.ViewHolder
  {
    private final LinearLayout container;
    private final ImageView iconImageView;
    private final TextView titleTextView;
    private final TextView badgeTextView;
    private final SwitchCompat toggle;

    public ViewHolder(View view)
    {
      super(view);
      container = view.findViewById(R.id.bottom_sheet_menu_item);
      iconImageView = view.findViewById(R.id.bottom_sheet_menu_item_icon);
      titleTextView = view.findViewById(R.id.bottom_sheet_menu_item_text);
      badgeTextView = view.findViewById(R.id.bottom_sheet_menu_item_badge);
      toggle = view.findViewById(R.id.bottom_sheet_menu_item_switch);
    }

    public ImageView getIconImageView()
    {
      return iconImageView;
    }

    public TextView getTitleTextView()
    {
      return titleTextView;
    }

    public TextView getBadgeTextView()
    {
      return badgeTextView;
    }

    public SwitchCompat getToggle()
    {
      return toggle;
    }

    public LinearLayout getContainer()
    {
      return container;
    }
  }
}
