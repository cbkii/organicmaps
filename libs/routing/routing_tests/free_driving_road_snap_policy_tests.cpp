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

UNIT_TEST(FreeDrivingRoadSnapPolicy_UsesAdaptiveAccuracyBands)
{
  TEST_EQUAL(GetAccuracyBand(MakeFix(10.0, 8.0, 1.0)), AccuracyBand::Good, ());
  TEST_EQUAL(GetAccuracyBand(MakeFix(10.0, 25.0, 1.0)), AccuracyBand::Moderate, ());
  TEST_EQUAL(GetAccuracyBand(MakeFix(10.0, 50.0, 1.0)), AccuracyBand::Poor, ());
  TEST_EQUAL(GetAccuracyBand(MakeFix(10.0, 81.0, 1.0)), AccuracyBand::Unusable, ());
  TEST_EQUAL(GetAccuracyBand(MakeFix(10.0, 0.0, 1.0)), AccuracyBand::Unusable, ());
}

UNIT_TEST(FreeDrivingRoadSnapPolicy_SearchRadiusDoesNotExcludeLikelyRoad)
{
  TEST_ALMOST_EQUAL_ULPS(SearchRadiusM(MakeFix(10.0, 5.0, 1.0)), 20.0, ());
  TEST_ALMOST_EQUAL_ULPS(SearchRadiusM(MakeFix(10.0, 25.0, 1.0)), 42.5, ());
  TEST_ALMOST_EQUAL_ULPS(SearchRadiusM(MakeFix(10.0, 50.0, 1.0)), 50.0, ());
}

UNIT_TEST(FreeDrivingRoadSnapPolicy_HeadingWeightFallsAtLowSpeed)
{
  TEST_ALMOST_EQUAL_ULPS(HeadingWeight(MakeFix(0.0, 8.0, 1.0)), 0.0, ());
  TEST_ALMOST_EQUAL_ULPS(HeadingWeight(MakeFix(kDirectionUsefulMinSpeedMps, 8.0, 1.0)), 0.0, ());
  TEST_GREATER(HeadingWeight(MakeFix(kCruiseSpeedMps, 8.0, 1.0)), 0.0, ());
  TEST_ALMOST_EQUAL_ULPS(HeadingWeight(MakeFix(kDirectionFullWeightSpeedMps, 8.0, 1.0)), 1.0, ());
}

UNIT_TEST(FreeDrivingRoadSnapPolicy_RecentDirectionWindowScalesWithSpeed)
{
  TEST_ALMOST_EQUAL_ULPS(RecentDirectionTrackLengthM(MakeFix(2.0, 8.0, 1.0)), kRecentDirectionMinTrackM, ());
  TEST_ALMOST_EQUAL_ULPS(RecentDirectionTrackLengthM(MakeFix(10.0, 8.0, 1.0)), 25.0, ());
  TEST_ALMOST_EQUAL_ULPS(RecentDirectionTrackLengthM(MakeFix(30.0, 8.0, 1.0)), kRecentDirectionMaxTrackM, ());
}

UNIT_TEST(FreeDrivingRoadSnapPolicy_TemporalEvidenceRejectsBadClockIntervals)
{
  TEST_ALMOST_EQUAL_ULPS(TemporalEvidenceDeltaSeconds(100.0, 101.0), 1.0, ());
  TEST_ALMOST_EQUAL_ULPS(TemporalEvidenceDeltaSeconds(100.0, 103.0), kMaxTemporalEvidenceDeltaSeconds, ());
  TEST_ALMOST_EQUAL_ULPS(TemporalEvidenceDeltaSeconds(100.0, 99.0), 0.0, ());
  TEST_ALMOST_EQUAL_ULPS(TemporalEvidenceDeltaSeconds(100.0, 106.0), 0.0, ());
}

UNIT_TEST(FreeDrivingRoadSnapPolicy_ConnectedTurnsAreCheaperAtLowSpeed)
{
  TEST_LESS(ContinuityPenalty(RoadRelation::SameDirectedEdge, true), ContinuityPenalty(RoadRelation::SameRoad, true),
            ());
  TEST_LESS(ContinuityPenalty(RoadRelation::SameRoad, true), ContinuityPenalty(RoadRelation::Connected, true), ());
  TEST_LESS(ContinuityPenalty(RoadRelation::Connected, true), ContinuityPenalty(RoadRelation::Unrelated, true), ());
  TEST_LESS(ContinuityPenalty(RoadRelation::Connected, true), ContinuityPenalty(RoadRelation::Connected, false), ());
}

UNIT_TEST(FreeDrivingRoadSnapPolicy_ParkingReleaseRequiresLowSpeedAndContext)
{
  AreaContext surfaceParking;
  surfaceParking.m_insideParking = true;
  TEST(!CanEnterParkingFree(MakeFix(20.0 / 3.6, 8.0, 10.0), surfaceParking, 10.0, false), ());
  TEST(!CanEnterParkingFree(MakeFix(10.0 / 3.6, 8.0, 10.0), surfaceParking, 2.9, false), ());
  TEST(CanEnterParkingFree(MakeFix(10.0 / 3.6, 8.0, 10.0), surfaceParking, 3.0, false), ());

  AreaContext building;
  building.m_insideLargeBuilding = true;
  TEST(!CanEnterParkingFree(MakeFix(5.0 / 3.6, 8.0, 10.0), building, 6.0, false), ());
  TEST(CanEnterParkingFree(MakeFix(5.0 / 3.6, 8.0, 10.0), building, 6.0, true), ());
}

UNIT_TEST(FreeDrivingRoadSnapPolicy_StructuredParkingReleasesSooner)
{
  AreaContext surface;
  surface.m_insideParking = true;
  AreaContext structured;
  structured.m_insideStructuredParking = true;
  TEST_LESS(ParkingReleaseTimeSeconds(structured), ParkingReleaseTimeSeconds(surface), ());
}

UNIT_TEST(FreeDrivingRoadSnapPolicy_OpenLandOnlyAcceleratesSustainedRelease)
{
  AreaContext none;
  AreaContext strong;
  strong.m_insideStrongOpenArea = true;
  AreaContext weak;
  weak.m_insideWeakOpenArea = true;

  TEST_LESS(OffRoadReleaseTimeSeconds(strong), OffRoadReleaseTimeSeconds(weak), ());
  TEST_LESS(OffRoadReleaseTimeSeconds(weak), OffRoadReleaseTimeSeconds(none), ());
  TEST_LESS(OffRoadReleaseDistanceM(strong), OffRoadReleaseDistanceM(weak), ());
  TEST_LESS(OffRoadReleaseDistanceM(weak), OffRoadReleaseDistanceM(none), ());
  TEST(!CanEnterOffRoadFree(strong, OffRoadReleaseTimeSeconds(strong) - 0.1, 100.0), ());
  TEST(!CanEnterOffRoadFree(strong, 100.0, OffRoadReleaseDistanceM(strong) - 0.1), ());
  TEST(CanEnterOffRoadFree(strong, OffRoadReleaseTimeSeconds(strong), OffRoadReleaseDistanceM(strong)), ());
}

UNIT_TEST(FreeDrivingRoadSnapPolicy_ContextRefreshIsBounded)
{
  TEST(ShouldRefreshAreaContext(0.0, 100.0, 0.0), ());
  TEST(!ShouldRefreshAreaContext(100.0, 101.0, 5.0), ());
  TEST(ShouldRefreshAreaContext(100.0, 102.0, 5.0), ());
  TEST(ShouldRefreshAreaContext(100.0, 101.0, 20.0), ());
  TEST(ShouldRefreshAreaContext(100.0, 99.0, 0.0), ());
}
}  // namespace free_driving_road_snap_policy_tests
