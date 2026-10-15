package app.organicmaps.incar;

import androidx.annotation.NonNull;
import java.util.Locale;

/** Whole value/unit expressions for the bounded automotive footer. */
public final class InCarRouteSummaryPolicy
{
  private InCarRouteSummaryPolicy() {}

  /** Width in density-independent units: content may expand the ordinary footprint, never exceed bounds. */
  public static float footerWidth(float available, float distanceWidth, float timeWidth)
  {
    return Math.max(1, Math.min(available, Math.max(460, Math.min(540, distanceWidth + timeWidth + 180))));
  }

  /** Last-resort whole-expression compacting retains every value and translated unit. */
  @NonNull
  public static String compactExpression(@NonNull CharSequence expression)
  {
    return expression.toString().replace("\u00a0", "");
  }

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
