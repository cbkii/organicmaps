package app.organicmaps.incar;

import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import androidx.annotation.NonNull;

/** The compact START rail is opaque black in both map themes. Never inherit mobile text colours. */
public final class InCarRouteSummary
{
  private InCarRouteSummary() {}

  @NonNull
  public static CharSequence onBlackRail(@NonNull CharSequence source)
  {
    final SpannableStringBuilder copy = new SpannableStringBuilder(source);
    for (ForegroundColorSpan span : copy.getSpans(0, copy.length(), ForegroundColorSpan.class))
      copy.removeSpan(span);
    if (copy.length() > 0)
      copy.setSpan(new ForegroundColorSpan(Color.WHITE), 0, copy.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    return copy;
  }
}
