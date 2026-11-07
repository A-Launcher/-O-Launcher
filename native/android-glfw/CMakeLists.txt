cmake_minimum_required(VERSION 3.22.1)
project(OEngineAndroidGlfw LANGUAGES C)

# Build the pinned MojoLauncher Android platform port of GLFW 3.5.
# This is a separate platform backend artifact; it is not yet the Minecraft
# LWJGL JNI library and must not be described as a working game event pump.
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
if(NOT EXISTS "${OENGINE_UPSTREAM_DIR}/mojo-glfw/src/android_window.c")
    message(FATAL_ERROR "Pinned Mojo Android GLFW submodule is missing.")
endif()
if(NOT EXISTS "${OENGINE_UPSTREAM_DIR}/mojoexec/CMakeLists.txt")
    message(FATAL_ERROR "Pinned MojoExec submodule is missing.")
endif()

add_subdirectory("${OENGINE_UPSTREAM_DIR}/mojoexec" mojoexec)
add_subdirectory("${OENGINE_UPSTREAM_DIR}/mojo-glfw" glfw)

# The Android GLFW poll adapter consumes O Engine's stable public C event ABI.
target_include_directories(glfw PRIVATE "${CMAKE_CURRENT_LIST_DIR}/../oengine/include")
target_link_libraries(glfw PRIVATE ${CMAKE_DL_LIBS})
