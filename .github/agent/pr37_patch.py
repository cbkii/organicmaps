from pathlib import Path

src = Path('android/app/src/main/java/app/organicmaps/util/InCarVisuals.java')
text = src.read_text()
old = """  private static int selectDimen(@NonNull Activity activity, boolean enabled, @NonNull ControlSizeTier controlSizeTier,
                                 @DimenRes int normal, @DimenRes int inCar, @DimenRes int inCarCompact,
                                 @DimenRes int inCarExtraCompact)
  {
    if (controlSizeTier == ControlSizeTier.EXTRA_COMPACT)
      return dimen(activity, inCarExtraCompact);
    if (!enabled)
      return dimen(activity, normal);
    return dimen(activity, controlSizeTier == ControlSizeTier.COMPACT ? inCarCompact : inCar);
  }
"""
new = """  private static int selectDimen(@NonNull Activity activity, boolean enabled, @NonNull ControlSizeTier controlSizeTier,
                                 @DimenRes int normal, @DimenRes int inCar, @DimenRes int inCarCompact,
                                 @DimenRes int inCarExtraCompact)
  {
    return dimen(activity, selectDimenRes(enabled, controlSizeTier, normal, inCar, inCarCompact, inCarExtraCompact));
  }

  @DimenRes
  @VisibleForTesting
  static int selectDimenRes(boolean enabled, @NonNull ControlSizeTier controlSizeTier, @DimenRes int normal,
                            @DimenRes int inCar, @DimenRes int inCarCompact, @DimenRes int inCarExtraCompact)
  {
    if (!enabled)
      return normal;
    if (controlSizeTier == ControlSizeTier.EXTRA_COMPACT)
      return inCarExtraCompact;
    return controlSizeTier == ControlSizeTier.COMPACT ? inCarCompact : inCar;
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
                 InCarVisuals.selectDimenRes(false, InCarVisuals.ControlSizeTier.EXTRA_COMPACT, 100, 90, 80, 55));
  }

"""
if addition in text:
    raise SystemExit('regression test already present')
if text.count(marker) != 1:
    raise SystemExit('test insertion marker not found exactly once')
test.write_text(text.replace(marker, addition + marker))
