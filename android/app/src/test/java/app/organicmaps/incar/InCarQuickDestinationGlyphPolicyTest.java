package app.organicmaps.incar;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class InCarQuickDestinationGlyphPolicyTest
{
  @Test
  public void extractsFirstTwoMeaningfulCharacters()
  {
    assertEquals("St", InCarQuickDestinationGlyphPolicy.glyph("  St Kilda"));
    assertEquals("OC", InCarQuickDestinationGlyphPolicy.glyph("...O'Connor"));
  }

  @Test
  public void supportsOneCharacterAndUnicodeNames()
  {
    assertEquals("A", InCarQuickDestinationGlyphPolicy.glyph("A"));
    assertEquals("東京", InCarQuickDestinationGlyphPolicy.glyph("東京駅"));
    assertEquals("Éc", InCarQuickDestinationGlyphPolicy.glyph("École"));
  }

  @Test
  public void usesFallbackWhenNoMeaningfulCharacterExists()
  {
    assertEquals("?", InCarQuickDestinationGlyphPolicy.glyph(null));
    assertEquals("?", InCarQuickDestinationGlyphPolicy.glyph("   ---  "));
  }
}
