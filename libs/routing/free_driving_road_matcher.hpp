#pragma once

#include "routing/free_driving_road_snap_policy.hpp"

#include <cstddef>
#include <cstdint>

namespace routing::free_driving_snap
{
enum class MatcherAction
{
  None,
  UseBestRoad,
  HoldCurrentRoad,
  EnterParkingFree,
  EnterOffRoadFree,
};

struct MatcherEvidence
{
  bool m_hasBestRoad = false;
  bool m_bestAcceptable = false;
  bool m_bestStrong = false;
  bool m_bestUnambiguous = false;
  bool m_bestBeatsCurrent = false;
  bool m_bestParkingAisle = false;
  bool m_stationaryHold = false;
  bool m_motionEstablished = false;
  bool m_parkingReleaseEligible = false;
  bool m_offRoadEvidence = false;
  bool m_weakRoadMatch = false;
  bool m_allowFreeStateRoadReacquire = false;
  bool m_nearParkingEntrance = false;

  AccuracyBand m_accuracy = AccuracyBand::Unusable;
  RoadRelation m_relation = RoadRelation::Unrelated;
  uint64_t m_candidateToken = 0;
  double m_deltaSeconds = 0.0;
  double m_rawStepM = 0.0;
};

struct MatcherDecision
{
  MatcherAction m_action = MatcherAction::None;
  MatchState m_state = MatchState::Unsnapped;
  bool m_stateChanged = false;
};

// Small deterministic state machine. Geometry search/scoring stays outside this class so the
// behavioural policy can be replay-tested without constructing maps or a routing graph.
class FreeDrivingRoadMatcher
{
public:
  MatcherDecision Update(MatcherEvidence const & evidence, AreaContext const & context);

  void Reset();
  void RestoreFreeState(MatchState state);
  void SetRoadRestoreHint(bool enabled) { m_roadRestoreHint = enabled; }

  MatchState GetState() const { return m_state; }
  bool HasRecentParkingEntranceHint() const { return m_parkingEntranceHintSeconds > 0.0; }

private:
  bool AdvancePending(uint64_t token, size_t requiredCount);
  void ClearPending();
  MatcherDecision SetState(MatchState state, MatcherAction action);

  MatchState m_state = MatchState::Unsnapped;
  uint64_t m_pendingToken = 0;
  size_t m_pendingCount = 0;
  double m_parkingEvidenceSeconds = 0.0;
  double m_offRoadEvidenceSeconds = 0.0;
  double m_offRoadEvidenceDistanceM = 0.0;
  double m_parkingEntranceHintSeconds = 0.0;
  bool m_roadRestoreHint = false;
};
}  // namespace routing::free_driving_snap
