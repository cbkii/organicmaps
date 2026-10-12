package app.organicmaps.incar;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

/** Physical-side placement, deliberately independent of the language layout direction. */
public final class InCarControlPositionPolicy
{
  private InCarControlPositionPolicy() {}

  public static boolean isLeft(String position)
  {
    return !"LHD".equals(position);
  }

  public static int horizontalGravity(boolean left)
  {
    return left ? Gravity.LEFT : Gravity.RIGHT;
  }

  public static void apply(@NonNull View view, boolean left)
  {
    final ViewGroup.LayoutParams raw = view.getLayoutParams();
    final int side = horizontalGravity(left);
    boolean changed;
    if (raw instanceof ConstraintLayout.LayoutParams params)
    {
      changed = params.startToStart != -1 || params.startToEnd != -1 || params.endToStart != -1
                || params.endToEnd != -1 || params.leftToLeft != (left ? 0 : -1)
                || params.rightToRight != (left ? -1 : 0) || params.leftToRight != -1 || params.rightToLeft != -1;
      params.startToStart = params.startToEnd = params.endToStart = params.endToEnd = -1;
      params.leftToLeft = left ? ConstraintLayout.LayoutParams.PARENT_ID : -1;
      params.rightToRight = left ? -1 : ConstraintLayout.LayoutParams.PARENT_ID;
      params.leftToRight = params.rightToLeft = -1;
    }
    else if (raw instanceof RelativeLayout.LayoutParams params)
    {
      final int selected = left ? RelativeLayout.ALIGN_PARENT_LEFT : RelativeLayout.ALIGN_PARENT_RIGHT;
      changed = params.getRule(selected) == 0 || params.getRule(left ? RelativeLayout.ALIGN_PARENT_RIGHT : RelativeLayout.ALIGN_PARENT_LEFT) != 0
                || params.getRule(RelativeLayout.ALIGN_PARENT_START) != 0 || params.getRule(RelativeLayout.ALIGN_PARENT_END) != 0;
      params.removeRule(RelativeLayout.ALIGN_PARENT_START);
      params.removeRule(RelativeLayout.ALIGN_PARENT_END);
      params.removeRule(left ? RelativeLayout.ALIGN_PARENT_RIGHT : RelativeLayout.ALIGN_PARENT_LEFT);
      params.addRule(left ? RelativeLayout.ALIGN_PARENT_LEFT : RelativeLayout.ALIGN_PARENT_RIGHT);
    }
    else if (raw instanceof FrameLayout.LayoutParams params)
    {
      final int gravity = (params.gravity & Gravity.VERTICAL_GRAVITY_MASK) | side;
      changed = params.gravity != gravity;
      params.gravity = gravity;
    }
    else if (raw instanceof CoordinatorLayout.LayoutParams params)
    {
      final int gravity = (params.gravity & Gravity.VERTICAL_GRAVITY_MASK) | side;
      changed = params.gravity != gravity;
      params.gravity = gravity;
    }
    else
      return;
    if (changed)
      view.setLayoutParams(raw);
  }
}
