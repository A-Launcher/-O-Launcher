package com.alauncher.olauncher

import android.content.Context
import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.awaitFirstDown
import androidx.compose.ui.input.pointer.waitForUpOrCancellation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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
            if (c.id == id) c.copy(x = x.coerceIn(0f, 1f - c.width), y = y.coerceIn(0f, 1f - c.height)) else c
        }
        validate(updated)
        require(updated.any { it.id == id }) { "Unknown touch control: $id" }
        return updated
    }

    fun resize(controls: List<OEngineTouchControl>, id: String, scale: Float): List<OEngineTouchControl> {
        require(scale.isFinite() && scale > 0f)
        val updated = controls.map { c ->
            if (c.id == id) c.copy(
                width = (c.width * scale).coerceIn(.04f, .3f),
                height = (c.height * scale).coerceIn(.06f, .35f),
                x = c.x.coerceAtMost(1f - (c.width * scale).coerceIn(.04f, .3f)),
                y = c.y.coerceAtMost(1f - (c.height * scale).coerceIn(.06f, .35f))
            ) else c
        }
        validate(updated)
        require(updated.any { it.id == id }) { "Unknown touch control: $id" }
        return updated
    }

    fun add(controls: List<OEngineTouchControl>, label: String, keyCode: Int): List<OEngineTouchControl> {
        require(controls.size < 48) { "The touch layout already has 48 controls." }
        val id = "custom_${(controls.maxOfOrNull { it.id.hashCode() } ?: 0) + controls.size + 1}"
        val added = controls + OEngineTouchControl(
            id = id, label = label.take(12).ifBlank { "KEY" }, keyCode = keyCode,
            x = .5f, y = .5f, width = .085f, height = .13f
        )
        validate(added)
        return added
    }

    fun remove(controls: List<OEngineTouchControl>, id: String): List<OEngineTouchControl> {
        require(controls.size > 1) { "At least one touch control must remain." }
        require(controls.any { it.id == id }) { "Unknown touch control: $id" }
        return controls.filterNot { it.id == id }
    }

    fun remap(controls: List<OEngineTouchControl>, id: String, keyCode: Int): List<OEngineTouchControl> {
        require(keyCode >= 0)
        require(controls.any { it.id == id }) { "Unknown touch control: $id" }
        return controls.map { if (it.id == id) it.copy(keyCode = keyCode) else it }
    }

    fun reset(): List<OEngineTouchControl> = defaults()

    private fun validate(controls: List<OEngineTouchControl>) {
        require(controls.size in 1..48) { "A touch layout must contain 1–48 controls." }
        require(controls.map { it.id }.distinct().size == controls.size) { "Touch control IDs must be unique." }
    }
}


@androidx.compose.runtime.Composable
fun OEngineTouchControlsOverlay(
    initialControls: List<OEngineTouchControl>,
    onKey: (Int, Boolean) -> Unit,
    onLayoutChanged: (List<OEngineTouchControl>) -> Unit
) {
    val controls = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateListOf<OEngineTouchControl>().apply { addAll(initialControls) } }
    var editing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var selectedId by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("jump") }
    val toggled = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    val keyChoices = listOf(
        KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D,
        KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_CONTROL_LEFT,
        KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_C,
        KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_4,
        KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_8,
        KeyEvent.KEYCODE_9
    )
    androidx.compose.foundation.layout.BoxWithConstraints(
        androidx.compose.ui.Modifier.fillMaxSize()
    ) {
        androidx.compose.foundation.layout.Box(
            androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.TopStart)
                .padding(12.dp)
        ) {
            androidx.compose.material3.TextButton(
                onClick = { editing = !editing },
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.ui.graphics.Color.White)
            ) { androidx.compose.material3.Text(if (editing) "DONE" else "EDIT CONTROLS") }
        }
        if (editing) {
            androidx.compose.foundation.layout.Row(
                modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
                    .fillMaxWidth().background(androidx.compose.ui.graphics.Color(0xDD171717))
                    .padding(8.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(5.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                androidx.compose.material3.TextButton(onClick = {
                    controls.clear(); controls.addAll(OEngineTouchControls.reset())
                    selectedId = "jump"; onLayoutChanged(controls.toList())
                }) { androidx.compose.material3.Text("RESET") }
                androidx.compose.material3.TextButton(onClick = {
                    val added = OEngineTouchControls.add(controls.toList(), "DROP", KeyEvent.KEYCODE_Q)
                    controls.clear(); controls.addAll(added); selectedId = added.last().id
                    onLayoutChanged(added)
                }, enabled = controls.size < 48) { androidx.compose.material3.Text("+ DROP") }
                val selectedControl = controls.firstOrNull { it.id == selectedId }
                androidx.compose.material3.TextButton(onClick = {
                    if (selectedControl != null) {
                        val index = keyChoices.indexOf(selectedControl.keyCode).let { if (it < 0) 0 else it }
                        val updated = OEngineTouchControls.remap(controls.toList(), selectedId, keyChoices[(index - 1 + keyChoices.size) % keyChoices.size])
                        controls.clear(); controls.addAll(updated); onLayoutChanged(updated)
                    }
                }) { androidx.compose.material3.Text("KEY −") }
                androidx.compose.material3.Text(
                    selectedControl?.let { KeyEvent.keyCodeToString(it.keyCode).removePrefix("KEYCODE_") } ?: "KEY",
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 10.sp
                )
                androidx.compose.material3.TextButton(onClick = {
                    if (selectedControl != null) {
                        val index = keyChoices.indexOf(selectedControl.keyCode).let { if (it < 0) 0 else it }
                        val updated = OEngineTouchControls.remap(controls.toList(), selectedId, keyChoices[(index + 1) % keyChoices.size])
                        controls.clear(); controls.addAll(updated); onLayoutChanged(updated)
                    }
                }) { androidx.compose.material3.Text("KEY +") }
                androidx.compose.material3.TextButton(onClick = {
                    val resized = OEngineTouchControls.resize(controls.toList(), selectedId, 1.12f)
                    controls.clear(); controls.addAll(resized); onLayoutChanged(resized)
                }) { androidx.compose.material3.Text("SIZE +") }
                androidx.compose.material3.TextButton(onClick = {
                    val resized = OEngineTouchControls.resize(controls.toList(), selectedId, .89f)
                    controls.clear(); controls.addAll(resized); onLayoutChanged(resized)
                }) { androidx.compose.material3.Text("SIZE −") }
                androidx.compose.material3.TextButton(onClick = {
                    if (controls.size > 1) {
                        val remaining = OEngineTouchControls.remove(controls.toList(), selectedId)
                        controls.clear(); controls.addAll(remaining); selectedId = remaining.first().id
                        onLayoutChanged(remaining)
                    }
                }, enabled = controls.size > 1) { androidx.compose.material3.Text("DELETE") }
            }
        }
        controls.forEach { control ->
            androidx.compose.foundation.layout.Box(
                modifier = androidx.compose.ui.Modifier
                    .offset {
                        androidx.compose.ui.unit.IntOffset(
                            (constraints.maxWidth * control.x).toInt(),
                            (constraints.maxHeight * control.y).toInt()
                        )
                    }
                    .size(maxWidth * control.width, maxHeight * control.height)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .background(if (editing) androidx.compose.ui.graphics.Color(0xAA8B6B35) else androidx.compose.ui.graphics.Color(0x66505050))
                    .border(
                        if (editing && selectedId == control.id) 2.dp else 1.dp,
                        if (editing && selectedId == control.id) androidx.compose.ui.graphics.Color(0xFFFFFFFF)
                        else if (editing) androidx.compose.ui.graphics.Color(0xFFFFD27A) else androidx.compose.ui.graphics.Color(0x66FFFFFF),
                        androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    )
                    .pointerInput(control.id, editing, control.toggle) {
                        if (editing) {
                            androidx.compose.foundation.gestures.detectTapGestures(onTap = { selectedId = control.id })