package com.alauncher.olauncher

import org.json.JSONObject
import java.io.File

data class OEngineZinkDriverEnvironment(
    val zinkDriver: File,
    val vulkanIcdManifest: File,
    val vulkanDriver: File,
    val environment: Map<String, String>,
    val librarySearchPaths: List<File>
)

/**
 * Resolves a complete Mesa Zink + Vulkan ICD package before the game JVM is started.
 * It deliberately refuses partial packages: selecting Zink without both the Gallium
 * driver and a loadable Vulkan ICD is a predictable black-screen/crash condition.
 */
object OEngineGraphicsDriverEnvironment {
    fun prepareZink(
        rendererRoot: File,
        abi: String,
        zinkDriver: File = File(rendererRoot, "$abi/dri/zink_dri.so"),
        vulkanIcdManifest: File = File(rendererRoot, "$abi/vulkan/icd.d/oengine-vulkan.json")
    ): OEngineZinkDriverEnvironment {
        require(abi in setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")) {
            "Zink is not packaged for Android ABI $abi."
        }
        val root = rendererRoot.canonicalFile
        val zink = zinkDriver.canonicalFile
        val manifest = vulkanIcdManifest.canonicalFile
        require(zink.toPath().startsWith(root.toPath()) && zink.isFile && zink.length() > 0L) {
            "Zink Gallium driver zink_dri.so is missing from the selected ABI package."
        }
        require(manifest.toPath().startsWith(root.toPath()) && manifest.isFile && manifest.length() in 2..1024 * 1024) {
            "Zink Vulkan ICD manifest is missing or invalid."
        }
        val icd = JSONObject(manifest.readText()).optJSONObject("ICD")
            ?: throw IllegalStateException("Vulkan ICD manifest has no ICD object.")
        val libraryValue = icd.optString("library_path")
        require(libraryValue.isNotBlank()) { "Vulkan ICD manifest has no library_path." }
        val vulkanDriver = File(libraryValue).let { supplied ->
            (if (supplied.isAbsolute) supplied else File(manifest.parentFile, libraryValue)).canonicalFile
        }
        require(vulkanDriver.isFile && vulkanDriver.length() > 0L) {
            "The Vulkan ICD library referenced by the Zink manifest is missing."
        }
        val searchPaths = listOf(zink.parentFile, vulkanDriver.parentFile).distinct()
        return OEngineZinkDriverEnvironment(
            zinkDriver = zink,
            vulkanIcdManifest = manifest,
            vulkanDriver = vulkanDriver,
            environment = linkedMapOf(
                "MESA_LOADER_DRIVER_OVERRIDE" to "zink",
                "GALLIUM_DRIVER" to "zink",
                "VK_ICD_FILENAMES" to manifest.absolutePath
            ),
            librarySearchPaths = searchPaths
        )
    }
}
