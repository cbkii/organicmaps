package app.organicmaps.incar;

import static org.junit.Assert.*;

import org.junit.Test;

public class InCarControlPositionPolicyTest
{
  @Test
  public void physicalSideDefaultsSafelyToRightHandDrive()
  {
    assertTrue(InCarControlPositionPolicy.isLeft(null));
    assertTrue(InCarControlPositionPolicy.isLeft("RHD"));
    assertTrue(InCarControlPositionPolicy.isLeft("invalid"));
    assertFalse(InCarControlPositionPolicy.isLeft("LHD"));
  }
}
