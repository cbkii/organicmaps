package app.organicmaps.incar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Pure recent-destination glyph extraction for InCar Quick Destinations. */
public final class InCarQuickDestinationGlyphPolicy
{
  private static final String FALLBACK = "?";

  private InCarQuickDestinationGlyphPolicy() {}

  @NonNull
  public static String glyph(@Nullable String label)
  {
    if (label == null || label.isEmpty())
      return FALLBACK;

    final StringBuilder result = new StringBuilder();
    int index = 0;
    while (index < label.length() && result.codePointCount(0, result.length()) < 2)
    {
      final int codePoint = label.codePointAt(index);
      index += Character.charCount(codePoint);
      if (Character.isLetterOrDigit(codePoint))
        result.appendCodePoint(codePoint);
    }

    return result.length() == 0 ? FALLBACK : result.toString();
  }
}
