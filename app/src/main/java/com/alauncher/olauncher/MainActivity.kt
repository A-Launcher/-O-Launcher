package com.alauncher.olauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Cream = Color(0xFFF4EFE4)
private val Ink = Color(0xFF292821)
private val Gold = Color(0xFFD69B38)
private val Muted = Color(0xFF817B70)
private val Card = Color(0xFFFFFCF5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OLauncherApp() }
    }
}

@Composable
private fun OLauncherApp() {
    var selectedTab by remember { mutableIntStateOf(0) }
    MaterialTheme {
        Scaffold(
            containerColor = Cream,
            bottomBar = {
                NavigationBar(containerColor = Card) {
                    val items = listOf(
                        "Home" to Icons.Default.Home,
                        "Versions" to Icons.Default.Extension,
                        "Profile" to Icons.Default.Person,
                        "Settings" to Icons.Default.Settings
                    )
                    items.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Icon(item.second, contentDescription = item.first) },
                            label = { Text(item.first) }
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("O LAUNCHER", color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Black)
                        Text("Your blocky worlds, together.", color = Muted, fontSize = 13.sp)
                    }
                    Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFE8D9BC)) {
                        Text("OL", modifier = Modifier.padding(13.dp), color = Ink, fontWeight = FontWeight.Bold)
                    }
                }

                when (selectedTab) {
                    0 -> HomeContent()
                    1 -> PlaceholderContent("Versions", "Your installed Minecraft versions will appear here.")
                    2 -> PlaceholderContent("Player profile", "Connect your Minecraft account in a future update.")
                    else -> PlaceholderContent("Settings", "Launcher preferences will live here.")
                }
            }
        }
    }
}

@Composable
private fun HomeContent() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(shape = RoundedCornerShape(28.dp), color = Color.Transparent) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF4B3432), Color(0xFF9B5938), Color(0xFFD19A54)))
                    )
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("YOUR NEXT ADVENTURE", color = Color(0xFFFFE4B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Ready to
return home?", color = Color.White, fontSize = 31.sp, lineHeight = 35.sp, fontWeight = FontWeight.Black)
                Text("A cozy home for your Minecraft Java worlds.", color = Color(0xFFFFEBDD), fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { },
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("PLAY  ·  COMING SOON", fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        Text("QUICK ACCESS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoCard("VERSION", "Not installed", Modifier.weight(1f))
            InfoCard("MEMORY", "Auto managed", Modifier.weight(1f))
        }
        Surface(color = Card, shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("A little note", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    "The launcher interface is taking shape. Game downloads, account sign-in, and the Android Java runtime are still under development.",
                    color = Muted,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = Card, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(value, color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun PlaceholderContent(title: String, description: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = Ink, fontSize = 27.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Text(description, color = Muted, fontSize = 14.sp)
    }
}
