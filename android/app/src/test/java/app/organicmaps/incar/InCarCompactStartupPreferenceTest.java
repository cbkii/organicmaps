package app.organicmaps.incar;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import android.content.SharedPreferences;
import org.junit.Test;

public class InCarCompactStartupPreferenceTest
{
  @Test public void absentPreferenceAndNonInCarFlavoursStayOff()
  {
    final SharedPreferences prefs = mock(SharedPreferences.class);
    when(prefs.getBoolean("InCarCompactStartup", false)).thenReturn(false);
    assertFalse(InCarSettingsStore.compactStartupEnabled(prefs, true));
    when(prefs.getBoolean("InCarCompactStartup", false)).thenReturn(true);
    assertTrue(InCarSettingsStore.compactStartupEnabled(prefs, true));
    assertFalse(InCarSettingsStore.compactStartupEnabled(prefs, false));
    verify(prefs, never()).edit();
  }
}
