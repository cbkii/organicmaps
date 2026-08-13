cmake_minimum_required(VERSION 3.22.1)

project(omim C CXX)

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_C_STANDARD 17)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_C_EXTENSIONS OFF)

set(CMAKE_C_VISIBILITY_PRESET hidden)
set(CMAKE_CXX_VISIBILITY_PRESET hidden)
set(CMAKE_VISIBILITY_INLINES_HIDDEN ON)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR})

if (POLICY CMP0156)
  cmake_policy(SET CMP0156 NEW)
endif()

set(OMIM_ROOT ${CMAKE_SOURCE_DIR})
set(CMAKE_MODULE_PATH ${CMAKE_MODULE_PATH} "${OMIM_ROOT}/cmake")

include(OmimPlatform)
include(OmimOptions)
include(OmimConfig)
include(OmimHelpers)
include(OmimTesting)

# This fork intentionally supports the Android native build only. Host desktop,
# iOS and Qt targets live in upstream Organic Maps and must not creep back in via
# a broad upstream merge.
if (NOT PLATFORM_ANDROID)
  message(FATAL_ERROR "This repository is Android-only. Configure through the Android Gradle/NDK build.")
endif()

# Android production builds do not compile the inherited host-native test tree.
# Kotlin/JVM tests and Android validation are driven from Gradle/CI instead.
set(BUILD_TESTING OFF CACHE BOOL "Build host-native tests" FORCE)

message(STATUS "Using compiler ${CMAKE_CXX_COMPILER_ID} ${CMAKE_CXX_COMPILER_VERSION}")

if (CMAKE_UNITY_BUILD)
  message(STATUS "Using Unity Build with batch ${CMAKE_UNITY_BUILD_BATCH_SIZE}, use -DCMAKE_UNITY_BUILD=OFF to disable it.")
endif()

if (USE_CCACHE)
  find_program(CCACHE_PROGRAM ccache HINTS /usr/local/bin/)
  if (CCACHE_PROGRAM)
    message(STATUS "Using ccache, use -DUSE_CCACHE=OFF to disable it.")
    set_property(GLOBAL PROPERTY RULE_LAUNCH_COMPILE "${CCACHE_PROGRAM}")
    set_property(GLOBAL PROPERTY RULE_LAUNCH_LINK "${CCACHE_PROGRAM}")
    set(CMAKE_C_COMPILER_LAUNCHER "${CCACHE_PROGRAM}")
    set(CMAKE_CXX_COMPILER_LAUNCHER "${CCACHE_PROGRAM}")
  endif()
endif()

add_compile_options(-ffast-math $<$<CXX_COMPILER_ID:GNU>:-Wno-psabi>)

add_compile_definitions(
  $<$<CONFIG:Debug>:DEBUG>
  $<$<OR:$<CONFIG:Release>,$<CONFIG:RelWithDebInfo>,$<CONFIG:MinSizeRel>>:RELEASE>
)
add_compile_options(
  $<$<CONFIG:Debug>:-fno-omit-frame-pointer>
  $<$<OR:$<CONFIG:Release>,$<CONFIG:RelWithDebInfo>,$<CONFIG:MinSizeRel>>:-O3>
)

get_property(_is_multi_config GLOBAL PROPERTY GENERATOR_IS_MULTI_CONFIG)
if (NOT _is_multi_config)
  if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug" AND NOT CMAKE_BUILD_TYPE MATCHES "Rel")
    message(FATAL_ERROR "Unknown build type: " ${CMAKE_BUILD_TYPE})
  endif()
  message(STATUS "Build type: " ${CMAKE_BUILD_TYPE})
else()
  message(STATUS "Multi-config generator: build type selected per-build (Debug/Release/RelWithDebInfo/MinSizeRel)")
endif()

find_program(LLD_FOUND ld.lld)
if (LLD_FOUND)
  message(STATUS "Using ld.lld linker")
  set(CMAKE_EXE_LINKER_FLAGS "-fuse-ld=lld")
else()
  find_program(GOLD_FOUND ld.gold)
  if (GOLD_FOUND)
    message(STATUS "Using ld.gold")
    set(CMAKE_EXE_LINKER_FLAGS "-fuse-ld=gold")
  endif()
endif()

if (NOT PYTHON_VERSION)
  set(PYTHON_VERSION 3.5)
endif()

find_package(Threads REQUIRED)

# To allow #include "base/file_name.hpp" in all sources.
include_directories("${CMAKE_HOME_DIRECTORY}" "${CMAKE_HOME_DIRECTORY}/libs" "${CMAKE_HOME_DIRECTORY}/tools")

if (USE_PCH)
  message(STATUS "Precompiled headers are ON")
  set(OMIM_PCH_TARGET_NAME "omim_pch")
  add_precompiled_headers(
    ${OMIM_ROOT}/precompiled_headers.hpp
    ${OMIM_PCH_TARGET_NAME}
  )
endif()

if (WITH_SYSTEM_PROVIDED_3PARTY)
  find_package(ICU COMPONENTS uc i18n data REQUIRED)
  find_package(Freetype REQUIRED)
  find_package(harfbuzz REQUIRED)
  set(GFLAGS_USE_TARGET_NAMESPACE ON)
  find_package(gflags REQUIRED)
  find_package(expat CONFIG REQUIRED)
  find_package(pugixml REQUIRED)
  find_package(utf8cpp REQUIRED)
  find_package(Boost REQUIRED)
  find_package(ZLIB REQUIRED)
endif()

add_subdirectory(3party)

# Android shader/resource generation uses Python from the NDK/Gradle build.
find_package(Python3 REQUIRED COMPONENTS Interpreter)

add_subdirectory(libs)
add_subdirectory(android/sdk/src/main/cpp)
