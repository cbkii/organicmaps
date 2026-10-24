package app.organicmaps.widget.recycler;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class SpanningLinearLayoutManager extends LinearLayoutManager
{
  private final int mMinimumItemSizePx;
  public SpanningLinearLayoutManager(@NonNull Context context, int orientation, boolean reverseLayout)
  {
    this(context, orientation, reverseLayout, 0);
  }

  public SpanningLinearLayoutManager(@NonNull Context context, int orientation, boolean reverseLayout,
                                     int minimumItemSizePx)
  {
    super(context, orientation, reverseLayout);
    mMinimumItemSizePx = Math.max(0, minimumItemSizePx);
  }

  @Override
  public RecyclerView.LayoutParams generateDefaultLayoutParams()
  {
    return spanLayoutSize(super.generateDefaultLayoutParams());
  }

  @Override
  public RecyclerView.LayoutParams generateLayoutParams(@NonNull Context c, @NonNull AttributeSet attrs)
  {
    return spanLayoutSize(super.generateLayoutParams(c, attrs));
  }

  @Override
  public RecyclerView.LayoutParams generateLayoutParams(@NonNull ViewGroup.LayoutParams lp)
  {
    return spanLayoutSize(super.generateLayoutParams(lp));
  }

  @Override
  public boolean checkLayoutParams(@NonNull RecyclerView.LayoutParams lp)
  {
    return super.checkLayoutParams(lp);
  }

  private RecyclerView.LayoutParams spanLayoutSize(@NonNull RecyclerView.LayoutParams layoutParams)
  {
    if (getOrientation() == HORIZONTAL)
    {
      layoutParams.width = Math.max(mMinimumItemSizePx, (int) Math.round(getHorizontalSpace() / (double) Math.max(1, getItemCount())));
    }
    else if (getOrientation() == VERTICAL)
    {
      layoutParams.height = (int) Math.round(getVerticalSpace() / (double) getItemCount());
    }
    return layoutParams;
  }

  @Override
  public boolean canScrollVertically()
  {
    return false;
  }

  @Override
  public boolean canScrollHorizontally()
  {
    return mMinimumItemSizePx > 0 && getItemCount() * mMinimumItemSizePx > getHorizontalSpace();
  }

  private int getHorizontalSpace()
  {
    return getWidth() - getPaddingEnd() - getPaddingStart();
  }

  private int getVerticalSpace()
  {
    return getHeight() - getPaddingBottom() - getPaddingTop();
  }
}
