package app.organicmaps.util.bottomsheet;

public class MenuBottomSheetItem
{
  public final int titleRes;
  public final int iconRes;
  public final int badgeCount;
  public final boolean checkable;
  public final boolean checked;
  private boolean actionDispatched;
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

  // A checkable command can dispatch once per menu snapshot, including after RecyclerView rebinds.
  // This is an event guard, not recorder state: checked always comes from the native recorder.
  public boolean claimAction()
  {
    if (checkable && actionDispatched)
      return false;
    actionDispatched = true;
    return true;
  }

  public interface OnClickListener
  {
    void onClick();
  }
}
