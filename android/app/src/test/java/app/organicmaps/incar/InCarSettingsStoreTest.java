package app.organicmaps.incar;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.SharedPreferences;
import org.junit.Test;

public class InCarSettingsStoreTest
{
  @Test
  public void locationDisabledWarningDefaultsOffAndReadsSavedChoice()
  {
    final SharedPreferences prefs = mock(SharedPreferences.class);
    assertFalse(InCarSettingsStore.showLocationDisabledWarning(prefs));
    when(prefs.getBoolean("InCarLocationDisabledWarning", false)).thenReturn(true);
    assertTrue(InCarSettingsStore.showLocationDisabledWarning(prefs));
  }
}
