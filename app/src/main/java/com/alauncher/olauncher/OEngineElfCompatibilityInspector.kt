package com.alauncher.olauncher

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONArray
import org.json.JSONObject

enum class OEngineElfCompatibility {
    INVALID_ELF,
    ABI_MISMATCH,
    LIKELY_GLIBC,
    ANDROID_COMPATIBILITY_UNVERIFIED
}

data class OEngineElfInspection(
    val file: File,
    val elfClassBits: Int?,
    val machine: Int?,
    val detectedAndroidAbi: String?,
    val expectedAndroidAbi: String,
    val compatibility: OEngineElfCompatibility,
    val glibcMarkers: List<String>,
    val explanation: String
)

/**
 * Conservative ELF triage for native archives. This does not certify a library as safe to load:
 * matching ELF machine and absence of obvious glibc markers are necessary clues, not proof of
 * Android/Bionic compatibility or transitive dependency availability.
 */
internal object OEngineElfCompatibilityInspector {
    private val glibcMarkers = listOf(
        "GLIBC_", "GLIBCXX_", "CXXABI_", "libc.so.6", "libpthread.so.0",
        "libdl.so.2", "librt.so.1", "ld-linux"
    )

    fun writeReport(root: File, expectedAndroidAbi: String, reportFile: File): Int {
        val files = root.walkTopDown().filter { it.isFile && (it.name.endsWith(".so") || Regex("""\.so\.[0-9.]+$""").containsMatchIn(it.name)) }.toList()
        val entries = JSONArray()
        files.forEach { file ->
            val result = inspect(file, expectedAndroidAbi)
            entries.put(JSONObject().apply {
                put("path", file.relativeTo(root).invariantSeparatorsPath)
                put("elfClassBits", result.elfClassBits ?: JSONObject.NULL)
                put("machine", result.machine ?: JSONObject.NULL)
                put("detectedAndroidAbi", result.detectedAndroidAbi ?: JSONObject.NULL)
                put("expectedAndroidAbi", result.expectedAndroidAbi)
                put("compatibility", result.compatibility.name)
                put("glibcMarkers", JSONArray(result.glibcMarkers))
                put("explanation", result.explanation)
            })
        }
        val report = JSONObject().apply {
            put("schemaVersion", 1)
            put("notice", "Diagnostic only. No entry is certified safe to load on Android.")
            put("expectedAndroidAbi", expectedAndroidAbi)
            put("files", entries)
        }
        val parent = reportFile.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw IllegalStateException("Could not create native compatibility report directory.")
        }
        reportFile.writeText(report.toString(2))
        return files.size
    }

    fun inspect(file: File, expectedAndroidAbi: String): OEngineElfInspection {
        fun invalid(reason: String) = OEngineElfInspection(
            file, null, null, null, expectedAndroidAbi,
            OEngineElfCompatibility.INVALID_ELF, emptyList(), reason
        )
        if (!file.isFile || file.length() < 20L || file.length() > Int.MAX_VALUE) {
            return invalid("File is missing, too small, or too large for safe ELF inspection.")
        }
        val bytes = try { file.readBytes() } catch (error: Exception) {
            return invalid("Could not read ELF file: " + (error.message ?: error.javaClass.simpleName))
        }
        if (bytes.size < 20 || bytes[0] != 0x7f.toByte() ||
            bytes[1] != 'E'.code.toByte() || bytes[2] != 'L'.code.toByte() ||
            bytes[3] != 'F'.code.toByte()
        ) return invalid("The file does not have a valid ELF magic header.")

        val elfClass = bytes[4].toInt() and 0xff
        val dataEncoding = bytes[5].toInt() and 0xff
        if (elfClass !in 1..2 || dataEncoding !in 1..2) return invalid("Unsupported ELF class or byte order.")
        val order = if (dataEncoding == 1) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN
        val header = ByteBuffer.wrap(bytes).order(order)
        val machine = header.getShort(18).toInt() and 0xffff
        val abi = when (machine) {
            183 -> "arm64-v8a"
            40 -> "armeabi-v7a"
            62 -> "x86_64"
            3 -> "x86"
            else -> null
        }
        val markers = glibcMarkers.filter { marker ->
            String(bytes, Charsets.ISO_8859_1).contains(marker)
        }
        val compatibility = when {
            abi == null -> OEngineElfCompatibility.ABI_MISMATCH
            abi != expectedAndroidAbi -> OEngineElfCompatibility.ABI_MISMATCH
            markers.isNotEmpty() -> OEngineElfCompatibility.LIKELY_GLIBC
            else -> OEngineElfCompatibility.ANDROID_COMPATIBILITY_UNVERIFIED
        }
        val explanation = when (compatibility) {
            OEngineElfCompatibility.ABI_MISMATCH ->
                "ELF machine does not match the requested Android ABI ($expectedAndroidAbi)."
            OEngineElfCompatibility.LIKELY_GLIBC ->
                "ELF architecture matches, but glibc/Linux markers were found: " + markers.joinToString()
            OEngineElfCompatibility.ANDROID_COMPATIBILITY_UNVERIFIED ->
                "ELF architecture matches and no common glibc markers were found; Android compatibility is still unverified."
            OEngineElfCompatibility.INVALID_ELF -> "Invalid ELF file."
        }
        return OEngineElfInspection(
            file, if (elfClass == 1) 32 else 64, machine, abi, expectedAndroidAbi,
            compatibility, markers, explanation
        )
    }
}
