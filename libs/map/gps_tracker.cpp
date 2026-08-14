#include "map/gps_tracker.hpp"

#include "platform/platform.hpp"
#include "platform/settings.hpp"

#include "base/file_name_utils.hpp"

#include "defines.hpp"

namespace
{

// Kept only to neutralize the legacy behaviour where every active recording was persisted.
std::string_view constexpr kLegacyEnabledKey = "GpsTrackingEnabled";
std::string_view constexpr kAutoResumeFeatureKey = "GpsTrackingAutoResumeFeatureEnabled";
std::string_view constexpr kAutoResumeSessionKey = "GpsTrackingAutoResumeSession";

inline std::string GetFilePath()
{
  return base::JoinPath(GetPlatform().WritableDir(), GPS_TRACK_FILENAME);
}

inline bool GetBoolSetting(std::string_view key)
{
  bool enabled;
  if (!settings::Get(key, enabled))
    enabled = false;
  return enabled;
}

inline void SetBoolSetting(std::string_view key, bool enabled)
{
  settings::Set(key, enabled);
}

inline bool ShouldAutoResume()
{
  return GetBoolSetting(kAutoResumeFeatureKey) && GetBoolSetting(kAutoResumeSessionKey);
}

inline void SetAutoResumeSession(bool enabled)
{
  SetBoolSetting(kAutoResumeSessionKey, enabled);
  // A new explicit session marker replaces the old generic enabled flag. Keep the old key false so
  // downgrades/upgrades cannot accidentally turn every recording into an auto-resuming recording.
  SetBoolSetting(kLegacyEnabledKey, false);
}

}  // namespace

GpsTracker & GpsTracker::Instance()
{
  static GpsTracker instance;
  return instance;
}

GpsTracker::GpsTracker() : m_enabled(ShouldAutoResume()), m_track(GetFilePath(), std::make_unique<GpsTrackFilter>())
{
  // Old builds persisted GpsTrackingEnabled for every recording. Ignore and clear that legacy state;
  // only the two new explicit consent markers may restore an interrupted recording.
  SetBoolSetting(kLegacyEnabledKey, false);
  if (!GetBoolSetting(kAutoResumeFeatureKey))
    SetAutoResumeSession(false);
}

void GpsTracker::SetEnabled(bool enabled)
{
  if (enabled == m_enabled)
    return;

  if (enabled)
  {
    // A newly started recording is once-only until the user explicitly opts this session into
    // auto-resume. This also keeps a crash before the choice is committed fail-closed.
    SetAutoResumeSession(false);
    m_track.Clear();
  }

  // Disabling the live recorder does not itself clear resume consent. This lets an unexpected service
  // teardown or orderly device shutdown pause the runtime recorder while preserving a user-approved
  // auto-resume session. Explicit user stop/save/cancel clears the session marker separately.
  m_enabled = enabled;
}

void GpsTracker::SetAutoResumeFeatureEnabled(bool enabled)
{
  SetBoolSetting(kAutoResumeFeatureKey, enabled);
  if (!enabled)
    SetAutoResumeSession(false);
}

void GpsTracker::SetAutoResumeForCurrentRecording(bool enabled)
{
  bool const allow = enabled && m_enabled && GetBoolSetting(kAutoResumeFeatureKey);
  SetAutoResumeSession(allow);
}

void GpsTracker::Clear()
{
  m_track.Clear();
}

bool GpsTracker::IsEnabled() const
{
  return m_enabled;
}

bool GpsTracker::IsEmpty() const
{
  return m_track.IsEmpty();
}

TrackStatistics GpsTracker::GetTrackStatistics()
{
  return m_track.GetTrackStatistics();
}

ElevationInfo const & GpsTracker::GetElevationInfo()
{
  return m_track.GetElevationInfo();
}

void GpsTracker::Connect(TGpsTrackDiffCallback const & fn)
{
  m_track.SetCallback(fn);
}

void GpsTracker::Disconnect()
{
  m_track.SetCallback(nullptr);
}

void GpsTracker::OnLocationUpdated(location::GpsInfo const & info)
{
  if (!m_enabled)
    return;
  m_track.AddPoint(info);
}
