#include <jni.h>
#include <dlfcn.h>

#include <cstdio>
#include <cstring>
#include <mutex>
#include <string>

namespace {

std::mutex g_jvm_mutex;
void* g_jvm_handle = nullptr;
std::string g_jvm_path;

jstring makeResult(JNIEnv* env, const char* status, const char* detail) {
    char buffer[2048];
    std::snprintf(buffer, sizeof(buffer), "%s:%s", status, detail ? detail : "");
    return env->NewStringUTF(buffer);
}

}  // namespace

extern "C"
JNIEXPORT jstring JNICALL
Java_com_alauncher_olauncher_OEngineJvmNativeBridge_nativeProbeJvmLibrary(
    JNIEnv* env,
    jobject /* receiver */,
    jstring library_path) {
    if (library_path == nullptr) {
        return makeResult(env, "invalid_path", "libjvm.so path is null");
    }

    const char* path = env->GetStringUTFChars(library_path, nullptr);
    if (path == nullptr) {
        return makeResult(env, "jni_error", "could not read libjvm.so path");
    }

    if (path[0] != '/') {
        env->ReleaseStringUTFChars(library_path, path);
        return makeResult(env, "invalid_path", "libjvm.so path must be absolute");
    }

    std::lock_guard<std::mutex> lock(g_jvm_mutex);
    const std::string requested_path(path);
    if (g_jvm_handle != nullptr && g_jvm_path != requested_path) {
        env->ReleaseStringUTFChars(library_path, path);
        return makeResult(env, "runtime_already_loaded", "A different libjvm.so is already retained in this process");
    }

    void* handle = g_jvm_handle;
    if (handle == nullptr) {
        dlerror();
        handle = dlopen(path, RTLD_NOW | RTLD_LOCAL);
        if (handle == nullptr) {
            const char* error = dlerror();
            char detail[1536];
            std::snprintf(detail, sizeof(detail), "%s", error ? error : "dlopen failed without detail");
            env->ReleaseStringUTFChars(library_path, path);
            return makeResult(env, "load_error", detail);
        }
    }

    dlerror();
    void* create_vm = dlsym(handle, "JNI_CreateJavaVM");
    const char* symbol_error = dlerror();
    const bool has_create_vm = create_vm != nullptr && symbol_error == nullptr;

    dlerror();
    void* get_vms = dlsym(handle, "JNI_GetCreatedJavaVMs");
    const char* get_vms_error = dlerror();
    const bool has_get_vms = get_vms != nullptr && get_vms_error == nullptr;

    if (!has_create_vm || !has_get_vms) {
        if (g_jvm_handle == nullptr) dlclose(handle);
        env->ReleaseStringUTFChars(library_path, path);
        if (!has_create_vm) {
            return makeResult(env, "missing_symbol", "JNI_CreateJavaVM is not exported");
        }
        return makeResult(env, "missing_symbol", "JNI_GetCreatedJavaVMs is not exported");
    }

    // Keep the validated VM library mapped. A future bootstrap must use this exact
    // handle; unloading/reloading libjvm can invalidate runtime-owned native state.
    if (g_jvm_handle == nullptr) {
        g_jvm_handle = handle;
        g_jvm_path = requested_path;
    }
    env->ReleaseStringUTFChars(library_path, path);

    return makeResult(env, "symbols_ok", "JNI Invocation API exports validated; libjvm remains mapped for this process");
}
