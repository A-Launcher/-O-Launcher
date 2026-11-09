package com.alauncher.olauncher

import android.content.Context
import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
        require(json.length <= 16_384) { "Touch layout file is too large." }
        val root = JSONObject(json)
        require(root.optString("format") == "oengine-touch-layout" && root.optInt("version") == 1) {
            "Unsupported touch-layout format."
        }
        val array = root.getJSONArray("controls")
        require(array.length() in 1..48) { "A touch layout must contain 1–48 controls." }
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

    fun rename(controls: List<OEngineTouchControl>, id: String, label: String): List<OEngineTouchControl> {
        require(controls.any { it.id == id }) { "Unknown touch control: $id" }
        val normalized = label.trim()
        require(normalized.isNotEmpty()) { "A control label cannot be empty." }
        require(normalized.length <= 12) { "A control label can contain at most 12 characters." }
        return controls.map { if (it.id == id) it.copy(label = normalized) else it }
    }

    fun applyPreset(controls: List<OEngineTouchControl>, preset: String): List<OEngineTouchControl> {
        require(controls.isNotEmpty())
        val result = when (preset.lowercase()) {
            "classic" -> defaults()
            "left-handed" -> controls.map { control ->
                control.copy(x = (1f - control.x - control.width).coerceIn(0f, 1f - control.width))
            }
            "compact" -> controls.map { control ->
                val width = (control.width * .78f).coerceAtLeast(.04f)
                val height = (control.height * .78f).coerceAtLeast(.06f)
                control.copy(
                    width = width, height = height,
                    x = (control.x + (control.width - width) / 2f).coerceIn(0f, 1f - width),
                    y = (control.y + (control.height - height) / 2f).coerceIn(0f, 1f - height)
                )
            }
            else -> throw IllegalArgumentException("Unknown touch layout preset: $preset")
        }
        validate(result)
        return result
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val mojoJoystickConfig = androidx.compose.runtime.remember(context) {
        runCatching { OEngineMojoControlMapImporter.loadJoystickRuntimeConfigs(context).firstOrNull() }.getOrNull()
    }
    val mojoDrawerConfigs = androidx.compose.runtime.remember(context) {
        runCatching { OEngineMojoControlMapImporter.loadDrawerRuntimeConfigs(context) }.getOrDefault(emptyList())
    }
    val openMojoDrawers = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateMapOf<Int, Boolean>()
    }
    var editing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var selectedId by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("jump") }
    var presetIndex by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var showLabelEditor by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var labelDraft by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
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
        KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_CTRL_LEFT,
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
            val joystickPosition = if (mojoJoystickConfig?.xDp != null && mojoJoystickConfig.yDp != null) {
                androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.TopStart)
                    .offset(x = mojoJoystickConfig.xDp.dp, y = mojoJoystickConfig.yDp.dp)
            } else {
                androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomStart)
                    .padding(start = 24.dp, bottom = 28.dp)
            }
            OEngineTouchJoystick(
                modifier = joystickPosition,
                fixedCenter = mojoJoystickConfig?.absolute == true,
                widthDp = mojoJoystickConfig?.widthDp ?: 150f,
                heightDp = mojoJoystickConfig?.heightDp ?: 150f,
                onKey = onKey
            )
            if (mojoJoystickConfig?.forwardLock == true) {
                OEngineMojoForwardLockButton(
                    modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomStart)
                        .padding(start = 75.dp, bottom = 190.dp),
                    onKey = onKey
                )
            }
            mojoDrawerConfigs.forEachIndexed { index, drawer ->
                val drawerAlignment = when (drawer.orientation) {
                    "RIGHT" -> androidx.compose.ui.Alignment.CenterEnd
                    "UP" -> androidx.compose.ui.Alignment.TopCenter
                    "DOWN" -> androidx.compose.ui.Alignment.BottomCenter
                    else -> androidx.compose.ui.Alignment.CenterStart
                }
                val drawerModifier = if (drawer.xDp != null && drawer.yDp != null) {
                    androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.TopStart)
                        .offset(x = drawer.xDp.dp, y = drawer.yDp.dp)
                } else androidx.compose.ui.Modifier.align(drawerAlignment)
                    .padding(24.dp)
                OEngineMojoDrawer(
                    modifier = drawerModifier,
                    config = drawer,
                    expanded = openMojoDrawers[index] == true,
                    onExpandedChange = { open -> openMojoDrawers[index] = open },
                    onKey = onKey
                )
            }
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
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                    clipboard.setPrimaryClip(
                        android.content.ClipData.newPlainText(
                            "O Launcher touch layout",
                            OEngineTouchControls.encode(controls.toList())
                        )
                    )
                }) { androidx.compose.material3.Text("COPY") }
                androidx.compose.material3.TextButton(onClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                    val raw = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                    if (!raw.isNullOrBlank()) {
                        runCatching { OEngineTouchControls.decode(raw) }.onSuccess { imported ->
                            toggled.forEach { (id, pressed) ->
                                if (pressed) controls.firstOrNull { it.id == id }?.let { onKey(it.keyCode, false) }
                            }
                            controls.clear(); controls.addAll(imported)
                            selectedId = imported.firstOrNull { it.id == "jump" }?.id ?: imported.first().id
                            toggled.clear()
                            onLayoutChanged(imported)
                        }
                    }
                }) { androidx.compose.material3.Text("PASTE") }
                androidx.compose.material3.TextButton(
                    onClick = {
                        labelDraft = selected?.label.orEmpty()
                        showLabelEditor = selected != null
                    },
                    enabled = selected != null
                ) { androidx.compose.material3.Text("RENAME") }
                androidx.compose.material3.TextButton(onClick = {
                    val presets = listOf("classic", "left-handed", "compact")
                    presetIndex = (presetIndex + 1) % presets.size
                    val updated = OEngineTouchControls.applyPreset(controls.toList(), presets[presetIndex])
                    controls.clear(); controls.addAll(updated)
                    selectedId = updated.firstOrNull { it.id == "jump" }?.id ?: updated.first().id
                    toggled.clear()
                    onLayoutChanged(updated)
                }) { androidx.compose.material3.Text("LAYOUT") }
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

        if (showLabelEditor) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showLabelEditor = false },
                title = { androidx.compose.material3.Text("Rename control") },
                text = {
                    androidx.compose.material3.OutlinedTextField(
                        value = labelDraft,
                        onValueChange = { labelDraft = it.take(12) },
                        singleLine = true,
                        label = { androidx.compose.material3.Text("Button label") }
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        runCatching { OEngineTouchControls.rename(controls.toList(), selectedId, labelDraft) }
                            .onSuccess { updated ->
                                controls.clear(); controls.addAll(updated)
                                onLayoutChanged(updated)
                                showLabelEditor = false
                            }
                    }) { androidx.compose.material3.Text("SAVE") }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showLabelEditor = false }) {
                        androidx.compose.material3.Text("CANCEL")
                    }
                }
            )
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
    fixedCenter: Boolean = false,
    widthDp: Float = 150f,
    heightDp: Float = 150f,
    onKey: (Int, Boolean) -> Unit
) {
    var knobX by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var knobY by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var inputX by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var inputY by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
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
        modifier = modifier.size(widthDp.dp, heightDp.dp)
            .background(androidx.compose.ui.graphics.Color(0x55303030), CircleShape)
            .border(2.dp, androidx.compose.ui.graphics.Color(0x88FFFFFF), CircleShape)
            .pointerInput(fixedCenter, widthDp, heightDp) {
                val maxTravel = minOf(size.width, size.height) * .32f
                val threshold = maxTravel * .35f
                detectDragGestures(
                    onDragStart = { point ->
                        val startX = (point.x - size.width / 2f).coerceIn(-maxTravel, maxTravel)
                        val startY = (point.y - size.height / 2f).coerceIn(-maxTravel, maxTravel)
                        // Mojo absolute=true keeps the visual knob centered while
                        // using the touch position as the initial movement vector.
                        inputX = startX
                        inputY = startY
                        knobX = if (fixedCenter) 0f else startX
                        knobY = if (fixedCenter) 0f else startY
                        updateKeys(inputX, inputY, threshold)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        if (fixedCenter) {
                            inputX = (inputX + amount.x).coerceIn(-maxTravel, maxTravel)
                            inputY = (inputY + amount.y).coerceIn(-maxTravel, maxTravel)
                            updateKeys(inputX, inputY, threshold)
                        } else {
                            knobX = (knobX + amount.x).coerceIn(-maxTravel, maxTravel)
                            knobY = (knobY + amount.y).coerceIn(-maxTravel, maxTravel)
                            inputX = knobX
                            inputY = knobY
                            updateKeys(inputX, inputY, threshold)
                        }
                    },
                    onDragEnd = {
                        activeKeys.forEach { onKey(it, false) }
                        activeKeys = emptySet(); knobX = 0f; knobY = 0f; inputX = 0f; inputY = 0f
                    },
                    onDragCancel = {
                        activeKeys.forEach { onKey(it, false) }
                        activeKeys = emptySet(); knobX = 0f; knobY = 0f; inputX = 0f; inputY = 0f
                    }
                )
            },
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = androidx.compose.ui.Modifier.size(minOf(widthDp, heightDp).dp * .43f).offset {
                androidx.compose.ui.unit.IntOffset(knobX.toInt(), knobY.toInt())
            }.background(androidx.compose.ui.graphics.Color(0xCCB9A58B), CircleShape)
                .border(2.dp, androidx.compose.ui.graphics.Color(0xCCFFFFFF), CircleShape)
        )
    }
}

@androidx.compose.runtime.Composable
private fun OEngineMojoForwardLockButton(
    modifier: androidx.compose.ui.Modifier,
    onKey: (Int, Boolean) -> Unit
) {
    var locked by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val latestOnKey by androidx.compose.runtime.rememberUpdatedState(onKey)
    DisposableEffect(Unit) {
        onDispose {
            if (locked) latestOnKey(KeyEvent.KEYCODE_W, false)
        }
    }
    androidx.compose.foundation.layout.Box(
        modifier = modifier.size(52.dp)
            .clip(CircleShape)
            .background(if (locked) androidx.compose.ui.graphics.Color(0xCCB18A43) else androidx.compose.ui.graphics.Color(0x66505050))
            .border(1.dp, androidx.compose.ui.graphics.Color(0xAAFFFFFF), CircleShape)
            .clickable {
                locked = !locked
                onKey(KeyEvent.KEYCODE_W, locked)
            },
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.material3.Text(
            "W LOCK",
            color = androidx.compose.ui.graphics.Color.White,
            fontSize = 9.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
    }
}


/** Runtime for the safe physical-key subset of imported Mojo drawers. */
@androidx.compose.runtime.Composable
private fun OEngineMojoDrawer(
    modifier: androidx.compose.ui.Modifier,
    config: OEngineMojoControlMapImporter.DrawerRuntimeConfig,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onKey: (Int, Boolean) -> Unit
) {
    val toggled = androidx.compose.runtime.remember(config) {
        androidx.compose.runtime.mutableStateMapOf<Int, Boolean>()
    }
    DisposableEffect(config) {
        onDispose {
            toggled.forEach { (index, pressed) ->
                if (pressed) config.buttons.getOrNull(index)?.let { onKey(it.keyCode, false) }
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(expanded) {
        if (!expanded) {
            toggled.forEach { (index, pressed) ->
                if (pressed) config.buttons.getOrNull(index)?.let { onKey(it.keyCode, false) }
            }
            toggled.clear()
        }
    }
    androidx.compose.foundation.layout.Column(
        modifier = modifier,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
    ) {
        androidx.compose.material3.TextButton(
            onClick = { onExpandedChange(!expanded) },
            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                containerColor = androidx.compose.ui.graphics.Color(0xCC252525),
                contentColor = androidx.compose.ui.graphics.Color.White
            )
        ) { androidx.compose.material3.Text(if (expanded) "× ${config.label}" else "☰ ${config.label}") }
        if (expanded) {
            val horizontal = config.orientation == "LEFT" || config.orientation == "RIGHT"
            val panelModifier = androidx.compose.ui.Modifier
                .width(config.widthDp.dp)
                .height(config.heightDp.dp)
                .background(androidx.compose.ui.graphics.Color(0xCC171717), RoundedCornerShape(10.dp))
                .border(1.dp, androidx.compose.ui.graphics.Color(0x88FFFFFF), RoundedCornerShape(10.dp))
                .padding(6.dp)
            if (horizontal) {
                androidx.compose.foundation.layout.Row(
                    modifier = panelModifier,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(5.dp)
                ) {
                    config.buttons.forEachIndexed { index, button ->
                        OEngineMojoDrawerButton(button, index, toggled, onKey)
                    }
                }
            } else {
                androidx.compose.foundation.layout.Column(
                    modifier = panelModifier,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(5.dp)
                ) {
                    config.buttons.forEachIndexed { index, button ->
                        OEngineMojoDrawerButton(button, index, toggled, onKey)
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun OEngineMojoDrawerButton(
    button: OEngineMojoControlMapImporter.DrawerButtonConfig,
    index: Int,
    toggled: androidx.compose.runtime.MutableMap<Int, Boolean>,
    onKey: (Int, Boolean) -> Unit
) {
    val pressed = toggled[index] == true
    androidx.compose.foundation.layout.Box(
        modifier = androidx.compose.ui.Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 42.dp)
            .background(
                if (pressed) androidx.compose.ui.graphics.Color(0xFF80612F)
                else androidx.compose.ui.graphics.Color(0x99505050),
                RoundedCornerShape(8.dp)
            )
            .border(1.dp, androidx.compose.ui.graphics.Color(0x77FFFFFF), RoundedCornerShape(8.dp))
            .then(
                if (button.toggle) androidx.compose.ui.Modifier.clickable {
                    val next = toggled[index] != true
                    toggled[index] = next
                    onKey(button.keyCode, next)
                } else androidx.compose.ui.Modifier.pointerInput(button.keyCode) {
                    androidx.compose.foundation.gestures.detectTapGestures(
                        onPress = {
                            onKey(button.keyCode, true)
                            try { tryAwaitRelease() } finally { onKey(button.keyCode, false) }
                        }
                    )
                }
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.material3.Text(
            button.label,
            color = androidx.compose.ui.graphics.Color.White,
            fontSize = 11.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
    }
}
