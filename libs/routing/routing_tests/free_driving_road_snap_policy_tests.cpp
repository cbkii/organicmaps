#include "testing/testing.hpp"

#include "routing/free_driving_road_snap_policy.hpp"

namespace free_driving_road_snap_policy_tests
{
using namespace routing::free_driving_snap;

location::GpsInfo MakeFix(double speedMps, double accuracyM, double timestamp, bool hasSpeed = true)
{
  location::GpsInfo info;
  info.m_source = location::EAndroidNative;
  info.m_timestamp = timestamp;
  info.m_horizontalAccuracy = accuracyM;
  info.m_speed = hasSpeed ? speedMps : -1.0;
  return info;
}

UNIT_TEST(FreeDrivingRoadSnapPolicy_MovingStartsAtFifteenKmh)
{
  TEST_EQUAL(ResolveMode(MakeFix(kMovingMinSpeedMps, 8.0, 100.0), false, 0.0), Mode::Moving, ());
  TEST_EQUAL(ResolveMode(MakeFix(kMovingMinSpeedMps - 0.01, 8.0, 100.0), false, 0.0), Mode::None, ());
}

UNIT_TEST(FreeDrivingRoadSnapPolicy_RejectsPoorAccuracy)
{
  TEST_EQUAL(ResolveMode(MakeFix(20.0, kMaxHorizontalAccuracyM + 0.1, 100.0), false, 0.0), Mode::None, ());
  TEST_EQUAL(ResolveMode(MakeFix(20.0, 0.0, 100.0), false, 0.0), Mode::None, ());
}

UNIT_TEST(FreeDrivingRoadSnapPolicy_HoldsOnlyAfterConfidentMovingMatch)
{
  auto const stopped = MakeFix(0.0, 8.0, 150.0);
  TEST_EQUAL(ResolveMode(stopped, false, 100.0), Mode::None, ());
  TEST_EQUAL(ResolveMode(stopped, true, 100.0), Mode::StationaryHold, ());

  auto const noSpeed = MakeFix(0.0, 8.0, 150.0, false);
  TEST_EQUAL(ResolveMode(noSpeed, true, 100.0), Mode::StationaryHold, ());
}

UNIT_TEST(FreeDrivingRoadSnapPolicy_DoesNotSnapIntermediateSpeeds)
{
  TEST_EQUAL(ResolveMode(MakeFix(10.0 / 3.6, 8.0, 120.0), true, 100.0), Mode::None, ());
}

UNIT_TEST(FreeDrivingRoadSnapPolicy_StationaryHoldExpires)
{
  TEST_EQUAL(ResolveMode(MakeFix(0.0, 8.0, 191.0), true, 100.0), Mode::None, ());
  TEST_EQUAL(ResolveMode(MakeFix(0.0, 8.0, 99.0), true, 100.0), Mode::None, ());
}

UNIT_TEST(FreeDrivingRoadSnapPolicy_MovingRadiusIsTightlyBounded)
{
  TEST_ALMOST_EQUAL_ULPS(MovingProjectionRadiusM(MakeFix(10.0, 3.0, 1.0)), kMinMovingProjectionRadiusM, ());
  TEST_ALMOST_EQUAL_ULPS(MovingProjectionRadiusM(MakeFix(10.0, 8.0, 1.0)), 8.0, ());
  TEST_ALMOST_EQUAL_ULPS(MovingProjectionRadiusM(MakeFix(10.0, 20.0, 1.0)), kMaxMovingProjectionRadiusM, ());
}
}  // namespace free_driving_road_snap_policy_tests
