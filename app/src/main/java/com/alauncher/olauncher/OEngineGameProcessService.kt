package com.alauncher.olauncher

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.Build
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.view.Surface
import android.system.Os
import java.io.File

/**
 * IPC endpoint hosted in the isolated :game process.
 *
 * The service owns runtime linker diagnostics and the experimental HotSpot VM smoke test.
 * All JNI_CreateJavaVM work runs on its dedicated HandlerThread, away from the launcher UI process.
 */
class OEngineGameProcessService : Service() {
    override fun onCreate() {
        super.onCreate()
        OEngineSessionLog.append(this, "GAME_PROCESS", "Isolated game-process service created; pid=" + Process.myPid())
    }
    private var workerThread: HandlerThread? = null

    private var inputThread: HandlerThread? = null
    private var surfaceThread: HandlerThread? = null
    @Volatile private var inputResetWasLogged = false
    /** A separate Binder endpoint remains responsive while the JVM owner thread is inside Minecraft main. */
    private val inputMessenger: Messenger by lazy {
        val thread = HandlerThread("OEngineInputIngress").apply { start() }
        inputThread = thread
        Messenger(Handler(thread.looper) { message ->
            if (message.what != MSG_INPUT_BATCH) return@Handler false
            try {
                val data = message.data
                val devices = data.getIntArray(KEY_INPUT_DEVICE) ?: error("Missing input device array.")
                val actions = data.getIntArray(KEY_INPUT_ACTION) ?: error("Missing input action array.")
                val codes = data.getIntArray(KEY_INPUT_CODE) ?: error("Missing input code array.")
                val scanCodes = data.getIntArray(KEY_INPUT_SCAN_CODE) ?: error("Missing input scancode array.")
                val xs = data.getFloatArray(KEY_INPUT_X) ?: error("Missing input X array.")
                val ys = data.getFloatArray(KEY_INPUT_Y) ?: error("Missing input Y array.")
                val values = data.getFloatArray(KEY_INPUT_VALUE) ?: error("Missing input value array.")
                val times = data.getLongArray(KEY_INPUT_TIME) ?: error("Missing input timestamp array.")
                val modifiers = data.getIntArray(KEY_INPUT_MODIFIERS) ?: error("Missing input modifiers array.")
                val codepoints = data.getIntArray(KEY_INPUT_CODEPOINT) ?: error("Missing input codepoint array.")
                val texts = data.getStringArray(KEY_INPUT_TEXT)
                val count = devices.size
                require(count in 1..MAX_INPUT_BATCH && listOf(actions.size, codes.size, scanCodes.size, xs.size, ys.size,
                    values.size, times.size, modifiers.size, codepoints.size).all { it == count } &&
                    (texts == null || texts.size == count)) { "Input batch arrays have inconsistent lengths or exceed the batch limit." }
                val events = ArrayList<OEngineInputEvent>(count)
                for (index in 0 until count) {
                    val device = OEngineInputDevice.values().getOrNull(devices[index])
                        ?: error("Input batch contains an unknown device.")
                    val action = OEngineInputAction.values().getOrNull(actions[index])
                        ?: error("Input batch contains an unknown action.")
                    events += OEngineInputEvent(
                        device = device, action = action, code = codes[index], scanCode = scanCodes[index],
                        x = xs[index], y = ys[index], value = values[index],
                        timestampNanos = times[index], modifiers = modifiers[index],
                        codepoint = codepoints[index], text = texts?.get(index)
                    )
                }
                val accepted = OEngineJvmNativeBridge.enqueueInputEvents(events)
                require(accepted >= 0) { "The Rust native input queue rejected the event batch." }
                val resetPending = OEngineJvmNativeBridge.inputResetPending()
                if (resetPending && !inputResetWasLogged) {
                    inputResetWasLogged = true
                    OEngineSessionLog.append(this, "INPUT_QUEUE_RESET_REQUIRED",
                        "Native input queue overflowed with digital transitions pending; the GLFW game-thread drain will synthesize all-controls-up.")
                } else if (!resetPending) {
                    inputResetWasLogged = false
                }
                if (accepted < events.size) {
                    OEngineSessionLog.append(this, "INPUT_BATCH_BACKPRESSURE",
                        "Native input queue accepted " + accepted + " of " + events.size + " events.")
                }
            } catch (error: Throwable) {
                OEngineSessionLog.append(this, "INPUT_BATCH_REJECTED",
                    error.message ?: error.javaClass.simpleName, error)
            }
            true
        })
    }
    @Volatile private var jvmCreationAttempted = false
    @Volatile private var jvmCreated = false
    @Volatile private var rendererHandle: Long = 0L
    @Volatile private var rendererLibraryName: String? = null
    @Volatile private var androidGlfwLibraryFile: File? = null
    @Volatile private var rendererHookInstalled = false
    @Volatile private var eglBootstrap: OEngineEglBootstrap? = null
    /** Surface IPC remains responsive while the JVM owner thread is executing Minecraft main. */
    private val surfaceMessenger: Messenger by lazy {
        val thread = HandlerThread("OEngineSurfaceIngress").apply { start() }
        surfaceThread = thread
        Messenger(Handler(thread.looper) { message ->
            when (message.what) {
                MSG_ATTACH_GAME_SURFACE -> handleQueuedSurfaceAttach(message)
                MSG_DETACH_GAME_SURFACE -> {
                    try {
                        OEngineAndroidGlfwBootstrap.surfaceDestroyed()
                        (eglBootstrap ?: error("LTW EGL session is unavailable.")).queueDetachWindow()
                        OEngineAndroidGlfwBootstrap.surfaceUpdated() // Wake GLFW after the detach event is in the native queue.
                        OEngineSessionLog.append(this, "GAME_SURFACE_DETACH_QUEUED",
                            "Surface detach queued for the GLFW/LTW owner thread.")
                    } catch (error: Throwable) {
                        OEngineSessionLog.append(this, "GAME_SURFACE_DETACH_QUEUE_FAILED",
                            error.message ?: error.javaClass.simpleName, error)
                    }
                    true
                }
                else -> false
            }
        })
    }

    private fun handleQueuedSurfaceAttach(message: Message): Boolean {
        val reply = Message.obtain(null, MSG_GAME_SURFACE_RESULT)
        reply.data.putLong(KEY_SURFACE_REQUEST_ID, message.data.getLong(KEY_SURFACE_REQUEST_ID))
        var surface: Surface? = null
        var candidateWindow = 0L
        var queuedWindow = false
        try {
            @Suppress("DEPRECATION")
            val incomingSurface = message.data.getParcelable<Surface>(KEY_GAME_SURFACE)
                ?: throw IllegalArgumentException("Game Surface IPC payload is missing.")
            surface = incomingSurface
            require(incomingSurface.isValid) { "The replacement Android Surface is invalid." }
            val width = message.data.getInt(KEY_SURFACE_WIDTH).coerceAtLeast(1)
            val height = message.data.getInt(KEY_SURFACE_HEIGHT).coerceAtLeast(1)
            val egl = eglBootstrap ?: throw IllegalStateException("LTW EGL session is not initialized.")
            candidateWindow = OEngineJvmNativeBridge.acquireNativeWindow(incomingSurface, width, height)
            egl.queueWindow(candidateWindow, width, height)
            queuedWindow = true
            candidateWindow = 0L // Ownership transferred to Rust's pending-surface queue.
            OEngineAndroidGlfwBootstrap.surfaceCreated(incomingSurface)
            incomingSurface.release()
            surface = null
            reply.data.putBoolean(KEY_PROBE_SUCCESS, true)
            reply.data.putString(KEY_PROBE_STATUS, "game_surface_change_queued")
            reply.data.putString(KEY_PROBE_DETAIL,
                "Surface replacement/resize queued; LTW EGL will apply it on the GLFW event thread.")
            OEngineSessionLog.append(this, "GAME_SURFACE_CHANGE_QUEUED",
                "Replacement Surface queued for GLFW owner-thread application • " + width + "x" + height)
        } catch (error: Throwable) {
            if (candidateWindow != 0L) runCatching { OEngineJvmNativeBridge.releaseNativeWindow(candidateWindow) }
            if (queuedWindow) {
                runCatching { eglBootstrap?.queueDetachWindow() }
                runCatching { OEngineAndroidGlfwBootstrap.surfaceUpdated() }
            }
            runCatching { OEngineAndroidGlfwBootstrap.surfaceDestroyed() }
            surface?.release()
            reply.data.putBoolean(KEY_PROBE_SUCCESS, false)
            reply.data.putString(KEY_PROBE_STATUS, "game_surface_queue_error")
            reply.data.putString(KEY_PROBE_DETAIL, error.message ?: error.javaClass.simpleName)
            OEngineSessionLog.append(this, "GAME_SURFACE_CHANGE_QUEUE_FAILED",
                error.message ?: error.javaClass.simpleName, error)
        }
        runCatching { message.replyTo?.send(reply) }
        return true
    }

    private val messenger by lazy {
        val thread = HandlerThread("OEngineGameProcess").apply { start() }
        workerThread = thread
        Messenger(Handler(thread.looper) { message ->
            when (message.what) {
                MSG_ATTACH_GAME_SURFACE -> {
                    val reply = Message.obtain(null, MSG_GAME_SURFACE_RESULT)
                    reply.data.putLong(KEY_SURFACE_REQUEST_ID, message.data.getLong(KEY_SURFACE_REQUEST_ID))
                    var receivedSurface: Surface? = null
                    var candidateNativeWindow = 0L
                    try {
                        @Suppress("DEPRECATION")
                        val surface = message.data.getParcelable<Surface>(KEY_GAME_SURFACE)
                            ?: throw IllegalArgumentException("Game Surface IPC payload is missing.")
                        receivedSurface = surface
                        val egl = eglBootstrap ?: throw IllegalStateException("LTW EGL session is not initialized yet.")
                        val width = message.data.getInt(KEY_SURFACE_WIDTH).coerceAtLeast(1)
                        val height = message.data.getInt(KEY_SURFACE_HEIGHT).coerceAtLeast(1)
                        candidateNativeWindow = OEngineJvmNativeBridge.acquireNativeWindow(surface, width, height)
                        OEngineAndroidGlfwBootstrap.surfaceCreated(surface)
                        egl.attachWindow(candidateNativeWindow, width, height)
                        candidateNativeWindow = 0L // LTW session now owns and releases this ANativeWindow reference.
                        surface.release()
                        receivedSurface = null
                        OEngineSessionLog.append(this, "GAME_SURFACE_ATTACHED", "LTW-tracked EGL window surface and ANativeWindow reference ready • " + width + "x" + height)
                        reply.data.putBoolean(KEY_PROBE_SUCCESS, true)
                        reply.data.putString(KEY_PROBE_STATUS, "game_surface_attached")
                        reply.data.putString(KEY_PROBE_DETAIL, "Android Surface attached through LTW eglMakeCurrent; LTW context tracking is active.")
                    } catch (error: Throwable) {
                        if (candidateNativeWindow != 0L) runCatching { OEngineJvmNativeBridge.releaseNativeWindow(candidateNativeWindow) }
                        else runCatching { eglBootstrap?.detachWindow() }
                        runCatching { OEngineAndroidGlfwBootstrap.surfaceDestroyed() }
                        receivedSurface?.release()
                        OEngineSessionLog.append(this, "GAME_SURFACE_ATTACH_FAILED", error.message ?: error.javaClass.simpleName, error)
                        reply.data.putBoolean(KEY_PROBE_SUCCESS, false)
                        reply.data.putString(KEY_PROBE_STATUS, "game_surface_error")
                        reply.data.putString(KEY_PROBE_DETAIL, error.message ?: error.javaClass.simpleName)
                    }
                    runCatching { message.replyTo?.send(reply) }
                    true
                }
                MSG_DETACH_GAME_SURFACE -> {
                    try {
                        OEngineAndroidGlfwBootstrap.surfaceDestroyed()
                        eglBootstrap?.detachWindow()
                        OEngineSessionLog.append(this, "GAME_SURFACE_DETACHED", "EGL returned to pbuffer; ANativeWindow reference released")
                    } catch (error: Throwable) {
                        OEngineSessionLog.append(this, "GAME_SURFACE_DETACH_FAILED", error.message ?: error.javaClass.simpleName, error)
                    }
                    true
                }
                MSG_HEALTH_CHECK -> {
                    OEngineSessionLog.append(this, "IPC_HEALTH", "Health check received")
                    val reply = Message.obtain(null, MSG_HEALTH_CHECK_RESULT).apply {
                        data.putBoolean(KEY_READY, true)
                        data.putInt(KEY_PID, Process.myPid())
                        data.putString(KEY_PROCESS, packageName + ":game")
                        data.putString(KEY_PHASE, "process_ready")
                    }
                    reply.data.putBinder(KEY_INPUT_MESSENGER, inputMessenger.binder)
                    reply.data.putBinder(KEY_SURFACE_MESSENGER, surfaceMessenger.binder)

                    runCatching { message.replyTo?.send(reply) }
                    true
                }
                MSG_PREPARE_RENDERER -> {
                    OEngineSessionLog.append(this, "RENDERER_PRELOAD", "Renderer preload requested")
                    val reply = Message.obtain(null, MSG_PREPARE_RENDERER_RESULT)
                    try {
                        check(!jvmCreationAttempted) { "Renderer and driver environment must be prepared before JVM creation." }
                        val rendererFile = File(message.data.getString(KEY_RENDERER_FILE).orEmpty())
                        val rendererRoot = File(message.data.getString(KEY_RENDERER_ROOT).orEmpty())
                        require(rendererFile.name == "libltw.so") {
                            "Only LTW currently has a verified O Engine/LWJGL 3 preload route. GL4ES is staged for future LWJGL 2 integration and must not enter the LTW hook."
                        }
                        rendererLibraryName = rendererFile.name
                        val eglPath = message.data.getString(KEY_RENDERER_EGL).orEmpty()
                        val glesPath = message.data.getString(KEY_RENDERER_GLES).orEmpty()
                        fun applyDriverOverride(key: String, value: String) {
                            if (value.isBlank()) return
                            require(value == "libEGL.so" && key == "LIBGL_EGL" ||
                                value == "libGLESv2.so" && key == "LIBGL_GLES" ||
                                (File(value).isAbsolute && File(value).isFile && File(value).length() > 0L)) {
                                "Invalid $key renderer driver override."
                            }
                            Os.setenv(key, value, true)
                        }
                        applyDriverOverride("LIBGL_EGL", eglPath)
                        applyDriverOverride("LIBGL_GLES", glesPath)
                        val ltwEnvironment = OEngineGraphicsStack.ltwEnvironment()
                        ltwEnvironment.forEach { (key, value) -> Os.setenv(key, value, true) }
                        OEngineSessionLog.append(
                            this,
                            "RENDERER_ENVIRONMENT",
                            "Applied " + ltwEnvironment.size + " Mojo-informed LTW compatibility variables before HotSpot: " +
                                ltwEnvironment.keys.sorted().joinToString()
                        )
                        check(eglBootstrap == null) { "The LTW EGL session is already initialized." }
                        // LTW must be loaded first: its EGL wrappers register the context
                        // in the compatibility layer's context map.
                        val androidAbi = Build.SUPPORTED_ABIS.firstOrNull { it in setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64") }
                            ?: throw IllegalStateException("No supported Android ABI is available for the GLFW platform backend.")
                        val androidGlfw = OEngineAndroidGlfwArtifactInstaller.install(
                            sourceDirectory = File(applicationInfo.nativeLibraryDir),
                            destinationRoot = File(filesDir, "oengine/android-glfw"),
                            abi = androidAbi
                        )
                        androidGlfwLibraryFile = androidGlfw.libraries.single { it.name == "libglfw.so" }.canonicalFile
                        OEngineAndroidGlfwBootstrap.initialize(androidGlfwLibraryFile!!)
                        OEngineSessionLog.append(
                            this,
                            "ANDROID_GLFW_ARTIFACTS_STAGED",
                            "abi=" + androidGlfw.abi + ", libraries=" +
                                androidGlfw.libraries.joinToString { it.name + ":" + androidGlfw.sha256ByLibrary.getValue(it.name).take(12) } +
                                ", manifest=" + androidGlfw.manifest.absolutePath +
                                "; absolute LWJGL library path will be supplied before HotSpot creation; EGL/window behavior remains device-unverified."
                        )
                        rendererHandle = OEngineJvmNativeBridge.preloadRenderer(rendererFile, rendererRoot)
                        eglBootstrap = OEngineEglBootstrap.create(rendererHandle, OEngineGraphicsStack.LTW_MIN_GLES_MAJOR).also { egl ->
                            OEngineSessionLog.append(
                                this,
                                "EGL_BOOTSTRAP_READY",
                                "LTW-tracked EGL context current on game worker thread • " + egl.eglVersion +
                                    " • GLES " + egl.glesMajor + " • " + egl.glVersion + " • " + egl.glRenderer
                            )
                        }
                        OEngineSessionLog.append(
                            this,
                            "RENDERER_PRELOADED",
                            "provider=LTW, library=" + rendererFile.name + ", handle_retained=" + (rendererHandle != 0L)
                        )
                        reply.data.putBoolean(KEY_PROBE_SUCCESS, rendererHandle != 0L)
                        reply.data.putString(KEY_PROBE_STATUS, "renderer_preloaded")
                        reply.data.putString(KEY_PROBE_DETAIL, "Renderer retained in :game before HotSpot creation; LWJGL hook will be installed after VM creation.")
                    } catch (error: Throwable) {
                        OEngineSessionLog.append(this, "RENDERER_PRELOAD_FAILED", error.message ?: error.javaClass.simpleName, error)
                        reply.data.putBoolean(KEY_PROBE_SUCCESS, false)
                        reply.data.putString(KEY_PROBE_STATUS, "renderer_preload_error")
                        reply.data.putString(KEY_PROBE_DETAIL, error.message ?: error.javaClass.simpleName)
                    }
                    runCatching { message.replyTo?.send(reply) }
                    true
                }
                MSG_INSTALL_RENDERER_HOOK -> {
                    OEngineSessionLog.append(this, "RENDERER_HOOK", "LWJGL native-loader hook installation requested")
                    val reply = Message.obtain(null, MSG_INSTALL_RENDERER_HOOK_RESULT)
                    try {
                        check(jvmCreated) { "HotSpot must be created before installing a renderer hook." }
                        check(rendererHandle != 0L) { "Renderer must be preloaded before JVM creation." }
                        val targetClass = message.data.getString(KEY_LWJGL_TARGET_CLASS).orEmpty()
                        require(targetClass == OEngineLwjglNativeResolver.DYNAMIC_LINK_LOADER_CLASS_NAME) {
                            "LTW hook target must be the exact LWJGL DynamicLinkLoader class."
                        }
                        val alias = message.data.getString(KEY_RENDERER_ALIAS).orEmpty()
                        require(alias == OEngineGraphicsStack.LTW_LWJGL_LIBRARY_ALIAS) {
                            "The selected renderer must use the verified libGLMojo.so LWJGL alias."
                        }
                        check(rendererLibraryName == "libltw.so") {
                            "The libGLMojo.so hook is currently verified only for LTW; GL4ES requires a separate LWJGL 2 adapter."
                        }
                        val installed = OEngineJvmNativeBridge.installLwjglHook(
                            targetClass, alias, rendererHandle
                        )
                        check(installed) { "HotSpot rejected the LWJGL native-loader hook." }
                        rendererHookInstalled = true
                        OEngineSessionLog.append(
                            this,
                            "RENDERER_HOOK_INSTALLED",
                            "target=" + targetClass + ", alias=" + alias + ", provider=LTW"
                        )
                        reply.data.putBoolean(KEY_PROBE_SUCCESS, true)
                        reply.data.putString(KEY_PROBE_STATUS, "renderer_hook_installed")
                        reply.data.putString(KEY_PROBE_DETAIL, "Renderer loaded and LWJGL interception registered in :game.")
                    } catch (error: Throwable) {
                        OEngineSessionLog.append(this, "RENDERER_HOOK_FAILED", error.message ?: error.javaClass.simpleName, error)
                        reply.data.putBoolean(KEY_PROBE_SUCCESS, false)
                        reply.data.putString(KEY_PROBE_STATUS, "renderer_hook_error")
                        reply.data.putString(KEY_PROBE_DETAIL, error.message ?: error.javaClass.simpleName)
                    }
                    runCatching { message.replyTo?.send(reply) }
                    true
                }
                MSG_INVOKE_MAIN -> {
                    OEngineSessionLog.append(this, "MINECRAFT_MAIN_REQUEST", "Main invocation received")
                    val reply = Message.obtain(null, MSG_INVOKE_MAIN_RESULT)
                    try {
                        val mainClass = message.data.getString(KEY_MAIN_CLASS).orEmpty()
                        val arguments = message.data.getStringArray(KEY_GAME_ARGUMENTS)?.toList().orEmpty()
                        require(mainClass.isNotBlank()) { "Minecraft main class is empty." }
                        check(jvmCreated) { "HotSpot has not been created successfully in the isolated game process." }
                        check(rendererHookInstalled) { "A renderer must be preloaded and its LWJGL hook installed before Minecraft main can run." }
                        require(arguments.size <= 16_384 && arguments.all { it.length <= 65_536 && '\u0000' !in it }) {
                            "Minecraft game arguments exceed the IPC safety limits."
                        }
                        val accessTokenFlag = arguments.indexOfFirst { it == "--accessToken" || it == "--access-token" }
                        require(accessTokenFlag >= 0 && accessTokenFlag + 1 < arguments.size) {
                            "Minecraft launch arguments are missing the access-token option."
                        }
                        val minecraftAccessToken = arguments[accessTokenFlag + 1]
                        require(minecraftAccessToken.isNotBlank() && minecraftAccessToken != "0") {
                            "A placeholder access token cannot be used for an authenticated Minecraft launch."
                        }
                        val started = Message.obtain(null, MSG_INVOKE_MAIN_STARTED).apply {
                            data.putBoolean(KEY_PROBE_SUCCESS, true)
                            data.putString(KEY_PROBE_STATUS, "main_dispatched")
                            data.putString(KEY_PROBE_DETAIL, "Validated launch arguments; dispatching $mainClass on the isolated HotSpot runtime.")
                        }
                        OEngineSessionLog.append(this, "MINECRAFT_MAIN_DISPATCH", "Dispatching main class " + mainClass + " with " + arguments.size + " arguments; credential values omitted")
                        message.replyTo?.send(started)
                        val result = OEngineJvmNativeBridge.invokeMain(mainClass, arguments)
                        OEngineSessionLog.append(this, if (result.successful) "MINECRAFT_MAIN_RETURNED" else "MINECRAFT_MAIN_FAILED", result.status + " • " + result.detail)
                        reply.data.putBoolean(KEY_PROBE_SUCCESS, result.successful)
                        reply.data.putString(KEY_PROBE_STATUS, result.status)
                        reply.data.putString(KEY_PROBE_DETAIL, result.detail)
                    } catch (error: Throwable) {
                        OEngineSessionLog.append(this, "MINECRAFT_MAIN_EXCEPTION", error.message ?: error.javaClass.simpleName, error)
                        reply.data.putBoolean(KEY_PROBE_SUCCESS, false)
                        reply.data.putString(KEY_PROBE_STATUS, "main_invoke_error")
                        reply.data.putString(KEY_PROBE_DETAIL, error.message ?: error.javaClass.simpleName)
                    }
                    runCatching { message.replyTo?.send(reply) }
                    true
                }
                MSG_CREATE_JVM -> {
                    OEngineSessionLog.append(this, "HOTSPOT_CREATE", "JNI_CreateJavaVM request received")
                    val reply = Message.obtain(null, MSG_CREATE_JVM_RESULT)
                    try {
                        val runtimeHome = File(message.data.getString(KEY_RUNTIME_HOME).orEmpty()).canonicalFile
                        val workingDirectory = File(message.data.getString(KEY_WORKING_DIRECTORY).orEmpty()).canonicalFile
                        val libraryPath = File(message.data.getString(KEY_JVM_LIBRARY).orEmpty()).canonicalFile
                        val requestedOptions = message.data.getStringArray(KEY_VM_OPTIONS)?.toList().orEmpty()
                        val javaMajor = message.data.getInt(KEY_JAVA_MAJOR)
                        require(runtimeHome.isDirectory) { "Selected Java runtime directory is missing." }
                        require(workingDirectory.isDirectory) { "Selected game working directory is missing." }
                        require(libraryPath.name == "libjvm.so" && libraryPath.toPath().startsWith(runtimeHome.toPath())) {
                            "The JVM library must be inside the selected runtime directory."
                        }
                        require(requestedOptions.isNotEmpty() && requestedOptions.size <= 512) { "JVM options are empty or exceed the safety limit." }
                        check(rendererHandle != 0L) { "A renderer must be preloaded before HotSpot creation." }
                        check(rendererLibraryName == "libltw.so") {
                            "This JVM bootstrap profile is LTW-only; GL4ES cannot use the LTW LWJGL alias."
                        }
                        require(requestedOptions.contains("-Dorg.lwjgl.opengl.libname=${OEngineGraphicsStack.LTW_LWJGL_LIBRARY_ALIAS}")) {
                            "The JVM must use the libGLMojo.so LWJGL alias for the staged renderer."
                        }
                        val glfwLibrary = androidGlfwLibraryFile?.canonicalFile
                            ?: throw IllegalStateException("Android GLFW was not staged before HotSpot creation.")
                        require(glfwLibrary.name == "libglfw.so" && glfwLibrary.isFile && glfwLibrary.length() > 0L) {
                            "The staged Android GLFW native library is missing."
                        }
                        val expectedGlfwOption = "-Dorg.lwjgl.glfw.libname=${glfwLibrary.absolutePath}"
                        val suppliedGlfwOptions = requestedOptions.filter { it.startsWith("-Dorg.lwjgl.glfw.libname=") }
                        require(suppliedGlfwOptions.size == 1 && suppliedGlfwOptions.single() == expectedGlfwOption) {
                            "The JVM must use the exact staged libglfw.so absolute path through org.lwjgl.glfw.libname."
                        }
                        val options = requestedOptions
                        require(javaMajor in setOf(8, 17, 21)) { "Java $javaMajor has no verified native-library hook profile." }
                        val nativeDir = File(applicationInfo.nativeLibraryDir).canonicalFile
                        val searchPaths = listOf(
                            File(runtimeHome, "lib"),
                            File(runtimeHome, "lib/server"),
                            File(runtimeHome, "lib/jli"),
                            nativeDir,
                            glfwLibrary.parentFile
                        ).filter { it.isDirectory }.map { it.absolutePath }
                        val existing = System.getenv("LD_LIBRARY_PATH").orEmpty()
                        val ldPath = (searchPaths + existing.split(File.pathSeparator).filter { it.isNotBlank() })
                            .distinct().joinToString(File.pathSeparator)
                        Os.setenv("JAVA_HOME", runtimeHome.absolutePath, true)
                        Os.setenv("HOME", workingDirectory.absolutePath, true)
                        Os.setenv("TMPDIR", File(workingDirectory, "tmp").apply { mkdirs() }.absolutePath, true)
                        Os.setenv("LD_LIBRARY_PATH", ldPath, true)
                        val probe = OEngineJvmNativeBridge.probeLibrary(libraryPath.absolutePath, runtimeHome)
                        require(probe.successful) { probe.status + ": " + probe.detail }
                        jvmCreationAttempted = true
                        val result = OEngineJvmNativeBridge.createJvm(options, javaMajor)
                        jvmCreated = result.successful
                        OEngineSessionLog.append(this, if (result.successful) "HOTSPOT_CREATED" else "HOTSPOT_CREATE_FAILED", result.status + " • " + result.detail)
                        reply.data.putBoolean(KEY_PROBE_SUCCESS, result.successful)
                        reply.data.putString(KEY_PROBE_STATUS, result.status)
                        reply.data.putString(KEY_PROBE_DETAIL, result.detail)
                    } catch (error: LinkageError) {
                        OEngineSessionLog.append(this, "HOTSPOT_LINK_ERROR", error.message ?: error.javaClass.simpleName, error)
                        reply.data.putBoolean(KEY_PROBE_SUCCESS, false)
                        reply.data.putString(KEY_PROBE_STATUS, "native_link_error")
                        reply.data.putString(KEY_PROBE_DETAIL, error.message ?: error.javaClass.simpleName)
                    } catch (error: Exception) {
                        OEngineSessionLog.append(this, "HOTSPOT_CREATE_EXCEPTION", error.message ?: error.javaClass.simpleName, error)
                        reply.data.putBoolean(KEY_PROBE_SUCCESS, false)
                        reply.data.putString(KEY_PROBE_STATUS, "create_error")
                        reply.data.putString(KEY_PROBE_DETAIL, error.message ?: error.javaClass.simpleName)
                    }
                    runCatching { message.replyTo?.send(reply) }
                    true
                }
                MSG_PROBE_JVM_LIBRARY -> {
                    val reply = Message.obtain(null, MSG_PROBE_JVM_LIBRARY_RESULT)
                    try {
                        val runtimeHome = File(message.data.getString(KEY_RUNTIME_HOME).orEmpty()).canonicalFile
                        val workingDirectory = File(message.data.getString(KEY_WORKING_DIRECTORY).orEmpty()).canonicalFile
                        val libraryPath = File(message.data.getString(KEY_JVM_LIBRARY).orEmpty()).canonicalFile
                        require(runtimeHome.isDirectory) { "Selected Java runtime directory is missing." }
                        require(workingDirectory.isDirectory) { "Selected game working directory is missing." }
                        require(libraryPath.name == "libjvm.so" && libraryPath.toPath().startsWith(runtimeHome.toPath())) {
                            "The JVM library must be inside the selected runtime directory."
                        }
                        val nativeDir = File(applicationInfo.nativeLibraryDir).canonicalFile
                        val searchPaths = listOf(
                            File(runtimeHome, "lib"),
                            File(runtimeHome, "lib/server"),
                            File(runtimeHome, "lib/jli"),
                            nativeDir
                        ).filter { it.isDirectory }.map { it.absolutePath }
                        val existing = System.getenv("LD_LIBRARY_PATH").orEmpty()
                        val ldPath = (searchPaths + existing.split(File.pathSeparator).filter { it.isNotBlank() })
                            .distinct().joinToString(File.pathSeparator)
                        Os.setenv("JAVA_HOME", runtimeHome.absolutePath, true)
                        Os.setenv("HOME", workingDirectory.absolutePath, true)
                        Os.setenv("TMPDIR", File(workingDirectory, "tmp").apply { mkdirs() }.absolutePath, true)
                        Os.setenv("LD_LIBRARY_PATH", ldPath, true)
                        val probe = OEngineJvmNativeBridge.probeLibrary(libraryPath.absolutePath, runtimeHome)
                        reply.data.putBoolean(KEY_PROBE_SUCCESS, probe.successful)
                        reply.data.putString(KEY_PROBE_STATUS, probe.status)
                        reply.data.putString(KEY_PROBE_DETAIL, probe.detail)
                    } catch (error: Exception) {
                        reply.data.putBoolean(KEY_PROBE_SUCCESS, false)
                        reply.data.putString(KEY_PROBE_STATUS, "probe_error")
                        reply.data.putString(KEY_PROBE_DETAIL, error.message ?: error.javaClass.simpleName)
                    }
                    runCatching { message.replyTo?.send(reply) }
                    true
                }
                else -> false
            }
        })
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        OEngineSessionLog.append(this, "GAME_PROCESS_DESTROY", "Service destroyed; jvmCreated=" + jvmCreated + ", jvmCreationAttempted=" + jvmCreationAttempted)
        // JNI requires the VM-creating thread to remain attached until we implement a
        // coordinated DetachCurrentThread shutdown path.
        if (!jvmCreationAttempted) {
            val thread = workerThread
            if (thread != null) {
                Handler(thread.looper).post {
                    runCatching { eglBootstrap?.close() }
                        .onFailure { OEngineSessionLog.append(this, "EGL_BOOTSTRAP_RELEASE_FAILED", it.message ?: it.javaClass.simpleName, it) }
                    eglBootstrap = null
                    thread.quitSafely()
                }
            }
            workerThread = null
        }
        inputThread?.quitSafely()
        inputThread = null
        surfaceThread?.quitSafely()
        surfaceThread = null
        super.onDestroy()
    }

    companion object {
        const val MSG_HEALTH_CHECK = 1
        const val MSG_INPUT_BATCH = 17
        const val MAX_INPUT_BATCH = 128
        const val KEY_INPUT_MESSENGER = "oengine.input_messenger"
        const val KEY_SURFACE_MESSENGER = "oengine.surface_messenger"
        const val KEY_INPUT_DEVICE = "oengine.input_device"
        const val KEY_INPUT_ACTION = "oengine.input_action"
        const val KEY_INPUT_CODE = "oengine.input_code"
        const val KEY_INPUT_SCAN_CODE = "oengine.input_scan_code"
        const val KEY_INPUT_X = "oengine.input_x"
        const val KEY_INPUT_Y = "oengine.input_y"
        const val KEY_INPUT_VALUE = "oengine.input_value"
        const val KEY_INPUT_TIME = "oengine.input_time"
        const val KEY_INPUT_MODIFIERS = "oengine.input_modifiers"
        const val KEY_INPUT_CODEPOINT = "oengine.input_codepoint"
        const val KEY_INPUT_TEXT = "oengine.input_text"

        const val MSG_ATTACH_GAME_SURFACE = 14
        const val MSG_GAME_SURFACE_RESULT = 15
        const val MSG_DETACH_GAME_SURFACE = 16
        const val MSG_HEALTH_CHECK_RESULT = 2
        const val MSG_PROBE_JVM_LIBRARY = 3
        const val MSG_PROBE_JVM_LIBRARY_RESULT = 4
        const val MSG_CREATE_JVM = 5
        const val MSG_CREATE_JVM_RESULT = 6
        const val MSG_INVOKE_MAIN = 7
        const val MSG_INVOKE_MAIN_RESULT = 8
        const val MSG_INVOKE_MAIN_STARTED = 13
        const val MSG_PREPARE_RENDERER = 11
        const val MSG_PREPARE_RENDERER_RESULT = 12
        const val MSG_INSTALL_RENDERER_HOOK = 9
        const val MSG_INSTALL_RENDERER_HOOK_RESULT = 10
        const val KEY_VM_OPTIONS = "oengine.vm_options"
        const val KEY_JAVA_MAJOR = "oengine.java_major"
        const val KEY_MAIN_CLASS = "oengine.main_class"
        const val KEY_GAME_ARGUMENTS = "oengine.game_arguments"
        const val KEY_RENDERER_FILE = "oengine.renderer_file"
        const val KEY_RENDERER_ROOT = "oengine.renderer_root"
        const val KEY_LWJGL_TARGET_CLASS = "oengine.lwjgl_target_class"
        const val KEY_RENDERER_ALIAS = "oengine.renderer_alias"
        const val KEY_RENDERER_EGL = "oengine.renderer_egl"
        const val KEY_RENDERER_GLES = "oengine.renderer_gles"
        const val KEY_RUNTIME_HOME = "oengine.runtime_home"
        const val KEY_WORKING_DIRECTORY = "oengine.working_directory"
        const val KEY_JVM_LIBRARY = "oengine.jvm_library"
        const val KEY_PROBE_SUCCESS = "oengine.probe_success"
        const val KEY_PROBE_STATUS = "oengine.probe_status"
        const val KEY_PROBE_DETAIL = "oengine.probe_detail"
        const val KEY_READY = "oengine.ready"
        const val KEY_PID = "oengine.pid"
        const val KEY_PROCESS = "oengine.process"
        const val KEY_PHASE = "oengine.phase"
        const val KEY_GAME_SURFACE = "oengine.game_surface"
        const val KEY_SURFACE_WIDTH = "oengine.surface_width"
        const val KEY_SURFACE_HEIGHT = "oengine.surface_height"
        const val KEY_SURFACE_REQUEST_ID = "oengine.surface_request_id"
    }
}
