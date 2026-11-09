package com.alauncher.olauncher

import org.json.JSONObject
import android.view.KeyEvent

/**
 * Imports the portable, fixed-position subset of MojoLauncher V9 control maps.
 *
 * Mojo stores control maps in a LayoutBitmaps container; callers must extract the
 * JSON payload first. Dynamic expression positions, special mouse/keyboard actions,
 * drawers and joysticks are rejected rather than silently flattened or discarded.
 */
object OEngineMojoControlMapImporter {
    data class ImportResult(
        val controls: List<OEngineTouchControl>,
        val sourceVersion: Int,
        val skippedHiddenControls: Int
    )

    fun importJson(json: String, viewportWidthPx: Float, viewportHeightPx: Float, density: Float = 1f): ImportResult {
        require(json.length <= 1_048_576) { "Mojo control map JSON is too large." }
        require(viewportWidthPx.isFinite() && viewportWidthPx > 0f)
        require(viewportHeightPx.isFinite() && viewportHeightPx > 0f)
        require(density.isFinite() && density > 0f)

        val root = JSONObject(json)
        val version = root.optInt("version", -1)
        require(version in 0..9) { "Unsupported Mojo control-map version." }
        require((root.optJSONArray("mDrawerDataList")?.length() ?: 0) == 0) {
            "This Mojo map contains drawers; import it in Mojo or export a simple-button layout first."
        }
        require((root.optJSONArray("mJoystickDataList")?.length() ?: 0) == 0) {
            "This Mojo map contains joysticks; this importer cannot preserve them yet."
        }

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
            val keyCode = (0 until keys.length()).mapNotNull { i ->
                keys.optInt(i, Int.MIN_VALUE).takeIf { it >= 0 && it != KeyEvent.KEYCODE_UNKNOWN }
            }.firstOrNull() ?: throw IllegalArgumentException(
                "Mojo button $index uses a special action or has no physical key mapping."
            )
            val widthDp = item.optDouble("width", 50.0).toFloat()
            val heightDp = item.optDouble("height", 50.0).toFloat()
            require(widthDp.isFinite() && heightDp.isFinite() && widthDp > 0f && heightDp > 0f) {
                "Mojo button $index has invalid dimensions."
            }
            val normalizedWidth = (widthDp * density / viewportWidthPx).coerceIn(.04f, .3f)
            val normalizedHeight = (heightDp * density / viewportHeightPx).coerceIn(.06f, .35f)
            val normalizedX = (x / viewportWidthPx).coerceIn(0f, 1f - normalizedWidth)
            val normalizedY = (y / viewportHeightPx).coerceIn(0f, 1f - normalizedHeight)
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
        return ImportResult(result, version, hidden)
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
