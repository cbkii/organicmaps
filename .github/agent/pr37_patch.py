from pathlib import Path

src = Path('android/app/src/main/java/app/organicmaps/util/InCarVisuals.java')
text = src.read_text()
old = """  @DimenRes
  private static int selectDimen(@NonNull final Activity activity, final boolean enabled,
                                 @NonNull final ControlSizeTier tier,
                                 @DimenRes final int normalRes,
                                 @DimenRes final int compactRes,
                                 @DimenRes final int extraCompactRes)
  {
    if (tier == ControlSizeTier.EXTRA_COMPACT)
      return extraCompactRes;
    return select(enabled, normalRes, compactRes);
  }
"""
new = """  @DimenRes
  private static int selectDimen(@NonNull final Activity activity, final boolean enabled,
                                 @NonNull final ControlSizeTier tier,
                                 @DimenRes final int normalRes,
                                 @DimenRes final int compactRes,
                                 @DimenRes final int extraCompactRes)
  {
    return selectDimen(enabled, tier, normalRes, compactRes, extraCompactRes);
  }

  @DimenRes
  static int selectDimen(final boolean enabled, @NonNull final ControlSizeTier tier,
                         @DimenRes final int normalRes, @DimenRes final int compactRes,
                         @DimenRes final int extraCompactRes)
  {
    if (!enabled)
      return normalRes;
    if (tier == ControlSizeTier.EXTRA_COMPACT)
      return extraCompactRes;
    return compactRes;
  }
"""
if text.count(old) != 1:
    raise SystemExit('expected selectDimen source block not found exactly once')
src.write_text(text.replace(old, new))

test = Path('android/app/src/test/java/app/organicmaps/util/InCarVisualsTest.java')
text = test.read_text()
marker = """  @Test
  public void invalidBoundsAreUnknown()
"""
addition = """  @Test
  public void disabledVisualsIgnoreExtraCompactTier()
  {
    assertEquals(100,
                 InCarVisuals.selectDimen(false, InCarVisuals.ControlSizeTier.EXTRA_COMPACT, 100, 80, 55));
  }

"""
if addition in text:
    raise SystemExit('regression test already present')
if text.count(marker) != 1:
    raise SystemExit('test insertion marker not found exactly once')
test.write_text(text.replace(marker, addition + marker))
