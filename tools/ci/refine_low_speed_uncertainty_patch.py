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
    """inline double EffectiveSpeedMps(location::GpsInfo const & info, double rawStepM, double deltaSeconds)\n{\n  if (info.HasSpeed())\n  {\n    double const providerSpeed = std::max(0.0, info.m_speed);\n    if (info.HasSpeedAccuracy() && deltaSeconds > 0.0 && rawStepM >= 0.0)\n    {\n      double const plausibleRawLimitM = std::max(20.0, providerSpeed * deltaSeconds * 4.0 + 10.0);\n      if (rawStepM <= plausibleRawLimitM)\n      {\n        double const displacementSpeed = rawStepM / deltaSeconds;\n        double const providerWeight = std::clamp(1.0 - info.m_speedAccuracy / 5.0, 0.25, 1.0);\n        return providerWeight * providerSpeed + (1.0 - providerWeight) * displacementSpeed;\n      }\n    }\n    return providerSpeed;\n  }\n  if (deltaSeconds <= 0.0 || rawStepM < 0.0 || rawStepM > 80.0)\n    return 0.0;\n  return rawStepM / deltaSeconds;\n}\n""",
    """inline double EffectiveSpeedMps(location::GpsInfo const & info, double rawStepM, double deltaSeconds)\n{\n  // Android's provider speed is normally Doppler-derived and should remain the speed authority.\n  // Position-step speed is only a fallback when the provider supplies no speed at all; otherwise\n  // low-speed GNSS jitter could be amplified into a false high-speed observation.\n  if (info.HasSpeed())\n    return std::max(0.0, info.m_speed);\n  if (deltaSeconds <= 0.0 || rawStepM < 0.0 || rawStepM > 80.0)\n    return 0.0;\n  return rawStepM / deltaSeconds;\n}\n\ninline double SpeedReliability(location::GpsInfo const & info)\n{\n  if (!info.HasSpeed())\n    return 0.0;\n  if (!info.HasSpeedAccuracy())\n    return 1.0;\n  if (info.m_speedAccuracy <= 0.5)\n    return 1.0;\n  if (info.m_speedAccuracy >= 5.0)\n    return 0.20;\n  return 1.0 - (info.m_speedAccuracy - 0.5) / 4.5 * 0.80;\n}\n""")

replace_once(
    "libs/routing/free_driving_road_snap_policy.hpp",
    """inline bool HasEstablishedMotion(location::GpsInfo const & info, double rawStepM, double deltaSeconds)\n{\n  if (info.HasSpeed() && info.m_speed >= kMotionEstablishedSpeedMps)\n    return true;\n  if (deltaSeconds <= 0.0)\n    return false;\n  double const displacementThresholdM = std::clamp(info.m_horizontalAccuracy * 0.4, 2.0, 6.0);\n  return rawStepM >= displacementThresholdM && rawStepM / deltaSeconds >= kMotionEstablishedSpeedMps;\n}\n""",
    """inline bool HasEstablishedMotion(location::GpsInfo const & info, double rawStepM, double deltaSeconds)\n{\n  if (info.HasSpeed() && info.m_speed >= kMotionEstablishedSpeedMps && SpeedReliability(info) >= 0.35)\n    return true;\n  if (deltaSeconds <= 0.0)\n    return false;\n  double displacementThresholdM = std::clamp(info.m_horizontalAccuracy * 0.4, 2.0, 6.0);\n  if (info.HasSpeedAccuracy() && SpeedReliability(info) < 0.35)\n    displacementThresholdM = std::max(displacementThresholdM, std::clamp(info.m_horizontalAccuracy * 0.8, 4.0, 12.0));\n  return rawStepM >= displacementThresholdM && rawStepM / deltaSeconds >= kMotionEstablishedSpeedMps;\n}\n""")

replace_once(
    "libs/routing/routing_session_free_driving.cpp",
    """  auto const motionEvidence = m_freeDrivingMotionEstimator.Push(rawLocation, rawPoint);\n  m_freeDrivingLastMotionEvidence = motionEvidence;\n\n  location::GpsInfo policyInfo = rawLocation;\n  double const effectiveSpeedMps =\n      free_driving_snap::EffectiveSpeedMps(rawLocation, rawStepM, observationIntervalSeconds);\n  if (rawLocation.HasSpeed() || observationIntervalSeconds > 0.0)\n    policyInfo.m_speed = effectiveSpeedMps;\n\n  m2::PointD displacementDirection = motionEvidence.HasDirection() ? motionEvidence.m_direction : m2::PointD{};\n""",
    """  location::GpsInfo policyInfo = rawLocation;\n  double const effectiveSpeedMps =\n      free_driving_snap::EffectiveSpeedMps(rawLocation, rawStepM, observationIntervalSeconds);\n  if (rawLocation.HasSpeed() || observationIntervalSeconds > 0.0)\n    policyInfo.m_speed = effectiveSpeedMps;\n\n  free_driving_snap::MotionEvidence motionEvidence;\n  if (effectiveSpeedMps <= free_driving_snap::kCruiseSpeedMps)\n  {\n    motionEvidence = m_freeDrivingMotionEstimator.Push(rawLocation, rawPoint);\n  }\n  else\n  {\n    // Do not carry a fast-road trajectory into the first few fixes after slowing for a junction,\n    // driveway or parking facility. Low-speed direction must be rebuilt from low-speed fixes.\n    m_freeDrivingMotionEstimator.Reset();\n  }\n  m_freeDrivingLastMotionEvidence = motionEvidence;\n\n  m2::PointD displacementDirection = motionEvidence.HasDirection() ? motionEvidence.m_direction : m2::PointD{};\n""")

replace_once(
    "libs/routing/routing_tests/free_driving_road_snap_policy_tests.cpp",
    """UNIT_TEST(FreeDrivingRoadSnapPolicy_UsesProviderUncertainty)\n{\n  auto fix = MakeFix(2.0, 8.0, 1.0);\n  fix.m_bearing = 90.0;\n  fix.m_bearingAccuracy = 5.0;\n  TEST_GREATER(BearingReliability(fix), 0.9, ());\n  fix.m_bearingAccuracy = 50.0;\n  TEST_LESS(BearingReliability(fix), 0.4, ());\n\n  fix.m_speedAccuracy = 0.2;\n  TEST_LESS(std::abs(EffectiveSpeedMps(fix, 10.0, 1.0) - 2.0), 0.5, ());\n}\n""",
    """UNIT_TEST(FreeDrivingRoadSnapPolicy_UsesProviderUncertainty)\n{\n  auto fix = MakeFix(2.0, 8.0, 1.0);\n  fix.m_bearing = 90.0;\n  fix.m_bearingAccuracy = 5.0;\n  TEST_GREATER(BearingReliability(fix), 0.9, ());\n  fix.m_bearingAccuracy = 50.0;\n  TEST_LESS(BearingReliability(fix), 0.4, ());\n\n  fix.m_speedAccuracy = 0.2;\n  TEST_GREATER(SpeedReliability(fix), 0.9, ());\n  TEST_ALMOST_EQUAL_ULPS(EffectiveSpeedMps(fix, 10.0, 1.0), 2.0, ());\n\n  // Poor speed accuracy lowers motion confidence; one lateral positional jump must not replace\n  // the provider's speed estimate or establish motion by itself.\n  fix.m_speedAccuracy = 5.0;\n  TEST_LESS(SpeedReliability(fix), 0.25, ());\n  TEST_ALMOST_EQUAL_ULPS(EffectiveSpeedMps(fix, 10.0, 1.0), 2.0, ());\n  TEST(!HasEstablishedMotion(fix, 5.0, 1.0), ());\n}\n""")

print("uncertainty refinement materialised")
