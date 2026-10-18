package app.organicmaps.location;

import static org.junit.Assert.*;
import org.junit.Test;

public class InCarLocationPermissionPolicyTest
{
  @Test public void combinedBackgroundRequestIsOnlyForInCarAndroidTen()
  {
    assertTrue(InCarLocationPermissionPolicy.includeBackgroundInForegroundRequest(true, 29));
    for (int api : new int[] {23, 28, 30, 31, 36})
      assertFalse(InCarLocationPermissionPolicy.includeBackgroundInForegroundRequest(true, api));
    assertFalse(InCarLocationPermissionPolicy.includeBackgroundInForegroundRequest(false, 29));
  }

  @Test public void denialOfBackgroundDoesNotInvalidateForegroundAccess()
  {
    assertEquals(InCarLocationPermissionPolicy.Access.WHILE_USING,
                 InCarLocationPermissionPolicy.access(29, true, false));
    assertEquals(InCarLocationPermissionPolicy.Access.ALWAYS,
                 InCarLocationPermissionPolicy.access(29, true, true));
    assertEquals(InCarLocationPermissionPolicy.Access.DENIED,
                 InCarLocationPermissionPolicy.access(29, false, true));
    assertEquals(InCarLocationPermissionPolicy.Access.ALWAYS,
                 InCarLocationPermissionPolicy.access(28, true, false));
    assertEquals(InCarLocationPermissionPolicy.Access.WHILE_USING,
                 InCarLocationPermissionPolicy.access(36, true, false));
  }
}
