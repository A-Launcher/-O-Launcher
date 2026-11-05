package com.alauncher.olauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Cream = Color(0xFFF6EBDD)
private val Panel = Color(0xFFFFF8EE)
private val Ink = Color(0xFF211F1B)
private val Muted = Color(0xFF766E64)
private val Gold = Color(0xFFFFC64D)
private val Border = Color(0xFFEADBC8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(246, 235, 221)
        window.navigationBarColor = android.graphics.Color.rgb(246, 235, 221)
        window.decorView.systemUiVisibility = 0
        setContent { OLauncherApp() }
    }
}

@Composable
private fun OLauncherApp() {
    var tab by remember { mutableIntStateOf(0) }
    MaterialTheme(colorScheme = lightColorScheme(
        background = Cream,
        surface = Panel,
        primary = Gold,
        onPrimary = Ink,
        onBackground = Ink,
        onSurface = Ink
    )) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Cream)) {
            val desktop = maxWidth >= 850.dp
            if (desktop) {
                Row(Modifier.fillMaxSize().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Sidebar(tab, { tab = it }, Modifier.width(116.dp).fillMaxHeight())
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        TopBar()
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                                if (tab == 0) {
                                    HeroBanner(Modifier.fillMaxWidth().height(390.dp))
                                    Spacer(Modifier.height(14.dp))
                                    FeaturedNews()
                                } else Destination(tab)
                            }
                            Column(Modifier.width(310.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                WhatsNew()
                                QuickLinks()
                                RealmsCard()
                            }
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    TopBar(Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (tab == 0) {
                            HeroBanner(Modifier.fillMaxWidth().height(360.dp))
                            WhatsNew()
                            QuickLinks()
                            FeaturedNews()
                            RealmsCard()
                        } else Destination(tab)
                    }
                    NavigationBar(containerColor = Panel, tonalElevation = 8.dp) {
                        val labels = listOf("Home", "Installations", "Skins", "Settings")
                        val icons = listOf("⌂", "▦", "♟", "⚙")
                        labels.forEachIndexed { index, label ->
                            NavigationBarItem(
                                selected = tab == index,
                                onClick = { tab = index },
                                icon = { Text(icons[index], fontSize = 21.sp) },
                                label = { Text(label, fontSize = 10.sp) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("🟩", fontSize = 27.sp)
        Text("MINECRAFT\nJAVA EDITION", fontSize = 16.sp, lineHeight = 15.sp, fontWeight = FontWeight.Black, color = Ink)
        Spacer(Modifier.weight(1f))
        Surface(color = Panel, shape = RoundedCornerShape(30.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🔔   🎮   ", fontSize = 17.sp)
                Text("CreeperFan  ⌄", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun Sidebar(tab: Int, onTab: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(26.dp)).background(Panel).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🟩", fontSize = 36.sp, modifier = Modifier.padding(vertical = 14.dp))
        listOf("⌂" to "Home", "▦" to "Installations", "♟" to "Skins", "▣" to "Marketplace", "⚙" to "Settings", "ⓘ" to "About").forEachIndexed { i, item ->
            val selected = (i == tab)
            Surface(
                onClick = { onTab(i.coerceAtMost(3)) },
                color = if (selected) Color(0xFFFFE5A5) else Color.Transparent,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
            ) {
                Column(Modifier.padding(vertical = 11.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(item.first, fontSize = 23.sp, color = Ink)
                    Text(item.second, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Surface(color = Color(0xFFFFF1D9), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(9.dp)) {
                Text("🟩 CreeperFan", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("🟢 Online", fontSize = 10.sp, color = Color(0xFF4C8B43))
                Text("Latest Release\n1.21.10", fontSize = 10.sp, color = Muted)
            }
        }
    }
}

@Composable
private fun HeroBanner(modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(28.dp)).background(
        Brush.linearGradient(listOf(Color(0xFF351D2A), Color(0xFF8D352F), Color(0xFFE47A38), Color(0xFF311D35)))
    )) {
        // Stylized block-world silhouettes keep the hero self-contained until final licensed art is supplied.
        Row(Modifier.align(Alignment.BottomEnd).padding(end = 22.dp, bottom = 28.dp), verticalAlignment = Alignment.Bottom) {
            repeat(5) { i ->
                Box(Modifier.padding(horizontal = 3.dp).size((42 + i * 12).dp).background(
                    listOf(Color(0xFF442B35), Color(0xFF5A3440), Color(0xFF8B4436), Color(0xFF382B43), Color(0xFF6D3A35))[i]
                ))
            }
        }
        Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(0.82f).padding(24.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("MINECRAFT", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
            Text("JAVA EDITION", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            Text("The End", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
            Text("awaits...", color = Gold, fontSize = 35.sp, fontWeight = FontWeight.Black)
            Text("Face the Nether, explore new challenges and discover more!", color = Color(0xFFFFEBDD), fontSize = 14.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(5.dp))
            Button(
                onClick = { },
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink),
                shape = RoundedCornerShape(28.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 13.dp)
            ) { Text("▶  Play Now", fontSize = 17.sp, fontWeight = FontWeight.Black) }
            Text("PLAY FEATURE COMING SOON", color = Color(0xFFFFE4B8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Text("◆  ◆  ◆  ◆", Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp), color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
    }
}

@Composable
private fun WhatsNew() {
    PanelCard("✨  What's New?", "View all  →") {
        Box(Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(16.dp)).background(
            Brush.linearGradient(listOf(Color(0xFF512B43), Color(0xFFE06C3B), Color(0xFF311E3A)))
        ), contentAlignment = Alignment.Center) {
            Text("THE NETHER UPDATE", color = Color.White, fontWeight = FontWeight.Black, fontSize = 19.sp)
        }
        Text("The Nether Update", fontWeight = FontWeight.Black, fontSize = 16.sp)
        Text("New biomes, new features!", color = Muted, fontSize = 13.sp)
        Text("▦   Jun 30, 2024", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun QuickLinks() {
    PanelCard("🔗  Quick Links") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickLink("◎", "Official\nWebsite", Modifier.weight(1f))
            QuickLink("▤", "Patch\nNotes", Modifier.weight(1f))
            QuickLink("🎮", "Discord", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickLink("🛒", "Marketplace", Modifier.weight(1f))
            QuickLink("♟", "Skins", Modifier.weight(1f))
            QuickLink("🎁", "Gift Codes", Modifier.weight(1f))
        }
    }
}

@Composable
private fun QuickLink(icon: String, label: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = Color(0xFFFFF0DA), shape = RoundedCornerShape(15.dp)) {
        Column(Modifier.padding(vertical = 11.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, fontSize = 21.sp)
            Text(label, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium, color = Ink)
        }
    }
}

@Composable
private fun FeaturedNews() {
    PanelCard("▤  Featured News", "View all  →") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NewsCard("🌸", "Spring to Life", "A fresh new look for Minecraft!", "Mar 25, 2025", Color(0xFFF0B8C9))
            NewsCard("🔥", "The Tricky Trials Update", "Take on new challenges!", "Jun 13, 2024", Color(0xFFE9A35B))
            NewsCard("🐪", "Camel! Ride in Style", "Explore the desert with camels.", "Apr 3, 2024", Color(0xFFE5C18D))
        }
    }
}

@Composable
private fun NewsCard(emoji: String, title: String, description: String, date: String, art: Color) {
    Surface(color = Color(0xFFFFF4E6), shape = RoundedCornerShape(18.dp), modifier = Modifier.width(218.dp)) {
        Column {
            Box(Modifier.fillMaxWidth().height(95.dp).background(Brush.linearGradient(listOf(art, Color(0xFF9C7759)))), contentAlignment = Alignment.Center) {
                Text(emoji, fontSize = 44.sp)
            }
            Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, fontWeight = FontWeight.Black, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(description, color = Muted, fontSize = 11.sp, maxLines = 2)
                Text("▦  $date     →", color = Muted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun RealmsCard() {
    Surface(color = Color(0xFFFFF0D9), shape = RoundedCornerShape(22.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("👑  Join Realms", fontWeight = FontWeight.Black, fontSize = 16.sp)
                Text("Play with friends anywhere, anytime!", fontSize = 12.sp, color = Muted)
                Text("Learn More  →", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Text("🏰", fontSize = 50.sp)
        }
    }
}

@Composable
private fun PanelCard(title: String, action: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(22.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border.copy(alpha = .7f)), shadowElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.Black, fontSize = 16.sp)
                if (action != null) {
                    Spacer(Modifier.weight(1f))
                    Text(action, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Muted)
                }
            }
            content()
        }
    }
}

@Composable
private fun Destination(tab: Int) {
    val title = listOf("Home", "Installations", "Skins", "Settings")[tab.coerceIn(0, 3)]
    Surface(color = Panel, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontSize = 27.sp, fontWeight = FontWeight.Black)
            Text(
                when (tab) {
                    1 -> "Your Minecraft versions and game profiles will appear here."
                    2 -> "Preview and manage your player skins here."
                    else -> "Customize O Launcher preferences here."
                },
                color = Muted,
                fontSize = 14.sp
            )
            Text("This section is under development.", color = Color(0xFF9B6A25), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}
