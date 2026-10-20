#pragma once

#include <cstdint>
#include <functional>
#include <mutex>

namespace android
{
// One factory-owned observation shared with its render context. No EGL/UI calls.
class PresentationState
{
public:
  using Handler = std::function<void(bool, uint64_t)>;
  void SetHandler(Handler handler)
  {
    Handler notify;
    bool ready;
    uint64_t generation;
    {
      std::lock_guard<std::mutex> lock(m_mutex);
      m_handler = std::move(handler);
      notify = m_handler;
      ready = m_ready;
      generation = m_generation;
    }
    if (notify)
      notify(ready, generation);
  }
  void Reset()
  {
    Handler notify;
    uint64_t generation;
    {
      std::lock_guard<std::mutex> lock(m_mutex);
      m_ready = false;
      generation = ++m_generation;
      notify = m_handler;
    }
    if (notify)
      notify(false, generation);
  }
  void Presented(bool ready)
  {
    Handler notify;
    uint64_t generation;
    {
      std::lock_guard<std::mutex> lock(m_mutex);
      if (m_ready == ready)
        return;
      m_ready = ready;
      generation = m_generation;
      notify = m_handler;
    }
    if (notify)
      notify(ready, generation);
  }

private:
  std::mutex m_mutex;
  Handler m_handler;
  bool m_ready = false;
  uint64_t m_generation = 1;
};
}  // namespace android
