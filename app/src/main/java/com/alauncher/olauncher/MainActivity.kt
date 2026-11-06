package com.alauncher.olauncher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Cream = Color(0xFFF6EBDD)
private val Panel = Color(0xFFFFF8EE)
private val Ink = Color(0xFF211F1B)
private val Muted = Color(0xFF766E64)
private val Gold = Color(0xFFFFC64D)
private val Border = Color(0xFFEADBC8)
private val MediaGray = Color(0xFF444444)

private data class LauncherImages(
    val hero: ImageBitmap,
    val logo: ImageBitmap,
    val avatar: ImageBitmap,
    val whatsNew: ImageBitmap,
    val spring: ImageBitmap,
    val trials: ImageBitmap,
    val camel: ImageBitmap,
    val realms: ImageBitmap
)

private fun fallbackBitmap(color: Int): Bitmap =
    Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

private fun readBitmap(context: android.content.Context, file: String, fallbackColor: Int): Bitmap {
    return try {
        context.assets.open(file).use { input ->
            BitmapFactory.decodeStream(input) ?: fallbackBitmap(fallbackColor)
        }
    } catch (_: Exception) {
        // A missing or unreadable decorative asset must never prevent the launcher from opening.
        fallbackBitmap(fallbackColor)
    }
}

private fun cropBitmapSafe(
    source: Bitmap, x: Int, y: Int, width: Int, height: Int, fallbackColor: Int
): ImageBitmap {
    val left = x.coerceIn(0, (source.width - 1).coerceAtLeast(0))
    val top = y.coerceIn(0, (source.height - 1).coerceAtLeast(0))
    val cropWidth = width.coerceAtMost(source.width - left).coerceAtLeast(1)
    val cropHeight = height.coerceAtMost(source.height - top).coerceAtLeast(1)
    return try {
        Bitmap.createBitmap(source, left, top, cropWidth, cropHeight).asImageBitmap()
    } catch (_: Exception) {
        fallbackBitmap(fallbackColor).asImageBitmap()
    }
}

@Composable
private fun rememberLauncherImages(): LauncherImages {
    val context = LocalContext.current
    return remember(context) {
        val atlas = readBitmap(context, "ui_atlas.png", android.graphics.Color.DKGRAY)
        val logo = readBitmap(context, "brand_logo.png", android.graphics.Color.DKGRAY)
        LauncherImages(
            hero = cropBitmapSafe(atlas, 0, 0, 720, 319, android.graphics.Color.DKGRAY),
            whatsNew = cropBitmapSafe(atlas, 0, 329, 257, 110, android.graphics.Color.LTGRAY),
            spring = cropBitmapSafe(atlas, 267, 329, 240, 87, android.graphics.Color.MAGENTA),
            trials = cropBitmapSafe(atlas, 0, 449, 239, 90, android.graphics.Color.BLUE),
            camel = cropBitmapSafe(atlas, 249, 449, 240, 87, android.graphics.Color.YELLOW),
            realms = cropBitmapSafe(atlas, 499, 449, 110, 130, android.graphics.Color.GREEN),
            avatar = cropBitmapSafe(atlas, 620, 449, 62, 64, android.graphics.Color.GREEN),
            logo = logo.asImageBitmap()
        )
    }
}

class MainActivity : ComponentActivity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        setContent { OLauncherApp() }
    }
}

@Composable
private fun OLauncherApp() {
    val images = rememberLauncherImages()
    var tab by remember { mutableIntStateOf(0) }
    var showPlayInfo by remember { mutableStateOf(false) }

    MaterialTheme(colorScheme = lightColorScheme(
        background = Cream, surface = Panel, primary = Gold,
        onPrimary = Ink, onBackground = Ink, onSurface = Ink
    )) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Cream)) {
            // Width proportions follow the supplied PC reference instead of squeezing the center column.
            val sidebarWidth = maxWidth * 0.095f
            val rightRailWidth = maxWidth * 0.235f
            val gap = (maxWidth * 0.009f).coerceIn(8.dp, 16.dp)
            val dashboardHeight = maxHeight
            val heroHeight = dashboardHeight * 0.505f
            val newsHeight = dashboardHeight * 0.325f

            Row(
                Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                Sidebar(tab, { tab = it }, images, Modifier.width(sidebarWidth).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    TopBar(images, Modifier.fillMaxWidth().height(dashboardHeight * 0.095f).padding(top = 12.dp))
                    Spacer(Modifier.height(gap))
                    Row(
                        Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        Column(
                            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(gap)
                        ) {
                            if (tab == 0) {
                                HeroBanner(images, Modifier.fillMaxWidth().height(heroHeight)) { showPlayInfo = true }
                                FeaturedNews(images, Modifier.fillMaxWidth().height(newsHeight))
                            } else {
                                Destination(tab)
                            }
                        }
                        Column(
                            Modifier.width(rightRailWidth).fillMaxHeight().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(gap)
                        ) {
                            WhatsNew(images, Modifier.fillMaxWidth().height(dashboardHeight * 0.35f))
                            QuickLinks(Modifier.fillMaxWidth().height(dashboardHeight * 0.245f)) { destination -> tab = destination }
                            RealmsCard(images, Modifier.fillMaxWidth().height(dashboardHeight * 0.225f))
                        }
                    }
                }
            }

            // The reference includes desktop window controls at the top-right edge.
            Row(
                Modifier.align(Alignment.TopEnd).padding(top = 0.dp, end = 13.dp),
                horizontalArrangement = Arrangement.spacedBy(15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("—", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("□", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("×", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }

            if (showPlayInfo) {
                AlertDialog(
                    onDismissRequest = { showPlayInfo = false },
                    title = { Text("Minecraft runtime not ready") },
                    text = { Text("The launcher interface is ready for the next development stage. Game launch still needs Microsoft sign-in, version downloads, an Android-compatible Java runtime, rendering and touch controls.") },
                    confirmButton = {
                        TextButton(onClick = { showPlayInfo = false }) { Text("Got it") }
                    }
                )
            }
        }
    }
}

@Composable
private fun MediaPlaceholder(modifier: Modifier = Modifier, shape: RoundedCornerShape = RoundedCornerShape(12.dp)) {
    Box(modifier.clip(shape).background(MediaGray))
}

@Composable
private fun TopBar(images: LauncherImages, modifier: Modifier = Modifier) {
    var showNotifications by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Image(images.logo, contentDescription = "O Launcher", modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Fit)
        Surface(
            Modifier.weight(1f).height(46.dp),
            color = Color(0xFFFFF8F0),
            shape = RoundedCornerShape(30.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Border)
        ) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Search, contentDescription = "Search", tint = Ink, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                var query by remember { mutableStateOf("") }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(color = Ink, fontSize = 13.sp),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) Text("SEARCH FOR ANYTHING...", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                            inner()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Box {
            Surface(onClick = { showNotifications = true }, color = Panel, shape = CircleShape, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Notifications, contentDescription = "Notifications", tint = Ink, modifier = Modifier.size(22.dp))
                }
            }
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp)
                    .size(19.dp).clip(CircleShape).background(Color(0xFFD83228)),
                contentAlignment = Alignment.Center
            ) { Text("3", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
        }
        Surface(color = Panel, shape = CircleShape, modifier = Modifier.size(44.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.SportsEsports, contentDescription = "Game", tint = Ink, modifier = Modifier.size(22.dp))
            }
        }
        Surface(
            onClick = { showProfile = true },
            color = Panel,
            shape = RoundedCornerShape(28.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Border)
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(images.avatar, contentDescription = "Player avatar", modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(8.dp))
                Text("CREEPERFAN", fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 13.sp, letterSpacing = (-0.25).sp)
                Spacer(Modifier.width(7.dp))
                Icon(Icons.Filled.ExpandMore, contentDescription = "Profile menu", tint = Ink, modifier = Modifier.size(18.dp))
            }
        }
    }
    if (showNotifications) {
        AlertDialog(
            onDismissRequest = { showNotifications = false },
            title = { Text("NOTIFICATIONS") },
            text = { Text("You’re all caught up! Launcher update notices and game news will appear here.") },
            confirmButton = { TextButton(onClick = { showNotifications = false }) { Text("CLOSE") } }
        )
    }
    if (showProfile) {
        AlertDialog(
            onDismissRequest = { showProfile = false },
            title = { Text("PLAYER PROFILE") },
            text = { Text("CREEPERFAN - Microsoft account sign-in will be added with the game authentication system.") },
            confirmButton = { TextButton(onClick = { showProfile = false }) { Text("GOT IT") } }
        )
    }
}

@Composable
private fun Sidebar(tab: Int, onTab: (Int) -> Unit, images: LauncherImages, modifier: Modifier = Modifier) {
    val entries = listOf(
        Triple(Icons.Filled.Home, "HOME", 0),
        Triple(Icons.Filled.Inventory2, "INSTALLATIONS", 1),
        Triple(Icons.Filled.Checkroom, "SKINS", 2),
        Triple(Icons.Filled.Storefront, "MARKETPLACE", 3),
        Triple(Icons.Filled.Settings, "SETTINGS", 4),
        Triple(Icons.Filled.Info, "ABOUT", 5)
    )
    Column(
        modifier.clip(RoundedCornerShape(26.dp)).background(Panel).padding(horizontal = 9.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(images.logo, contentDescription = "O Launcher logo", modifier = Modifier.padding(vertical = 8.dp).size(62.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Fit)
        Spacer(Modifier.height(8.dp))
        entries.forEach { (icon, label, index) ->
            val selected = tab == index
            Surface(
                onClick = { onTab(index) },
                color = if (selected) Color(0xFFFFE5A5) else Color.Transparent,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
            ) {
                Column(
                    Modifier.padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(icon, contentDescription = label, tint = Ink, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.height(3.dp))
                    Text(label, fontSize = 10.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, color = Ink, maxLines = 1, letterSpacing = (-0.2).sp)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Surface(color = Color(0xFFFFF1D9), shape = RoundedCornerShape(17.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(images.avatar, contentDescription = "Player avatar", modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(5.dp))
                    Column {
                        Text("CREEPERFAN", fontSize = 10.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                    }
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Filled.ExpandMore, contentDescription = "Account", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun HeroBanner(images: LauncherImages, modifier: Modifier = Modifier, onPlay: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(27.dp))) {
        Image(images.hero, contentDescription = "Minecraft featured banner", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        // Keep the primary action visible while the hero artwork is still a placeholder.
        Surface(
            onClick = onPlay,
            color = Gold,
            contentColor = Ink,
            shape = RoundedCornerShape(13.dp),
            shadowElevation = 3.dp,
            modifier = Modifier.align(Alignment.BottomStart)
                .padding(start = 22.dp, bottom = 20.dp)
        ) {
            Row(
                Modifier.padding(horizontal = 25.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Icon(Icons.Filled.SportsEsports, contentDescription = null, modifier = Modifier.size(19.dp))
                Text("PLAY NOW", fontSize = 15.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, letterSpacing = (-0.2).sp)
            }
        }
    }
}

@Composable
private fun WhatsNew(images: LauncherImages, modifier: Modifier = Modifier) {
    PanelCard("WHAT’S NEW?", "VIEW ALL  →", modifier) {
        Image(images.whatsNew, contentDescription = "What's new artwork", modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(15.dp)), contentScale = ContentScale.Crop)
        Text("THE NETHER UPDATE", fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 15.sp, letterSpacing = (-0.25).sp)
        Text("NEW BIOMES, NEW FEATURES!", color = Muted, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, fontSize = 12.sp, letterSpacing = 0.1.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CalendarToday, contentDescription = null, tint = Muted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(7.dp))
            Text("JUN 30, 2024", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
        }
    }
}

@Composable
private fun QuickLinks(modifier: Modifier = Modifier, onNavigate: (Int) -> Unit) {
    val context = LocalContext.current
    fun openPage(url: String) { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { } }
    PanelCard("QUICK LINKS", null, modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.weight(1f)) {
            QuickLink(Icons.Filled.Language, "Official\nWebsite", Modifier.weight(1f)) { openPage("https://www.minecraft.net/en-us") }
            QuickLink(Icons.Filled.Description, "Patch\nNotes", Modifier.weight(1f)) { openPage("https://www.minecraft.net/en-us/articles") }
            QuickLink(Icons.Filled.SportsEsports, "Discord", Modifier.weight(1f)) { openPage("https://www.minecraft.net/en-us/community") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.weight(1f)) {
            QuickLink(Icons.Filled.ShoppingCart, "Marketplace", Modifier.weight(1f)) { onNavigate(3) }
            QuickLink(Icons.Filled.Checkroom, "Skins", Modifier.weight(1f)) { onNavigate(2) }
            QuickLink(Icons.Filled.CardGiftcard, "Gift Codes", Modifier.weight(1f)) { openPage("https://www.minecraft.net/redeem") }
        }
    }
}

@Composable
private fun QuickLink(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, color = Color(0xFFFFF0DA), shape = RoundedCornerShape(15.dp)) {
        Column(Modifier.fillMaxSize().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = label, tint = Ink, modifier = Modifier.size(23.dp))
            Spacer(Modifier.height(4.dp))
            Text(label.uppercase(), fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, color = Ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun FeaturedNews(images: LauncherImages, modifier: Modifier = Modifier) {
    PanelCard("FEATURED NEWS", "VIEW ALL  →", modifier) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NewsCard(images.spring, "SPRING TO LIFE", "A FRESH NEW LOOK FOR MINECRAFT!", "Mar 25, 2025", Modifier.weight(1f))
            NewsCard(images.trials, "THE TRICKY TRIALS UPDATE", "TAKE ON NEW CHALLENGES!", "Jun 13, 2024", Modifier.weight(1f))
            NewsCard(images.camel, "CAMEL! RIDE IN STYLE", "EXPLORE THE DESERT WITH CAMELS.", "Apr 3, 2024", Modifier.weight(1f))
        }
    }
}

@Composable
private fun NewsCard(image: ImageBitmap, title: String, description: String, date: String, modifier: Modifier = Modifier) {
    Surface(color = Color(0xFFFFF4E6), shape = RoundedCornerShape(18.dp), modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            Image(image, contentDescription = title, modifier = Modifier.fillMaxWidth().height(82.dp).clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)), contentScale = ContentScale.Crop)
            Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title.uppercase(), fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 13.sp, letterSpacing = (-0.2).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(description, color = Muted, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CalendarToday, contentDescription = null, tint = Muted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(date.uppercase(), color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                }
            }
        }
    }
}

@Composable
private fun RealmsCard(images: LauncherImages, modifier: Modifier = Modifier) {
    Surface(color = Color(0xFFFFF0D9), shape = RoundedCornerShape(22.dp), modifier = modifier) {
        Row(Modifier.fillMaxSize().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("♛  JOIN REALMS", fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 16.sp)
                Text("PLAY WITH FRIENDS ANYWHERE, ANYTIME!", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, color = Ink)
                Surface(color = Ink, shape = RoundedCornerShape(25.dp)) {
                    Text("LEARN MORE  →", color = Color.White, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
                }
            }
            Image(images.realms, contentDescription = "Realms artwork", modifier = Modifier.width(92.dp).fillMaxHeight(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
private fun PanelCard(
    title: String,
    action: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = Panel,
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border.copy(alpha = .7f)),
        shadowElevation = 3.dp,
        modifier = modifier
    ) {
        Column(Modifier.fillMaxSize().padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 16.sp, letterSpacing = (-0.35).sp)
                if (action != null) {
                    Spacer(Modifier.weight(1f))
                    Text(action, fontSize = 11.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, color = Muted)
                }
            }
            content()
        }
    }
}

@Composable
private fun Destination(tab: Int) {
    val title = listOf("Home", "Installations", "Skins", "Marketplace", "Settings", "About")[tab.coerceIn(0, 5)]
    if (tab == 1) {
        InstallationsDestination()
    } else {
        Surface(color = Panel, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, fontSize = 27.sp, fontWeight = FontWeight.Black)
                Text(
                    when (tab) {
                        2 -> "Preview and manage your player skins here."
                        3 -> "Browse community content and launcher add-ons here."
                        4 -> "Customize O Launcher preferences here."
                        else -> "O Launcher information and credits."
                    },
                    color = Muted, fontSize = 14.sp
                )
                Text("This section is under development.", color = Color(0xFF9B6A25), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun InstallationsDestination() {
    val versions = listOf("1.21.4", "1.21.1", "1.20.6", "1.20.4", "1.19.4")
    var selectedVersion by remember { mutableStateOf("1.21.4") }
    var versionMenu by remember { mutableStateOf(false) }
    var showCreateProfile by remember { mutableStateOf(false) }
    var profileName by remember { mutableStateOf("") }
    val profiles = remember { mutableStateListOf("Default") }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(color = Panel, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("GAME INSTALLATIONS", fontSize = 23.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                        Text("Choose the Minecraft version for your launcher profile.", color = Muted, fontSize = 12.sp)
                    }
                    Surface(color = Color(0xFFFFE5A5), shape = RoundedCornerShape(12.dp)) {
                        Text("JAVA EDITION", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }
                Surface(color = Color(0xFFFFF2DF), shape = RoundedCornerShape(17.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
                    Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("GAME VERSION", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Box {
                            Surface(
                                onClick = { versionMenu = true },
                                color = Panel,
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Border),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Inventory2, contentDescription = null, tint = Ink, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text("Minecraft $selectedVersion", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.weight(1f))
                                    Icon(Icons.Filled.ExpandMore, contentDescription = "Choose version", tint = Ink)
                                }
                            }
                            DropdownMenu(expanded = versionMenu, onDismissRequest = { versionMenu = false }) {
                                versions.forEach { version ->
                                    DropdownMenuItem(
                                        text = { Text("Minecraft $version") },
                                        onClick = { selectedVersion = version; versionMenu = false },
                                        leadingIcon = { if (selectedVersion == version) Icon(Icons.Filled.Checkroom, contentDescription = null) }
                                    )
                                }
                            }
                        }
                        Text("Version choices are launcher UI entries only. The game files have not been downloaded.", color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }

        Surface(color = Panel, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("GAME PROFILES", fontSize = 19.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                        Text("Separate settings for different play styles.", color = Muted, fontSize = 12.sp)
                    }
                    Button(
                        onClick = { profileName = ""; showCreateProfile = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Text("+ CREATE PROFILE", fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                }
                profiles.forEachIndexed { index, name ->
                    Surface(color = Color(0xFFFFF2DF), shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(MediaGray), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.SportsEsports, contentDescription = null, tint = Gold)
                            }
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text(name, fontWeight = FontWeight.Black, fontSize = 14.sp)
                                Text("Minecraft $selectedVersion  •  Local profile draft", color = Muted, fontSize = 11.sp)
                            }
                            if (index == 0) {
                                Surface(color = Color(0xFFFFE5A5), shape = RoundedCornerShape(8.dp)) {
                                    Text("DEFAULT", modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp), fontSize = 9.sp, fontWeight = FontWeight.Black)
                                }
                            } else {
                                TextButton(onClick = { profiles.removeAt(index) }) { Text("REMOVE", color = Muted, fontSize = 10.sp) }
                            }
                        }
                    }
                }
                Text("Profiles are currently saved only while this screen is open. Persistent storage and version downloads are not connected yet.", color = Muted, fontSize = 11.sp)
            }
        }
    }

    if (showCreateProfile) {
        AlertDialog(
            onDismissRequest = { showCreateProfile = false },
            title = { Text("CREATE GAME PROFILE") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Give this profile a name.", color = Muted, fontSize = 12.sp)
                    OutlinedTextField(
                        value = profileName,
                        onValueChange = { profileName = it },
                        singleLine = true,
                        label = { Text("Profile name") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = profileName.isNotBlank() && profiles.none { it.equals(profileName.trim(), ignoreCase = true) },
                    onClick = { profiles.add(profileName.trim()); showCreateProfile = false }
                ) { Text("CREATE") }
            },
            dismissButton = { TextButton(onClick = { showCreateProfile = false }) { Text("CANCEL") } }
        )
    }
}
