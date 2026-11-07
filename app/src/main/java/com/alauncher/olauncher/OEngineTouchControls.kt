package com.alauncher.olauncher

import android.content.Context
import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject

/** Persisted touch layout. Coordinates are normalized to the game surface (0..1). */
data class OEngineTouchControl(
    val id: String,
    val label: String,
    val keyCode: Int,
    val x: Float,
    val y: Float,
    val width: Float = 0.085f,
    val height: Float = 0.13f,
    val toggle: Boolean = false
) {
    init {
        require(id.matches(Regex("[A-Za-z0-9_.-]{1,48}")))
        require(label.isNotBlank() && label.length <= 12)
        require(keyCode >= 0)
        require(x.isFinite() && y.isFinite() && width.isFinite() && height.isFinite())
        require(x in 0f..1f && y in 0f..1f)
        require(width in 0.04f..0.3f && height in 0.06f..0.35f)
    }
}

object OEngineTouchControls {
    private const val PREFS = "o_engine_touch_controls"
    private const val LAYOUT_KEY = "layout_v1"

    fun defaults(): List<OEngineTouchControl> = listOf(
        OEngineTouchControl("forward", "▲", KeyEvent.KEYCODE_W, .50f, .73f, .075f, .12f),
        OEngineTouchControl("back", "▼", KeyEvent.KEYCODE_S, .50f, .88f, .075f, .12f),
        OEngineTouchControl("left", "◀", KeyEvent.KEYCODE_A, .41f, .805f, .075f, .12f),
        OEngineTouchControl("right", "▶", KeyEvent.KEYCODE_D, .59f, .805f, .075f, .12f),
        OEngineTouchControl("jump", "JUMP", KeyEvent.KEYCODE_SPACE, .89f, .77f, .095f, .15f),
        OEngineTouchControl("sneak", "SNEAK", KeyEvent.KEYCODE_SHIFT_LEFT, .78f, .88f, .095f, .13f, true),
        OEngineTouchControl("inventory", "INV", KeyEvent.KEYCODE_E, .91f, .12f, .075f, .12f)
    )

    fun load(context: Context): List<OEngineTouchControl> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(LAYOUT_KEY, null)
            ?: return defaults()
        return try { decode(raw) } catch (_: Exception) { defaults() }
    }

    fun save(context: Context, controls: List<OEngineTouchControl>) {
        validate(controls)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(LAYOUT_KEY, encode(controls)).apply()
    }

    fun encode(controls: List<OEngineTouchControl>): String {
        validate(controls)
        val array = JSONArray()
        controls.forEach { c ->
            array.put(JSONObject().apply {
                put("id", c.id); put("label", c.label); put("keyCode", c.keyCode)
                put("x", c.x.toDouble()); put("y", c.y.toDouble())
                put("width", c.width.toDouble()); put("height", c.height.toDouble())
                put("toggle", c.toggle)
            })
        }
        return JSONObject().put("format", "oengine-touch-layout").put("version", 1).put("controls", array).toString()
    }

    fun decode(json: String): List<OEngineTouchControl> {
        val root = JSONObject(json)
        require(root.optString("format") == "oengine-touch-layout" && root.optInt("version") == 1) {
            "Unsupported touch-layout format."
        }
        val array = root.getJSONArray("controls")
        val result = ArrayList<OEngineTouchControl>(array.length())
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            result += OEngineTouchControl(
                item.getString("id"), item.getString("label"), item.getInt("keyCode"),
                item.getDouble("x").toFloat(), item.getDouble("y").toFloat(),
                item.optDouble("width", .085).toFloat(), item.optDouble("height", .13).toFloat(),
                item.optBoolean("toggle", false)
            )
        }
        validate(result)
        return result
    }

    fun move(controls: List<OEngineTouchControl>, id: String, x: Float, y: Float): List<OEngineTouchControl> {
        require(x.isFinite() && y.isFinite())
        val updated = controls.map { c ->
            if (c.id == id) c.copy(x = x.coerceIn(0f, 1f), y = y.coerceIn(0f, 1f)) else c
        }
        validate(updated)
        require(updated.any { it.id == id }) { "Unknown touch control: $id" }
        return updated
    }

    private fun validate(controls: List<OEngineTouchControl>) {
        require(controls.size in 1..48) { "A touch layout must contain 1–48 controls." }
        require(controls.map { it.id }.distinct().size == controls.size) { "Touch control IDs must be unique." }
    }
}
