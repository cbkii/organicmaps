// Linked only into the debug SDK test library: exercises the real BookmarkManager/KML implementation.
#include <jni.h>
#include <ctime>
#include <stdexcept>
#include <string>
#include "base/file_name_utils.hpp"
#include "base/scope_guard.hpp"
#include "coding/file_reader.hpp"
#include "coding/internal/file_data.hpp"
#include "drape_frontend/visual_params.hpp"
#include "map/bookmark_helpers.hpp"
#include "map/bookmark_manager.hpp"
#include "platform/platform.hpp"

namespace
{
void Require(bool condition, char const * message)
{
  if (!condition)
    throw std::runtime_error(message);
}

void QualifyBookmarks(std::string const & directory)
{
  auto & platform = GetPlatform();
  auto const oldSettings = platform.SettingsDir();
  Require(Platform::MkDirRecursively(base::JoinPath(directory, "bookmarks")), "isolated bookmarks directory");
  platform.SetSettingsDir(directory);
  SCOPE_GUARD(restore, [&]() { platform.SetSettingsDir(oldSettings); });
  df::VisualParams::Init(1.0, 1024);
  auto const path = base::JoinPath(directory, "bookmarks", "reload.kml");
  SCOPE_GUARD(remove, [&]() { (void)base::DeleteFileX(path); });

  BookmarkManager manager({[]() -> StringsBundle const &
  {
    static StringsBundle const strings;
    return strings;
  }, nullptr, nullptr, nullptr, nullptr, nullptr, nullptr});
  manager.EnableTestMode(true);
  auto write = [&](char const * name, char const * description, std::time_t timestamp, size_t count)
  {
    kml::FileData data;
    kml::SetDefaultStr(data.m_categoryData.m_name, name);
    kml::SetDefaultStr(data.m_categoryData.m_description, description);
    data.m_categoryData.m_lastModified = kml::TimestampClock::from_time_t(timestamp);
    kml::CategoryData compilation;
    compilation.m_compilationId = 88;
    compilation.m_type = kml::CompilationType::Collection;
    kml::SetDefaultStr(compilation.m_name, "collection");
    data.m_compilationsData.push_back(std::move(compilation));
    for (size_t i = 0; i < count; ++i)
    {
      kml::BookmarkData bookmark;
      kml::SetDefaultStr(bookmark.m_name, "point " + std::to_string(i));
      bookmark.m_point = m2::PointD(1.0 + i, 2.0);
      bookmark.m_compilations.push_back(88);
      data.m_bookmarksData.push_back(std::move(bookmark));
    }
    kml::TrackData track;
    // Match the retained production-core fixture: serialisation/rendering requires a layer.
    track.m_layers.emplace_back();
    track.m_geometry.AddLine({{{0.0, 0.0}, 1}, {{1.0, 0.0}, 2}});
    track.m_geometry.AddTimestamps({});
    data.m_tracksData.push_back(std::move(track));
    Require(SaveKmlFileSafe(data, path, FileType::Kml), "write actual KML");
    Require(Platform::SetFileModificationTime(path, 5000), "set KML modification time");
  };
  auto load = [&](bool autoSave)
  {
    auto data = LoadKmlData(FileReader(path), FileType::Kml);
    Require(data != nullptr, "parse actual KML");
    BookmarkManager::KMLDataCollection collection;
    collection.emplace_back(path, std::move(data));
    manager.CreateCategories(std::move(collection), autoSave);
  };
  write("MapName", "initial", 1000, 2);
  load(false);
  auto const id = manager.GetCategoryByFileName(path);
  Require(id != kml::kInvalidMarkGroupId, "initial category identity");
  auto oldBookmark = *manager.GetUserMarkIds(id).begin();
  auto oldTrack = *manager.GetTrackIds(id).begin();
  Require(manager.GetChildrenCollections(id).size() == 1, "initial compilation");
  auto oldCompilation = manager.GetChildrenCollections(id).front();
  auto reload = [&](char const * name, char const * description, std::time_t timestamp, size_t count)
  {
    write(name, description, timestamp, count);
    load(true);
    auto const & category = manager.GetCategoryData(id);
    Require(manager.GetBmGroupsCount() == 1 && category.m_id == id && manager.GetCategoryByFileName(path) == id,
            "reload preserves category identity");
    Require(manager.GetCategoryName(id) == name && kml::GetDefaultStr(category.m_description) == description,
            "reload replaces category metadata");
    Require(category.m_lastModified == kml::TimestampClock::from_time_t(timestamp), "KML timestamp is preserved");
    Require(manager.GetUserMarkIds(id).size() == count && manager.GetTrackIds(id).size() == 1,
            "reload replaces bookmarks and tracks");
    Require(!manager.HasBookmark(oldBookmark) && !manager.HasTrack(oldTrack), "obsolete items are removed");
    auto const children = manager.GetChildrenCollections(id);
    Require(children.size() == 1 && children.front() != oldCompilation && !manager.IsCompilation(oldCompilation),
            "reload retires old compilation identity");
    Require(manager.GetUserMarkIds(children.front()).size() == count, "replacement compilation owns loaded items");
    oldCompilation = children.front();
    Require(Platform::GetFileModificationTime(path) == 5000, "reload does not rewrite KML");
    oldBookmark = *manager.GetUserMarkIds(id).begin();
    oldTrack = *manager.GetTrackIds(id).begin();
  };
  reload("MapName", "same name", 2000, 1);
  reload("Renamed", "new name", 3000, 2);
  auto const other = manager.CreateBookmarkCategory("Taken", false);
  write("Taken", "collision", 4000, 1);
  load(true);
  Require(manager.GetBmGroupsCount() == 2 && manager.GetCategoryName(id) == "Taken1" &&
              manager.GetCategoryName(other) == "Taken",
          "reload avoids duplicate category names");
  Require(Platform::GetFileModificationTime(path) == 5000, "collision reload does not rewrite KML");
}
}  // namespace

extern "C" JNIEXPORT void JNICALL
Java_app_organicmaps_test_NativeCoreQualificationTest_nativeQualifyBookmarks(JNIEnv * env, jclass, jstring directory)
{
  auto const * chars = env->GetStringUTFChars(directory, nullptr);
  if (chars == nullptr)
    return;
  std::string path(chars);
  env->ReleaseStringUTFChars(directory, chars);
  try
  {
    QualifyBookmarks(path);
  }
  catch (std::exception const & error)
  {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
  }
}
