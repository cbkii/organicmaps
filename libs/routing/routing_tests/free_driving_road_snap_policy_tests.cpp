#include "testing/testing.hpp"

#include "routing/free_driving_area_context.hpp"
#include "routing/free_driving_road_snap_policy.hpp"

#include <cmath>

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

UNIT_TEST(FreeDrivingRoadSnapPolicy_TightensAcceptanceAsAccuracyDegrades)
{
  TEST_ALMOST_EQUAL_ULPS(AcceptanceDistanceM(MakeFix(10.0, 8.0, 1.0)), 13.2, ());
  TEST_ALMOST_EQUAL_ULPS(AcceptanceDistanceM(MakeFix(10.0, 25.0, 1.0)), 25.75, ());
  TEST_ALMOST_EQUAL_ULPS(AcceptanceDistanceM(MakeFix(10.0, 50.0, 1.0)), 32.0, ());
  TEST_LESS(StrongRoadDistanceM(MakeFix(10.0, 8.0, 1.0)), AcceptanceDistanceM(MakeFix(10.0, 8.0, 1.0)), ());
}

UNIT_TEST(FreeDrivingRoadSnapPolicy_SearchRadiusDoesNotExcludeRecoveryRoads)
{
  TEST_ALMOST_EQUAL_ULPS(SearchRadiusM(MakeFix(10.0, 5.0, 1.0)), 20.0, ());
  TEST_ALMOST_EQUAL_ULPS(SearchRadiusM(MakeFix(10.0, 25.0, 1.0)), 42.5, ());
  TEST_ALMOST_EQUAL_ULPS(SearchRadiusM(MakeFix(10.0, 50.0, 1.0)), 50.0, ());
}

UNIT_TEST(FreeDrivingRoadSnapPolicy_UsesBoundedEffectiveCandidateBudgets)
{
  TEST_EQUAL(kNormalSpatialRoadCount, 6, ());
  TEST_EQUAL(kRecoverySpatialRoadCount, 8, ());
}

UNIT_TEST(FreeDrivingRoadSnapPolicy_ModerateAccuracyRequiresMoreAmbiguityMargin)
{
  TEST_LESS(RequiredRunnerUpMargin(AccuracyBand::Good), RequiredRunnerUpMargin(AccuracyBand::Moderate), ());
  TEST(IsUnambiguous(1.0, 1.4, AccuracyBand::Good), ());
  TEST(!IsUnambiguous(1.0, 1.4, AccuracyBand::Moderate), ());
  TEST(!IsUnambiguous(1.0, 10.0, AccuracyBand::Poor), ());
}

UNIT_TEST(FreeDrivingRoadSnapPolicy_HeadingWeightFallsAtLowSpeedAndConflict)
{
  TEST_ALMOST_EQUAL_ULPS(HeadingWeightForSpeed(0.0), 0.0, ());
  TEST_ALMOST_EQUAL_ULPS(HeadingWeightForSpeed(kDirectionUsefulMinSpeedMps), 0.0, ());
  TEST_GREATER(HeadingWeightForSpeed(kCruiseSpeedMps), 0.0, ());
  TEST_ALMOST_EQUAL_ULPS(HeadingWeightForSpeed(kDirectionFullWeightSpeedMps), 1.0, ());
  TEST_GREATER(HeadingAgreementFactor(20.0), HeadingAgreementFactor(70.0), ());
  TEST_GREATER(HeadingAgreementFactor(70.0), HeadingAgreementFactor(150.0), ());
}

UNIT_TEST(FreeDrivingRoadSnapPolicy_InferMotionOnlyFromCredibleDisplacement)
{
  auto noSpeed = MakeFix(0.0, 8.0, 1.0, false);
  TEST_ALMOST_EQUAL_ULPS(EffectiveSpeedMps(noSpeed, 10.0, 2.0), 5.0, ());
  TEST_ALMOST_EQUAL_ULPS(EffectiveSpeedMps(noSpeed, 90.0, 2.0), 0.0, ());
  TEST(HasEstablishedMotion(noSpeed, 6.0, 2.0), ());
}

UNIT_TEST(FreeDrivingRoadSnapPolicy_StationaryHoldRejectsJitterSwitches)
{
  auto stopped = MakeFix(0.0, 8.0, 1.0);
  TEST(IsStationaryHold(stopped, 3.0, 1.0), ());
  TEST(!IsStationaryHold(stopped, 10.0, 1.0), ());
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

UNIT_TEST(FreeDrivingRoadSnapPolicy_ContinuitySupportsTurnsAndUTurnsWithoutEquatingThem)
{
  TEST_LESS(ContinuityPenalty(RoadRelation::SameDirectedEdge, true), ContinuityPenalty(RoadRelation::SameRoad, true),
            ());
  TEST_LESS(ContinuityPenalty(RoadRelation::SameRoad, true), ContinuityPenalty(RoadRelation::Connected, true), ());
  TEST_LESS(ContinuityPenalty(RoadRelation::ReverseSameRoad, true), ContinuityPenalty(RoadRelation::Unrelated, true),
            ());
  TEST_LESS(ContinuityPenalty(RoadRelation::Connected, true), ContinuityPenalty(RoadRelation::Connected, false), ());
}

UNIT_TEST(FreeDrivingRoadSnapPolicy_RoadClassSpeedPriorIsWeakAndOnlyForUnrelatedRoads)
{
  auto fast = MakeFix(90.0 / 3.6, 8.0, 1.0);
  RoadMetadata motorway;
  motorway.m_class = RoadClass::Motorway;
  RoadMetadata service;
  service.m_class = RoadClass::Service;

  TEST_ALMOST_EQUAL_ULPS(RoadClassSpeedPenalty(fast, motorway, RoadRelation::Unrelated), 0.0, ());
  TEST_GREATER(RoadClassSpeedPenalty(fast, service, RoadRelation::Unrelated), 0.0, ());
  TEST_ALMOST_EQUAL_ULPS(RoadClassSpeedPenalty(fast, service, RoadRelation::Connected), 0.0, ());
}

UNIT_TEST(FreeDrivingRoadSnapPolicy_ParkingEntranceIsHintNotReleaseAuthority)
{
  auto slow = MakeFix(10.0 / 3.6, 8.0, 10.0);
  AreaContext entranceOnly;
  entranceOnly.m_nearParkingEntrance = true;
  TEST(!ParkingReleaseEligible(slow, entranceOnly, true, false, false), ());

  AreaContext surface;
  surface.m_insideParking = true;
  TEST(ParkingReleaseEligible(slow, surface, false, false, false), ());
}

UNIT_TEST(FreeDrivingRoadSnapPolicy_MappedParkingAisleStaysUsefulUntilFinePositioning)
{
  AreaContext surface;
  surface.m_insideParking = true;
  auto traversing = MakeFix(10.0 / 3.6, 8.0, 10.0);
  auto manoeuvring = MakeFix(5.0 / 3.6, 8.0, 10.0);

  TEST(!ParkingReleaseEligible(traversing, surface, false, true, false), ());
  TEST(ParkingReleaseEligible(manoeuvring, surface, false, true, false), ());
  TEST(ParkingReleaseEligible(traversing, surface, false, true, true), ());
}

UNIT_TEST(FreeDrivingRoadSnapPolicy_StructuredParkingReleasesSooner)
{
  AreaContext surface;
  surface.m_insideParking = true;
  AreaContext structured;
  structured.m_insideStructuredParking = true;
  TEST_LESS(ParkingReleaseTimeSeconds(structured, false), ParkingReleaseTimeSeconds(surface, false), ());
}

UNIT_TEST(FreeDrivingRoadSnapPolicy_OpenLandAcceleratesOnlySustainedRelease)
{
  AreaContext none;
  AreaContext strong;
  strong.m_insideStrongOpenArea = true;
  AreaContext weak;
  weak.m_insideWeakOpenArea = true;

  TEST_LESS(OffRoadReleaseTimeSeconds(strong, true), OffRoadReleaseTimeSeconds(weak, true), ());
  TEST_LESS(OffRoadReleaseTimeSeconds(weak, true), OffRoadReleaseTimeSeconds(none, true), ());
  TEST_LESS(OffRoadReleaseDistanceM(strong, true), OffRoadReleaseDistanceM(weak, true), ());
  TEST_LESS(OffRoadReleaseDistanceM(weak, true), OffRoadReleaseDistanceM(none, true), ());
}

UNIT_TEST(FreeDrivingRoadSnapPolicy_ContextRefreshIsBounded)
{
  TEST(ShouldRefreshAreaContext(0.0, 100.0, 0.0), ());
  TEST(!ShouldRefreshAreaContext(100.0, 101.0, 5.0), ());
  TEST(ShouldRefreshAreaContext(100.0, 102.0, 5.0), ());
  TEST(ShouldRefreshAreaContext(100.0, 101.0, 20.0), ());
  TEST(ShouldRefreshAreaContext(100.0, 99.0, 0.0), ());
}

UNIT_TEST(FreeDrivingRoadSnapPolicy_ParkingAreaConstraintPreservesInsidePosition)
{
  AreaContext context;
  context.m_freeAreaTriangles = {{0.0, 0.0}, {1.0, 0.0}, {0.0, 1.0}};

  m2::PointD constrained;
  m2::PointD const inside{0.2, 0.2};
  TEST(ConstrainPointToFreeArea(context, inside, constrained), ());
  TEST_LESS((constrained - inside).SquaredLength(), 1e-12, ());

  m2::PointD const outside{0.8, 0.8};
  TEST(ConstrainPointToFreeArea(context, outside, constrained), ());
  TEST_LESS(std::abs(constrained.x - 0.5), 1e-9, ());
  TEST_LESS(std::abs(constrained.y - 0.5), 1e-9, ());
}
}  // namespace free_driving_road_snap_policy_tests
