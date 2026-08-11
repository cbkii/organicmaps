package app.organicmaps.incar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Calendar;
import java.util.Date;

/** Pure policy for the optional non-blocking InCar map-data age notice. */
public final class InCarMapAgePolicy
{
  private static final int OUTDATED_MONTHS = 6;

  private InCarMapAgePolicy() {}

  public static boolean isOutdated(@Nullable Date dataVersion, @NonNull Date now)
  {
    if (dataVersion == null)
      return false;

    final Calendar cutoff = Calendar.getInstance();
    cutoff.setTime(now);
    cutoff.set(Calendar.HOUR_OF_DAY, 0);
    cutoff.set(Calendar.MINUTE, 0);
    cutoff.set(Calendar.SECOND, 0);
    cutoff.set(Calendar.MILLISECOND, 0);
    cutoff.add(Calendar.MONTH, -OUTDATED_MONTHS);
    return dataVersion.before(cutoff.getTime());
  }
}
