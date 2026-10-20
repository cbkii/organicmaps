package app.organicmaps.incar;

import android.graphics.Color;
import androidx.core.graphics.ColorUtils;

/** Contrast calculation only: the drawn ribbon keeps its original alpha. */
final class InCarContrast
{
  private InCarContrast() {}

  static int foreground(int background, int surface)
  {
    // The live map cannot be sampled here. Use the card surface's solid RGB as
    // the deterministic backing, including when the theme itself is translucent.
    final int opaque = ColorUtils.compositeColors(background, ColorUtils.setAlphaComponent(surface, 255));
    return ColorUtils.calculateContrast(Color.BLACK, opaque) >= ColorUtils.calculateContrast(Color.WHITE, opaque)
        ? Color.BLACK
        : Color.WHITE;
  }
}
