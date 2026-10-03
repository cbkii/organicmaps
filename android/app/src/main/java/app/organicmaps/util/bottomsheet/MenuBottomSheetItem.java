package app.organicmaps.util.bottomsheet;

public class MenuBottomSheetItem
{
  public final int titleRes;
  public final int iconRes;
  public final int badgeCount;
  public final boolean checkable;
  public final boolean checked;
  public final OnClickListener onClickListener;

  public MenuBottomSheetItem(int titleRes, int iconRes, OnClickListener onClickListener)
  {
    this(titleRes, iconRes, 0, false, false, onClickListener);
  }

  public MenuBottomSheetItem(int titleRes, int iconRes, int badgeCount, OnClickListener onClickListener)
  {
    this(titleRes, iconRes, badgeCount, false, false, onClickListener);
  }

  public static MenuBottomSheetItem checkable(int titleRes, int iconRes, boolean checked,
                                              OnClickListener onClickListener)
  {
    return new MenuBottomSheetItem(titleRes, iconRes, 0, true, checked, onClickListener);
  }

  private MenuBottomSheetItem(int titleRes, int iconRes, int badgeCount, boolean checkable, boolean checked,
                              OnClickListener onClickListener)
  {
    this.titleRes = titleRes;
    this.iconRes = iconRes;
    this.badgeCount = badgeCount;
    this.checkable = checkable;
    this.checked = checked;
    this.onClickListener = onClickListener;
  }

  public interface OnClickListener
  {
    void onClick();
  }
}
