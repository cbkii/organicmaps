package app.organicmaps.incar;

import static org.junit.Assert.assertEquals;

import java.util.Locale;
import org.junit.Test;

public class InCarRouteSummaryPolicyTest
{
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
