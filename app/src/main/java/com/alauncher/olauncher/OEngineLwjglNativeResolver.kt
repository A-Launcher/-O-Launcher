package com.alauncher.olauncher

import java.io.File
import java.util.jar.JarFile

/** Identifies the LWJGL generation actually supplied by Mojang's version classpath. */
enum class OEngineLwjglGeneration { LWJGL2, LWJGL3, UNKNOWN }

/** A conservative description of the native-loader surface found in the selected LWJGL core JAR. */
enum class OEngineLwjglLoaderSurface {
    LEGACY_LWJGL2_SYSTEM_LOAD,
    UPSTREAM_LWJGL3_LINUX,
    UNKNOWN
}

data class OEngineLwjglModule(
    val artifact: String,
    val version: String,
    val jar: File
)

data class OEngineLwjglResolution(
    val generation: OEngineLwjglGeneration,
    val modules: List<OEngineLwjglModule>,
    val loaderSurface: OEngineLwjglLoaderSurface,
    val coreJar: File?,
    val dynamicLinkLoaderClassPresent: Boolean,
    val ndlopenSignaturePresent: Boolean,
    val blockers: List<String>,
    val warnings: List<String>
) {
    val hasSingleConsistentVersion: Boolean
        get() = modules.isNotEmpty() && modules.groupBy { it.artifact }.values.all { group ->
            group.map { it.version }.distinct().size == 1
        }
}

/**
 * Reads the actual Mojang classpath and inspects, but never loads, LWJGL classes.
 *
 * A bytecode marker confirms the upstream JNI surface only. O Engine installs its
 * separately implemented retained-handle hook; it does not load Mojo's patched hook.
 */
object OEngineLwjglNativeResolver {
    private val artifactPattern = Regex(
        """(?:^|/)org/lwjgl/(lwjgl(?:-[a-z0-9-]+)?)/([0-9][^/]+)/\1-\2\.jar$"""
    )
    private const val DYNAMIC_LOADER_CLASS = "org/lwjgl/system/linux/DynamicLinkLoader.class"
    private const val LWJGL2_SYS_CLASS = "org/lwjgl/Sys.class"

    fun resolve(classpath: List<File>): OEngineLwjglResolution {
        val blockers = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val modules = classpath.mapNotNull { file ->
            val normalized = file.absolutePath.replace('\\', '/')
            val match = artifactPattern.find(normalized) ?: return@mapNotNull null
            OEngineLwjglModule(match.groupValues[1], match.groupValues[2], file)
        }

        if (modules.isEmpty()) {
            blockers += "No Maven-layout LWJGL artifacts were found in the selected Minecraft classpath."
        }
        val generations = modules.mapNotNull { module ->
            module.version.substringBefore('.').toIntOrNull()?.let { major ->
                when (major) {
                    2 -> OEngineLwjglGeneration.LWJGL2
                    3 -> OEngineLwjglGeneration.LWJGL3
                    else -> OEngineLwjglGeneration.UNKNOWN
                }
            }
        }.distinct()
        val generation = when {
            generations.size > 1 -> OEngineLwjglGeneration.UNKNOWN
            generations.size == 1 -> generations.single()
            else -> OEngineLwjglGeneration.UNKNOWN
        }
        if (generations.size > 1) blockers += "The classpath mixes LWJGL major generations."
        if (modules.any { it.version.substringBefore('.').toIntOrNull() !in setOf(2, 3) }) {
            blockers += "The selected LWJGL major version has no O Engine compatibility profile."
        }
        modules.groupBy { it.artifact }.filterValues { it.map(OEngineLwjglModule::version).distinct().size > 1 }
            .forEach { (artifact, versions) ->
                blockers += "Multiple versions of $artifact are present: " +
                    versions.map { it.version }.distinct().joinToString()
            }

        val core = modules.filter { it.artifact == "lwjgl" }
        val coreJar = core.singleOrNull()?.jar
        if (core.size > 1) blockers += "The LWJGL core artifact is ambiguous."
        var dynamicLoaderPresent = false
        var ndlopenSignaturePresent = false
        var lwjgl2SysClassPresent = false
        var lwjgl2SystemLoadMarkerPresent = false
        if (coreJar != null && coreJar.isFile) {
            try {
                JarFile(coreJar).use { jar ->
                    val entry = jar.getJarEntry(DYNAMIC_LOADER_CLASS)
                    dynamicLoaderPresent = entry != null
                    if (entry != null) {
                        val bytes = jar.getInputStream(entry).use { it.readBytes() }
                        val constants = bytes.toString(Charsets.ISO_8859_1)
                        ndlopenSignaturePresent = constants.contains("ndlopen") && constants.contains("(JI)J")
                    }
                    val sysEntry = jar.getJarEntry(LWJGL2_SYS_CLASS)
                    lwjgl2SysClassPresent = sysEntry != null
                    if (sysEntry != null) {
                        val bytes = jar.getInputStream(sysEntry).use { it.readBytes() }
                        val constants = bytes.toString(Charsets.ISO_8859_1)
                        lwjgl2SystemLoadMarkerPresent =
                            constants.contains("org.lwjgl.librarypath") &&
                            (constants.contains("loadLibrary") || constants.contains("java/lang/System"))
                    }
                }
            } catch (error: Exception) {
                blockers += "Could not inspect the LWJGL core JAR: " + (error.message ?: error.javaClass.simpleName)
            }
        } else if (generation == OEngineLwjglGeneration.LWJGL3) {
            blockers += "LWJGL 3 core JAR is missing or ambiguous."
        }

        val surface = when {
            generation == OEngineLwjglGeneration.LWJGL2 -> OEngineLwjglLoaderSurface.LEGACY_LWJGL2
            generation == OEngineLwjglGeneration.LWJGL3 && dynamicLoaderPresent ->
                OEngineLwjglLoaderSurface.UPSTREAM_LWJGL3_LINUX
            else -> OEngineLwjglLoaderSurface.UNKNOWN
        }

        if (dynamicLoaderPresent && !ndlopenSignaturePresent) {
            blockers += "LWJGL DynamicLinkLoader is present but the expected ndlopen(long,int) JNI signature was not found."
        }

        when (surface) {
            OEngineLwjglLoaderSurface.LEGACY_LWJGL2 ->
                blockers += "LWJGL 2 uses a legacy native-loading path; no verified O Engine Android interception adapter exists."
            OEngineLwjglLoaderSurface.UPSTREAM_LWJGL3_LINUX -> {
                if (!dynamicLoaderPresent || !ndlopenSignaturePresent) {
                    blockers += "LWJGL 3 native-loader interception requires DynamicLinkLoader.ndlopen(long,int)."
                } else {
                    warnings += "O Engine can register its retained-renderer ndlopen hook after HotSpot creation and before Minecraft main."
                }
            }
            OEngineLwjglLoaderSurface.UNKNOWN ->
                blockers += "The LWJGL native-loader surface is unknown and cannot be safely intercepted."
        }
        if (generation == OEngineLwjglGeneration.LWJGL3 && !dynamicLoaderPresent) {
            warnings += "LWJGL 3 core JAR does not contain the expected Linux DynamicLinkLoader class."
        }

        return OEngineLwjglResolution(
            generation = generation,
            modules = modules,
            loaderSurface = surface,
            coreJar = coreJar,
            dynamicLinkLoaderClassPresent = dynamicLoaderPresent,
            ndlopenSignaturePresent = ndlopenSignaturePresent,
            blockers = blockers.distinct(),
            warnings = warnings.distinct()
        )
    }
}
