cmake_minimum_required(VERSION 3.22.1)
project(oengine_mojo_awt C)

if(NOT DEFINED MOJO_JNI_ROOT OR NOT IS_DIRECTORY "${MOJO_JNI_ROOT}")
    message(FATAL_ERROR "MOJO_JNI_ROOT must point at the pinned Mojo app_mojo/src/main/jni directory")
endif()
if(NOT DEFINED MOJOEXEC_LIBRARY OR NOT EXISTS "${MOJOEXEC_LIBRARY}")
    message(FATAL_ERROR "MOJOEXEC_LIBRARY must point at the already-built libmojoexec.so")
endif()
if(NOT DEFINED MOJOEXEC_INCLUDE_DIR OR NOT IS_DIRECTORY "${MOJOEXEC_INCLUDE_DIR}")
    message(FATAL_ERROR "MOJOEXEC_INCLUDE_DIR must point at the pinned MojoExec public headers")
endif()

add_library(mojoexec SHARED IMPORTED GLOBAL)
set_target_properties(mojoexec PROPERTIES IMPORTED_LOCATION "${MOJOEXEC_LIBRARY}")

add_library(mojort SHARED
    "${MOJO_JNI_ROOT}/jvm_hooks/emui_iterator_fix_hook.c"
    "${MOJO_JNI_ROOT}/jvm_hooks/java_exec_hooks.c"
    "${MOJO_JNI_ROOT}/jvm_hooks/lwjgl_dlopen_hook.c"
    "${MOJO_JNI_ROOT}/jre_launcher/jre_launcher.c"
    "${MOJO_JNI_ROOT}/jre_launcher/native_library_hook.c"
    "${MOJO_JNI_ROOT}/jre_launcher/elf_hinter.c"
    "${MOJO_JNI_ROOT}/jre_launcher/abort_wait.c"
    "${MOJO_JNI_ROOT}/utils.c"
    "${MOJO_JNI_ROOT}/stdio_is.c"
    "${MOJO_JNI_ROOT}/minibridge.c"
)
target_include_directories(mojort PRIVATE "${MOJO_JNI_ROOT}" "${MOJOEXEC_INCLUDE_DIR}")
target_link_libraries(mojort PRIVATE log mojoexec)

add_library(mojoawt SHARED
    "${MOJO_JNI_ROOT}/awt/awt.c"
    "${MOJO_JNI_ROOT}/awt/awt_window.c"
    "${MOJO_JNI_ROOT}/awt/awt_util.c"
    "${MOJO_JNI_ROOT}/awt/awt_input.c"
    "${MOJO_JNI_ROOT}/awt/awt_clipboard.c"
    "${MOJO_JNI_ROOT}/awt/awt_mapper.c"
)
target_include_directories(mojoawt PRIVATE "${MOJO_JNI_ROOT}")
target_link_libraries(mojoawt PRIVATE android log)

add_library(awt_headless SHARED "${MOJO_JNI_ROOT}/awt_xawt/dummy.c")
target_include_directories(awt_headless PRIVATE "${MOJO_JNI_ROOT}")

add_library(awt_xawt SHARED "${MOJO_JNI_ROOT}/awt_xawt/xawt_fake.c")
target_include_directories(awt_xawt PRIVATE "${MOJO_JNI_ROOT}")
target_link_libraries(awt_xawt PRIVATE awt_headless)
