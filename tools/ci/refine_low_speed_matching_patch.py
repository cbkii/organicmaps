#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def replace_once(path: str, old: str, new: str) -> None:
    target = ROOT / path
    text = target.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected one match, found {count}")
    target.write_text(text.replace(old, new, 1), encoding="utf-8")


replace_once(
    "libs/routing/free_driving_road_snap_policy.hpp",
    """inline double ProgressScaleM(location::GpsInfo const & info, double expectedM, double motionConfidence)\n{\n  double floorM = 10.0;\n  auto const accuracy = GetAccuracyBand(info);\n  if (accuracy == AccuracyBand::Good && motionConfidence >= 0.35)\n  {\n    double const confidence = std::clamp((motionConfidence - 0.35) / 0.65, 0.0, 1.0);\n    floorM = 6.0 - 2.0 * confidence;\n  }\n  else if (accuracy == AccuracyBand::Moderate && motionConfidence >= 0.60)\n  {\n    floorM = 7.0;\n  }\n  return std::max(floorM, expectedM + 3.0);\n}\n""",
    """inline double ProgressScaleM(location::GpsInfo const & info, double expectedM, double motionConfidence)\n{\n  // Preserve PR #38's normal/cruise-speed scale exactly. Finer progress discrimination is only\n  // admitted while crawling and only when trajectory evidence is coherent enough to support it.\n  if (!info.HasSpeed() || info.m_speed > kCruiseSpeedMps)\n    return std::max(10.0, expectedM + 5.0);\n\n  double floorM = 10.0;\n  double paddingM = 5.0;\n  auto const accuracy = GetAccuracyBand(info);\n  if (accuracy == AccuracyBand::Good && motionConfidence >= 0.35)\n  {\n    double const confidence = std::clamp((motionConfidence - 0.35) / 0.65, 0.0, 1.0);\n    floorM = 6.0 - 2.0 * confidence;\n    paddingM = 3.0;\n  }\n  else if (accuracy == AccuracyBand::Moderate && motionConfidence >= 0.60)\n  {\n    floorM = 7.0;\n    paddingM = 4.0;\n  }\n  return std::max(floorM, expectedM + paddingM);\n}\n""")

replace_once(
    "libs/routing/routing_session_free_driving.cpp",
    """  location::GpsInfo policyInfo = rawLocation;\n  double const effectiveSpeedMps =\n      free_driving_snap::EffectiveSpeedMps(rawLocation, rawStepM, observationIntervalSeconds);\n  if (!policyInfo.HasSpeed() && observationIntervalSeconds > 0.0)\n    policyInfo.m_speed = effectiveSpeedMps;\n""",
    """  location::GpsInfo policyInfo = rawLocation;\n  double const effectiveSpeedMps =\n      free_driving_snap::EffectiveSpeedMps(rawLocation, rawStepM, observationIntervalSeconds);\n  if (rawLocation.HasSpeed() || observationIntervalSeconds > 0.0)\n    policyInfo.m_speed = effectiveSpeedMps;\n""")

replace_once(
    "libs/routing/routing_tests/free_driving_road_snap_policy_tests.cpp",
    """UNIT_TEST(FreeDrivingRoadSnapPolicy_LowSpeedProgressUsesFinerScaleOnlyWithEvidence)\n{\n  auto good = MakeFix(4.0 / 3.6, 6.0, 1.0);\n  TEST_LESS(ProgressScaleM(good, 2.0, 0.8), ProgressScaleM(good, 2.0, 0.0), ());\n  auto moderate = MakeFix(4.0 / 3.6, 25.0, 1.0);\n  TEST_ALMOST_EQUAL_ULPS(ProgressScaleM(moderate, 2.0, 0.2), 10.0, ());\n}\n""",
    """UNIT_TEST(FreeDrivingRoadSnapPolicy_LowSpeedProgressUsesFinerScaleOnlyWithEvidence)\n{\n  auto good = MakeFix(4.0 / 3.6, 6.0, 1.0);\n  TEST_LESS(ProgressScaleM(good, 2.0, 0.8), ProgressScaleM(good, 2.0, 0.0), ());\n  auto moderate = MakeFix(4.0 / 3.6, 25.0, 1.0);\n  TEST_ALMOST_EQUAL_ULPS(ProgressScaleM(moderate, 2.0, 0.2), 10.0, ());\n  auto cruising = MakeFix(40.0 / 3.6, 6.0, 1.0);\n  TEST_ALMOST_EQUAL_ULPS(ProgressScaleM(cruising, 20.0, 1.0), 25.0, ());\n}\n""")

print("low-speed refinement materialised")
