cmake_minimum_required(VERSION 3.22.1)
project(OEngineAndroidGlfw LANGUAGES C)

# Build the pinned MojoLauncher Android platform port of GLFW 3.5.
# This builds the Android GLFW C API consumed by LWJGL's function-pointer table.
# O Engine performs a runtime export/version gate before HotSpot binds this library;
# the build alone does not prove Minecraft's renderer or event loop works.
set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
set(GLFW_LIBRARY_TYPE SHARED CACHE STRING "" FORCE)
set(GLFW_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(GLFW_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(GLFW_BUILD_DOCS OFF CACHE BOOL "" FORCE)
set(GLFW_INSTALL OFF CACHE BOOL "" FORCE)
set(GLFW_BUILD_ANDROID ON CACHE BOOL "" FORCE)
set(GLFW_BUILD_X11 OFF CACHE BOOL "" FORCE)
set(GLFW_BUILD_WAYLAND OFF CACHE BOOL "" FORCE)
set(GLFW_BUILD_WIN32 OFF CACHE BOOL "" FORCE)
set(GLFW_BUILD_COCOA OFF CACHE BOOL "" FORCE)

set(OENGINE_UPSTREAM_DIR "${CMAKE_CURRENT_LIST_DIR}/../upstream")
set(MOJOEXEC_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../third_party/MojoLauncher/mojoexec")
if(NOT EXISTS "${OENGINE_UPSTREAM_DIR}/mojo-glfw/src/android_window.c")
    message(FATAL_ERROR "Pinned Mojo Android GLFW submodule is missing.")
endif()
if(NOT EXISTS "${MOJOEXEC_ROOT}/CMakeLists.txt")
    message(FATAL_ERROR "MojoExec submodule nested in the pinned MojoLauncher source is missing.")
endif()

# GLFW, the AWT runtime, and the JVM hooks must use one ABI-compatible MojoExec revision.
add_subdirectory("${MOJOEXEC_ROOT}" mojoexec)
add_subdirectory("${OENGINE_UPSTREAM_DIR}/mojo-glfw" glfw)

# The Android GLFW poll adapter consumes O Engine's stable public C event ABI.
target_include_directories(glfw PRIVATE "${CMAKE_CURRENT_LIST_DIR}/../oengine/include")
target_link_libraries(glfw PRIVATE ${CMAKE_DL_LIBS})
