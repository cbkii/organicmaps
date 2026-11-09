package app.organicmaps.incar;

import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

/** Keep route summary emphasis while using the START rail theme for all text. */
public final class InCarRouteSummary
{
  private InCarRouteSummary() {}

  @NonNull
  public static CharSequence withForeground(@NonNull CharSequence source, @ColorInt int foreground)
  {
    final SpannableStringBuilder copy = new SpannableStringBuilder(source);
    for (ForegroundColorSpan span : copy.getSpans(0, copy.length(), ForegroundColorSpan.class))
      copy.removeSpan(span);
    if (copy.length() > 0)
      copy.setSpan(new ForegroundColorSpan(foreground), 0, copy.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    return copy;
  }
}
