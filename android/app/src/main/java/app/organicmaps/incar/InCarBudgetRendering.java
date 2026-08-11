package app.organicmaps.incar;

import android.content.Context;
import androidx.annotation.NonNull;
import app.organicmaps.sdk.Framework;

/** Narrow, reversible rendering budget for the fixed-display InCar flavour. */
public final class InCarBudgetRendering
{
  private InCarBudgetRendering() {}

  public static void applyCurrent(@NonNull Context context)
  {
    apply(context, InCarSettingsStore.budgetRenderingEnabled(context));
  }

  public static void apply(@NonNull Context context, boolean enabled)
  {
    final Framework.Params3dMode current = new Framework.Params3dMode();
    Framework.nativeGet3dMode(current);

    if (enabled)
    {
      if (!InCarSettingsStore.hasSavedBudget3dBuildings(context))
        InCarSettingsStore.saveBudget3dBuildings(context, current.buildings);
      if (current.buildings)
        Framework.nativeSet3dMode(current.enabled, false /* allow3dBuildings */);
      return;
    }

    if (!InCarSettingsStore.hasSavedBudget3dBuildings(context))
      return;

    final boolean restoreBuildings = InCarSettingsStore.getSavedBudget3dBuildings(context);
    if (current.buildings != restoreBuildings)
      Framework.nativeSet3dMode(current.enabled, restoreBuildings);
    InCarSettingsStore.clearSavedBudget3dBuildings(context);
  }
}
