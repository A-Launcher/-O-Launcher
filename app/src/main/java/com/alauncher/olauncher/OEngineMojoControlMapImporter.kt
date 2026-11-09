package com.alauncher.olauncher

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import android.view.KeyEvent

/**
 * Imports the portable, fixed-position subset of MojoLauncher control maps.
 * Drawer and joystick definitions are retained as opaque JSON snapshots. They
 * are not executable O Engine controls yet, but callers can persist them and
 * avoid silently destroying compound-control settings during an import.
 */
object OEngineMojoControlMapImporter {
    data class ImportResult(
        val controls: List<OEngineTouchControl>,
        val sourceVersion: Int,
        val skippedHiddenControls: Int,
        val preservedDrawerJson: List<String>,
        val preservedJoystickJson: List<String>
    ) {
        val unsupportedDrawers: Int get() = preservedDrawerJson.size
        val unsupportedJoysticks: Int get() = preservedJoystickJson.size
    }

    data class PreservedCompoundControls(
        val drawers: List<String>,
        val joysticks: List<String>
    )

    data class JoystickRuntimeConfig(
        val forwardLock: Boolean,
        val absolute: Boolean,
        val xDp: Float?,
        val yDp: Float?,
        val widthDp: Float,
        val heightDp: Float
    )

    /** Decode Mojo joystick behavior and fixed numeric geometry.
     * Dynamic expressions remain preserved but are not guessed or evaluated. */
    fun decodeJoystickRuntimeConfig(json: String): JoystickRuntimeConfig {
        require(json.length <= 65_536) { "Mojo joystick definition is too large." }
        val item = JSONObject(json)
        fun fixedPosition(field: String): Float? {
            val raw = item.optString(field, "")
            if (raw.isBlank()) return null
            return raw.toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f }
        }
        val width = item.optDouble("width", 150.0).toFloat()
        val height = item.optDouble("height", 150.0).toFloat()
        require(width.isFinite() && height.isFinite() && width in 48f..360f && height in 48f..360f) {
            "Mojo joystick dimensions are outside supported bounds."
        }
        return JoystickRuntimeConfig(
            forwardLock = item.optBoolean("forwardLock", false),
            absolute = item.optBoolean("absolute", false),
            xDp = fixedPosition("dynamicX"),
            yDp = fixedPosition("dynamicY"),
            widthDp = width,
            heightDp = height
        )
    }

    fun loadJoystickRuntimeConfigs(context: android.content.Context): List<JoystickRuntimeConfig> =
        loadPreservedCompoundControls(context).joysticks.map(::decodeJoystickRuntimeConfig)


    private const val PREFS = "o_engine_mojo_control_import"
    private const val COMPOUND_KEY = "preserved_compound_v1"

    /** Save executable simple buttons through the normal layout store and keep
     * unsupported Mojo definitions alongside them for a future lossless upgrade. */
    fun saveImport(context: android.content.Context, result: ImportResult) {
        val compound = encodePreservedCompoundControls(
            PreservedCompoundControls(result.preservedDrawerJson, result.preservedJoystickJson)
        )
        OEngineTouchControls.save(context, result.controls)
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putString(COMPOUND_KEY, compound).apply()
    }

    fun loadPreservedCompoundControls(context: android.content.Context): PreservedCompoundControls {
        val raw = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getString(COMPOUND_KEY, null) ?: return PreservedCompoundControls(emptyList(), emptyList())
        return decodePreservedCompoundControls(raw)
    }

    fun encodePreservedCompoundControls(value: PreservedCompoundControls): String {
        require(value.drawers.size <= 48 && value.joysticks.size <= 48)
        val root = JSONObject().put("format", "oengine-mojo-compound-preserved").put("version", 1)
        root.put("drawers", JSONArray().apply { value.drawers.forEach { put(JSONObject(it).toString()) } })
        root.put("joysticks", JSONArray().apply { value.joysticks.forEach { put(JSONObject(it).toString()) } })
        val encoded = root.toString()
        require(encoded.length <= 1_048_576) { "Preserved Mojo compound controls are too large." }
        return encoded
    }

    fun decodePreservedCompoundControls(json: String): PreservedCompoundControls {
        require(json.length <= 1_048_576) { "Preserved Mojo compound controls are too large." }
        val root = JSONObject(json)
        require(root.optString("format") == "oengine-mojo-compound-preserved" && root.optInt("version") == 1) {
            "Unsupported preserved Mojo compound-control format."
        }
        fun read(field: String): List<String> {
            val array = root.getJSONArray(field)
            require(array.length() <= 48) { "Too many preserved Mojo $field." }
            return (0 until array.length()).map { index ->
                val raw = array.getString(index)
                JSONObject(raw).toString()
            }
        }
        return PreservedCompoundControls(read("drawers"), read("joysticks"))
    }

    /**
     * Accepts Mojo's raw JSON format or its LayoutBitmaps ZIP container.
     * Bitmap assets are intentionally not discarded: maps referencing bitmap
     * controls are rejected until O Engine can render those assets.
     */
    fun importBytes(bytes: ByteArray, viewportWidthPx: Float, viewportHeightPx: Float, density: Float = 1f): ImportResult {
        require(bytes.isNotEmpty() && bytes.size <= 25 * 1024 * 1024) { "Mojo control map file size is invalid." }
        val json = if (bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var layout: ByteArray? = null
                var firstFileSeen = false
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    if (!firstFileSeen) {
                        firstFileSeen = true
                        // Mojo's LayoutBitmaps.storeZip writes layout.json before bitmap
                        // assets. Reject other first entries instead of draining an
                        // untrusted asset (which could be a highly-compressed ZIP bomb).
                        require(entry.name == "layout.json") {
                            "Mojo ZIP control map must place layout.json before bitmap assets."
                        }
                    }
                    if (entry.name == "layout.json") {
                        val out = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var total = 0
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= 1_048_576) { "Mojo layout.json is too large." }
                            out.write(buffer, 0, count)
                        }
                        layout = out.toByteArray()
                        break
                    }
                }
                requireNotNull(layout) { "Mojo ZIP control map does not contain layout.json." }
                    .toString(Charsets.UTF_8)
            }
        } else {
            bytes.toString(Charsets.UTF_8)
        }
        return importJson(json, viewportWidthPx, viewportHeightPx, density)
    }

    fun importJson(json: String, viewportWidthPx: Float, viewportHeightPx: Float, density: Float = 1f): ImportResult {
        require(json.length <= 1_048_576) { "Mojo control map JSON is too large." }
        require(viewportWidthPx.isFinite() && viewportWidthPx > 0f)
        require(viewportHeightPx.isFinite() && viewportHeightPx > 0f)
        require(density.isFinite() && density > 0f)

        val root = JSONObject(json)
        val version = root.optInt("version", -1)
        require(version in 0..9) { "Unsupported Mojo control-map version." }

        val source = root.optJSONArray("mControlDataList")
            ?: throw IllegalArgumentException("Mojo control map has no button list.")
        require(source.length() in 1..48) { "Mojo control map must contain 1–48 buttons." }

        val result = ArrayList<OEngineTouchControl>(source.length())
        var hidden = 0
        for (index in 0 until source.length()) {
            val item = source.optJSONObject(index)
                ?: throw IllegalArgumentException("Invalid Mojo button at index $index.")
            if (!item.optBoolean("displayInGame", true)) {
                hidden++
                continue
            }
            val x = numericPosition(item, "dynamicX", index)
            val y = numericPosition(item, "dynamicY", index)
            val keys = item.optJSONArray("keycodes")
                ?: throw IllegalArgumentException("Mojo button $index has no keycodes.")
            // Mojo serializes its four-slot key array with -1 as the unused sentinel.
            // Android's KEYCODE_UNKNOWN is 0; neither value represents a pressed key.
            val mappedKeys = (0 until keys.length()).map { i -> keys.optInt(i, Int.MIN_VALUE) }
                .filter { it != -1 && it != KeyEvent.KEYCODE_UNKNOWN }
            require(mappedKeys.size == 1 && mappedKeys.single() >= 0) {
                "Mojo button $index uses a special action or a multi-key chord that O Engine cannot represent yet."
            }
            val keyCode = mappedKeys.single()
            val widthDp = item.optDouble("width", 50.0).toFloat()
            val heightDp = item.optDouble("height", 50.0).toFloat()
            require(widthDp.isFinite() && heightDp.isFinite() && widthDp > 0f && heightDp > 0f) {
                "Mojo button $index has invalid dimensions."
            }
            val normalizedWidth = (widthDp * density / viewportWidthPx).coerceIn(.04f, .3f)
            val normalizedHeight = (heightDp * density / viewportHeightPx).coerceIn(.06f, .35f)
            val normalizedX = (x / viewportWidthPx).coerceIn(0f, 1f - normalizedWidth)
            val normalizedY = (y / viewportHeightPx).coerceIn(0f, 1f - normalizedHeight)
            require(item.optString("bitmapTag").isBlank() || item.optString("bitmapTag") == "null") {
                "Mojo button $index uses a bitmap asset that O Engine cannot render yet."
            }
            val label = item.optString("name", "KEY").trim().take(12).ifBlank { "KEY" }
            val id = "mojo_$index"
            result += OEngineTouchControl(
                id = id,
                label = label,
                keyCode = keyCode,
                x = normalizedX,
                y = normalizedY,
                width = normalizedWidth,
                height = normalizedHeight,
                toggle = item.optBoolean("isToggle", false)
            )
        }
        require(result.isNotEmpty()) { "Mojo control map contains no importable game buttons." }
        return ImportResult(
            controls = result,
            sourceVersion = version,
            skippedHiddenControls = hidden,
            preservedDrawerJson = preserveCompoundControls(root, "mDrawerDataList"),
            preservedJoystickJson = preserveCompoundControls(root, "mJoystickDataList")
        )
    }

    /**
     * Keep full Mojo objects (including nested drawer buttons and joystick
     * options) intact. These snapshots are for round-tripping/storage only;
     * they must not be presented as imported, working touch controls.
     */
    private fun preserveCompoundControls(root: JSONObject, field: String): List<String> {
        val source = root.optJSONArray(field) ?: return emptyList()
        require(source.length() <= 48) { "Mojo $field contains too many compound controls." }
        return (0 until source.length()).map { index ->
            val item = source.optJSONObject(index)
                ?: throw IllegalArgumentException("Invalid Mojo compound control in $field at index $index.")
            item.toString()
        }
    }

    private fun numericPosition(item: JSONObject, field: String, index: Int): Float {
        val value = item.optString(field, "")
        val parsed = value.toFloatOrNull()
            ?: throw IllegalArgumentException(
                "Mojo button $index uses a dynamic $field expression. Preserve or simplify that position in Mojo before importing."
            )
        require(parsed.isFinite() && parsed >= 0f) { "Mojo button $index has an invalid $field position." }
        return parsed
    }
}
