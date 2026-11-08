package com.alauncher.olauncher

import android.content.Context
import java.io.File
import java.util.jar.JarFile

/** Stages and validates the unmodified MojoLauncher AWT installer agent packaged in the APK. */
object OEngineInstallerAgent {
    private const val ASSET_NAME = "forge_installer.jar"
    private const val MAX_AGENT_BYTES = 16L * 1024L * 1024L
    private const val AGENT_CLASS = "git.artdeell.installer_agent.Agent"

    fun stage(context: Context, gameDirectory: File): File {
        val gameRoot = gameDirectory.canonicalFile
        val cacheRoot = File(gameRoot, "oengine/installers").canonicalFile
        require(cacheRoot.toPath().startsWith(gameRoot.toPath())) { "Unsafe installer cache root." }
        val agentDirectory = File(cacheRoot, "agent").canonicalFile
        require(agentDirectory.toPath().startsWith(cacheRoot.toPath())) { "Unsafe installer agent directory." }
        if (!agentDirectory.exists() && !agentDirectory.mkdirs()) {
            error("Could not create installer-agent cache.")
        }
        val target = File(agentDirectory, ASSET_NAME)
        if (target.isFile && target.length() in 1..MAX_AGENT_BYTES && isValidAgentJar(target)) {
            return target
        }
        val temporary = File(agentDirectory, "$ASSET_NAME.part")
        temporary.delete()
        try {
            context.assets.open(ASSET_NAME).use { input ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_AGENT_BYTES) { "Packaged installer agent is unexpectedly large." }
                        output.write(buffer, 0, count)
                    }
                }
            }
            require(temporary.length() > 0L && isValidAgentJar(temporary)) {
                "Packaged Mojo installer agent is invalid or missing its premain entry point."
            }
            require(agentDirectory.canonicalFile.toPath().startsWith(cacheRoot.toPath())) {
                "Installer-agent cache path changed during staging."
            }
            if (target.exists() && !target.delete()) error("Could not replace cached installer agent.")
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
            return target
        } catch (failure: Exception) {
            temporary.delete()
            throw failure
        }
    }

    fun isValidAgentJar(file: File): Boolean {
        if (!file.isFile || file.length() !in 1..MAX_AGENT_BYTES) return false
        return runCatching {
            JarFile(file).use { jar ->
                val attributes = jar.manifest?.mainAttributes ?: return@use false
                attributes.getValue("Premain-Class") == AGENT_CLASS &&
                    jar.getJarEntry(AGENT_CLASS.replace('.', '/') + ".class") != null
            }
        }.getOrDefault(false)
    }

    /**
     * Matches Mojo AWTActivity's real Intent contract: javaArgs contains JVM options only,
     * while the installer JAR is passed separately as modPath. AWTActivity reads Main-Class
     * and the class-file Java version from that JAR itself.
     */
    data class AwtInvocation(val javaArgs: List<String>, val installerPath: String)

    fun buildAwtInvocation(agent: File, installer: File, optiFine: Boolean = false): AwtInvocation {
        require(isValidAgentJar(agent)) { "Mojo installer agent is not staged or valid." }
        require(installer.isFile && installer.length() > 0L) { "Official loader installer JAR is missing." }
        val agentArgument = "-javaagent:${agent.absolutePath}" + if (optiFine) "=OF" else ""
        return AwtInvocation(listOf(agentArgument), installer.canonicalPath)
    }
}
