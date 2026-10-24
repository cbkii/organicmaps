package app.organicmaps.maplayer;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import app.organicmaps.R;
import app.organicmaps.adapter.OnItemClickListener;

class LayerHolder extends RecyclerView.ViewHolder
{
  @NonNull
  final ImageView mButton;
  @NonNull
  final TextView mTitle;
  @NonNull
  final View mNewMarker;
  @Nullable
  LayerBottomSheetItem mItem;
  @Nullable
  OnItemClickListener<LayerBottomSheetItem> mListener;

  LayerHolder(@NonNull View root)
  {
    super(root);
    mTitle = root.findViewById(R.id.name);
    mNewMarker = root.findViewById(R.id.marker);
    mButton = root.findViewById(R.id.btn);
    if (app.organicmaps.BuildConfig.IS_IN_CAR)
    {
      app.organicmaps.incar.InCarSettingsRows.apply(root);
      root.setMinimumWidth(root.getResources().getDimensionPixelSize(R.dimen.in_car_touch_target_preferred));
      root.setOnClickListener(this::onItemClicked);
      root.setFocusable(true);
      mButton.setClickable(false);
      mButton.setFocusable(false);
      mButton.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    else
      mButton.setOnClickListener(this::onItemClicked);
  }

  public void onItemClicked(@NonNull View v)
  {
    if (mListener != null && mItem != null)
      mListener.onItemClick(v, mItem);
  }
}
