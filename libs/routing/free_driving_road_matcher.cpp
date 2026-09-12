#include "routing/free_driving_road_matcher.hpp"

#include <algorithm>

namespace routing::free_driving_snap
{
namespace
{
size_t RequiredPersistentObservations(AccuracyBand accuracy)
{
  return accuracy == AccuracyBand::Moderate ? 3 : 2;
}
}  // namespace

bool FreeDrivingRoadMatcher::AdvancePending(uint64_t token, size_t requiredCount)
{
  if (token == 0)
  {
    ClearPending();
    return false;
  }

  if (m_pendingToken != token)
  {
    m_pendingToken = token;
    m_pendingCount = 1;
  }
  else
  {
    ++m_pendingCount;
  }

  if (m_pendingCount < requiredCount)
    return false;

  ClearPending();
  return true;
}

void FreeDrivingRoadMatcher::ClearPending()
{
  m_pendingToken = 0;
  m_pendingCount = 0;
}

MatcherDecision FreeDrivingRoadMatcher::SetState(MatchState state, MatcherAction action)
{
  MatcherDecision decision;
  decision.m_action = action;
  decision.m_stateChanged = m_state != state;
  m_state = state;
  decision.m_state = m_state;
  ClearPending();
  return decision;
}

void FreeDrivingRoadMatcher::Reset()
{
  m_state = MatchState::Unsnapped;
  ClearPending();
  m_parkingEvidenceSeconds = 0.0;
  m_offRoadEvidenceSeconds = 0.0;
  m_offRoadEvidenceDistanceM = 0.0;
  m_parkingEntranceHintSeconds = 0.0;
  m_roadRestoreHint = false;
}

void FreeDrivingRoadMatcher::RestoreFreeState(MatchState state)
{
  Reset();
  if (state == MatchState::ParkingFree || state == MatchState::OffRoadFree)
    m_state = state;
}

MatcherDecision FreeDrivingRoadMatcher::Update(MatcherEvidence const & evidence, AreaContext const & context)
{
  double const deltaSeconds = std::max(0.0, evidence.m_deltaSeconds);

  if (evidence.m_nearParkingEntrance)
    m_parkingEntranceHintSeconds = kParkingEntranceHintSeconds;
  else
    m_parkingEntranceHintSeconds = std::max(0.0, m_parkingEntranceHintSeconds - deltaSeconds);

  if (evidence.m_parkingReleaseEligible)
    m_parkingEvidenceSeconds += deltaSeconds;
  else
    m_parkingEvidenceSeconds = 0.0;

  if (evidence.m_offRoadEvidence)
  {
    m_offRoadEvidenceSeconds += deltaSeconds;
    m_offRoadEvidenceDistanceM += std::max(0.0, evidence.m_rawStepM);
  }
  else
  {
    m_offRoadEvidenceSeconds = 0.0;
    m_offRoadEvidenceDistanceM = 0.0;
  }

  if (m_state == MatchState::ParkingFree || m_state == MatchState::OffRoadFree)
  {
    if (evidence.m_allowFreeStateRoadReacquire && evidence.m_bestAcceptable && evidence.m_bestUnambiguous &&
        evidence.m_accuracy != AccuracyBand::Poor && evidence.m_accuracy != AccuracyBand::Unusable)
    {
      if (AdvancePending(evidence.m_candidateToken, RequiredPersistentObservations(evidence.m_accuracy)))
      {
        m_parkingEvidenceSeconds = 0.0;
        m_offRoadEvidenceSeconds = 0.0;
        m_offRoadEvidenceDistanceM = 0.0;
        return SetState(MatchState::Road, MatcherAction::UseBestRoad);
      }
      return {MatcherAction::None, m_state, false};
    }

    ClearPending();
    if (m_state == MatchState::OffRoadFree && evidence.m_parkingReleaseEligible &&
        m_parkingEvidenceSeconds >= ParkingReleaseTimeSeconds(context, HasRecentParkingEntranceHint()))
    {
      return SetState(MatchState::ParkingFree, MatcherAction::EnterParkingFree);
    }
    if (m_state == MatchState::ParkingFree && evidence.m_offRoadEvidence &&
        m_offRoadEvidenceSeconds >= OffRoadReleaseTimeSeconds(context, evidence.m_weakRoadMatch) &&
        m_offRoadEvidenceDistanceM >= OffRoadReleaseDistanceM(context, evidence.m_weakRoadMatch))
    {
      return SetState(MatchState::OffRoadFree, MatcherAction::EnterOffRoadFree);
    }
    return {MatcherAction::None, m_state, false};
  }

  if (evidence.m_parkingReleaseEligible &&
      m_parkingEvidenceSeconds >= ParkingReleaseTimeSeconds(context, HasRecentParkingEntranceHint()))
  {
    m_offRoadEvidenceSeconds = 0.0;
    m_offRoadEvidenceDistanceM = 0.0;
    return SetState(MatchState::ParkingFree, MatcherAction::EnterParkingFree);
  }

  if (evidence.m_offRoadEvidence &&
      m_offRoadEvidenceSeconds >= OffRoadReleaseTimeSeconds(context, evidence.m_weakRoadMatch) &&
      m_offRoadEvidenceDistanceM >= OffRoadReleaseDistanceM(context, evidence.m_weakRoadMatch))
  {
    m_parkingEvidenceSeconds = 0.0;
    return SetState(MatchState::OffRoadFree, MatcherAction::EnterOffRoadFree);
  }

  if (m_state == MatchState::Unsnapped)
  {
    if (!evidence.m_hasBestRoad || !evidence.m_bestAcceptable || !evidence.m_bestUnambiguous ||
        evidence.m_accuracy == AccuracyBand::Poor || evidence.m_accuracy == AccuracyBand::Unusable)
    {
      ClearPending();
      return {MatcherAction::None, m_state, false};
    }

    size_t required = RequiredPersistentObservations(evidence.m_accuracy);
    if (m_roadRestoreHint && evidence.m_bestStrong)
      required = 1;

    if (AdvancePending(evidence.m_candidateToken, required))
    {
      m_roadRestoreHint = false;
      return SetState(MatchState::Road, MatcherAction::UseBestRoad);
    }
    return {MatcherAction::None, m_state, false};
  }

  if (!evidence.m_bestAcceptable)
  {
    ClearPending();
    return {MatcherAction::HoldCurrentRoad, m_state, false};
  }

  if (evidence.m_relation == RoadRelation::SameDirectedEdge || evidence.m_relation == RoadRelation::SameRoad)
  {
    ClearPending();
    return {MatcherAction::UseBestRoad, m_state, false};
  }

  if (evidence.m_stationaryHold)
  {
    ClearPending();
    return {MatcherAction::HoldCurrentRoad, m_state, false};
  }

  if (evidence.m_relation == RoadRelation::Connected)
  {
    ClearPending();
    bool const decisiveTurn = evidence.m_motionEstablished && evidence.m_bestUnambiguous &&
                              evidence.m_accuracy != AccuracyBand::Poor &&
                              evidence.m_accuracy != AccuracyBand::Unusable;
    return {decisiveTurn ? MatcherAction::UseBestRoad : MatcherAction::HoldCurrentRoad, m_state, false};
  }

  if (evidence.m_relation == RoadRelation::ReverseSameRoad)
  {
    if (!evidence.m_motionEstablished || !evidence.m_bestUnambiguous || evidence.m_accuracy == AccuracyBand::Poor ||
        evidence.m_accuracy == AccuracyBand::Unusable)
    {
      ClearPending();
      return {MatcherAction::HoldCurrentRoad, m_state, false};
    }

    if (AdvancePending(evidence.m_candidateToken, 2))
      return {MatcherAction::UseBestRoad, m_state, false};
    return {MatcherAction::HoldCurrentRoad, m_state, false};
  }

  if (!evidence.m_bestUnambiguous || !evidence.m_bestBeatsCurrent || evidence.m_accuracy == AccuracyBand::Poor ||
      evidence.m_accuracy == AccuracyBand::Unusable)
  {
    ClearPending();
    return {MatcherAction::HoldCurrentRoad, m_state, false};
  }

  if (AdvancePending(evidence.m_candidateToken, RequiredPersistentObservations(evidence.m_accuracy)))
    return {MatcherAction::UseBestRoad, m_state, false};
  return {MatcherAction::HoldCurrentRoad, m_state, false};
}
}  // namespace routing::free_driving_snap
