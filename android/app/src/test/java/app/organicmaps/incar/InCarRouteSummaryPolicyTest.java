package app.organicmaps.incar;

import static org.junit.Assert.assertEquals;

import java.util.Locale;
import org.junit.Test;

public class InCarRouteSummaryPolicyTest
{
  @Test
  public void footerWidthAdaptsToContentAndWindow()
  {
    assertEquals(460, InCarRouteSummaryPolicy.footerWidth(800, 100, 140), 0);
    assertEquals(500, InCarRouteSummaryPolicy.footerWidth(800, 140, 180), 0);
    assertEquals(540, InCarRouteSummaryPolicy.footerWidth(800, 300, 300), 0);
    assertEquals(320, InCarRouteSummaryPolicy.footerWidth(320, 140, 180), 0);
    assertEquals("2Std. 18Min.", InCarRouteSummaryPolicy.compactExpression("2\u00a0Std. 18\u00a0Min."));
    assertEquals("٢د", InCarRouteSummaryPolicy.compactExpression("٢\u00a0د"));
  }

  private static String duration(int seconds)
  {
    return InCarRouteSummaryPolicy.duration(seconds, "min", "h", "d", Locale.UK);
  }

  @Test
  public void wholeExpressionsKeepUnitsAttached()
  {
    assertEquals("9999\u00a0km", InCarRouteSummaryPolicy.distance("9999", "km"));
    assertEquals("9999\u00a0mi", InCarRouteSummaryPolicy.distance("9999", "mi"));
    assertEquals("2\u00a0min", duration(120));
    assertEquals("2\u00a0h 18\u00a0min", duration(8280));
  }

  @Test
  public void minuteAndHourBoundaries()
  {
    assertEquals("0\u00a0min", duration(59));
    assertEquals("1\u00a0min", duration(60));
    assertEquals("59\u00a0min", duration(3599));
    assertEquals("1\u00a0h 0\u00a0min", duration(3600));
  }

  @Test
  public void signedIntDurationHasBoundedDayHourRepresentation()
  {
    assertEquals("47\u00a0h 59\u00a0min", duration(48 * 3600 - 1));
    assertEquals("2\u00a0d 0\u00a0h", duration(48 * 3600));
    assertEquals("24855\u00a0d 3\u00a0h", duration(Integer.MAX_VALUE));
    assertEquals("0\u00a0min", duration(Integer.MIN_VALUE));
  }

  @Test
  public void localeDigitsAndLocalisedUnitsArePreserved()
  {
    assertEquals("٢\u00a0د", InCarRouteSummaryPolicy.duration(120, "د", "س", "ي", Locale.forLanguageTag("ar")));
    assertEquals("2\u00a0Min.", InCarRouteSummaryPolicy.duration(120, "Min.", "Std.", "T.", Locale.GERMANY));
  }
}
