package app.organicmaps.incar;

import androidx.annotation.NonNull;
import java.util.Locale;

/** Whole value/unit expressions for the bounded automotive footer. */
public final class InCarRouteSummaryPolicy
{
  private InCarRouteSummaryPolicy() {}

  @NonNull
  public static String distance(@NonNull String value, @NonNull String units)
  {
    return value + "\u00a0" + units;
  }

  @NonNull
  public static String duration(int seconds, @NonNull String minute, @NonNull String hour, @NonNull String day,
                                @NonNull Locale locale)
  {
    final long totalMinutes = Math.max(0L, seconds) / 60;
    final long hours = totalMinutes / 60;
    if (hours >= 48)
      return String.format(locale, "%d\u00a0%s %d\u00a0%s", hours / 24, day, hours % 24, hour);
    if (hours > 0)
      return String.format(locale, "%d\u00a0%s %d\u00a0%s", hours, hour, totalMinutes % 60, minute);
    return String.format(locale, "%d\u00a0%s", totalMinutes, minute);
  }
}
