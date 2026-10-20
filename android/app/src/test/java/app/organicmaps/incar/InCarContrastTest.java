package app.organicmaps.incar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mockStatic;

import android.graphics.Color;
import androidx.core.graphics.ColorUtils;
import org.junit.Test;
import org.mockito.MockedStatic;

public class InCarContrastTest
{
  @Test
  public void translucentWarningAndThemeColoursNeverReachContrastAsTranslucent()
  {
    // Supply Android's channel primitives at the JVM boundary; execute the real
    // AndroidX composition/contrast and production helper, not a mocked policy.
    try (MockedStatic<Color> color = mockStatic(Color.class))
    {
      color.when(() -> Color.alpha(anyInt())).thenAnswer(call -> (int) call.getArgument(0) >>> 24);
      color.when(() -> Color.red(anyInt())).thenAnswer(call -> ((int) call.getArgument(0) >> 16) & 255);
      color.when(() -> Color.green(anyInt())).thenAnswer(call -> ((int) call.getArgument(0) >> 8) & 255);
      color.when(() -> Color.blue(anyInt())).thenAnswer(call -> (int) call.getArgument(0) & 255);
      color.when(() -> Color.argb(anyInt(), anyInt(), anyInt(), anyInt()))
          .thenAnswer(call
                      -> ((int) call.getArgument(0) << 24) | ((int) call.getArgument(1) << 16)
                             | ((int) call.getArgument(2) << 8) | (int) call.getArgument(3));
      for (int surface : new int[] {0x00ffffff, 0xcd4f4e4e, 0xff101010, 0xffffffff})
      {
        for (int alpha = 0; alpha <= 255; ++alpha)
        {
          for (int rgb : new int[] {0x774c10, 0x7c4f0f, 0xff0000, 0xff8800})
          {
            final int background = (alpha << 24) | rgb;
            final int before = background;
            final int foreground = InCarContrast.foreground(background, surface);
            final int opaque = ColorUtils.compositeColors(background, surface | 0xff000000);
            assertTrue(foreground == Color.BLACK || foreground == Color.WHITE);
            assertTrue(ColorUtils.calculateContrast(foreground, opaque) >= 4.5);
            assertEquals(before, background);
          }
        }
      }
      assertEquals(Color.BLACK, InCarContrast.foreground(0xffffffff, 0xff000000));
      assertEquals(Color.WHITE, InCarContrast.foreground(0xff000000, 0xffffffff));
    }
  }
}
