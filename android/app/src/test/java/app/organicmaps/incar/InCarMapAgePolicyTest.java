package app.organicmaps.incar;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;
import java.util.Date;
import org.junit.Test;

public class InCarMapAgePolicyTest
{
  @Test
  public void freshDataIsNotOutdated()
  {
    final Date now = date(2026, Calendar.AUGUST, 11);
    assertFalse(InCarMapAgePolicy.isOutdated(date(2026, Calendar.JUNE, 1), now));
  }

  @Test
  public void exactlySixMonthsOldIsNotOutdated()
  {
    final Date now = date(2026, Calendar.AUGUST, 11);
    assertFalse(InCarMapAgePolicy.isOutdated(date(2026, Calendar.FEBRUARY, 11), now));
  }

  @Test
  public void olderThanSixMonthsIsOutdated()
  {
    final Date now = date(2026, Calendar.AUGUST, 11);
    assertTrue(InCarMapAgePolicy.isOutdated(date(2026, Calendar.FEBRUARY, 10), now));
  }

  @Test
  public void unknownVersionDoesNotProduceFalseWarning()
  {
    assertFalse(InCarMapAgePolicy.isOutdated(null, date(2026, Calendar.AUGUST, 11)));
  }

  private static Date date(int year, int month, int day)
  {
    final Calendar calendar = Calendar.getInstance();
    calendar.clear();
    calendar.set(year, month, day, 12, 0, 0);
    return calendar.getTime();
  }
}
