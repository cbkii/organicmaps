#include "map/gps_tracker.hpp"

#include "platform/platform.hpp"
#include "platform/settings.hpp"

#include "base/file_name_utils.hpp"

#include "defines.hpp"

namespace
{

std::string_view constexpr kEnabledKey = "GpsTrackingEnabled";
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
  return GetBoolSetting(kEnabledKey) && GetBoolSetting(kAutoResumeFeatureKey) &&
         GetBoolSetting(kAutoResumeSessionKey);
}

inline void ClearAutoResumeSession()
{
  SetBoolSetting(kEnabledKey, false);
  SetBoolSetting(kAutoResumeSessionKey, false);
}

}  // namespace

GpsTracker & GpsTracker::Instance()
{
  static GpsTracker instance;
  return instance;
}

GpsTracker::GpsTracker() : m_enabled(ShouldAutoResume()), m_track(GetFilePath(), std::make_unique<GpsTrackFilter>())
{
  // Old builds persisted GpsTrackingEnabled for every recording. Require both new explicit consent
  // markers before restoring it so an upgrade cannot unexpectedly resume a legacy recording.
  if (!m_enabled)
    ClearAutoResumeSession();
}

void GpsTracker::SetEnabled(bool enabled)
{
  if (enabled == m_enabled)
    return;

  if (enabled)
  {
    // A newly started recording is once-only until the user explicitly opts this session into
    // auto-resume. This also prevents a crash between start and the resume-mode choice from arming
    // future recording unexpectedly.
    ClearAutoResumeSession();
    m_track.Clear();
  }
  else
  {
    ClearAutoResumeSession();
  }

  m_enabled = enabled;
}

void GpsTracker::SetAutoResumeFeatureEnabled(bool enabled)
{
  SetBoolSetting(kAutoResumeFeatureKey, enabled);
  if (!enabled)
    ClearAutoResumeSession();
}

void GpsTracker::SetAutoResumeForCurrentRecording(bool enabled)
{
  bool const allow = enabled && m_enabled && GetBoolSetting(kAutoResumeFeatureKey);
  SetBoolSetting(kAutoResumeSessionKey, allow);
  SetBoolSetting(kEnabledKey, allow);
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
