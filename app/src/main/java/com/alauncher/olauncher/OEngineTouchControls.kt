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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.awaitFirstDown
import androidx.compose.ui.input.pointer.waitForUpOrCancellation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.DisposableEffect
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
        require(controls.any { it.id == id }) { "Unknown touch control: $id" }
        val updated = controls.map { c ->
            if (c.id == id) c.copy(x = x.coerceIn(0f, 1f - c.width), y = y.coerceIn(0f, 1f - c.height)) else c
        }
        validate(updated)
        return updated
    }

    fun resize(controls: List<OEngineTouchControl>, id: String, scale: Float): List<OEngineTouchControl> {
        require(scale.isFinite() && scale > 0f)
        require(controls.any { it.id == id }) { "Unknown touch control: $id" }
        val updated = controls.map { c ->
            if (c.id == id) {
                val w = (c.width * scale).coerceIn(.04f, .3f)
                val h = (c.height * scale).coerceIn(.06f, .35f)
                c.copy(width = w, height = h, x = c.x.coerceAtMost(1f - w), y = c.y.coerceAtMost(1f - h))
            } else c
        }
        validate(updated)
        return updated
    }

    fun add(controls: List<OEngineTouchControl>, label: String, keyCode: Int): List<OEngineTouchControl> {
        require(controls.size < 48) { "The touch layout already has 48 controls." }
        require(keyCode >= 0)
        var suffix = controls.size + 1
        var id = "custom_$suffix"
        while (controls.any { it.id == id }) { suffix++; id = "custom_$suffix" }
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
    val controls = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateListOf<OEngineTouchControl>().apply { addAll(initialControls) }
    }
    var editing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var selectedId by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("jump") }
    val toggled = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    DisposableEffect(Unit) {
        onDispose {
            toggled.forEach { (id, pressed) ->
                if (pressed) controls.firstOrNull { it.id == id }?.let { onKey(it.keyCode, false) }
            }
        }
    }
    val keyChoices = listOf(
        KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D,
        KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_CONTROL_LEFT,
        KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_C,
        KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_4,
        KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_9
    )
    androidx.compose.foundation.layout.BoxWithConstraints(androidx.compose.ui.Modifier.fillMaxSize()) {
        androidx.compose.material3.TextButton(
            modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.TopStart).padding(12.dp),
            onClick = {
                if (!editing) {
                    // Entering the editor must not leave a toggle key held in Minecraft.
                    toggled.forEach { (id, pressed) ->
                        if (pressed) controls.firstOrNull { it.id == id }?.let { onKey(it.keyCode, false) }
                    }
                    toggled.clear()
                }
                editing = !editing
            },
            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.ui.graphics.Color.White)
        ) { androidx.compose.material3.Text(if (editing) "DONE" else "EDIT CONTROLS") }

        if (!editing) {
            OEngineTouchJoystick(
                modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomStart)
                    .padding(start = 24.dp, bottom = 28.dp),
                onKey = onKey
            )
        }

        if (editing) {
            androidx.compose.foundation.layout.Row(
                modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
                    .fillMaxWidth().background(androidx.compose.ui.graphics.Color(0xDD171717)).padding(6.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                val selected = controls.firstOrNull { it.id == selectedId }
                androidx.compose.material3.TextButton(onClick = {
                    val reset = OEngineTouchControls.reset()
                    toggled.forEach { (id, pressed) ->
                        if (pressed) controls.firstOrNull { it.id == id }?.let { onKey(it.keyCode, false) }
                    }
                    controls.clear(); controls.addAll(reset); selectedId = "jump"; toggled.clear()
                    onLayoutChanged(reset)
                }) { androidx.compose.material3.Text("RESET") }
                androidx.compose.material3.TextButton(onClick = {
                    val updated = OEngineTouchControls.add(controls.toList(), "DROP", KeyEvent.KEYCODE_Q)
                    controls.clear(); controls.addAll(updated); selectedId = updated.last().id; onLayoutChanged(updated)
                }, enabled = controls.size < 48) { androidx.compose.material3.Text("+ ADD") }
                androidx.compose.material3.TextButton(onClick = {
                    if (selected != null) {
                        val i = keyChoices.indexOf(selected.keyCode).let { if (it < 0) 0 else it }
                        val updated = OEngineTouchControls.remap(controls.toList(), selectedId, keyChoices[(i - 1 + keyChoices.size) % keyChoices.size])
                        controls.clear(); controls.addAll(updated); onLayoutChanged(updated)
                    }
                }) { androidx.compose.material3.Text("KEY −") }
                androidx.compose.material3.TextButton(onClick = {
                    if (selected != null) {
                        val updated = controls.map { if (it.id == selectedId) it.copy(toggle = !it.toggle) else it }
                        controls.clear(); controls.addAll(updated); onLayoutChanged(updated)
                    }
                }) { androidx.compose.material3.Text(if (selected?.toggle == true) "TOGGLE" else "HOLD") }
                androidx.compose.material3.Text(
                    selected?.let { KeyEvent.keyCodeToString(it.keyCode).removePrefix("KEYCODE_") } ?: "KEY",
                    color = androidx.compose.ui.graphics.Color.White, fontSize = 10.sp
                )
                androidx.compose.material3.TextButton(onClick = {
                    if (selected != null) {
                        val i = keyChoices.indexOf(selected.keyCode).let { if (it < 0) 0 else it }
                        val updated = OEngineTouchControls.remap(controls.toList(), selectedId, keyChoices[(i + 1) % keyChoices.size])
                        controls.clear(); controls.addAll(updated); onLayoutChanged(updated)
                    }
                }) { androidx.compose.material3.Text("KEY +") }
                androidx.compose.material3.TextButton(onClick = {
                    val updated = OEngineTouchControls.resize(controls.toList(), selectedId, 1.12f)
                    controls.clear(); controls.addAll(updated); onLayoutChanged(updated)
                }) { androidx.compose.material3.Text("SIZE +") }
                androidx.compose.material3.TextButton(onClick = {
                    val updated = OEngineTouchControls.resize(controls.toList(), selectedId, .89f)
                    controls.clear(); controls.addAll(updated); onLayoutChanged(updated)
                }) { androidx.compose.material3.Text("SIZE −") }
                androidx.compose.material3.TextButton(onClick = {
                    if (controls.size > 1) {
                        if (toggled[selectedId] == true) {
                            controls.firstOrNull { it.id == selectedId }?.let { onKey(it.keyCode, false) }
                            toggled.remove(selectedId)
                        }
                        val updated = OEngineTouchControls.remove(controls.toList(), selectedId)
                        controls.clear(); controls.addAll(updated); selectedId = updated.first().id
                        onLayoutChanged(updated)
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
                    .clip(RoundedCornerShape(12.dp))
                    .then(
                        if (editing) androidx.compose.ui.Modifier.clickable { selectedId = control.id }
                        else androidx.compose.ui.Modifier
                    )
                    .background(if (editing) androidx.compose.ui.graphics.Color(0xAA8B6B35) else androidx.compose.ui.graphics.Color(0x66505050))
                    .border(
                        if (editing && selectedId == control.id) 2.dp else 1.dp,
                        if (editing && selectedId == control.id) androidx.compose.ui.graphics.Color.White
                        else if (editing) androidx.compose.ui.graphics.Color(0xFFFFD27A) else androidx.compose.ui.graphics.Color(0x66FFFFFF),
                        RoundedCornerShape(12.dp)
                    )
                    .pointerInput(control.id, editing, control.toggle) {
                        if (editing) {
                            detectDragGestures(
                                onDragStart = { selectedId = control.id },
                                onDrag = { change, drag ->
                                selectedId = control.id
                                change.consume()
                                val current = controls.first { it.id == control.id }
                                val moved = OEngineTouchControls.move(
                                    controls.toList(), control.id,
                                    current.x + drag.x / constraints.maxWidth.toFloat().coerceAtLeast(1f),
                                    current.y + drag.y / constraints.maxHeight.toFloat().coerceAtLeast(1f)
                                )
                                controls.clear(); controls.addAll(moved); onLayoutChanged(moved)
                                }
                            )
                        } else {
                            awaitEachGesture {
                                awaitFirstDown()
                                if (control.toggle) {
                                    val pressed = !(toggled[control.id] ?: false)
                                    toggled[control.id] = pressed
                                    onKey(control.keyCode, pressed)
                                } else {
                                    onKey(control.keyCode, true)
                                    try { waitForUpOrCancellation() } finally { onKey(control.keyCode, false) }
                                }
                            }
                        }
                    },
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                androidx.compose.material3.Text(
                    control.label, color = androidx.compose.ui.graphics.Color.White,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 12.sp
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun OEngineTouchJoystick(
    modifier: androidx.compose.ui.Modifier,
    onKey: (Int, Boolean) -> Unit
) {
    var knobX by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var knobY by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var activeKeys by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(setOf<Int>()) }
    // DisposableEffect(Unit) is installed only once. Keep the latest pressed-key
    // set so closing the game surface during a joystick gesture cannot strand keys.
    val latestActiveKeys by androidx.compose.runtime.rememberUpdatedState(activeKeys)
    val latestOnKey by androidx.compose.runtime.rememberUpdatedState(onKey)
    DisposableEffect(Unit) {
        onDispose {
            latestActiveKeys.forEach { latestOnKey(it, false) }
        }
    }

    fun updateKeys(x: Float, y: Float, threshold: Float) {
        val next = buildSet {
            if (y < -threshold) add(KeyEvent.KEYCODE_W)
            if (y > threshold) add(KeyEvent.KEYCODE_S)
            if (x < -threshold) add(KeyEvent.KEYCODE_A)
            if (x > threshold) add(KeyEvent.KEYCODE_D)
        }
        (activeKeys - next).forEach { onKey(it, false) }
        (next - activeKeys).forEach { onKey(it, true) }
        activeKeys = next
    }

    androidx.compose.foundation.layout.Box(
        modifier = modifier.size(150.dp)
            .background(androidx.compose.ui.graphics.Color(0x55303030), CircleShape)
            .border(2.dp, androidx.compose.ui.graphics.Color(0x88FFFFFF), CircleShape)
            .pointerInput(Unit) {
                val maxTravel = size.width * .32f
                val threshold = maxTravel * .35f
                detectDragGestures(
                    onDragStart = { point ->
                        knobX = (point.x - size.width / 2f).coerceIn(-maxTravel, maxTravel)
                        knobY = (point.y - size.height / 2f).coerceIn(-maxTravel, maxTravel)
                        updateKeys(knobX, knobY, threshold)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        knobX = (knobX + amount.x).coerceIn(-maxTravel, maxTravel)
                        knobY = (knobY + amount.y).coerceIn(-maxTravel, maxTravel)
                        updateKeys(knobX, knobY, threshold)
                    },
                    onDragEnd = {
                        activeKeys.forEach { onKey(it, false) }
                        activeKeys = emptySet(); knobX = 0f; knobY = 0f
                    },
                    onDragCancel = {
                        activeKeys.forEach { onKey(it, false) }
                        activeKeys = emptySet(); knobX = 0f; knobY = 0f
                    }
                )
            },
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = androidx.compose.ui.Modifier.size(64.dp).offset {
                androidx.compose.ui.unit.IntOffset(knobX.toInt(), knobY.toInt())
            }.background(androidx.compose.ui.graphics.Color(0xCCB9A58B), CircleShape)
                .border(2.dp, androidx.compose.ui.graphics.Color(0xCCFFFFFF), CircleShape)
        )
    }
}
