package app.organicmaps.incar;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
  @Test public void persistedChoiceRoundTripsAndOtherFlavoursCannotOverwriteIt()
  {
    final SharedPreferences prefs = mock(SharedPreferences.class);
    final SharedPreferences.Editor editor = mock(SharedPreferences.Editor.class);
    final java.util.concurrent.atomic.AtomicBoolean stored = new java.util.concurrent.atomic.AtomicBoolean();
    when(prefs.edit()).thenReturn(editor);
    when(editor.putBoolean(org.mockito.ArgumentMatchers.eq("InCarCompactStartup"), anyBoolean())).thenAnswer(call -> {
      stored.set(call.getArgument(1));
      return editor;
    });
    when(prefs.getBoolean("InCarCompactStartup", false)).thenAnswer(call -> stored.get());
    assertFalse(InCarSettingsStore.compactStartupEnabled(prefs, true));
    InCarSettingsStore.setCompactStartupEnabled(prefs, true, true);
    assertTrue(InCarSettingsStore.compactStartupEnabled(prefs, true));
    InCarSettingsStore.setCompactStartupEnabled(prefs, false, false);
    assertTrue(InCarSettingsStore.compactStartupEnabled(prefs, true));
    InCarSettingsStore.setCompactStartupEnabled(prefs, true, false);
    assertFalse(InCarSettingsStore.compactStartupEnabled(prefs, true));
    verify(editor, org.mockito.Mockito.times(2)).apply();
    verify(editor, never()).clear();
    verify(editor, never()).remove(org.mockito.ArgumentMatchers.anyString());
  }

}
