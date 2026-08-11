#include "drape_frontend/drape_engine.hpp"

#include "drape_frontend/message_subclasses.hpp"

namespace df
{
void DrapeEngine::SetDrivingView(bool enabled, bool autoReturn, bool recenter)
{
  m_threadCommutator->PostMessage(
      ThreadsCommutator::RenderThread,
      make_unique_dp<NotifyRenderThreadMessage>([this, enabled, autoReturn, recenter](uint64_t)
  { m_frontend->SetDrivingView(enabled, autoReturn, recenter); }, 0 /* notifyId */),
      MessagePriority::Normal);
}
}  // namespace df
