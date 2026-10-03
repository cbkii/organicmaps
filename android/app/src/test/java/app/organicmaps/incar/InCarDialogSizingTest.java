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
  public void boundedSizeClampsNonPositiveAvailableToOne()
  {
    assertEquals(1, InCarDialogSizing.boundedSizePx(0, 288, 560, 0.5f));
    assertEquals(1, InCarDialogSizing.boundedSizePx(-10, 288, 560, 0.5f));
  }

  @Test
  public void boundedSizeHandlesFractionBounds()
  {
    assertEquals(288, InCarDialogSizing.boundedSizePx(1280, 288, 560, 0.0f));
    assertEquals(560, InCarDialogSizing.boundedSizePx(1280, 288, 560, 1.0f));
  }

  @Test
  public void pickerHeightIsClampedWithoutExceedingUsableHeight()
  {
    assertEquals(560, InCarDialogSizing.boundedSizePx(720, 280, 560, 0.82f));
    assertEquals(410, InCarDialogSizing.boundedSizePx(500, 280, 560, 0.82f));
    assertEquals(250, InCarDialogSizing.boundedSizePx(250, 280, 560, 0.82f));
  }

  @Test
  public void measuredWindowDimensionWinsOverDisplayFallback()
  {
    assertEquals(640, InCarDialogSizing.measuredOrFallback(640, 1280));
  }

  @Test
  public void displayFallbackIsUsedBeforeWindowIsMeasured()
  {
    assertEquals(1280, InCarDialogSizing.measuredOrFallback(0, 1280));
    assertEquals(1280, InCarDialogSizing.measuredOrFallback(-1, 1280));
  }
}
