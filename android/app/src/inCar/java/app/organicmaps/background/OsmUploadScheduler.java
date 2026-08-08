package app.organicmaps.background;

import android.content.Context;
import androidx.annotation.NonNull;

/** No-op scheduler for the in-car flavour, which does not package WorkManager. */
public final class OsmUploadScheduler
{
  private OsmUploadScheduler() {}

  public static void schedule(@NonNull Context context) {}
}
