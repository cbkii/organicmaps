#include "testing/testing.hpp"

#include "routing/free_driving_road_matcher.hpp"

namespace free_driving_road_matcher_tests
{
using namespace routing::free_driving_snap;

MatcherEvidence RoadEvidence(uint64_t token, AccuracyBand accuracy = AccuracyBand::Good)
{
  MatcherEvidence evidence;
  evidence.m_hasBestRoad = true;
  evidence.m_bestAcceptable = true;
  evidence.m_bestStrong = true;
  evidence.m_bestUnambiguous = true;
  evidence.m_bestBeatsCurrent = true;
  evidence.m_motionEstablished = true;
  evidence.m_accuracy = accuracy;
  evidence.m_relation = RoadRelation::Unrelated;
  evidence.m_candidateToken = token;
  evidence.m_deltaSeconds = 1.0;
  evidence.m_rawStepM = 8.0;
  return evidence;
}

void AcquireRoad(FreeDrivingRoadMatcher & matcher, uint64_t token = 1)
{
  AreaContext context;
  auto evidence = RoadEvidence(token);
  TEST_EQUAL(matcher.Update(evidence, context).m_state, MatchState::Unsnapped, ());
  auto const decision = matcher.Update(evidence, context);
  TEST_EQUAL(decision.m_action, MatcherAction::UseBestRoad, ());
  TEST_EQUAL(decision.m_state, MatchState::Road, ());
}

UNIT_TEST(FreeDrivingRoadMatcher_GoodRoadRequiresTwoRealObservations)
{
  FreeDrivingRoadMatcher matcher;
  AreaContext context;
  auto const evidence = RoadEvidence(11);
  TEST_EQUAL(matcher.Update(evidence, context).m_state, MatchState::Unsnapped, ());
  TEST_EQUAL(matcher.Update(evidence, context).m_state, MatchState::Road, ());
}

UNIT_TEST(FreeDrivingRoadMatcher_ModerateRoadRequiresThreeObservations)
{
  FreeDrivingRoadMatcher matcher;
  AreaContext context;
  auto const evidence = RoadEvidence(12, AccuracyBand::Moderate);
  TEST_EQUAL(matcher.Update(evidence, context).m_state, MatchState::Unsnapped, ());
  TEST_EQUAL(matcher.Update(evidence, context).m_state, MatchState::Unsnapped, ());
  TEST_EQUAL(matcher.Update(evidence, context).m_state, MatchState::Road, ());
}

UNIT_TEST(FreeDrivingRoadMatcher_PersistedRoadHintReacquiresOneStrongObservation)
{
  FreeDrivingRoadMatcher matcher;
  matcher.SetRoadRestoreHint(true);
  AreaContext context;
  TEST_EQUAL(matcher.Update(RoadEvidence(13), context).m_state, MatchState::Road, ());
}

UNIT_TEST(FreeDrivingRoadMatcher_StationaryJunctionHoldsRoadIdentity)
{
  FreeDrivingRoadMatcher matcher;
  AcquireRoad(matcher);

  AreaContext context;
  auto evidence = RoadEvidence(22);
  evidence.m_relation = RoadRelation::Connected;
  evidence.m_stationaryHold = true;
  evidence.m_motionEstablished = false;
  auto const decision = matcher.Update(evidence, context);
  TEST_EQUAL(decision.m_action, MatcherAction::HoldCurrentRoad, ());
  TEST_EQUAL(decision.m_state, MatchState::Road, ());
}

UNIT_TEST(FreeDrivingRoadMatcher_ConnectedTurnNeedsMotionAndClearWinner)
{
  FreeDrivingRoadMatcher matcher;
  AcquireRoad(matcher);
  AreaContext context;

  auto evidence = RoadEvidence(23);
  evidence.m_relation = RoadRelation::Connected;
  evidence.m_bestUnambiguous = false;
  TEST_EQUAL(matcher.Update(evidence, context).m_action, MatcherAction::HoldCurrentRoad, ());

  evidence.m_bestUnambiguous = true;
  TEST_EQUAL(matcher.Update(evidence, context).m_action, MatcherAction::UseBestRoad, ());
}

UNIT_TEST(FreeDrivingRoadMatcher_UTurnRequiresTwoMovingReverseObservations)
{
  FreeDrivingRoadMatcher matcher;
  AcquireRoad(matcher);
  AreaContext context;

  auto evidence = RoadEvidence(24);
  evidence.m_relation = RoadRelation::ReverseSameRoad;
  TEST_EQUAL(matcher.Update(evidence, context).m_action, MatcherAction::HoldCurrentRoad, ());
  TEST_EQUAL(matcher.Update(evidence, context).m_action, MatcherAction::UseBestRoad, ());
}

UNIT_TEST(FreeDrivingRoadMatcher_UnrelatedSwitchUsesPersistentMarginEvidence)
{
  FreeDrivingRoadMatcher matcher;
  AcquireRoad(matcher);
  AreaContext context;

  auto evidence = RoadEvidence(25);
  evidence.m_relation = RoadRelation::Unrelated;
  TEST_EQUAL(matcher.Update(evidence, context).m_action, MatcherAction::HoldCurrentRoad, ());
  TEST_EQUAL(matcher.Update(evidence, context).m_action, MatcherAction::UseBestRoad, ());
}

UNIT_TEST(FreeDrivingRoadMatcher_ModerateUnrelatedSwitchRequiresThreeObservations)
{
  FreeDrivingRoadMatcher matcher;
  AcquireRoad(matcher);
  AreaContext context;

  auto evidence = RoadEvidence(26, AccuracyBand::Moderate);
  evidence.m_relation = RoadRelation::Unrelated;
  TEST_EQUAL(matcher.Update(evidence, context).m_action, MatcherAction::HoldCurrentRoad, ());
  TEST_EQUAL(matcher.Update(evidence, context).m_action, MatcherAction::HoldCurrentRoad, ());
  TEST_EQUAL(matcher.Update(evidence, context).m_action, MatcherAction::UseBestRoad, ());
}

UNIT_TEST(FreeDrivingRoadMatcher_ParkingEntranceHintAloneNeverReleasesRoad)
{
  FreeDrivingRoadMatcher matcher;
  AcquireRoad(matcher);
  AreaContext context;

  auto evidence = RoadEvidence(1);
  evidence.m_relation = RoadRelation::SameDirectedEdge;
  evidence.m_nearParkingEntrance = true;
  evidence.m_parkingReleaseEligible = false;
  for (size_t i = 0; i < 6; ++i)
    TEST_EQUAL(matcher.Update(evidence, context).m_state, MatchState::Road, ());
}

UNIT_TEST(FreeDrivingRoadMatcher_ParkingManoeuvreEntersFinePositionMode)
{
  FreeDrivingRoadMatcher matcher;
  AcquireRoad(matcher);
  AreaContext context;
  context.m_insideParking = true;

  auto evidence = RoadEvidence(1);
  evidence.m_relation = RoadRelation::SameDirectedEdge;
  evidence.m_parkingReleaseEligible = true;
  evidence.m_deltaSeconds = 1.0;
  TEST_EQUAL(matcher.Update(evidence, context).m_state, MatchState::Road, ());
  TEST_EQUAL(matcher.Update(evidence, context).m_state, MatchState::Road, ());
  auto const decision = matcher.Update(evidence, context);
  TEST_EQUAL(decision.m_state, MatchState::ParkingFree, ());
  TEST_EQUAL(decision.m_action, MatcherAction::EnterParkingFree, ());
}

UNIT_TEST(FreeDrivingRoadMatcher_ParkingFreeReacquiresLowSpeedExitRoad)
{
  FreeDrivingRoadMatcher matcher;
  matcher.RestoreFreeState(MatchState::ParkingFree);
  AreaContext context;

  auto evidence = RoadEvidence(31);
  evidence.m_allowFreeStateRoadReacquire = true;
  TEST_EQUAL(matcher.Update(evidence, context).m_state, MatchState::ParkingFree, ());
  auto const decision = matcher.Update(evidence, context);
  TEST_EQUAL(decision.m_state, MatchState::Road, ());
  TEST_EQUAL(decision.m_action, MatcherAction::UseBestRoad, ());
}

UNIT_TEST(FreeDrivingRoadMatcher_SustainedHardMismatchEntersOffRoad)
{
  FreeDrivingRoadMatcher matcher;
  AcquireRoad(matcher);
  AreaContext context;

  MatcherEvidence evidence;
  evidence.m_offRoadEvidence = true;
  evidence.m_accuracy = AccuracyBand::Good;
  evidence.m_deltaSeconds = 2.0;
  evidence.m_rawStepM = 10.0;
  for (size_t i = 0; i < 3; ++i)
    TEST_EQUAL(matcher.Update(evidence, context).m_state, MatchState::Road, ());
  TEST_EQUAL(matcher.Update(evidence, context).m_state, MatchState::OffRoadFree, ());
}

UNIT_TEST(FreeDrivingRoadMatcher_ParallelWeakRoadInOpenLandCanRelease)
{
  FreeDrivingRoadMatcher matcher;
  AcquireRoad(matcher);
  AreaContext context;
  context.m_insideStrongOpenArea = true;

  auto evidence = RoadEvidence(1);
  evidence.m_relation = RoadRelation::SameDirectedEdge;
  evidence.m_offRoadEvidence = true;
  evidence.m_weakRoadMatch = true;
  evidence.m_deltaSeconds = 1.0;
  evidence.m_rawStepM = 4.0;
  TEST_EQUAL(matcher.Update(evidence, context).m_state, MatchState::Road, ());
  TEST_EQUAL(matcher.Update(evidence, context).m_state, MatchState::Road, ());
  TEST_EQUAL(matcher.Update(evidence, context).m_state, MatchState::OffRoadFree, ());
}

UNIT_TEST(FreeDrivingRoadMatcher_OffRoadReacquisitionRequiresPersistence)
{
  FreeDrivingRoadMatcher matcher;
  matcher.RestoreFreeState(MatchState::OffRoadFree);
  AreaContext context;

  auto evidence = RoadEvidence(41);
  evidence.m_allowFreeStateRoadReacquire = true;
  TEST_EQUAL(matcher.Update(evidence, context).m_state, MatchState::OffRoadFree, ());
  TEST_EQUAL(matcher.Update(evidence, context).m_state, MatchState::Road, ());
}
}  // namespace free_driving_road_matcher_tests
