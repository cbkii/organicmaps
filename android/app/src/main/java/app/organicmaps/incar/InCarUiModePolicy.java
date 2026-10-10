package app.organicmaps.incar;

import android.content.res.Configuration;

/** Size/focus callbacks with the same UI mode must leave the geometry coordinator in charge. */
public final class InCarUiModePolicy
{
  private InCarUiModePolicy() {}

  public static boolean shouldRecreate(int previousUiMode, int currentUiMode)
  {
    final boolean nightChanged = (previousUiMode & Configuration.UI_MODE_NIGHT_MASK)
                                != (currentUiMode & Configuration.UI_MODE_NIGHT_MASK);
    final int previousType = previousUiMode & Configuration.UI_MODE_TYPE_MASK;
    final int currentType = currentUiMode & Configuration.UI_MODE_TYPE_MASK;
    final boolean carTransition =
        previousType == Configuration.UI_MODE_TYPE_CAR || currentType == Configuration.UI_MODE_TYPE_CAR;
    return nightChanged || (previousType != currentType && !carTransition);
  }
}
