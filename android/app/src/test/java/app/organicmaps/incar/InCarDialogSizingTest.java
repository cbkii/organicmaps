package app.organicmaps.incar;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class InCarDialogSizingTest
{
  @Test
  public void boundedSizeUsesFractionWithinAvailableBounds()
  {
    assertEquals(560, InCarDialogSizing.boundedSizePx(1280, 288, 560, 0.5f));
    assertEquals(320, InCarDialogSizing.boundedSizePx(640, 288, 560, 0.5f));
    assertEquals(288, InCarDialogSizing.boundedSizePx(400, 288, 560, 0.5f));
    assertEquals(260, InCarDialogSizing.boundedSizePx(260, 288, 560, 0.5f));
  }

  @Test
  public void pickerHeightIsClampedWithoutExceedingUsableHeight()
  {
    assertEquals(560, InCarDialogSizing.boundedSizePx(720, 280, 560, 0.82f));
    assertEquals(410, InCarDialogSizing.boundedSizePx(500, 280, 560, 0.82f));
    assertEquals(250, InCarDialogSizing.boundedSizePx(250, 280, 560, 0.82f));
  }
}
