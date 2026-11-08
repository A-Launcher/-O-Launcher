package com.alauncher.olauncher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Toast
import git.artdeell.dnbootstrap.glfw.GLFW
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private val Cream = Color(0xFFF6EBDD)
private val Panel = Color(0xFFFFF8EE)
private val Ink = Color(0xFF211F1B)
private val Muted = Color(0xFF766E64)
private val Gold = Color(0xFFFFC64D)
private val Border = Color(0xFFEADBC8)

private data class LauncherImages(
    val hero: ImageBitmap,
    val logo: ImageBitmap,
    val avatar: ImageBitmap,
    val whatsNew: ImageBitmap,
    val spring: ImageBitmap,
    val trials: ImageBitmap,
    val camel: ImageBitmap,
    val realms: ImageBitmap,
    val realmsCrown: ImageBitmap,
    val installations: ImageBitmap,
    val skins: ImageBitmap,
    val skinsScreen: ImageBitmap,
    val marketplace: ImageBitmap,
    val settings: ImageBitmap,
    val about: ImageBitmap,
    val home: ImageBitmap,
    val folder: ImageBitmap,
    val quickOfficial: ImageBitmap,
    val quickPatch: ImageBitmap,
    val quickDiscord: ImageBitmap,
    val quickMarketplace: ImageBitmap,
    val quickSkins: ImageBitmap,
    val quickGiftCodes: ImageBitmap
)

private fun fallbackBitmap(color: Int): Bitmap =
    Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

private fun createArtworkAtlas(): Bitmap {
    val bitmap = Bitmap.createBitmap(720, 579, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    fun rect(x: Float, y: Float, w: Float, h: Float, color: Int) {
        paint.color = color
        canvas.drawRect(x, y, x + w, y + h, paint)
    }
    fun circle(x: Float, y: Float, r: Float, color: Int) {
        paint.color = color
        canvas.drawCircle(x, y, r, paint)
    }
    fun polygon(points: List<Pair<Float, Float>>, color: Int) {
        val path = android.graphics.Path()
        path.moveTo(points.first().first, points.first().second)
        points.drop(1).forEach { path.lineTo(it.first, it.second) }
        path.close()
        paint.color = color
        canvas.drawPath(path, paint)
    }
    fun gradient(x: Float, y: Float, w: Float, h: Float, top: Int, bottom: Int) {
        paint.shader = android.graphics.LinearGradient(x, y, x, y + h, top, bottom, android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRect(x, y, x + w, y + h, paint)
        paint.shader = null
    }

    // Main Nether hero artwork, composed from block-like shapes at the same atlas coordinates.
    gradient(0f, 0f, 720f, 319f, android.graphics.Color.rgb(53, 22, 48), android.graphics.Color.rgb(255, 94, 27))
    for (i in 0..34) {
        val x = ((i * 137 + 41) % 720).toFloat()
        val y = ((i * 61 + 17) % 210).toFloat()
        circle(x, y, (12 + i % 20).toFloat(), android.graphics.Color.rgb(82 + i % 65, 24 + i % 24, 42 + i % 18))
    }
    polygon(listOf(0f to 230f, 90f to 170f, 165f to 230f, 260f to 154f, 360f to 230f, 470f to 160f, 570f to 230f, 650f to 155f, 720f to 220f, 720f to 319f, 0f to 319f), android.graphics.Color.rgb(54, 31, 43))
    for (i in 0..5) {
        val x = (35 + i * 128).toFloat()
        rect(x, 82f + (i % 3) * 19, 18f, 170f, android.graphics.Color.rgb(255, 103, 19))
        rect(x + 5, 82f + (i % 3) * 19, 5f, 170f, android.graphics.Color.rgb(255, 194, 48))
    }
    for (i in 0..8) {
        val x = ((i * 83 + 16) % 700).toFloat()
        rect(x, 175f - (i % 4) * 15, 12f, 145f, android.graphics.Color.rgb(38, 24, 39))
        for (j in 0..3) circle(x + (j - 1) * 13f, 158f - (j % 2) * 20f, 18f, android.graphics.Color.rgb(112 + j * 8, 25, 51))
    }
    // Purple Nether portal
    rect(552f, 143f, 100f, 130f, android.graphics.Color.rgb(20, 13, 34))
    rect(562f, 153f, 80f, 110f, android.graphics.Color.rgb(104, 29, 195))
    rect(571f, 162f, 62f, 92f, android.graphics.Color.rgb(193, 49, 255))
    rect(579f, 170f, 46f, 76f, android.graphics.Color.rgb(117, 47, 222))
    // Floating ghast
    rect(267f, 91f, 38f, 32f, android.graphics.Color.rgb(224, 215, 211))
    rect(275f, 100f, 6f, 8f, android.graphics.Color.BLACK)
    rect(292f, 100f, 6f, 8f, android.graphics.Color.BLACK)
    // Player with diamond sword
    rect(337f, 157f, 46f, 55f, android.graphics.Color.rgb(93, 53, 34))
    rect(345f, 166f, 30f, 30f, android.graphics.Color.rgb(181, 119, 76))
    rect(327f, 211f, 65f, 66f, android.graphics.Color.rgb(14, 128, 139))
    rect(317f, 219f, 15f, 49f, android.graphics.Color.rgb(14, 128, 139))
    rect(389f, 219f, 14f, 45f, android.graphics.Color.rgb(14, 128, 139))
    rect(335f, 275f, 23f, 43f, android.graphics.Color.rgb(35, 42, 87))
    rect(367f, 275f, 23f, 43f, android.graphics.Color.rgb(35, 42, 87))
    rect(405f, 226f, 8f, 54f, android.graphics.Color.rgb(35, 221, 238))
    rect(399f, 243f, 22f, 8f, android.graphics.Color.rgb(24, 180, 202))
    for (i in 0..90) {
        val x = ((i * 71 + 19) % 720).toFloat()
        val y = ((i * 37 + 11) % 319).toFloat()
        rect(x, y, 2f, 3f, android.graphics.Color.rgb(255, 174 + i % 70, 34))
    }

    // News thumbnails and the floating Realms island.
    fun thumb(x: Float, y: Float, w: Float, h: Float, top: Int, ground: Int, variant: Int) {
        gradient(x, y, w, h, top, ground)
        polygon(listOf(x to (y + h * .65f), (x + w * .2f) to (y + h * .35f), (x + w * .42f) to (y + h * .68f), (x + w * .66f) to (y + h * .3f), (x + w) to (y + h * .65f), (x + w) to (y + h), x to (y + h)), android.graphics.Color.rgb(67, 101, 67))
        if (variant == 0) {
            for (tx in listOf(x + 30, x + w - 35)) {
                rect(tx, y + 28, 7f, h - 20, android.graphics.Color.rgb(93, 57, 35))
                for (k in 0..7) circle(tx + ((k * 13) % 43) - 18, y + 15 + ((k * 17) % 42), 9f, android.graphics.Color.rgb(250, 151 + k % 70, 196 + k % 40))
            }
            rect(x + w * .39f, y + h * .42f, w * .25f, h * .42f, android.graphics.Color.rgb(186, 125, 74))
            polygon(listOf((x + w * .36f) to (y + h * .45f), (x + w * .51f) to (y + h * .18f), (x + w * .67f) to (y + h * .45f)), android.graphics.Color.rgb(103, 57, 52))
        } else if (variant == 1) {
            rect(x + w * .30f, y + h * .22f, w * .4f, h * .66f, android.graphics.Color.rgb(28, 84, 112))
            rect(x + w * .4f, y + h * .28f, w * .2f, h * .56f, android.graphics.Color.rgb(255, 131, 34))
            rect(x + w * .47f, y + h * .22f, w * .08f, h * .66f, android.graphics.Color.rgb(255, 209, 81))
        } else {
            polygon(listOf((x + 4) to (y + h), (x + w * .2f) to (y + h * .2f), (x + w * .42f) to (y + h)), android.graphics.Color.rgb(199, 145, 81))
            polygon(listOf((x + w * .42f) to (y + h), (x + w * .66f) to (y + h * .12f), (x + w * .9f) to (y + h)), android.graphics.Color.rgb(218, 166, 98))
            rect(x + w * .54f, y + h * .45f, w * .2f, h * .22f, android.graphics.Color.rgb(184, 113, 47))
            rect(x + w * .69f, y + h * .25f, 10f, 25f, android.graphics.Color.rgb(184, 113, 47))
            rect(x + w * .57f, y + h * .62f, 6f, 19f, android.graphics.Color.rgb(87, 62, 42))
            rect(x + w * .73f, y + h * .62f, 6f, 19f, android.graphics.Color.rgb(87, 62, 42))
        }
    }
    thumb(0f, 329f, 257f, 110f, android.graphics.Color.rgb(87, 28, 57), android.graphics.Color.rgb(255, 112, 31), 1)
    thumb(267f, 329f, 240f, 87f, android.graphics.Color.rgb(134, 194, 246), android.graphics.Color.rgb(73, 135, 75), 0)
    thumb(0f, 449f, 239f, 90f, android.graphics.Color.rgb(26, 94, 145), android.graphics.Color.rgb(28, 39, 62), 1)
    thumb(249f, 449f, 240f, 87f, android.graphics.Color.rgb(137, 198, 238), android.graphics.Color.rgb(214, 160, 87), 2)
    gradient(499f, 449f, 110f, 130f, android.graphics.Color.rgb(116, 186, 243), android.graphics.Color.rgb(243, 207, 145))
    polygon(listOf(504f to 515f, 606f to 515f, 578f to 555f, 552f to 579f, 529f to 554f), android.graphics.Color.rgb(91, 67, 49))
    polygon(listOf(504f to 515f, 550f to 491f, 606f to 515f, 576f to 535f, 526f to 534f), android.graphics.Color.rgb(75, 147, 69))
    rect(540f, 478f, 34f, 35f, android.graphics.Color.rgb(193, 143, 90))
    rect(535f, 471f, 44f, 10f, android.graphics.Color.rgb(104, 67, 52))
    rect(550f, 489f, 9f, 23f, android.graphics.Color.rgb(236, 197, 129))
    // Creeper face avatar
    rect(620f, 449f, 62f, 64f, android.graphics.Color.rgb(36, 173, 65))
    rect(630f, 459f, 42f, 44f, android.graphics.Color.rgb(23, 132, 47))
    rect(637f, 468f, 8f, 8f, android.graphics.Color.BLACK)
    rect(657f, 468f, 8f, 8f, android.graphics.Color.BLACK)
    rect(644f, 480f, 15f, 16f, android.graphics.Color.BLACK)
    rect(638f, 480f, 7f, 8f, android.graphics.Color.BLACK)
    rect(658f, 480f, 7f, 8f, android.graphics.Color.BLACK)
    return bitmap
}

private fun createRealmsArtwork(): ImageBitmap {
    val width = 420
    val height = 320
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    fun rect(x: Float, y: Float, w: Float, h: Float, c: Int) { p.color=c; canvas.drawRect(x,y,x+w,y+h,p) }
    fun round(x: Float, y: Float, w: Float, h: Float, r: Float, c: Int) { p.color=c; canvas.drawRoundRect(x,y,x+w,y+h,r,r,p) }
    fun poly(points: List<Pair<Float,Float>>, c: Int) {
        val path=android.graphics.Path(); path.moveTo(points[0].first,points[0].second)
        points.drop(1).forEach { path.lineTo(it.first,it.second) }; path.close()
        p.color=c; canvas.drawPath(path,p)
    }
    p.shader=android.graphics.LinearGradient(0f,0f,0f,height.toFloat(),android.graphics.Color.rgb(116,190,255),android.graphics.Color.rgb(255,222,171),android.graphics.Shader.TileMode.CLAMP)
    canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),p); p.shader=null
    // Soft clouds and distant floating land
    p.color=android.graphics.Color.argb(150,255,255,255)
    canvas.drawCircle(70f,55f,25f,p); canvas.drawCircle(95f,49f,20f,p); canvas.drawCircle(117f,58f,16f,p)
    canvas.drawCircle(330f,76f,20f,p); canvas.drawCircle(350f,68f,17f,p); canvas.drawCircle(368f,78f,13f,p)
    poly(listOf(0f to 207f,58f to 171f,110f to 195f,177f to 158f,235f to 193f,300f to 162f,420f to 198f,420f to 247f,0f to 247f),android.graphics.Color.rgb(133,181,119))
    // Island top and tapered dirt underside
    poly(listOf(35f to 190f,94f to 154f,172f to 165f,242f to 143f,345f to 178f,390f to 203f,357f to 229f,319f to 268f,264f to 288f,206f to 310f,151f to 286f,101f to 263f,62f to 228f),android.graphics.Color.rgb(83,119,60))
    poly(listOf(35f to 190f,94f to 154f,172f to 165f,242f to 143f,345f to 178f,390f to 203f,357f to 229f,319f to 268f,264f to 288f,206f to 310f,151f to 286f,101f to 263f,62f to 228f),android.graphics.Color.rgb(117,77,49))
    poly(listOf(35f to 190f,94f to 154f,172f to 165f,242f to 143f,345f to 178f,390f to 203f,365f to 217f,285f to 207f,209f to 225f,130f to 210f,64f to 214f),android.graphics.Color.rgb(80,145,66))
    // Castle central keep
    rect(166f,89f,105f,89f,android.graphics.Color.rgb(211,177,130))
    rect(158f,80f,121f,15f,android.graphics.Color.rgb(120,77,55))
    rect(178f,113f,22f,34f,android.graphics.Color.rgb(91,66,54))
    rect(235f,113f,22f,34f,android.graphics.Color.rgb(91,66,54))
    rect(205f,124f,27f,54f,android.graphics.Color.rgb(133,83,48))
    // Main roof and battlements
    poly(listOf(151f to 91f,218f to 39f,286f to 91f),android.graphics.Color.rgb(110,67,52))
    rect(174f,52f,14f,29f,android.graphics.Color.rgb(208,173,124))
    rect(250f,52f,14f,29f,android.graphics.Color.rgb(208,173,124))
    poly(listOf(165f to 54f,181f to 27f,197f to 54f),android.graphics.Color.rgb(91,57,48))
    poly(listOf(242f to 54f,257f to 27f,273f to 54f),android.graphics.Color.rgb(91,57,48))
    // Left and right towers
    rect(113f,106f,47f,78f,android.graphics.Color.rgb(224,190,144))
    rect(108f,94f,57f,15f,android.graphics.Color.rgb(121,76,53))
    poly(listOf(106f to 96f,136f to 57f,167f to 96f),android.graphics.Color.rgb(105,61,48))
    rect(277f,105f,47f,79f,android.graphics.Color.rgb(224,190,144))
    rect(272f,93f,57f,15f,android.graphics.Color.rgb(121,76,53))
    poly(listOf(270f to 95f,301f to 56f,331f to 95f),android.graphics.Color.rgb(105,61,48))
    // Windows, door and warm highlights
    for (x in listOf(126f,145f,190f,245f,290f,308f)) {
        round(x,119f,8f,18f,4f,android.graphics.Color.rgb(255,213,116))
    }
    rect(208f,148f,20f,30f,android.graphics.Color.rgb(91,59,44))
    // Trees and shrubs on the island
    for ((x,y) in listOf(55f to 170f,78f to 160f,344f to 165f,369f to 183f)) {
        rect(x+9,y+12,7f,24f,android.graphics.Color.rgb(105,72,45))
        p.color=android.graphics.Color.rgb(65,139,63); canvas.drawCircle(x,y,19f,p)
        p.color=android.graphics.Color.rgb(93,169,74); canvas.drawCircle(x+10,y-8,12f,p)
    }
    return bmp.asImageBitmap()
}

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

private suspend fun fetchArtwork(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
    var connection: HttpURLConnection? = null
    try {
        connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 12000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "O-Launcher/1.0")
        }
        if (connection.responseCode !in 200..299) return@withContext null
        connection.inputStream.use { stream ->
            BitmapFactory.decodeStream(stream)?.asImageBitmap()
        }
    } catch (_: Exception) {
        null
    } finally {
        connection?.disconnect()
    }
}

private fun readAssetImage(context: android.content.Context, file: String): ImageBitmap? {
    return try {
        context.assets.open(file).use { input ->
            BitmapFactory.decodeStream(input)?.asImageBitmap()
        }
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun rememberLauncherImages(): LauncherImages {
    val context = LocalContext.current
    val localImages = remember(context) {
        val atlas = createArtworkAtlas()
        val hero = readAssetImage(context, "hero_nether.png")
            ?: cropBitmapSafe(atlas, 0, 0, 720, 319, android.graphics.Color.DKGRAY)
        val logo = readAssetImage(context, "minecraft_java_logo.png")
            ?: readBitmap(context, "brand_logo.webp", android.graphics.Color.DKGRAY).asImageBitmap()
        LauncherImages(
            hero = hero,
            // No separate What's New image was included in the uploads; use the supplied Nether
            // scene rather than incorrectly showing the Tricky Trials card artwork here.
            whatsNew = hero,
            spring = readAssetImage(context, "news_spring.png")
                ?: cropBitmapSafe(atlas, 267, 329, 240, 87, android.graphics.Color.MAGENTA),
            trials = readAssetImage(context, "news_trials.png")
                ?: cropBitmapSafe(atlas, 0, 449, 239, 90, android.graphics.Color.BLUE),
            camel = readAssetImage(context, "news_camel.png")
                ?: cropBitmapSafe(atlas, 249, 449, 240, 87, android.graphics.Color.YELLOW),
            realms = readAssetImage(context, "reference_ui/59c8b720-d9cf-11f1-9dc3-a5410c9336b4.png")
                ?: createRealmsArtwork(),
            realmsCrown = readAssetImage(context, "reference_ui/IMG_20261106_130717.png")
                ?: cropBitmapSafe(atlas, 620, 449, 62, 64, android.graphics.Color.YELLOW),
            installations = readAssetImage(context, "488492b1bb75fdbe827502ca1971af25-removebg-preview.png") ?: cropBitmapSafe(atlas, 0, 449, 239, 90, android.graphics.Color.BLUE),
            skins = readAssetImage(context, "c09fde2532c9b9645a64208dc1500146-removebg-preview.png") ?: cropBitmapSafe(atlas, 249, 449, 240, 87, android.graphics.Color.YELLOW),
            skinsScreen = readAssetImage(context, "IMG_20261106_143355.png") ?: fallbackBitmap(android.graphics.Color.DKGRAY).asImageBitmap(),
            marketplace = readAssetImage(context, "2c484ef06d618170c43f2a2d288a85b4-removebg-preview.png") ?: cropBitmapSafe(atlas, 267, 329, 240, 87, android.graphics.Color.MAGENTA),
            settings = readAssetImage(context, "39667c1838a0eb444686e797eccf1cd1-removebg-preview.png") ?: cropBitmapSafe(atlas, 620, 449, 62, 64, android.graphics.Color.GRAY),
            about = readAssetImage(context, "203e305e530e01e9668893e7aad11194-removebg-preview.png") ?: cropBitmapSafe(atlas, 620, 449, 62, 64, android.graphics.Color.LTGRAY),
            home = readAssetImage(context, "04dcbaa24e1e838ef4beb7e104552196-removebg-preview.png") ?: cropBitmapSafe(atlas, 620, 449, 62, 64, android.graphics.Color.GREEN),
            folder = readAssetImage(context, "614f83ddf7fa4a294bee8fd9f527ba28-removebg-preview.png") ?: fallbackBitmap(android.graphics.Color.TRANSPARENT).asImageBitmap(),
            quickOfficial = readAssetImage(context, "c6d2a4480dd5f44520b7e5dfcf892dcd-removebg-preview.png") ?: fallbackBitmap(android.graphics.Color.LTGRAY).asImageBitmap(),
            quickPatch = readAssetImage(context, "1472e22819d836c1f4b3c7f973e932d7-removebg-preview.png") ?: fallbackBitmap(android.graphics.Color.LTGRAY).asImageBitmap(),
            quickDiscord = readAssetImage(context, "626064dcb825a8d549cc95fcb7e88786-removebg-preview.png") ?: fallbackBitmap(android.graphics.Color.LTGRAY).asImageBitmap(),
            quickMarketplace = readAssetImage(context, "27803d3ba6cc566240a2fe9f6d8aaf95-removebg-preview.png") ?: fallbackBitmap(android.graphics.Color.LTGRAY).asImageBitmap(),
            quickSkins = readAssetImage(context, "df3cdf5396a34ae2109c4981c1a4b939-removebg-preview.png") ?: fallbackBitmap(android.graphics.Color.LTGRAY).asImageBitmap(),
            quickGiftCodes = readAssetImage(context, "4ff903d3d3b2771e9888991258adc96a-removebg-preview.png") ?: fallbackBitmap(android.graphics.Color.LTGRAY).asImageBitmap(),
            avatar = readAssetImage(context, "reference_ui/IMG_20261106_130824.png")
                ?: cropBitmapSafe(atlas, 620, 449, 62, 64, android.graphics.Color.GREEN),
            logo = logo
        )
    }
    return localImages
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
                    TopBar(images, Modifier.fillMaxWidth().height(dashboardHeight * 0.095f).padding(top = 12.dp), onAccountClick = { tab = 7 })
                    Spacer(Modifier.height(gap))
                    Row(
                        Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        Column(
                            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(gap)
                        ) {
                            when (tab) {
                                0 -> {
                                    HeroBanner(images, Modifier.fillMaxWidth().height(heroHeight)) { showPlayInfo = true }
                                    FeaturedNews(images, Modifier.fillMaxWidth().height(newsHeight))
                                }
                                1 -> InstallationsDestination(images)
                                2 -> SkinDestination(images)
                                3 -> MarketplaceDestination(images, dashboardHeight * 0.88f)
                                4 -> SettingsDestination(images)
                                5 -> AboutDestination(images)
                                6 -> LauncherFilesDestination(images)
                                else -> AccountDestination(images)
                            }
                        }
                        if (tab == 0) {
                            Column(
                                Modifier.width(rightRailWidth).fillMaxHeight().verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(gap)
                            ) {
                                WhatsNew(images, Modifier.fillMaxWidth().height(dashboardHeight * 0.35f))
                                QuickLinks(images, Modifier.fillMaxWidth().height(dashboardHeight * 0.265f)) { destination -> tab = destination }
                                RealmsCard(images, Modifier.fillMaxWidth().height(dashboardHeight * 0.205f))
                            }
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
private fun TopBar(images: LauncherImages, modifier: Modifier = Modifier, onAccountClick: () -> Unit = {}) {
    var showNotifications by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier.width(165.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(images.logo, contentDescription = "Minecraft Java Edition", modifier = Modifier.width(195.dp).height(60.dp), contentScale = ContentScale.Fit)
        }
        Surface(
            Modifier.weight(1f).height(42.dp),
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
                            if (query.isEmpty()) Text("Search for anything...", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            inner()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Box {
            Surface(onClick = { showNotifications = true }, color = Panel, shape = CircleShape, modifier = Modifier.size(40.dp)) {
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
            onClick = onAccountClick,
            color = Panel,
            shape = RoundedCornerShape(28.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Border)
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(images.avatar, contentDescription = "Player avatar", modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(8.dp))
                Text("CREEPERFAN", fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = (-0.25).sp)
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
        "HOME" to 0,
        "INSTALLATIONS" to 1,
        "SKINS" to 2,
        "MARKETPLACE" to 3,
        "SETTINGS" to 4,
        "ABOUT" to 5,
        "FOLDER" to 6
    )
    Column(
        modifier.clip(RoundedCornerShape(25.dp)).background(Panel).padding(horizontal = 7.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(images.avatar, contentDescription = "Minecraft player avatar", modifier = Modifier.padding(vertical = 7.dp).size(48.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
        Spacer(Modifier.height(7.dp))
        entries.forEach { (label, index) ->
            val selected = tab == index
            Surface(
                onClick = { onTab(index) },
                color = if (selected) Color(0xFFFFE5A5) else Color.Transparent,
                shape = RoundedCornerShape(19.dp),
                shadowElevation = if (selected) 5.dp else 0.dp,
                border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD979)) else null,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(
                    Modifier.padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (index == 6) {
                        Image(images.folder, contentDescription = label, modifier = Modifier.size(44.dp), contentScale = ContentScale.Fit)
                    } else if (index == 0) {
                        Image(images.home, contentDescription = label, modifier = Modifier.size(44.dp), contentScale = ContentScale.Fit)
                    } else {
                        val sidebarIcon = when (index) {
                            1 -> images.installations
                            2 -> images.skins
                            3 -> images.marketplace
                            4 -> images.settings
                            else -> images.about
                        }
                        Image(sidebarIcon, contentDescription = label, modifier = Modifier.size(44.dp), contentScale = ContentScale.Fit)
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(label, fontSize = 10.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, color = Ink, maxLines = 1, letterSpacing = (-0.35).sp)
                }
            }
        }
    }
}

@Composable
private fun HeroBanner(images: LauncherImages, modifier: Modifier = Modifier, onPlay: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(27.dp))) {
        Image(images.hero, contentDescription = "Minecraft featured banner", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
        Box(
            Modifier.align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(0.46f)
                .background(Brush.horizontalGradient(listOf(Color(0xCC1A0808), Color(0x001A0808))))
                // The supplied hero artwork already contains the Minecraft Java logo.
                // Keep the headline and controls below that baked-in logo, as in the reference.
                .padding(start = 25.dp, top = 108.dp, bottom = 12.dp),
            contentAlignment = Alignment.TopStart
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.height(2.dp))
                Text("The End", color = Color.White, fontSize = 37.sp, lineHeight = 39.sp, fontWeight = FontWeight.Black)
                Text("awaits...", color = Gold, fontSize = 37.sp, lineHeight = 39.sp, fontWeight = FontWeight.Black)
                Text("Face the Nether, explore new challenges and discover more!", color = Color.White, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(onClick = onPlay, color = Gold, contentColor = Ink, shape = RoundedCornerShape(28.dp), modifier = Modifier.width(220.dp).height(52.dp), shadowElevation = 5.dp) { Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.SportsEsports, contentDescription = null, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(10.dp)); Text("Play Now", fontSize = 19.sp, fontWeight = FontWeight.Bold) } }
                    Surface(onClick = onPlay, color = Color(0xFFFFF8EE), shape = RoundedCornerShape(20.dp), modifier = Modifier.width(54.dp).height(52.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.ExpandMore, contentDescription = "More play options", tint = Ink, modifier = Modifier.size(25.dp)) }
                    }
                }
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(5) { index ->
                Box(
                    Modifier.width(if (index == 0) 19.dp else 7.dp).height(7.dp)
                        .clip(CircleShape).background(if (index == 0) Color.White else Color.White.copy(alpha = .72f))
                )
            }
        }
    }
}

@Composable
private fun WhatsNew(images: LauncherImages, modifier: Modifier = Modifier) {
    PanelCard("What’s New?", "View all  →", modifier) {
        Image(images.whatsNew, contentDescription = "What's new artwork", modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(15.dp)), contentScale = ContentScale.Crop)
        Text("The Nether Update", fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = (-0.25).sp)
        Text("New biomes, new features!", color = Muted, fontWeight = FontWeight.Normal, fontSize = 13.sp, letterSpacing = 0.1.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CalendarToday, contentDescription = null, tint = Muted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(7.dp))
            Text("Jun 30, 2024", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Normal)
        }
    }
}

@Composable
private fun QuickLinks(images: LauncherImages, modifier: Modifier = Modifier, onNavigate: (Int) -> Unit) {
    val context = LocalContext.current
    fun openPage(url: String) { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { } }
    PanelCard("Quick Links", null, modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.weight(1f)) {
            QuickLink(images.quickOfficial, "Official\\nWebsite", Modifier.height(68.dp).weight(1f)) { openPage("https://www.minecraft.net/en-us") }
            QuickLink(images.quickPatch, "Patch\\nNotes", Modifier.height(59.dp).weight(1f)) { openPage("https://www.minecraft.net/en-us/articles") }
            QuickLink(images.quickDiscord, "Discord", Modifier.height(59.dp).weight(1f)) { openPage("https://www.minecraft.net/en-us/community") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.weight(1f)) {
            QuickLink(images.quickMarketplace, "Marketplace", Modifier.height(59.dp).weight(1f)) { onNavigate(3) }
            QuickLink(images.quickSkins, "Skins", Modifier.height(59.dp).weight(1f)) { onNavigate(2) }
            QuickLink(images.quickGiftCodes, "Gift Codes", Modifier.height(59.dp).weight(1f)) { openPage("https://www.minecraft.net/redeem") }
        }
    }
}

@Composable
private fun QuickLink(icon: ImageBitmap, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = Color(0xFFFFF1DC),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFE5C5)),
        shadowElevation = 4.dp
    ) {
        Column(
            Modifier.fillMaxSize().padding(vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(icon, contentDescription = label, modifier = Modifier.size(34.dp), contentScale = ContentScale.Fit)
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 11.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, color = Ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun FeaturedNews(images: LauncherImages, modifier: Modifier = Modifier) {
    var selectedNews by remember { mutableStateOf<String?>(null) }
    PanelCard("Featured News", "View all  →", modifier) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NewsCard(images.spring, "SPRING TO LIFE", "A FRESH NEW LOOK FOR MINECRAFT!", "Mar 25, 2025", Modifier.weight(1f)) { selectedNews = "SPRING TO LIFE" }
            NewsCard(images.trials, "THE TRICKY TRIALS UPDATE", "TAKE ON NEW CHALLENGES!", "Jun 13, 2024", Modifier.weight(1f)) { selectedNews = "THE TRICKY TRIALS UPDATE" }
            NewsCard(images.camel, "CAMEL! RIDE IN STYLE", "EXPLORE THE DESERT WITH CAMELS.", "Apr 3, 2024", Modifier.weight(1f)) { selectedNews = "CAMEL! RIDE IN STYLE" }
        }
    }
    selectedNews?.let { title ->
        AlertDialog(
            onDismissRequest = { selectedNews = null },
            title = { Text(title) },
            text = { Text("Minecraft news article preview. Article links will be connected in the news browser stage.") },
            confirmButton = {
                TextButton(onClick = { selectedNews = null }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun NewsCard(
    image: ImageBitmap,
    title: String,
    description: String,
    date: String,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit
) {
    Surface(
        color = Color(0xFFFFF4E6),
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 1.dp,
        modifier = modifier
    ) {
        Column(Modifier.fillMaxSize()) {
            Image(
                image,
                contentDescription = title,
                modifier = Modifier.fillMaxWidth().height(104.dp)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
                // Preserve the complete uploaded thumbnail, including its News badge.
                contentScale = ContentScale.FillBounds
            )
            Column(
                Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = (-0.2).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(description, color = Muted, fontWeight = FontWeight.Normal, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CalendarToday, contentDescription = null, tint = Muted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(date, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Normal)
                    Spacer(Modifier.weight(1f))
                    Surface(
                        onClick = onOpen,
                        color = Color(0xFFFFD978),
                        shape = CircleShape,
                        shadowElevation = 3.dp,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("→", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RealmsCard(images: LauncherImages, modifier: Modifier = Modifier) {
    Surface(
        color = Color(0xFFFFF0D9),
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFE2B8)),
        shadowElevation = 2.dp,
        modifier = modifier
    ) {
        Row(Modifier.fillMaxSize().padding(start = 13.dp, top = 9.dp, bottom = 9.dp, end = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        bitmap = images.realmsCrown,
                        contentDescription = "Realms",
                        modifier = Modifier.size(23.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("JOIN REALMS", fontWeight = FontWeight.Black, fontSize = 16.sp, fontStyle = FontStyle.Italic, maxLines = 1)
                }
                Text("Play with friends anywhere, anytime!", fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, color = Ink)
                Surface(onClick = { }, color = Ink, shape = RoundedCornerShape(25.dp), shadowElevation = 2.dp) {
                    Text("LEARN MORE  →", color = Color.White, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp))
                }
            }
            Image(
                images.realms,
                contentDescription = "Minecraft Realms floating island castle",
                modifier = Modifier.width(112.dp).fillMaxHeight().clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)),
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center
            )
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
                Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = (-0.35).sp)
                if (action != null) {
                    Spacer(Modifier.weight(1f))
                    Text(action.replace("VIEW ALL", "View all"), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Muted)
                }
            }
            content()
        }
    }
}


@Composable
private fun LauncherFilesDestination(images: LauncherImages) {
    val context = LocalContext.current
    val root = remember(context) { OEngineInstanceManager.active(context).root.apply { mkdirs() } }
    var current by remember(root) { mutableStateOf(root) }
    var refresh by remember { mutableIntStateOf(0) }
    var showCreate by remember { mutableStateOf(false) }
    var showNewFile by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var editingFile by remember { mutableStateOf<File?>(null) }
    var editorText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var gridMode by remember { mutableStateOf(true) }
    var pendingDelete by remember { mutableStateOf<File?>(null) }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            try {
                val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val column = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && column >= 0) cursor.getString(column) else null
                } ?: "imported_file"
                val destination = current.resolve(File(name).name)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destination.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (_: Exception) { message = "Could not import one of the selected files." }
        }
        refresh++
    }
    val entries = remember(current, refresh, search) {
        current.listFiles()?.filter { search.isBlank() || it.name.contains(search, ignoreCase = true) }
            ?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
    }
    val parent = current.parentFile?.takeIf { current != root && it.canonicalPath.startsWith(root.canonicalPath) }
    val storageBytes = remember(root, refresh) {
        runCatching { root.walkTopDown().filter { it.isFile }.sumOf { it.length() } }.getOrDefault(0L)
    }
    val shortcuts = listOf(
        "Home" to root,
        "Internal Storage" to root,
        "Downloads" to File(root, "downloads").apply { mkdirs() },
        "Minecraft" to root,
        "Screenshots" to File(root, "screenshots"),
        "Backups" to File(root, "backups"),
        "Mods" to File(root, "mods")
    )

    Column(Modifier.fillMaxWidth().fillMaxHeight().padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        DestinationHero(images, "File Manager", "Browse, manage and organize your Minecraft files.", "INSTANCE STORAGE")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(Modifier.width(205.dp).fillMaxHeight(), color = Panel, shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    shortcuts.forEachIndexed { index, item ->
                        val exists = item.second.exists()
                        Surface(
                            onClick = {
                                if (exists && item.second.isDirectory) current = item.second
                                else message = "${item.first} folder is not available in this instance yet."
                            },
                            color = if (current == item.second) Gold else Color.Transparent,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(horizontal = 9.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(listOf("⬡", "💽", "⬇", "🟩", "🖼", "🕘", "🧩")[index], fontSize = 17.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(item.first, color = Ink, fontSize = 11.sp, fontWeight = if (current == item.second) FontWeight.Black else FontWeight.Medium, maxLines = 1)
                            }
                        }
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Surface(color = Panel, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
                    Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { parent?.let { current = it } }, enabled = parent != null) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = null); Text("BACK")
                            }
                            OutlinedButton(onClick = { parent?.let { current = it } }, enabled = parent != null) {
                                Text("↑", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("UP")
                            }
                            Spacer(Modifier.weight(1f))
                            Button(onClick = { newName = ""; showCreate = true }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) {
                                Icon(Icons.Filled.CreateNewFolder, contentDescription = null); Text("NEW FOLDER", fontSize = 10.sp)
                            }
                            Button(onClick = { newName = ""; showNewFile = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFE8C8), contentColor = Ink)) {
                                Icon(Icons.Filled.Description, contentDescription = null); Text("NEW FILE", fontSize = 10.sp)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.weight(1f).height(52.dp), singleLine = true, placeholder = { Text("Search files…", fontSize = 11.sp) })
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { importPicker.launch(arrayOf("*/*")) }) { Text("IMPORT") }
                            TextButton(onClick = { gridMode = true }) { Text("▦") }
                            TextButton(onClick = { gridMode = false }) { Text("☷") }
                        }
                        Text(current.relativeTo(root).path.let { if (it == ".") "Home /" else "Home / $it" }, color = Muted, fontSize = 10.sp, maxLines = 1)
                    }
                }
                Surface(Modifier.weight(1f).fillMaxWidth(), color = Panel, shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
                    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (entries.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(if (search.isBlank()) "This folder is empty." else "No files match your search.", color = Muted, fontSize = 12.sp)
                            }
                        } else if (gridMode) {
                            entries.chunked(4).forEach { group ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    group.forEach { file ->
                                        Surface(
                                            onClick = {
                                                if (file.isDirectory) current = file
                                                else if (file.extension.lowercase() in listOf("txt", "json", "properties", "cfg", "log", "yml", "yaml", "dat")) {
                                                    editingFile = file; editorText = runCatching { file.readText() }.getOrDefault("")
                                                } else message = "Stored file: ${file.name}"
                                            },
                                            modifier = Modifier.weight(1f).height(112.dp),
                                            color = Color(0xFFFFF3E2),
                                            shape = RoundedCornerShape(15.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFE2C0))
                                        ) {
                                            Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                                Text(if (file.isDirectory) "📁" else if (file.extension.lowercase() in listOf("png","jpg","jpeg")) "🖼️" else "📄", fontSize = 31.sp)
                                                Text(file.name, color = Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(if (file.isDirectory) "Folder" else OEngineInstalledContentManager.formatSize(file.length()), color = Muted, fontSize = 9.sp)
                                            }
                                        }
                                    }
                                    repeat(4 - group.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        } else {
                            entries.forEach { file ->
                                Row(Modifier.fillMaxWidth().clickable {
                                    if (file.isDirectory) current = file
                                    else if (file.extension.lowercase() in listOf("txt","json","properties","cfg","log","yml","yaml","dat")) {
                                        editingFile = file; editorText = runCatching { file.readText() }.getOrDefault("")
                                    } else message = "Stored file: ${file.name}"
                                }.padding(7.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (file.isDirectory) "📁" else "📄", fontSize = 19.sp)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(file.name, color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text(if (file.isDirectory) "Folder" else OEngineInstalledContentManager.formatSize(file.length()), color = Muted, fontSize = 9.sp)
                                    }
                                    TextButton(onClick = { pendingDelete = file }) { Text("DELETE", color = Color(0xFFB34B3B), fontSize = 9.sp) }
                                }
                            }
                        }
                        if (message.isNotBlank()) Text(message, color = Muted, fontSize = 10.sp)
                    }
                }
                Surface(color = Panel, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
                    Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("💽", fontSize = 22.sp)
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Storage Usage", color = Muted, fontSize = 10.sp)
                            Text(OEngineInstalledContentManager.formatSize(storageBytes) + " used in active instance", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }; message = "Temporary cache cleared." }) {
                            Icon(Icons.Filled.Delete, contentDescription = null); Text("CLEAR CACHE", fontSize = 9.sp)
                        }
                    }
                }
            }
        }
    }
    if (showCreate || showNewFile) AlertDialog(
        onDismissRequest = { showCreate = false; showNewFile = false },
        title = { Text(if (showCreate) "NEW FOLDER" else "NEW FILE") },
        text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton(enabled = newName.isNotBlank() && !newName.contains("/") && !newName.contains("\\"), onClick = {
            val target = current.resolve(newName.trim())
            val created = if (showCreate) target.mkdirs() else runCatching { target.createNewFile() }.getOrDefault(false)
            if (created) refresh++ else message = "Could not create that item."
            showCreate = false; showNewFile = false
        }) { Text("CREATE") } },
        dismissButton = { TextButton(onClick = { showCreate = false; showNewFile = false }) { Text("CANCEL") } }
    )
    pendingDelete?.let { file ->
        AlertDialog(onDismissRequest = { pendingDelete = null }, title = { Text("Delete ${file.name}?") }, text = { Text("This removes the selected item from the active instance.") },
            confirmButton = { TextButton(onClick = { runCatching { if (file.isDirectory) file.deleteRecursively() else file.delete() }; pendingDelete = null; refresh++ }) { Text("DELETE") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("CANCEL") } })
    }
    editingFile?.let { file ->
        AlertDialog(onDismissRequest = { editingFile = null }, title = { Text(file.name) },
            text = { OutlinedTextField(value = editorText, onValueChange = { editorText = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 360.dp), maxLines = 18) },
            confirmButton = { TextButton(onClick = { runCatching { file.writeText(editorText) }; editingFile = null; refresh++ }) { Text("SAVE") } },
            dismissButton = { TextButton(onClick = { editingFile = null }) { Text("CANCEL") } })
    }
}

@Composable
private fun SkinDestination(images: LauncherImages) {
    val context = LocalContext.current
    val skinDirectory = remember {
        (context.getExternalFilesDir(null) ?: context.filesDir).resolve("skins").apply { mkdirs() }
    }
    var selectedSkin by remember { mutableStateOf<File?>(null) }
    var message by remember { mutableStateOf("Import a 64×64 PNG skin to add it to your collection.") }
    var skinPreview by remember { mutableStateOf<ImageBitmap?>(null) }
    var skinVariant by remember { mutableStateOf("CLASSIC") }
    var uploadingSkin by remember { mutableStateOf(false) }
    val skinScope = rememberCoroutineScope()
    val skinPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                if (bitmap == null || !(bitmap.width == 64 && (bitmap.height == 64 || bitmap.height == 32))) {
                    message = "Choose a standard Minecraft skin PNG (64×64 or legacy 64×32)."
                } else {
                    val displayName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val column = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && column >= 0) cursor.getString(column) else null
                    } ?: "custom_skin.png"
                    val safeName = File(displayName).name.let { if (it.lowercase().endsWith(".png")) it else "$it.png" }
                    val destination = skinDirectory.resolve(safeName)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        destination.outputStream().use { output -> input.copyTo(output) }
                    }
                    selectedSkin = destination
                    skinPreview = bitmap.asImageBitmap()
                    message = "Skin imported. Sign in with Microsoft to apply it to your Java profile."
                }
            } catch (_: Exception) {
                message = "Could not import that skin. Please choose a PNG skin file."
            }
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DestinationHero(images, "Skins", "Personalize your Minecraft character", "PLAYER CUSTOMIZATION")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(
                modifier = Modifier.weight(1.25f).height(340.dp),
                color = Color(0xFF342B25),
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 3.dp
            ) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp))) {
                    // Reuse the exact Tricky Trials artwork shown in the Home news banner.
                    Image(
                        images.trials,
                        contentDescription = "The Tricky Trials Update character banner",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                0f to Color(0x00130908),
                                0.48f to Color(0x33130908),
                                1f to Color(0xE6100907)
                            )
                        )
                    )
                    Image(
                        images.avatar,
                        contentDescription = "Player avatar",
                        modifier = Modifier.align(Alignment.Center).size(132.dp)
                            .clip(RoundedCornerShape(20.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Column(
                        Modifier.align(Alignment.BottomStart).fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 17.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            "YOUR CHARACTER",
                            color = Color(0xFFFFD17A),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            selectedSkin?.name ?: "Default player",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text("Minecraft Java Edition", color = Color(0xFFF6EBDD), fontSize = 11.sp)
                    }
                }
            }
            Surface(modifier = Modifier.weight(1f).height(340.dp), color = Panel, shape = RoundedCornerShape(24.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
                Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("IMPORT A SKIN", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Ink)
                    Text("Bring your own character design. O Launcher accepts standard Minecraft skin PNG files.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                    Surface(color = Color(0xFFFFF1D9), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (skinPreview != null) {
                                Image(skinPreview!!, contentDescription = "Selected skin preview", modifier = Modifier.size(104.dp), contentScale = ContentScale.Fit)
                            } else {
                                Icon(Icons.Filled.Checkroom, contentDescription = null, tint = Color(0xFF9A6A48), modifier = Modifier.size(56.dp))
                            }
                        }
                    }
                    Button(onClick = { skinPicker.launch(arrayOf("image/png")) }, modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (selectedSkin == null) "CHOOSE SKIN PNG" else "CHANGE SKIN", fontWeight = FontWeight.Black)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("MODEL", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        listOf("CLASSIC", "SLIM").forEach { model ->
                            FilterChip(
                                selected = skinVariant == model,
                                onClick = { skinVariant = model },
                                label = { Text(model, fontSize = 9.sp) }
                            )
                        }
                    }
                    Button(
                        enabled = selectedSkin != null && !uploadingSkin,
                        onClick = {
                            val skin = selectedSkin ?: return@Button
                            uploadingSkin = true
                            message = "Refreshing Microsoft session…"
                            skinScope.launch {
                                try {
                                    val refreshToken = MicrosoftTokenVault.load(context)
                                        ?: throw IllegalStateException("Sign in with Microsoft on the Account screen first.")
                                    val account = MinecraftAuth.refreshAccount(refreshToken)
                                    MicrosoftTokenVault.save(context, account.refreshToken)
                                    message = MinecraftAuth.uploadSkin(account.accessToken, skin, skinVariant)
                                } catch (failure: Exception) {
                                    message = failure.message ?: "Could not apply this skin."
                                } finally {
                                    uploadingSkin = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB5E96D), contentColor = Ink),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(if (uploadingSkin) "APPLYING SKIN…" else "APPLY TO JAVA PROFILE", fontWeight = FontWeight.Black, fontSize = 10.sp)
                    }
                    Text(message, color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun PageHeading(title: String, subtitle: String, eyebrow: String = "O LAUNCHER") {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(eyebrow.uppercase(), color = Color(0xFF9B6A35), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp)
            Text(title, color = Ink, fontSize = 27.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.6).sp)
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
        Surface(color = Color(0xFFFFE7B6), shape = RoundedCornerShape(12.dp)) {
            Text("JAVA EDITION", Modifier.padding(horizontal = 13.dp, vertical = 8.dp), color = Color(0xFF6D4824), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = .7.sp)
        }
    }
}

@Composable
private fun DestinationHero(images: LauncherImages, title: String, subtitle: String, eyebrow: String) {
    Box(
        Modifier.fillMaxWidth().height(184.dp).clip(RoundedCornerShape(25.dp))
    ) {
        Image(
            images.hero,
            contentDescription = "Nether-themed launcher banner",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(Color(0xE61A0908), Color(0x9C24100C), Color(0x101A0908))
                )
            )
        )
        Column(
            Modifier.align(Alignment.CenterStart).fillMaxWidth(0.76f).padding(start = 25.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(eyebrow.uppercase(), color = Color(0xFFFFD17A), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
            Text(title, color = Color.White, fontSize = 29.sp, lineHeight = 33.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Color(0xFFF6EBDD), fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Surface(
            color = Color(0xD9FFF1D9),
            shape = RoundedCornerShape(11.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(13.dp)
        ) {
            Text("JAVA EDITION", Modifier.padding(horizontal = 11.dp, vertical = 7.dp), color = Color(0xFF684323), fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = .7.sp)
        }
    }
}

@Composable
private fun SettingsDestination(images: LauncherImages) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("o_launcher_settings", android.content.Context.MODE_PRIVATE)
    }
    var selectedSection by remember { mutableStateOf<String?>(null) }
    var performance by remember(context) { mutableStateOf(preferences.getBoolean("performance", true)) }
    var maxMemoryMb by remember(context) { mutableIntStateOf(preferences.getInt("minecraft_memory_mb", 2048).coerceIn(512, 8192)) }
    var animations by remember(context) { mutableStateOf(preferences.getBoolean("animations", true)) }
    var compactUi by remember(context) { mutableStateOf(preferences.getBoolean("compact_ui", false)) }
    var notifications by remember(context) { mutableStateOf(preferences.getBoolean("notifications", true)) }
    val sections = listOf(
        Triple("General", "App language, theme, and more.", "🖥️"),
        Triple("Minecraft", "Game settings and launch options.", "🟩"),
        Triple("Performance", "Allocate resources for smoother gameplay.", "🚀"),
        Triple("Downloads", "Manage downloads and cache.", "⬇️"),
        Triple("Launcher Appearance", "Theme, colors, and interface style.", "🎨"),
        Triple("Miscellaneous Settings", "UI, updates and other options.", "🛠️"),
        Triple("Experimental Stuff", "Try experimental and unsupported options.", "🧪"),
        Triple("Control Customization", "Customize keybinds, touch controls and layouts.", "🎮")
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DestinationHero(images, selectedSection ?: "Settings", selectedSection?.let { "Customize $it for your launcher." } ?: "Customize O Launcher Plus to match your style.", "PREFERENCES")
        if (selectedSection == null) {
            sections.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    pair.forEach { item ->
                        Surface(
                            onClick = { selectedSection = item.first },
                            modifier = Modifier.weight(1f).height(112.dp),
                            color = Panel,
                            shape = RoundedCornerShape(22.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFE5C5)),
                            shadowElevation = 2.dp
                        ) {
                            Row(Modifier.fillMaxSize().padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                                Surface(Modifier.size(58.dp), color = Color(0xFFFFE8BE), shape = RoundedCornerShape(18.dp)) {
                                    Box(contentAlignment = Alignment.Center) { Text(item.third, fontSize = 28.sp) }
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(item.first, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(item.second, color = Muted, fontSize = 10.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                Text("›", color = Color(0xFF8A542B), fontSize = 27.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { selectedSection = null }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    Text("ALL SETTINGS")
                }
                Text(selectedSection!!, color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Black)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsGroup("GENERAL", "Launcher interface preferences") {
                        SettingToggle("Smooth animations", "Use subtle transitions between screens", animations) {
                            animations = it; preferences.edit().putBoolean("animations", it).apply()
                        }
                        SettingToggle("Compact interface", "Reduce spacing in content panels", compactUi) {
                            compactUi = it; preferences.edit().putBoolean("compact_ui", it).apply()
                        }
                        SettingToggle("Launcher notifications", "Updates and important launcher notices", notifications) {
                            notifications = it; preferences.edit().putBoolean("notifications", it).apply()
                        }
                    }
                    SettingsGroup("PERFORMANCE", "Control launcher and game resource usage") {
                        SettingToggle("Performance mode", "Prefer a lighter interface on your device", performance) {
                            performance = it; preferences.edit().putBoolean("performance", it).apply()
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Minecraft memory", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text("$maxMemoryMb MiB", color = Color(0xFF8A542B), fontSize = 12.sp, fontWeight = FontWeight.Black)
                            }
                            Slider(
                                value = maxMemoryMb.toFloat(),
                                onValueChange = { value ->
                                    maxMemoryMb = ((value / 512f).toInt() * 512).coerceIn(512, 8192)
                                    preferences.edit().putInt("minecraft_memory_mb", maxMemoryMb).apply()
                                },
                                valueRange = 512f..8192f,
                                steps = 14
                            )
                            Text("Maximum heap size passed to Java when Minecraft starts.", color = Muted, fontSize = 10.sp)
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsGroup("MINECRAFT", "Game and profile settings") {
                        SettingInfo("Edition", "Minecraft Java Edition")
                        SettingInfo("Active instance", OEngineInstanceManager.active(context).name)
                        SettingInfo("Game directory", OEngineInstanceManager.active(context).root.name)
                    }
                    SettingsGroup("CONTROL CUSTOMIZATION", "Touch and keyboard controls") {
                        SettingInfo("Touch layout", "Editable in the game controls overlay")
                        SettingInfo("Control profiles", "Stored per device")
                        Text("Open an installation and use the controls editor while the game surface is active.", color = Muted, fontSize = 10.sp)
                    }
                    SettingsGroup("DOWNLOADS & CACHE", "Local storage") {
                        SettingInfo("Launcher cache", "Managed automatically")
                        TextButton(onClick = {
                            val cache = context.cacheDir
                            cache.listFiles()?.forEach { it.deleteRecursively() }
                        }) { Text("CLEAR TEMPORARY CACHE", color = Color(0xFF9A4C32), fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(title, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = .5.sp)
            Text(subtitle, color = Muted, fontSize = 10.sp)
            content()
        }
    }
}

@Composable
private fun SettingToggle(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Muted, fontSize = 10.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingInfo(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(value, color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AboutDestination(images: LauncherImages) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DestinationHero(images, "About O Launcher", "A home for your Minecraft Java Edition experience", "THE LAUNCHER")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("VERSION" to "Development build", "PLATFORM" to "Android", "EDITION" to "Java Edition").forEach { item ->
                Surface(Modifier.weight(1f), color = Panel, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(item.first, color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Text(item.second, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountTypeBanner(title: String, subtitle: String, tag: String, mark: String, colors: List<Color>) {
    Box(Modifier.fillMaxWidth().height(102.dp).clip(RoundedCornerShape(16.dp)).background(Brush.horizontalGradient(colors)).padding(horizontal = 18.dp, vertical = 13.dp)) {
        Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(0.78f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(tag, color = Color.White.copy(alpha = .78f), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
            Text(title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Color.White.copy(alpha = .88f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Surface(Modifier.align(Alignment.CenterEnd).size(58.dp), color = Color.White.copy(alpha = .17f), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .32f))) {
            Box(contentAlignment = Alignment.Center) { Text(mark, color = Color.White, fontSize = if (mark.length > 1) 17.sp else 30.sp, fontWeight = FontWeight.Black) }
        }
    }
}

@Composable
private fun AccountDestination(images: LauncherImages) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("olauncher_accounts", android.content.Context.MODE_PRIVATE) }
    var hasRecoveredMicrosoftAccount by remember { mutableStateOf(MicrosoftTokenVault.load(context) != null) }
    var playerName by remember { mutableStateOf(
        if (hasRecoveredMicrosoftAccount) prefs.getString("microsoft_name", "Saved Microsoft account") ?: "Saved Microsoft account"
        else prefs.getString("offline_name", "Not signed in") ?: "Not signed in"
    ) }
    var playerUuid by remember { mutableStateOf(
        if (hasRecoveredMicrosoftAccount) prefs.getString("microsoft_uuid", "") ?: ""
        else prefs.getString("offline_uuid", "") ?: ""
    ) }
    var offlineName by remember { mutableStateOf("") }
    var isOffline by remember { mutableStateOf(prefs.getBoolean("is_offline", false)) }
    var device by remember { mutableStateOf<MicrosoftDeviceCode?>(null) }
    var status by remember { mutableStateOf(
        when {
            hasRecoveredMicrosoftAccount -> "Saved Microsoft account recovered. Re-authentication is required before online play."
            isOffline -> "Offline profile saved on this device."
            else -> "Connect a Microsoft account that owns Minecraft: Java Edition."
        }
    ) }
    var busy by remember { mutableStateOf(false) }
    var signedIn by remember { mutableStateOf(false) }
    var showOfflineForm by remember { mutableStateOf(false) }
    var offlineError by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val savedRefreshToken = MicrosoftTokenVault.load(context)
        if (!savedRefreshToken.isNullOrBlank()) {
            busy = true
            status = "Restoring Microsoft session…"
            try {
                val account = MinecraftAuth.refreshAccount(savedRefreshToken)
                MicrosoftTokenVault.save(context, account.refreshToken)
                prefs.edit()
                    .putString("microsoft_name", account.name)
                    .putString("microsoft_uuid", account.uuid)
                    .putBoolean("is_offline", false)
                    .apply()
                playerName = account.name
                playerUuid = account.uuid
                isOffline = false
                signedIn = true
                hasRecoveredMicrosoftAccount = false
                status = "Microsoft session restored. Minecraft Java profile verified."
            } catch (e: Exception) {
                hasRecoveredMicrosoftAccount = true
                status = e.message?.takeIf { it.isNotBlank() }
                    ?: "Saved Microsoft session could not be restored. Please sign in again."
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DestinationHero(images, "Account Type", "Choose how you want to play Minecraft Java Edition", "PLAYER ACCOUNT")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(Modifier.weight(1f).heightIn(min = 340.dp), color = Panel, shape = RoundedCornerShape(24.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border), shadowElevation = 2.dp) {
                Column(Modifier.fillMaxSize().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Image(images.avatar, contentDescription = "Offline player", modifier = Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(17.dp)), contentScale = ContentScale.Crop)
                    Text("Offline Account", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Black)
                    Text("Play without signing in to Microsoft.", color = Muted, fontSize = 11.sp)
                    if (showOfflineForm) {
                        OutlinedTextField(value = offlineName, onValueChange = { offlineName = it; offlineError = "" }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Player name") }, placeholder = { Text("Enter Minecraft name") })
                        if (offlineError.isNotBlank()) Text(offlineError, color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
                        Button(onClick = {
                            val clean = offlineName.trim()
                            when {
                                clean.length !in 3..16 -> offlineError = "Name must be 3–16 characters."
                                !clean.matches(Regex("[A-Za-z0-9_]+")) -> offlineError = "Use letters, numbers and underscores only."
                                else -> {
                                    val uuid = java.util.UUID.nameUUIDFromBytes("OfflinePlayer:$clean".toByteArray(Charsets.UTF_8)).toString().replace("-", "")
                                    prefs.edit().putString("offline_name", clean).putString("offline_uuid", uuid).putBoolean("is_offline", true).apply()
                                    playerName = clean; playerUuid = uuid; isOffline = true; signedIn = false; showOfflineForm = false
                                    status = "Offline profile saved on this device."
                                }
                            }
                        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) { Text("SAVE & SELECT", fontWeight = FontWeight.Black) }
                        TextButton(onClick = { showOfflineForm = false; offlineError = "" }) { Text("CANCEL") }
                    } else {
                        if (isOffline) Text("Saved profile: $playerName", color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Button(onClick = { showOfflineForm = true; offlineName = if (isOffline) playerName else "" }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) {
                            Text(if (isOffline) "EDIT PROFILE" else "SELECT  →", fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
            Surface(Modifier.weight(1f).heightIn(min = 340.dp), color = Panel, shape = RoundedCornerShape(24.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border), shadowElevation = 2.dp) {
                Column(Modifier.fillMaxSize().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(17.dp)).background(Brush.linearGradient(listOf(Color(0xFFBFE8FF), Color(0xFFFFE4BD)))), contentAlignment = Alignment.Center) {
                        Image(images.logo, contentDescription = "Minecraft Java Edition", modifier = Modifier.size(112.dp), contentScale = ContentScale.Fit)
                    }
                    Text("Microsoft Account", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Black)
                    Text("Recommended for online play.", color = Muted, fontSize = 11.sp)
                    Surface(color = Color(0xFFFFE8B8), shape = RoundedCornerShape(14.dp)) {
                        Text("♛  O Launcher recommends playing with it.", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = Color(0xFF72501F), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(enabled = !busy && !signedIn, onClick = {
                        busy = true
                        status = "Requesting a secure sign-in code…"
                        scope.launch {
                            try {
                                val pending = MinecraftAuth.beginDeviceLogin()
                                device = pending
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(pending.verificationUri)))
                                status = "Waiting for Microsoft sign-in approval…"
                                val account = MinecraftAuth.completeDeviceLogin(pending)
                                MicrosoftTokenVault.save(context, account.refreshToken)
                                playerName = account.name; playerUuid = account.uuid
                                prefs.edit().putString("microsoft_name", account.name).putString("microsoft_uuid", account.uuid).putBoolean("is_offline", false).apply()
                                signedIn = true; isOffline = false; hasRecoveredMicrosoftAccount = false; device = null
                                status = "Minecraft Java profile verified."
                            } catch (e: Exception) {
                                status = e.message ?: "Sign-in failed. Please try again."
                                device = null
                            } finally { busy = false }
                        }
                    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB5E96D), contentColor = Ink), shape = RoundedCornerShape(24.dp)) {
                        Text(if (busy) "WAITING FOR MICROSOFT…" else "SIGN IN WITH MICROSOFT  →", fontWeight = FontWeight.Black, fontSize = 10.sp)
                    }
                    if (signedIn || hasRecoveredMicrosoftAccount) {
                        OutlinedButton(onClick = {
                            MicrosoftTokenVault.clear(context); signedIn = false; hasRecoveredMicrosoftAccount = false
                            prefs.edit().remove("microsoft_name").remove("microsoft_uuid").apply()
                            playerName = "Not signed in"; playerUuid = ""; status = "Microsoft account disconnected."
                        }, modifier = Modifier.fillMaxWidth()) { Text("SIGN OUT") }
                    }
                }
            }
        }
        Surface(Modifier.fillMaxWidth(), color = Panel, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
            Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (signedIn) "CONNECTED · $playerName" else if (isOffline) "OFFLINE · $playerName" else if (hasRecoveredMicrosoftAccount) "SAVED MICROSOFT ACCOUNT" else "NO ACCOUNT SELECTED", color = Ink, fontWeight = FontWeight.Black, fontSize = 12.sp)
                Text(status, color = Muted, fontSize = 10.sp)
                if (device != null) Text("Code: ${device!!.userCode}  •  ${device!!.verificationUri}", color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                if (playerUuid.isNotBlank()) Text("UUID: $playerUuid", color = Muted, fontSize = 9.sp)
            }
        }
    }
}
@Composable
private fun MarketplaceDestination(images: LauncherImages, availableHeight: Dp) {
    val context = LocalContext.current
    val categories = listOf("All", "Mods", "Modpacks", "Resource Packs", "Data Packs", "Shaders")
    var selectedCategory by remember { mutableStateOf("All") }
    var search by remember { mutableStateOf("") }
    val installedMinecraftVersions = remember(activeGameRoot) {
        File(activeGameRoot, "versions").listFiles().orEmpty()
            .filter { it.isDirectory && File(it, it.name + ".json").isFile }
            .map { it.name }.distinct().sortedDescending()
    }
    var compatibleGameVersion by remember { mutableStateOf("All versions") }
    var projects by remember { mutableStateOf<List<OEngineModrinthCatalog.Project>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var totalHits by remember { mutableIntStateOf(0) }
    var offset by remember { mutableIntStateOf(0) }
    var installingProject by remember { mutableStateOf<OEngineModrinthCatalog.Project?>(null) }
    var installVersions by remember { mutableStateOf<List<OEngineModrinthInstaller.ProjectVersion>>(emptyList()) }
    var loadingVersions by remember { mutableStateOf(false) }
    var installingFile by remember { mutableStateOf(false) }
    var installStatus by remember { mutableStateOf<String?>(null) }
    var choosingDataPackWorldFor by remember { mutableStateOf<Pair<OEngineModrinthCatalog.Project, OEngineModrinthInstaller.ProjectVersion>?>(null) }
    val scope = rememberCoroutineScope()
    var activeGameRoot by remember { mutableStateOf(OEngineInstanceManager.active(context).root) }
    var installedContent by remember { mutableStateOf(OEngineInstalledContentManager.list(activeGameRoot)) }
    var removingContent by remember { mutableStateOf<OEngineInstalledContentManager.Entry?>(null) }
    fun refreshInstalledContent() {
        installedContent = OEngineInstalledContentManager.list(activeGameRoot)
    }
    var importingModpack by remember { mutableStateOf(false) }
    var modpackStatus by remember { mutableStateOf<String?>(null) }
    val modpackPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !importingModpack) {
            importingModpack = true
            modpackStatus = "Importing and verifying modpack…"
            scope.launch {
                var created: OEngineInstance? = null
                val staged = File(context.cacheDir, "incoming-modpack-" + System.currentTimeMillis() + ".mrpack")
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        staged.outputStream().buffered().use { output ->
                            val buffer = ByteArray(65536)
                            var total = 0L
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                total += n
                                require(total <= 512L * 1024L * 1024L) { "Modpack archive exceeds 512 MiB." }
                                output.write(buffer, 0, n)
                            }
                        }
                    } ?: error("Could not read the selected modpack.")
                    val baseName = staged.nameWithoutExtension.replace(Regex("[^A-Za-z0-9 _.-]"), "").take(32).ifBlank { "Imported Modpack" }
                    var candidate = baseName
                    var suffix = 2
                    while (OEngineInstanceManager.list(context).any { it.name.equals(candidate, true) }) { candidate = baseName + " " + suffix; suffix++ }
                    val instance = OEngineInstanceManager.create(context, candidate)
                    created = instance
                    val result = OEngineModrinthPackImporter.importPack(staged, instance.root)
                    OEngineInstanceManager.select(context, instance.id)
                    activeGameRoot = instance.root
                    modpackStatus = "Imported " + result.name + ": " + result.downloadedFiles + " verified downloads, " + result.copiedOverrideFiles + " override files. Minecraft " + result.minecraftVersion + "; loader: " + result.loaders.entries.joinToString { it.key + " " + it.value }.ifBlank { "Vanilla" } + ". Install the base game and loader from Installations next."
                    refreshInstalledContent()
                } catch (failure: Exception) {
                    created?.let { runCatching { it.root.parentFile?.deleteRecursively() } }
                    modpackStatus = failure.message ?: "Modpack import failed."
                } finally { staged.delete(); importingModpack = false }
            }
        }
    }
    fun prepareProjectInstall(project: OEngineModrinthCatalog.Project) {
        if (project.projectType == OEngineModrinthCatalog.ProjectType.MODPACK ||
            project.projectType == OEngineModrinthCatalog.ProjectType.ALL) {
            installStatus = "Modpack archives need the dedicated instance-import workflow. Open the project page to view its instructions."
            return
        }
        installingProject = project
        installVersions = emptyList()
        installStatus = null
        loadingVersions = true
        scope.launch {
            try {
                installVersions = OEngineModrinthInstaller.versions(project.id, compatibleGameVersion.takeUnless { it == "All versions" })
                if (installVersions.isEmpty()) installStatus = "No verified downloadable versions were returned."
            } catch (failure: Exception) {
                installStatus = failure.message ?: "Could not load project versions."
            } finally { loadingVersions = false }
        }
    }

    fun installProjectVersion(project: OEngineModrinthCatalog.Project, version: OEngineModrinthInstaller.ProjectVersion, worldName: String? = null) {
        if (installingFile) return
        if (project.projectType == OEngineModrinthCatalog.ProjectType.DATA_PACK && worldName == null) {
            choosingDataPackWorldFor = project to version
            return
        }
        installingFile = true
        installStatus = "Downloading and verifying " + version.versionNumber + "…"
        scope.launch {
            try {
                val root = OEngineInstanceManager.active(context).root
                if (project.projectType == OEngineModrinthCatalog.ProjectType.MOD) {
                    val batch = OEngineModrinthInstaller.installWithRequiredDependencies(
                        project.id, version.id, project.projectType, root,
                        version.gameVersions.firstOrNull() ?: compatibleGameVersion.takeUnless { it == "All versions" },
                        version.loaders.firstOrNull()
                    )
                    refreshInstalledContent()
                    installStatus = "Installed " + batch.primary.filename + " plus " +
                        batch.installedDependencies.size + " required dependencies. " +
                        if (batch.unresolvedDependencies.isEmpty()) "All resolved files passed integrity verification."
                        else "Needs attention: " + batch.unresolvedDependencies.take(4).joinToString("; ")
                } else {
                    val result = OEngineModrinthInstaller.install(project.id, version.id, project.projectType, root, worldName)
                    refreshInstalledContent()
                    installStatus = "Installed " + result.filename + " in " +
                        (result.destination.parentFile?.name ?: "instance") + ". File integrity verified."
                }
            } catch (failure: Exception) {
                installStatus = failure.message ?: "Installation failed."
            } finally { installingFile = false }
        }
    }

    fun loadProjects(reset: Boolean = true, typeOverride: OEngineModrinthCatalog.ProjectType = OEngineModrinthCatalog.ProjectType.fromLabel(selectedCategory)) {
        if (loading) return
        loading = true
        error = null
        scope.launch {
            try {
                val nextOffset = if (reset) 0 else offset + 20
                offset = nextOffset
            } catch (failure: Exception) {
                error = failure.message ?: "Could not load community projects."
            } finally { loading = false }
        }
    }

    LaunchedEffect(Unit) { loadProjects() }

    BoxWithConstraints(Modifier.fillMaxWidth().height(availableHeight)) {
        val contentHeight = maxHeight
        val scale = (contentHeight / 700.dp).coerceIn(0.72f, 1.12f)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp * scale)
        ) {
            DestinationHero(images, "Marketplace", "Discover mods, worlds, resource packs and more", "COMMUNITY CONTENT")
            Row(
                Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Filled.Search, contentDescription = "Search", tint = Muted, modifier = Modifier.size(19.dp))
                Box(Modifier.weight(1f)) {
                    BasicTextField(
                        value = search,
                        onValueChange = { search = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = Ink, fontSize = 12.sp)
                    )
                }
                TextButton(onClick = { loadProjects() }, enabled = !loading) { Text("SEARCH", color = Ink, fontWeight = FontWeight.Bold) }
            }
            if (installedMinecraftVersions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("FILTER BY INSTALLED MINECRAFT VERSION", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        (listOf("All versions") + installedMinecraftVersions).forEach { version ->
                            val selected = compatibleGameVersion == version
                            Surface(onClick = {
                                if (compatibleGameVersion != version) {
                                    compatibleGameVersion = version
                                    if (installingProject != null) prepareProjectInstall(installingProject!!)
                                }
                            }, color = if (selected) Color(0xFF704329) else Panel,
                                shape = RoundedCornerShape(24.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
                                Text(version, Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    color = if (selected) Color.White else Ink, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { category ->
                    val selected = selectedCategory == category
                    Surface(
                        onClick = { if (!loading) { selectedCategory = category; loadProjects(typeOverride = OEngineModrinthCatalog.ProjectType.fromLabel(category)) } },
                        color = if (selected) Color(0xFF704329) else Panel,
                        shape = RoundedCornerShape(30.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Color(0xFF342139) else Border)
                    ) {
                        Text(category, Modifier.padding(horizontal = 15.dp, vertical = 9.dp), color = if (selected) Color.White else Ink, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (search.isBlank()) "Popular $selectedCategory" else "Search results", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Ink)
                    Text("Modrinth • $totalHits projects", color = Muted, fontSize = 11.sp)
                }
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Gold, strokeWidth = 2.dp)
            }
            error?.let { message ->
                Surface(color = Color(0xFFFFE5DF), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(message, Modifier.padding(14.dp), color = Color(0xFF8A2F20), fontSize = 12.sp)
                }
                TextButton(onClick = { loadProjects() }) { Text("RETRY") }
            }
            if (projects.isEmpty() && !loading && error == null) {
                Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Storefront, contentDescription = null, tint = Color(0xFF9A6A48), modifier = Modifier.size(31.dp))
                        Text("No projects found", color = Ink, fontWeight = FontWeight.Bold)
                        Text("Try another search or category.", color = Muted, fontSize = 11.sp)
                    }
                }
            }
            } else projects.forEach { project ->
                Surface(
                    color = Panel,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Border.copy(alpha = .8f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(project.title, Modifier.weight(1f), color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(project.projectType.label.uppercase(), color = Color(0xFF8B5731), fontSize = 8.sp, fontWeight = FontWeight.Black)
                        }
                        Text("by ${project.author}", color = Muted, fontSize = 10.sp)
                        Text(project.description, color = Ink, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
                        Text("${project.downloads} downloads  •  ${project.follows} followers", color = Muted, fontSize = 10.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = { prepareProjectInstall(project) },
                                enabled = !loadingVersions && !installingFile &&
                                    project.projectType !in setOf(OEngineModrinthCatalog.ProjectType.MODPACK, OEngineModrinthCatalog.ProjectType.ALL),
                                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)
                            ) { Text("INSTALL", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                            TextButton(onClick = {
                                val url = "https://modrinth.com/${project.projectType.apiValue.ifBlank { "mod" }}/${project.slug}"
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                            }) { Text("OPEN PROJECT  →", color = Color(0xFF8B5731), fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
            if (projects.size < totalHits) {
                Button(onClick = { loadProjects(reset = false) }, enabled = !loading, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) {
                    Text(if (loading) "LOADING…" else "LOAD MORE PROJECTS")
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { modpackPicker.launch(arrayOf("*/*")) }, enabled = !importingModpack, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) {
                    Text(if (importingModpack) "IMPORTING MODPACK…" else "IMPORT .MRPACK")
                }
                modpackStatus?.let { Text(it, color = Ink, fontSize = 10.sp, modifier = Modifier.weight(1f)) }
            }
            Text("Install into active instance: " + OEngineInstanceManager.active(context).name + ". Check Minecraft and loader compatibility before choosing a version.", color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
            Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("INSTALLED CONTENT", color = Ink, fontWeight = FontWeight.Black, fontSize = 13.sp)
                            Text("${installedContent.size} files in this instance", color = Muted, fontSize = 10.sp)
                        }
                        TextButton(onClick = { refreshInstalledContent() }) { Text("REFRESH", color = Color(0xFF8B5731), fontWeight = FontWeight.Bold) }
                    }
                    if (installedContent.isEmpty()) {
                        Text("No installed mods, resource packs, shaders or data packs found yet.", color = Muted, fontSize = 11.sp)
                    } else {
                        installedContent.forEach { entry ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(entry.name, color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(entry.kind.name.replace('_', ' ') + (entry.worldName?.let { " • $it" } ?: "") + " • " + OEngineInstalledContentManager.formatSize(entry.sizeBytes), color = Muted, fontSize = 9.sp)
                                }
                                TextButton(onClick = { removingContent = entry }) { Text("REMOVE", color = Color(0xFFB34A38), fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            }
            removingContent?.let { entry ->
                AlertDialog(
                    onDismissRequest = { removingContent = null },
                    title = { Text("Remove installed content?") },
                    text = { Text("Delete ${entry.name} from this instance? This cannot be undone.") },
                    confirmButton = {
                        TextButton(onClick = {
                            runCatching { OEngineInstalledContentManager.remove(activeGameRoot, entry) }
                                .onSuccess { refreshInstalledContent() }
                                .onFailure { installStatus = it.message ?: "Could not remove file." }
                            removingContent = null
                        }) { Text("REMOVE") }
                    },
                    dismissButton = { TextButton(onClick = { removingContent = null }) { Text("CANCEL") } }
                )
            }
            choosingDataPackWorldFor?.let { pending ->
                val worlds = OEngineInstalledContentManager.worldNames(OEngineInstanceManager.active(context).root)
                AlertDialog(
                    onDismissRequest = { choosingDataPackWorldFor = null },
                    title = { Text("CHOOSE A WORLD") },
                    text = {
                        Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Select the world that should receive this data pack.", color = Muted, fontSize = 11.sp)
                            if (worlds.isEmpty()) Text("No valid worlds found. Create or open a world first.", color = Muted, fontSize = 11.sp)
                            worlds.forEach { world ->
                                TextButton(onClick = {
                                    choosingDataPackWorldFor = null
                                    installProjectVersion(pending.first, pending.second, world)
                                }, modifier = Modifier.fillMaxWidth()) { Text(world) }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { choosingDataPackWorldFor = null }) { Text("CANCEL") } }
                )
            }
            if (installingProject != null) {
                AlertDialog(
                    onDismissRequest = { if (!installingFile) { installingProject = null; installVersions = emptyList(); installStatus = null } },
                    title = { Text("Install " + (installingProject?.title ?: "project")) },
                    text = {
                        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (installingProject!!.projectType == OEngineModrinthCatalog.ProjectType.DATA_PACK) "Destination: selected world's datapacks folder" else "Destination: " + OEngineModrinthInstaller.targetDirectory(OEngineInstanceManager.active(context).root, installingProject!!.projectType).name, fontSize = 11.sp, color = Muted)
                            if (loadingVersions) CircularProgressIndicator(color = Gold)
                            installVersions.take(12).forEach { version ->
                                Surface(color = Panel, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(version.name.ifBlank { version.versionNumber }, color = Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text("Version " + version.versionNumber + " • MC: " + version.gameVersions.take(5).joinToString() + " • Loaders: " + version.loaders.joinToString().ifBlank { "Any / not specified" }, color = Muted, fontSize = 9.sp)
                                        TextButton(onClick = { installProjectVersion(installingProject!!, version) }, enabled = !installingFile) { Text("INSTALL THIS VERSION") }
                                    }
                                }
                            }
                            if (installVersions.size > 12) Text("Showing the newest 12 versions. Open the project page for the full version history.", color = Muted, fontSize = 10.sp)
                            installStatus?.let { Text(it, color = if (it.startsWith("Installed")) Color(0xFF287A42) else Ink, fontSize = 11.sp) }
                            Text("Required dependencies are reported after installation; they are not silently installed. Data packs are installed directly into the selected world's datapacks folder.", color = Muted, fontSize = 9.sp)
                        }
                    },
                    confirmButton = { TextButton(onClick = { if (!installingFile) { installingProject = null; installVersions = emptyList(); installStatus = null } }) { Text("CLOSE") } }
                )
            }
        }
    }
}

@Composable
private fun Destination(tab: Int, images: LauncherImages) {
    val title = listOf("Home", "Installations", "Skins", "Marketplace", "Settings", "About")[tab.coerceIn(0, 5)]
    if (tab == 1) {
        InstallationsDestination(images)
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
private fun SettingsReferenceScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val reference = remember(context) { readAssetImage(context, "IMG_20261106_160537.png") }
    BoxWithConstraints(modifier = modifier.background(Color(0xFF241018))) {
        if (reference != null) {
            // Preserve the artwork's original proportions on every landscape display.
            val imageAspect = reference.width.toFloat() / reference.height.toFloat()
            val screenAspect = maxWidth / maxHeight
            val artworkWidth: Dp
            val artworkHeight: Dp
            if (screenAspect > imageAspect) {
                artworkHeight = maxHeight
                artworkWidth = artworkHeight * imageAspect
            } else {
                artworkWidth = maxWidth
                artworkHeight = artworkWidth / imageAspect
            }

            Box(
                Modifier.size(artworkWidth, artworkHeight).align(Alignment.Center)
            ) {
                Image(
                    bitmap = reference,
                    contentDescription = "O Launcher Settings reference screen",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                // Keep the navigation hotspot attached to the artwork, not the screen edges.
                Box(
                    Modifier.align(Alignment.TopStart)
                        .fillMaxWidth(0.12f)
                        .fillMaxHeight(0.12f)
                        .clickable(onClick = onBack)
                )
                val sections = listOf(
                    "Java Tweaks",
                    "Miscellaneous Settings",
                    "Video and Renderer",
                    "Experimental Stuff",
                    "Launcher Appearance",
                    "Control Customization",
                    "Launcher Language"
                )
                sections.forEachIndexed { index, section ->
                    Box(
                        Modifier.offset(
                            x = artworkWidth * 0.25f,
                            y = artworkHeight * (0.20f + index * 0.095f)
                        )
                            .size(
                                width = artworkWidth * 0.48f,
                                height = artworkHeight * 0.085f
                            )
                            .clickable {
                                Toast.makeText(
                                    context,
                                    "$section settings are being connected",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    )
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Settings artwork could not be loaded", color = Color.White)
            }
        }
    }
}

@Composable
private fun FileManagerReferenceScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val reference = remember(context) { readAssetImage(context, "IMG_20261106_155827.png") }
    BoxWithConstraints(modifier = modifier.background(Color(0xFF241018))) {
        if (reference != null) {
            // Keep the original screenshot ratio; center it instead of stretching it.
            val imageAspect = reference.width.toFloat() / reference.height.toFloat()
            val screenAspect = maxWidth / maxHeight
            val artworkWidth: Dp
            val artworkHeight: Dp
            if (screenAspect > imageAspect) {
                artworkHeight = maxHeight
                artworkWidth = artworkHeight * imageAspect
            } else {
                artworkWidth = maxWidth
                artworkHeight = artworkWidth / imageAspect
            }

            Box(Modifier.size(artworkWidth, artworkHeight).align(Alignment.Center)) {
                Image(
                    bitmap = reference,
                    contentDescription = "Minecraft Java Edition Manage Content screen",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                // Close control and category hotspots remain aligned to the artwork.
                Box(
                    Modifier.offset(x = artworkWidth * 0.735f, y = artworkHeight * 0.235f)
                        .size(width = artworkWidth * 0.045f, height = artworkHeight * 0.075f)
                        .clickable(onClick = onBack)
                )
                val categories = listOf("Mods", "Resource Packs", "Saves / Worlds", "Logs")
                val xPositions = listOf(0.14f, 0.295f, 0.45f, 0.605f)
                categories.forEachIndexed { index, category ->
                    Box(
                        Modifier.offset(
                            x = artworkWidth * xPositions[index],
                            y = artworkHeight * 0.36f
                        )
                            .size(
                                width = artworkWidth * 0.15f,
                                height = artworkHeight * 0.29f
                            )
                            .clickable {
                                Toast.makeText(
                                    context,
                                    "$category section is being connected",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    )
                }
                // Brand area returns to Home, positioned relative to the image.
                Box(
                    Modifier.align(Alignment.TopStart)
                        .fillMaxWidth(0.12f)
                        .fillMaxHeight(0.12f)
                        .clickable(onClick = onBack)
                )
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("File Manager artwork could not be loaded", color = Color.White)
            }
        }
    }
}

@Composable
private fun CreateAccountReferenceScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val reference = remember(context) { readAssetImage(context, "IMG_20261106_154508.png") }
    BoxWithConstraints(modifier = modifier.background(Color(0xFF241A17))) {
        if (reference != null) {
            val imageAspect = reference.width.toFloat() / reference.height.toFloat()
            val screenAspect = maxWidth / maxHeight
            val artworkWidth: Dp
            val artworkHeight: Dp
            if (screenAspect > imageAspect) {
                artworkHeight = maxHeight
                artworkWidth = artworkHeight * imageAspect
            } else {
                artworkWidth = maxWidth
                artworkHeight = artworkWidth / imageAspect
            }
            Box(Modifier.size(artworkWidth, artworkHeight).align(Alignment.Center)) {
                Image(
                    bitmap = reference,
                    contentDescription = "O Launcher Create Account screen",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                Box(
                    Modifier.align(Alignment.TopStart)
                        .fillMaxWidth(0.16f)
                        .fillMaxHeight(0.13f)
                        .clickable(onClick = onBack)
                )
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("O Launcher Create Account screen artwork could not be loaded", color = Color.White)
            }
        }
    }
}
@Composable
private fun AboutReferenceScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val reference = remember(context) { readAssetImage(context, "IMG_20261106_153734.png") }
    BoxWithConstraints(modifier = modifier.background(Color(0xFF241A17))) {
        if (reference != null) {
            val imageAspect = reference.width.toFloat() / reference.height.toFloat()
            val screenAspect = maxWidth / maxHeight
            val artworkWidth: Dp
            val artworkHeight: Dp
            if (screenAspect > imageAspect) {
                artworkHeight = maxHeight
                artworkWidth = artworkHeight * imageAspect
            } else {
                artworkWidth = maxWidth
                artworkHeight = artworkWidth / imageAspect
            }
            Box(Modifier.size(artworkWidth, artworkHeight).align(Alignment.Center)) {
                Image(
                    bitmap = reference,
                    contentDescription = "O Launcher About screen",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                Box(
                    Modifier.align(Alignment.TopStart)
                        .fillMaxWidth(0.16f)
                        .fillMaxHeight(0.13f)
                        .clickable(onClick = onBack)
                )
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("O Launcher About screen artwork could not be loaded", color = Color.White)
            }
        }
    }
}
@Composable
private fun InstallationReferenceScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val reference = remember(context) { readAssetImage(context, "IMG_20261106_152629.png") }
    BoxWithConstraints(modifier = modifier.background(Color(0xFF241A17))) {
        if (reference != null) {
            val imageAspect = reference.width.toFloat() / reference.height.toFloat()
            val screenAspect = maxWidth / maxHeight
            val artworkWidth: Dp
            val artworkHeight: Dp
            if (screenAspect > imageAspect) {
                artworkHeight = maxHeight
                artworkWidth = artworkHeight * imageAspect
            } else {
                artworkWidth = maxWidth
                artworkHeight = artworkWidth / imageAspect
            }
            Box(Modifier.size(artworkWidth, artworkHeight).align(Alignment.Center)) {
                Image(
                    bitmap = reference,
                    contentDescription = "Minecraft Java Edition Installations reference screen",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                Box(
                    Modifier.align(Alignment.TopStart)
                        .fillMaxWidth(0.16f)
                        .fillMaxHeight(0.13f)
                        .clickable(onClick = onBack)
                )
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Minecraft Java Edition Installations reference screen artwork could not be loaded", color = Color.White)
            }
        }
    }
}
@Composable
private fun LoaderOptionCard(
    name: String,
    subtitle: String,
    logo: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = if (selected) Color(0xFFFFE8B3) else Color(0xFFFFF2DF),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Gold else Border),
        modifier = modifier.height(104.dp)
    ) {
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(if (selected) listOf(Color(0xFFFFE5A5), Color(0xFFFFF3D8)) else listOf(Color(0xFFFFF8EC), Color(0xFFFFEBD0)))).padding(horizontal = 13.dp, vertical = 11.dp)) {
            Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(0.72f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (selected) "SELECTED" else "MOD LOADER", fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color(0xFF9B6A35), letterSpacing = 1.1.sp)
                Text(name, fontSize = 16.sp, fontWeight = FontWeight.Black, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, fontSize = 10.sp, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Surface(Modifier.align(Alignment.CenterEnd).size(66.dp), color = Color.White.copy(alpha = .92f), shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
                Image(painter = painterResource(id = logo), contentDescription = "$name logo", modifier = Modifier.fillMaxSize().padding(7.dp), contentScale = ContentScale.Fit)
            }
        }
    }
}
@Composable
private fun InstallationsDestination(images: LauncherImages) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profilePreferences = remember(context) {
        context.getSharedPreferences("o_launcher_profiles", android.content.Context.MODE_PRIVATE)
    }
    var selectedProfile by remember(context) {
        mutableStateOf(profilePreferences.getString("selected_profile", "Default") ?: "Default")
    }
    var selectedVersion by remember(context) {
        mutableStateOf(profilePreferences.getString("version.$selectedProfile", "Loading…") ?: "Loading…")
    }
    var selectedLoader by remember(context) {
        mutableStateOf(profilePreferences.getString("loader.$selectedProfile", "Vanilla") ?: "Vanilla")
    }
    var loaderVersions by remember { mutableStateOf<List<OEngineLoaderVersionCatalog.LoaderVersion>>(emptyList()) }
    var selectedLoaderVersion by remember { mutableStateOf<String?>(null) }
    var loaderVersionsLoading by remember { mutableStateOf(false) }
    var installingLoader by remember { mutableStateOf(false) }
    var loaderInstallStatus by remember { mutableStateOf("Select a loader and Minecraft version to discover compatible releases.") }
    var installedLoaderProfileId by remember(context, selectedProfile) {
        mutableStateOf(profilePreferences.getString("loader_profile.$selectedProfile", null))
    }
    var loaderVersionMenu by remember { mutableStateOf(false) }
    var versions by remember { mutableStateOf<List<MinecraftRelease>>(emptyList()) }
    var selectedRelease by remember { mutableStateOf<MinecraftRelease?>(null) }
    var latestReleaseId by remember { mutableStateOf<String?>(null) }
    var versionMenu by remember { mutableStateOf(false) }
    var versionsLoading by remember { mutableStateOf(true) }
    var versionStatus by remember { mutableStateOf("Loading official Minecraft releases…") }
    var downloadingClient by remember { mutableStateOf(false) }
    var downloadBytes by remember { mutableStateOf(0L) }
    var downloadTotal by remember { mutableStateOf(0L) }
    var downloadStatus by remember { mutableStateOf("Choose a version to download its official client file.") }
    var downloadingDependencies by remember { mutableStateOf(false) }
    var dependencyBytes by remember { mutableStateOf(0L) }
    var dependencyTotal by remember { mutableStateOf(0L) }
    var dependencyStatus by remember { mutableStateOf("Libraries and assets are not installed yet.") }
    var launchPlanStatus by remember { mutableStateOf("Launch configuration has not been prepared.") }
    var preparingLaunchPlan by remember { mutableStateOf(false) }
    var preparedLaunchPlan by remember { mutableStateOf<MinecraftVersionInstaller.LaunchPlan?>(null) }
    var commandPreviewStatus by remember { mutableStateOf("O Engine command preview has not been generated.") }
    var requiredJavaMajor by remember { mutableStateOf(17) }
    var downloadingRuntime by remember { mutableStateOf(false) }
    var installingRuntime by remember { mutableStateOf(false) }
    var probingRuntime by remember { mutableStateOf(false) }
    var runtimeProbeStatus by remember { mutableStateOf("Java process has not been tested on this device.") }
    var probingJvmNative by remember { mutableStateOf(false) }
    var jvmNativeProbeStatus by remember { mutableStateOf("JVM native library has not been inspected by the Android linker.") }
    var probingGraphics by remember { mutableStateOf(false) }
    var graphicsProbeStatus by remember { mutableStateOf("Android graphics backend has not been probed.") }
    var probingGameProcess by remember { mutableStateOf(false) }
    var gameProcessStatus by remember { mutableStateOf("The isolated game process has not been checked.") }
    var gameSessionLog by remember { mutableStateOf("No O Engine session log has been loaded.") }
    var showGameSessionLog by remember { mutableStateOf(false) }
    var launchingMinecraft by remember { mutableStateOf(false) }
    var showGameSurface by remember { mutableStateOf(false) }
    var gameSurfaceReady by remember { mutableStateOf(false) }
    var gameProcessClient by remember { mutableStateOf<OEngineGameProcessClient?>(null) }
    val savedTouchControls = remember(context) { OEngineTouchControls.load(context) }
    val gameInputForwarder = remember(gameProcessClient) {
        OEngineGameInputForwarder(sendBatch = { batch -> gameProcessClient?.sendInputEvents(batch) == true })
    }
    DisposableEffect(gameProcessClient) {
        onDispose { gameProcessClient?.close() }
    }
    var installedRuntimeStatus by remember { mutableStateOf("Java runtime is not extracted or registered.") }
    var runtimeBytes by remember { mutableStateOf(0L) }
    var runtimeTotal by remember { mutableStateOf(0L) }
    var runtimeStatus by remember { mutableStateOf("Android Java runtime will be installed automatically when preparing a version.") }
    var instanceNameDraft by remember { mutableStateOf("") }
    var activeInstance by remember { mutableStateOf(OEngineInstanceManager.active(context)) }
    var instances by remember { mutableStateOf(OEngineInstanceManager.list(context)) }

    LaunchedEffect(Unit) {
        try {
            val (loaded, latest) = MinecraftVersionInstaller.fetchReleases()
            versions = loaded
            latestReleaseId = latest
            val savedVersion = profilePreferences.getString("version.$selectedProfile", null)
            selectedRelease = loaded.firstOrNull { it.id == savedVersion }
                ?: loaded.firstOrNull { it.id == latest }
                ?: loaded.firstOrNull()
            selectedVersion = selectedRelease?.id ?: "Unavailable"
            selectedRelease?.let { profilePreferences.edit().putString("version.$selectedProfile", it.id).apply() }
            versionStatus = "Loaded ${loaded.size} official releases + snapshots. Latest release: $latest"
        } catch (error: Exception) {
            versionStatus = error.message ?: "Could not load Minecraft versions."
        } finally {
            versionsLoading = false
        }
    }
    LaunchedEffect(selectedLoader, selectedVersion) {
        val profileLoader = profilePreferences.getString("loader_profile_loader.$selectedProfile", null)
        val profileMinecraft = profilePreferences.getString("loader_profile_mc.$selectedProfile", null)
        if (installedLoaderProfileId != null &&
            (profileLoader != selectedLoader || profileMinecraft != selectedVersion)) {
            installedLoaderProfileId = null
            profilePreferences.edit().remove("loader_profile.$selectedProfile").apply()
        }
        val loader = when (selectedLoader) {
            "Fabric" -> OEngineModLoader.FABRIC
            "Quilt" -> OEngineModLoader.QUILT
            "Forge" -> OEngineModLoader.FORGE
            "NeoForge" -> OEngineModLoader.NEOFORGE
            "OptiFine" -> OEngineModLoader.OPTIFINE
            else -> OEngineModLoader.VANILLA
        }
        if (loader == OEngineModLoader.VANILLA || selectedVersion == "Loading…" || selectedVersion == "Unavailable") {
            loaderVersions = emptyList()
            selectedLoaderVersion = null
            return@LaunchedEffect
        }
        loaderVersionsLoading = true
        loaderInstallStatus = "Loading official $selectedLoader versions for Minecraft $selectedVersion…"
        try {
            loaderVersions = OEngineLoaderVersionCatalog.fetch(loader, selectedVersion)
            selectedLoaderVersion = profilePreferences.getString("loader_version.$selectedProfile", null)
                ?.takeIf { saved -> loaderVersions.any { it.version == saved } }
                ?: loaderVersions.firstOrNull()?.version
            loaderInstallStatus = if (loaderVersions.isEmpty()) {
                if (loader == OEngineModLoader.OPTIFINE) "OptiFine uses its official manual installer flow."
                else "No compatible $selectedLoader releases were returned for Minecraft $selectedVersion."
            } else "Found ${loaderVersions.size} compatible $selectedLoader releases."
        } catch (error: Exception) {
            loaderVersions = emptyList()
            selectedLoaderVersion = null
            loaderInstallStatus = error.message ?: "Could not load $selectedLoader versions."
        } finally {
            loaderVersionsLoading = false
        }
    }

    var showCreateProfile by remember { mutableStateOf(false) }
    var profileName by remember { mutableStateOf("") }
    val profiles = remember(context) {
        val saved = profilePreferences.getStringSet("names", null).orEmpty()
        mutableStateListOf<String>().apply {
            addAll(saved.filter { it.isNotBlank() }.sortedWith(compareBy<String> { !it.equals("Default", true) }.thenBy { it.lowercase() }))
            if (none { it.equals("Default", true) }) add(0, "Default")
        }
    }
    fun persistProfiles() {
        profilePreferences.edit().putStringSet("names", profiles.toSet()).apply()
    }
    fun restoreProfile(name: String) {
        selectedProfile = name
        val savedVersion = profilePreferences.getString("version.$name", null)
        selectedRelease = versions.firstOrNull { it.id == savedVersion }
            ?: versions.firstOrNull { it.id == latestReleaseId }
            ?: versions.firstOrNull()
        selectedVersion = selectedRelease?.id ?: "Unavailable"
        selectedRelease?.let { release ->
            profilePreferences.edit().putString("version.$name", release.id).apply()
        }
        selectedLoader = profilePreferences.getString("loader.$name", "Vanilla") ?: "Vanilla"
        installedLoaderProfileId = profilePreferences.getString("loader_profile.$name", null)
        profilePreferences.edit().putString("selected_profile", name).apply()
        preparedLaunchPlan = null
        launchPlanStatus = "Selected profile: $name"
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DestinationHero(images, "Installations", "Choose game versions and organize your profiles", "GAME SETUP")
        Surface(color = Panel, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("GAME INSTALLATIONS", fontSize = 23.sp, fontWeight = FontWeight.Black, )
                        Text("Choose the Minecraft version for your launcher profile.", color = Muted, fontSize = 12.sp)
                    }
                    Surface(color = Color(0xFFFFE5A5), shape = RoundedCornerShape(12.dp)) {
                        Text("JAVA EDITION", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }
                Surface(color = Color(0xFFEAF0F4), shape = RoundedCornerShape(17.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
                    Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("GAME INSTANCE", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Text(activeInstance.name, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        var instanceMenu by remember { mutableStateOf(false) }
                        Box {
                            Button(onClick = { instanceMenu = true }, colors = ButtonDefaults.buttonColors(containerColor = Ink)) {
                                Text("SWITCH INSTANCE")
                            }
                            DropdownMenu(expanded = instanceMenu, onDismissRequest = { instanceMenu = false }) {
                                instances.forEach { item ->
                                    DropdownMenuItem(text = { Text(item.name) }, onClick = {
                                        activeInstance = OEngineInstanceManager.select(context, item.id)
                                        instanceMenu = false
                                        preparedLaunchPlan = null
                                        launchPlanStatus = "Selected instance: ${item.name}. Install the selected version in this instance."
                                    })
                                }
                            }
                        }
                        BasicTextField(
                            value = instanceNameDraft,
                            onValueChange = { instanceNameDraft = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(8.dp)).padding(10.dp),
                            decorationBox = { inner ->
                                if (instanceNameDraft.isEmpty()) Text("New instance name (e.g. Survival / Modpack)", color = Muted)
                                inner()
                            }
                        )
                        Button(onClick = {
                            try {
                                val created = OEngineInstanceManager.create(context, instanceNameDraft)
                                instances = OEngineInstanceManager.list(context)
                                activeInstance = OEngineInstanceManager.select(context, created.id)
                                instanceNameDraft = ""
                                preparedLaunchPlan = null
                                launchPlanStatus = "Created isolated instance: ${created.name}"
                            } catch (error: Exception) {
                                launchPlanStatus = error.message ?: "Could not create instance."
                            }
                        }, enabled = instanceNameDraft.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF557F9E))) {
                            Text("CREATE INSTANCE")
                        }
                        Text("Each instance has its own versions, libraries, assets, saves and configuration.", color = Muted, fontSize = 11.sp)
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
                                        text = { Text("Minecraft ${version.id}") },
                                        onClick = {
                                            selectedVersion = version.id
                                            selectedRelease = version
                                            profilePreferences.edit().putString("version.$selectedProfile", version.id).apply()
                                            versionMenu = false
                                        },
                                        leadingIcon = { if (selectedVersion == version.id) Icon(Icons.Filled.Checkroom, contentDescription = null) }
                                    )
                                }
                            }
                        }
                        Text(if (versionsLoading) "Connecting to Mojang…" else versionStatus, color = Muted, fontSize = 11.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                enabled = selectedRelease != null && !downloadingClient,
                                onClick = {
                                    val release = selectedRelease ?: return@Button
                                    downloadingClient = true
                                    downloadBytes = 0L
                                    downloadTotal = 0L
                                    downloadStatus = "Preparing Minecraft ${release.id}…"
                                    scope.launch {
                                        try {
                                            val result = MinecraftVersionInstaller.downloadClient(release, activeInstance.root) { done, total ->
                                                downloadBytes = done
                                                downloadTotal = total
                                            }
                                            val mb = result.bytes / (1024.0 * 1024.0)
                                            downloadStatus = "Verified Minecraft ${result.version} client file • ${"%.1f".format(mb)} MB"
                                        } catch (error: Exception) {
                                            downloadStatus = error.message ?: "Minecraft client download failed."
                                        } finally {
                                            downloadingClient = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(if (downloadingClient) "DOWNLOADING…" else "DOWNLOAD CLIENT", fontSize = 11.sp, fontWeight = FontWeight.Black)
                            }
                            if (downloadingClient && downloadTotal > 0L) {
                                Text("${(downloadBytes * 100 / downloadTotal).coerceIn(0, 100)}%", color = Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                        if (downloadingClient) {
                            LinearProgressIndicator(
                                progress = { if (downloadTotal > 0L) (downloadBytes.toFloat() / downloadTotal).coerceIn(0f, 1f) else 0f },
                                modifier = Modifier.fillMaxWidth(),
                                color = Gold
                            )
                        }
                        Text(downloadStatus, color = Muted, fontSize = 11.sp)
                        Divider(color = Border)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                enabled = selectedRelease != null && !downloadingClient && !downloadingDependencies,
                                onClick = {
                                    val release = selectedRelease ?: return@Button
                                    downloadingDependencies = true
                                    dependencyBytes = 0L
                                    dependencyTotal = 0L
                                    dependencyStatus = "Preparing libraries and asset index…"
                                    scope.launch {
                                        try {
                                            val result = MinecraftVersionInstaller.downloadLibrariesAndAssets(
                                                release, activeInstance.root
                                            ) { done, total, label ->
                                                dependencyBytes = done
                                                dependencyTotal = total
                                                dependencyStatus = label
                                            }
                                            val mb = result.bytes / (1024.0 * 1024.0)
                                            dependencyStatus = "Verified ${result.libraries} libraries and ${result.assets} assets • staged ${result.nativeArchives} Linux native archives / ${result.nativeFiles} files (not assumed Android-compatible) • ${"%.1f".format(mb)} MB"
                                        } catch (error: Exception) {
                                            dependencyStatus = error.message ?: "Library and asset download failed."
                                        } finally {
                                            downloadingDependencies = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6D9D65), contentColor = Color.White),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(if (downloadingDependencies) "INSTALLING…" else "DOWNLOAD LIBRARIES + ASSETS", fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                            if (downloadingDependencies && dependencyTotal > 0L) {
                                Text("${(dependencyBytes * 100 / dependencyTotal).coerceIn(0, 100)}%", color = Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                        if (downloadingDependencies) {
                            LinearProgressIndicator(
                                progress = { if (dependencyTotal > 0L) (dependencyBytes.toFloat() / dependencyTotal).coerceIn(0f, 1f) else 0f },
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF6D9D65)
                            )
                        }
                        Text(dependencyStatus, color = Muted, fontSize = 11.sp)
                        Button(
                            enabled = selectedRelease != null && !downloadingClient && !downloadingDependencies && !preparingLaunchPlan,
                            onClick = {
                                val release = selectedRelease ?: return@Button
                                preparingLaunchPlan = true
                                launchPlanStatus = "Reading version metadata and checking installed files…"
                                scope.launch {
                                    try {
                                        downloadingClient = true
                                        downloadStatus = "Installing verified Minecraft client for ${release.id}…"
                                        MinecraftVersionInstaller.downloadClient(release, activeInstance.root) { received, total ->
                                            downloadBytes = received
                                            downloadTotal = total
                                        }
                                        downloadingClient = false

                                        downloadingDependencies = true
                                        dependencyStatus = "Installing libraries, native archives and assets…"
                                        val dependencies = MinecraftVersionInstaller.downloadLibrariesAndAssets(
                                            release, activeInstance.root
                                        ) { done, total, label ->
                                            dependencyBytes = done
                                            dependencyTotal = total
                                            dependencyStatus = label
                                        }
                                        downloadingDependencies = false

                                        val plan = MinecraftVersionInstaller.prepareLaunchPlan(
                                            release,
                                            activeInstance.root,
                                            maxMemoryMb = context.getSharedPreferences("o_launcher_settings", android.content.Context.MODE_PRIVATE)
                                                .getInt("minecraft_memory_mb", 2048).coerceIn(512, 8192)
                                        )
                                        preparedLaunchPlan = plan
                                        requiredJavaMajor = plan.javaMajorVersion
                                        installingRuntime = true
                                        launchPlanStatus = "Game files ready. Checking and installing Android Java ${plan.javaMajorVersion} automatically…"
                                        val runtime = AndroidRuntimeInstaller.ensureRuntime(
                                            plan.javaMajorVersion,
                                            activeInstance.root,
                                            { received, total -> runtimeBytes = received; runtimeTotal = total },
                                            { status -> runtimeStatus = status }
                                        )
                                        installedRuntimeStatus = "Ready: Android Java ${runtime.javaMajor} (${runtime.architecture})"
                                        val mb = dependencies.bytes / (1024.0 * 1024.0)
                                        launchPlanStatus = "Installed ${plan.version} in ${activeInstance.name}: client verified • ${dependencies.libraries} libraries • ${dependencies.assets} assets • ${dependencies.nativeArchives} Linux native archives staged • Java ${runtime.javaMajor} ready (${"%.1f".format(mb)} MB dependencies). Renderer compatibility is still required before launch."
                                    } catch (error: Exception) {
                                        launchPlanStatus = error.message ?: "Could not install and prepare this Minecraft version."
                                    } finally {
                                        downloadingClient = false
                                        downloadingDependencies = false
                                        installingRuntime = false
                                        preparingLaunchPlan = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (preparingLaunchPlan) "INSTALLING VERSION…" else "INSTALL VERSION + JAVA AUTOMATICALLY", fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                        Text(launchPlanStatus, color = Muted, fontSize = 11.sp)
                        Button(
                            enabled = preparedLaunchPlan != null,
                            onClick = {
                                val plan = preparedLaunchPlan ?: return@Button
                                val preview = OLauncherEngine.prepareCommandPreview(
                                    activeInstance.root, plan
                                )
                                commandPreviewStatus = buildString {
                                    append("O ENGINE COMMAND PREVIEW • ")
                                    append(preview.arguments.size)
                                    append(" arguments • ")
                                    append(if (preview.launchable) "READY" else preview.blockers.size.toString() + " BLOCKERS")
                                    preview.blockers.forEach { append("\\n• "); append(it) }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF514A78), contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("BUILD O ENGINE COMMAND PREVIEW", fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                        Text(commandPreviewStatus, color = Muted, fontSize = 11.sp)
                        Button(
                            enabled = !downloadingRuntime,
                            onClick = {
                                downloadingRuntime = true
                                runtimeBytes = 0L
                                runtimeTotal = 0L
                                runtimeStatus = "Downloading Android Java $requiredJavaMajor runtime archive…"
                                scope.launch {
                                    try {
                                        val result = AndroidRuntimeInstaller.downloadRuntime(
                                            requiredJavaMajor,
                                            activeInstance.root
                                        ) { received, total ->
                                            runtimeBytes = received
                                            runtimeTotal = total
                                        }
                                        val mb = result.bytes / (1024.0 * 1024.0)
                                        runtimeStatus = "Downloaded Java ${result.javaMajor} (${result.architecture}) archive • ${"%.1f".format(mb)} MB. Ready to extract."
                                    } catch (error: Exception) {
                                        runtimeStatus = error.message ?: "Could not download Android Java runtime."
                                    } finally {
                                        downloadingRuntime = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF557F9E), contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (downloadingRuntime) "DOWNLOADING JAVA $requiredJavaMajor…" else "DOWNLOAD JAVA $requiredJavaMajor RUNTIME", fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                        if (downloadingRuntime) {
                            LinearProgressIndicator(
                                progress = { if (runtimeTotal > 0L) (runtimeBytes.toFloat() / runtimeTotal).coerceIn(0f, 1f) else 0f },
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF557F9E)
                            )
                        }
                        Text(runtimeStatus, color = Muted, fontSize = 11.sp)
                        Button(
                            enabled = !downloadingRuntime && !installingRuntime,
                            onClick = {
                                installingRuntime = true
                                installedRuntimeStatus = "Preparing runtime extraction…"
                                scope.launch {
                                    try {
                                        val arch = AndroidRuntimeInstaller.deviceArchitecture()
                                        val filename = "jre" + requiredJavaMajor + "-android-" + arch + ".tar.xz"
                                        val archive = File(activeInstance.root, "oengine/runtime-archives/" + filename)
                                        val result = AndroidRuntimeInstaller.installRuntime(
                                            archive,
                                            requiredJavaMajor,
                                            activeInstance.root
                                        ) { status -> installedRuntimeStatus = status }
                                        installedRuntimeStatus = "Java " + result.javaMajor + " extracted to " + result.runtimeHome.name + " • " + result.filesExtracted + " files. Executable: " + result.javaExecutable.canExecute()
                                    } catch (error: Exception) {
                                        installedRuntimeStatus = error.message ?: "Could not extract the Java runtime."
                                    } finally {
                                        installingRuntime = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF477A62), contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (installingRuntime) "EXTRACTING JAVA " + requiredJavaMajor + "…" else "EXTRACT / INSTALL JAVA " + requiredJavaMajor, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                        Text(installedRuntimeStatus, color = Muted, fontSize = 11.sp)
                        Button(
                            enabled = !probingRuntime && !installingRuntime && !downloadingRuntime,
                            onClick = {
                                probingRuntime = true
                                runtimeProbeStatus = "O Engine is starting Java and checking the runtime…"
                                scope.launch {
                                    try {
                                        val result = OLauncherEngine.probeRuntime(activeInstance.root, requiredJavaMajor)
                                        runtimeProbeStatus = if (result.successful) {
                                            "JAVA TEST PASSED • " + result.runtime.name + " • " + result.output.trim().replace("\n", " ")
                                        } else {
                                            "JAVA TEST FAILED (exit " + result.exitCode + ") • " + result.output.takeLast(700)
                                        }
                                    } catch (error: Exception) {
                                        runtimeProbeStatus = error.message ?: "O Engine could not start this Java runtime."
                                    } finally {
                                        probingRuntime = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF365E83), contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (probingRuntime) "TESTING JAVA…" else "TEST JAVA WITH O ENGINE", fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                        Text(runtimeProbeStatus, color = Muted, fontSize = 11.sp)
                        Button(
                            enabled = preparedLaunchPlan != null && !probingJvmNative && !installingRuntime && !downloadingRuntime,
                            onClick = {
                                val plan = preparedLaunchPlan ?: return@Button
                                probingJvmNative = true
                                jvmNativeProbeStatus = "O Engine is asking Android's linker to inspect the selected libjvm.so…"
                                scope.launch {
                                    try {
                                        val preview = OLauncherEngine.prepareCommandPreview(
                                            activeInstance.root, plan
                                        )
                                        val bootstrap = preview.jvmBootstrapPlan
                                        if (bootstrap == null) {
                                            jvmNativeProbeStatus = "JVM PROBE BLOCKED • " +
                                                (preview.blockers.firstOrNull() ?: "No matching installed Java runtime.")
                                        } else if (!bootstrap.structurallyReady) {
                                            jvmNativeProbeStatus = "JVM PROBE BLOCKED • " +
                                                bootstrap.blockers.joinToString(" • ")
                                        } else {
                                            val result = OLauncherEngine.probeJvmInvocationApi(bootstrap)
                                            jvmNativeProbeStatus = (if (result.successful) "JVM SYMBOL PROBE PASSED" else "JVM SYMBOL PROBE FAILED") +
                                                " • " + result.status + " • " + result.detail
                                        }
                                    } catch (error: Exception) {
                                        jvmNativeProbeStatus = "JVM NATIVE PROBE ERROR • " +
                                            (error.message ?: "Android could not inspect libjvm.so.")
                                    } finally {
                                        probingJvmNative = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF765A3D), contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (probingJvmNative) "INSPECTING JVM NATIVE…" else "PROBE JVM NATIVE LINKER", fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                        Text(jvmNativeProbeStatus, color = Muted, fontSize = 11.sp)
                        Text("This only checks whether Android can open libjvm.so and find JNI_CreateJavaVM / JNI_GetCreatedJavaVMs. It does not create the JVM or launch Minecraft.", color = Muted, fontSize = 10.sp)
                        Button(
                            enabled = !probingGameProcess,
                            onClick = {
                                gameProcessClient?.close()
                                probingGameProcess = true
                                gameProcessStatus = "Starting the separate :game process and checking IPC…"
                                val client = OEngineGameProcessClient(context, { result ->
                                    gameProcessStatus = result.fold(
                                        onSuccess = { status ->
                                            probingGameProcess = false
                                            if (status.ready) {
                                                "GAME PROCESS IPC PASSED • PID " + status.pid + " • " + status.processName + " • " + status.phase
                                            } else {
                                                "GAME PROCESS NOT READY • " + status.phase
                                            }
                                        },
                                        onFailure = { error ->
                                            probingGameProcess = false
                                            "GAME PROCESS IPC FAILED • " + (error.message ?: "Unknown process error")
                                        }
                                    )
                                })
                                gameProcessClient = client
                                if (!client.connect()) probingGameProcess = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F755C), contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (probingGameProcess) "CHECKING GAME PROCESS…" else "TEST ISOLATED GAME PROCESS", fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                        Text(gameProcessStatus, color = Muted, fontSize = 11.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = {
                                    gameSessionLog = OEngineSessionLog.read(context)
                                    showGameSessionLog = true
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("VIEW SESSION LOG", fontSize = 9.sp, fontWeight = FontWeight.Black)
                            }
                            Text("Private diagnostic log • tokens and raw game arguments are not recorded", color = Muted, fontSize = 9.sp)
                        }
                        if (showGameSessionLog) {
                            AlertDialog(
                                onDismissRequest = { showGameSessionLog = false },
                                title = { Text("O ENGINE SESSION LOG") },
                                text = {
                                    Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                                        Text(gameSessionLog, fontSize = 10.sp, color = Ink)
                                    }
                                },
                                confirmButton = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(onClick = {
                                            val report = OEngineSessionLog.read(context, 120000)
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_SUBJECT, "O Launcher Engine Diagnostics")
                                                putExtra(Intent.EXTRA_TEXT, report)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share O Engine diagnostics"))
                                        }) { Text("SHARE") }
                                        TextButton(onClick = {
                                            gameSessionLog = OEngineSessionLog.read(context)
                                        }) { Text("REFRESH") }
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showGameSessionLog = false }) { Text("CLOSE") }
                                }
                            )
                        }
                        Text("This starts the private :game Android process and checks Messenger IPC only. It does not load Java or launch Minecraft.", color = Muted, fontSize = 10.sp)
                        Button(
                            enabled = preparedLaunchPlan != null && !probingGameProcess && !installingRuntime && !downloadingRuntime,
                            onClick = {
                                val launchPlan = preparedLaunchPlan ?: return@Button
                                val bootstrap = OLauncherEngine.prepareCommandPreview(activeInstance.root, launchPlan).jvmBootstrapPlan
                                if (bootstrap == null || !bootstrap.structurallyReady || bootstrap.jvmLibrary == null) {
                                    gameProcessStatus = "GAME PROCESS JVM PROBE BLOCKED • " +
                                        (bootstrap?.blockers?.joinToString(" • ") ?: "Prepare a valid Minecraft Java runtime first.")
                                    return@Button
                                }
                                probingGameProcess = true
                                gameProcessStatus = "Starting :game process; JVM linker probe will run there…"
                                lateinit var client: OEngineGameProcessClient
                                client = OEngineGameProcessClient(
                                    context,
                                    { result ->
                                        result.fold(
                                            onSuccess = { status ->
                                                gameProcessStatus = "GAME PROCESS READY • PID " + status.pid + " • " + status.processName + " • probing libjvm…"
                                                client.probeJvmLibrary(
                                                    bootstrap.runtime.home,
                                                    bootstrap.workingDirectory,
                                                    bootstrap.jvmLibrary
                                                )
                                            },
                                            onFailure = { error ->
                                                probingGameProcess = false
                                                gameProcessStatus = "GAME PROCESS IPC FAILED • " + (error.message ?: "Unknown process error")
                                            }
                                        )
                                    },
                                    { result ->
                                        probingGameProcess = false
                                        gameProcessStatus = result.fold(
                                            onSuccess = { probe ->
                                                (if (probe.successful) "ISOLATED JVM LINKER PROBE PASSED" else "ISOLATED JVM LINKER PROBE FAILED") +
                                                    " • " + probe.status + " • " + probe.detail
                                            },
                                            onFailure = { error ->
                                                "ISOLATED JVM LINKER PROBE ERROR • " + (error.message ?: "Unknown native linker error")
                                            }
                                        )
                                    }
                                )
                                gameProcessClient = client
                                client.connect()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A4F79), contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (probingGameProcess) "PROBING JVM IN GAME PROCESS…" else "PROBE JVM IN ISOLATED GAME PROCESS", fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                        Button(
                            enabled = preparedLaunchPlan != null && !probingGameProcess && !installingRuntime && !downloadingRuntime,
                            onClick = {
                                val launchPlan = preparedLaunchPlan ?: return@Button
                                val androidAbi = android.os.Build.SUPPORTED_ABIS.firstOrNull {
                                    it in setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
                                }
                                if (androidAbi == null) {
                                    gameProcessStatus = "ANDROID GLFW BLOCKED • no supported device ABI."
                                    return@Button
                                }
                                val androidGlfw = runCatching {
                                    OEngineAndroidGlfwArtifactInstaller.install(
                                        sourceDirectory = File(context.applicationInfo.nativeLibraryDir),
                                        destinationRoot = File(context.filesDir, "oengine/android-glfw"),
                                        abi = androidAbi
                                    )
                                }.getOrElse { error ->
                                    gameProcessStatus = "ANDROID GLFW STAGING FAILED • " + (error.message ?: error.javaClass.simpleName)
                                    return@Button
                                }
                                val androidGlfwLibrary = androidGlfw.libraries.single { it.name == "libglfw.so" }
                                val preview = OLauncherEngine.prepareCommandPreview(
                                    activeInstance.root,
                                    launchPlan,
                                    rendererLibraryAlias = OEngineGraphicsStack.LTW_LWJGL_LIBRARY_ALIAS,
                                    androidGlfwLibrary = androidGlfwLibrary
                                )
                                val bootstrap = preview.jvmBootstrapPlan
                                val jvmLibrary = bootstrap?.jvmLibrary
                                if (bootstrap == null || !bootstrap.structurallyReady || jvmLibrary == null) {
                                    gameProcessStatus = "JVM CREATION BLOCKED • " +
                                        (bootstrap?.blockers?.joinToString(" • ") ?: "Prepare a valid Minecraft Java runtime first.")
                                    return@Button
                                }
                                val lwjgl = OEngineLwjglNativeResolver.resolve(launchPlan.classpath.map(::File))
                                if (lwjgl.generation != OEngineLwjglGeneration.LWJGL3 ||
                                    !lwjgl.dynamicLinkLoaderClassPresent || !lwjgl.ndlopenSignaturePresent
                                ) {
                                    gameProcessStatus = "LTW STARTUP BLOCKED • Selected LWJGL does not expose the verified DynamicLinkLoader.ndlopen(long,int) surface."
                                    return@Button
                                }
                                val artifact = try {
                                    OEngineLtwArtifactInstaller.install(context)
                                } catch (error: Exception) {
                                    gameProcessStatus = "LTW ARTIFACT BLOCKED • " + (error.message ?: "Pinned LTW is missing from this APK.")
                                    return@Button
                                }
                                val rendererRoot = File(context.filesDir, "oengine/renderers").canonicalFile
                                probingGameProcess = true
                                gameProcessStatus = "Starting :game; staging LTW before HotSpot…"
                                lateinit var client: OEngineGameProcessClient
                                client = OEngineGameProcessClient(
                                    context = context,
                                    onStatus = { result ->
                                        result.fold(
                                            onSuccess = { status ->
                                                if (status.ready) {
                                                    gameProcessStatus = "GAME PROCESS READY • checking LTW graphics requirements…"
                                                    scope.launch {
                                                        try {
                                                            val graphics = OEngineGraphics.probe()
                                                            val blocker = OEngineGraphicsStack.ltwCompatibilityBlocker(graphics.openGlesMajor)
                                                            if (blocker != null) {
                                                                probingGameProcess = false
                                                                gameProcessStatus = "LTW DEVICE INCOMPATIBLE • " + blocker
                                                            } else {
                                                                gameProcessStatus = "GLES READY • ES " + graphics.openGlesMajor +
                                                                    " • " + graphics.glRenderer + " • staging LTW…"
                                                                val sent = client.prepareRenderer(artifact.libraryFile, rendererRoot, null, null)
                                                                if (!sent) {
                                                                    probingGameProcess = false
                                                                    gameProcessStatus = "LTW PRELOAD REQUEST FAILED • game process is not connected"
                                                                }
                                                            }
                                                        } catch (error: Exception) {
                                                            probingGameProcess = false
                                                            gameProcessStatus = "LTW GRAPHICS PREFLIGHT FAILED • " +
                                                                (error.message ?: "Could not create an Android EGL/OpenGL ES context")
                                                        }
                                                    }
                                                } else {
                                                    probingGameProcess = false
                                                    gameProcessStatus = "GAME PROCESS NOT READY • " + status.phase
                                                }
                                            },
                                            onFailure = { error ->
                                                probingGameProcess = false
                                                gameProcessStatus = "GAME PROCESS IPC FAILED • " + (error.message ?: "Unknown process error")
                                            }
                                        )
                                    },
                                    onJvmMainStarted = { started ->
                                        OEngineSessionLog.append(context, "MINECRAFT_MAIN_DISPATCHED", started.status + " • " + started.detail)
                                        launchingMinecraft = false
                                        gameProcessStatus = "MINECRAFT MAIN DISPATCHED • " + started.status + " • " + started.detail
                                    },
                                    onJvmMainRunning = { running ->
                                        OEngineSessionLog.append(context, "MINECRAFT_MAIN_RUNNING", running.status + " • " + running.detail)
                                        gameProcessStatus = "MINECRAFT RUNNING • " + running.status + " • " + running.detail
                                    },
                                    onJvmMain = { result ->
                                        OEngineSessionLog.append(context, "MINECRAFT_MAIN_RESULT", result.fold(
                                            onSuccess = { main -> main.status + " • " + main.detail },
                                            onFailure = { error -> error.message ?: "Unknown game-process error" }
                                        ), result.exceptionOrNull())
                                        launchingMinecraft = false
                                        showGameSurface = false
                                        gameProcessStatus = result.fold(
                                            onSuccess = { main ->
                                                if (main.successful) {
                                                    "MINECRAFT MAIN RETURNED SUCCESS • " + main.status + " • " + main.detail
                                                } else {
                                                    "MINECRAFT MAIN FAILED • " + main.status + " • " + main.detail
                                                }
                                            },
                                            onFailure = { error ->
                                                "MINECRAFT MAIN IPC FAILED • " + (error.message ?: "Unknown game-process error")
                                            }
                                        )
                                    },
                                    onJvmCreate = { result ->
                                        result.fold(
                                            onSuccess = { created ->
                                                if (created.successful) {
                                                    gameProcessStatus = "HOTSPOT CREATED • installing LWJGL LTW hook…"
                                                    client.installRendererHook(
                                                        artifact.libraryFile,
                                                        rendererRoot,
                                                        OEngineLwjglNativeResolver.DYNAMIC_LINK_LOADER_CLASS_NAME,
                                                        OEngineGraphicsStack.LTW_LWJGL_LIBRARY_ALIAS
                                                    )
                                                } else {
                                                    probingGameProcess = false
                                                    gameProcessStatus = "HOTSPOT VM CREATION FAILED • " + created.status + " • " + created.detail
                                                }
                                            },
                                            onFailure = { error ->
                                                probingGameProcess = false
                                                gameProcessStatus = "HOTSPOT VM CREATION ERROR • " + (error.message ?: "Unknown VM error")
                                            }
                                        )
                                    },
                                    onRendererHook = { result ->
                                        probingGameProcess = false
                                        gameProcessStatus = result.fold(
                                            onSuccess = { hook ->
                                                if (hook.successful) {
                                                    "LTW PRELOADED + LWJGL HOOK INSTALLED • HotSpot is ready; invoke Minecraft main next."
                                                } else {
                                                    "LWJGL LTW HOOK FAILED • " + hook.status + " • " + hook.detail
                                                }
                                            },
                                            onFailure = { error ->
                                                "LWJGL LTW HOOK ERROR • " + (error.message ?: "Unknown hook error")
                                            }
                                        )
                                    },
                                    onGameSurface = { result ->
                                        result.fold(
                                            onSuccess = { surface ->
                                                gameSurfaceReady = surface.successful
                                                gameProcessStatus = if (surface.successful) {
                                                    (if (surface.status == "game_surface_change_queued")
                                                        "ANDROID GAME SURFACE CHANGE QUEUED • "
                                                    else "ANDROID GAME SURFACE READY • ") + surface.detail
                                                } else {
                                                    "ANDROID GAME SURFACE FAILED • " + surface.detail
                                                }
                                            },
                                            onFailure = { error ->
                                                gameSurfaceReady = false
                                                gameProcessStatus = "ANDROID GAME SURFACE IPC FAILED • " + (error.message ?: "Unknown surface error")
                                            }
                                        )
                                    },
                                    onRendererPreload = { result ->
                                        result.fold(
                                            onSuccess = { preload ->
                                                if (preload.successful) {
                                                    gameProcessStatus = "LTW PRELOADED • creating HotSpot with libGLMojo.so alias…"
                                                    client.createJvm(
                                                        bootstrap.runtime.home,
                                                        bootstrap.workingDirectory,
                                                        jvmLibrary,
                                                        bootstrap.vmOptions,
                                                        bootstrap.runtime.javaMajor
                                                    )
                                                } else {
                                                    probingGameProcess = false
                                                    gameProcessStatus = "LTW PRELOAD FAILED • " + preload.status + " • " + preload.detail
                                                }
                                            },
                                            onFailure = { error ->
                                                probingGameProcess = false
                                                gameProcessStatus = "LTW PRELOAD ERROR • " + (error.message ?: "Unknown renderer error")
                                            }
                                        )
                                    }
                                )
                                gameProcessClient?.close()
                                gameProcessClient = client
                                if (!client.connect()) {
                                    probingGameProcess = false
                                    gameProcessStatus = "GAME PROCESS CONNECTION FAILED."
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8A563B), contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (probingGameProcess) "PREPARING LTW + HOTSPOT…" else "PRELOAD LTW + CREATE JVM", fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                        Text("This stages the pinned LTW renderer before JNI_CreateJavaVM, adds the LWJGL libGLMojo.so alias, creates HotSpot, and installs the native-loader hook. It does not prove Minecraft can render yet.", color = Muted, fontSize = 10.sp)
                        Text("EXPERIMENTAL: Android ART and HotSpot coexistence is device-dependent. A native failure may restart only the separate :game process.", color = Color(0xFFD6A06C), fontSize = 10.sp)
                        Button(
                            enabled = preparedLaunchPlan != null && selectedRelease != null && !probingGameProcess && !launchingMinecraft && gameProcessClient != null,
                            onClick = {
                                val release = selectedRelease ?: return@Button
                                val instance = activeInstance
                                launchingMinecraft = true
                                gameSurfaceReady = false
                                showGameSurface = true
                                gameProcessStatus = "REFRESHING MICROSOFT SESSION + BUILDING AUTHENTICATED GAME ARGUMENTS…"
                                scope.launch {
                                    try {
                                        val savedRefreshToken = MicrosoftTokenVault.load(context)
                                            ?: throw IllegalStateException("Sign in with Microsoft on the Account screen before launching Java Edition.")
                                        val account = MinecraftAuth.refreshAccount(savedRefreshToken)
                                        MicrosoftTokenVault.save(context, account.refreshToken)
                                        val authenticatedPlan = MinecraftVersionInstaller.prepareLaunchPlan(
                                            release,
                                            instance.root,
                                            account.name,
                                            account.uuid,
                                            account.accessToken,
                                            installedLoaderProfileId,
                                            context.getSharedPreferences("o_launcher_settings", android.content.Context.MODE_PRIVATE)
                                                .getInt("minecraft_memory_mb", 2048).coerceIn(512, 8192)
                                        )
                                        preparedLaunchPlan = authenticatedPlan
                                        withTimeout(15_000L) {
                                            snapshotFlow { gameSurfaceReady }.first { it }
                                        }
                                        val sent = gameProcessClient?.invokeMain(
                                            authenticatedPlan.mainClass,
                                            authenticatedPlan.gameArguments
                                        ) == true
                                        gameProcessStatus = if (sent) {
                                            "AUTHENTICATED LAUNCH REQUEST SENT • awaiting the game-process result for " + account.name + " • " + authenticatedPlan.version
                                        } else {
                                            launchingMinecraft = false
                                            showGameSurface = false
                                            "AUTHENTICATION SUCCEEDED, BUT MAIN INVOCATION COULD NOT BE SENT • game process is not connected"
                                        }
                                    } catch (error: Exception) {
                                        launchingMinecraft = false
                                        showGameSurface = false
                                        gameProcessStatus = "MINECRAFT LAUNCH PREPARATION FAILED • " + (error.message ?: "Unknown authentication error")
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B7650), contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (launchingMinecraft) "AUTHENTICATING + STARTING…" else "AUTHENTICATE + INVOKE MINECRAFT", fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                        Text("This invokes the prepared version main class with its generated game arguments after HotSpot is created. It is an experimental launch step; missing Android natives or renderer support can still stop Minecraft.", color = Muted, fontSize = 10.sp)
                        Button(
                            enabled = !probingGraphics,
                            onClick = {
                                probingGraphics = true
                                graphicsProbeStatus = "Creating a temporary Android EGL / OpenGL ES context…"
                                scope.launch {
                                    try {
                                        val result = OEngineGraphics.probe()
                                        graphicsProbeStatus = "GRAPHICS PROBE PASSED • OpenGL ES ${result.openGlesMajor} • ${result.glVersion} • ${result.glVendor} • ${result.glRenderer} • ${result.glExtensions.size} extensions"
                                    } catch (error: Exception) {
                                        graphicsProbeStatus = "GRAPHICS PROBE FAILED • " + (error.message ?: "Unknown EGL error")
                                    } finally {
                                        probingGraphics = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B568A), contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (probingGraphics) "PROBING GRAPHICS…" else "PROBE ANDROID GRAPHICS", fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                        Text(graphicsProbeStatus, color = Muted, fontSize = 11.sp)
                        Text("This tests Android EGL and OpenGL ES only. It does not provide desktop OpenGL, GLFW, LWJGL, or Minecraft compatibility yet.", color = Muted, fontSize = 10.sp)
                        Text("O Engine owns runtime discovery and process startup. A successful Java test is required before we connect Minecraft and graphics.", color = Muted, fontSize = 10.sp)
                    }
                }
            }
        }

        Surface(color = Panel, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("MOD LOADERS", fontSize = 20.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                        Text("Choose the mod platform for this game profile.", color = Muted, fontSize = 12.sp)
                    }
                    Surface(color = Color(0xFFFFE5A5), shape = RoundedCornerShape(10.dp)) {
                        Text("6 LOADERS", modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    LoaderOptionCard("Fabric", "Fast, lightweight", R.drawable.loader_fabric, selectedLoader == "Fabric", {
                        selectedLoader = "Fabric"
                        profilePreferences.edit().putString("loader.$selectedProfile", "Fabric").apply()
                    }, Modifier.weight(1f))
                    LoaderOptionCard("NeoForge", "Modern mod platform", R.drawable.loader_neoforge, selectedLoader == "NeoForge", {
                        selectedLoader = "NeoForge"
                        profilePreferences.edit().putString("loader.$selectedProfile", "NeoForge").apply()
                    }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    LoaderOptionCard("Forge", "Classic mod platform", R.drawable.loader_forge, selectedLoader == "Forge", {
                        selectedLoader = "Forge"
                        profilePreferences.edit().putString("loader.$selectedProfile", "Forge").apply()
                    }, Modifier.weight(1f))
                    LoaderOptionCard("OptiFine", "Graphics and performance", R.drawable.loader_optifine, selectedLoader == "OptiFine", {
                        selectedLoader = "OptiFine"
                        profilePreferences.edit().putString("loader.$selectedProfile", "OptiFine").apply()
                    }, Modifier.weight(1f))
                }
                Surface(
                    onClick = {
                        selectedLoader = "Vanilla"
                        installedLoaderProfileId = null
                        profilePreferences.edit().remove("loader_profile.$selectedProfile")
                            .remove("loader_profile_loader.$selectedProfile")
                            .remove("loader_profile_mc.$selectedProfile")
                            .putString("loader.$selectedProfile", "Vanilla").apply()
                        preparedLaunchPlan = null
                    },
                    color = if (selectedLoader == "Vanilla") Color(0xFFFFE8B3) else Color(0xFFFFF2DF),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (selectedLoader == "Vanilla") Gold else Border),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("V", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Ink)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Vanilla Minecraft", fontWeight = FontWeight.Bold, color = Ink)
                            Text("Official unmodified game", fontSize = 10.sp, color = Muted)
                        }
                        Spacer(Modifier.weight(1f))
                        if (selectedLoader == "Vanilla") Text("SELECTED", fontSize = 9.sp, color = Color(0xFF9B6A35), fontWeight = FontWeight.Black)
                    }
                }
                Surface(
                    onClick = {
                        selectedLoader = "Quilt"
                        installedLoaderProfileId = null
                        profilePreferences.edit().remove("loader_profile.$selectedProfile")
                            .remove("loader_profile_loader.$selectedProfile")
                            .remove("loader_profile_mc.$selectedProfile")
                            .putString("loader.$selectedProfile", "Quilt").apply()
                        preparedLaunchPlan = null
                    },
                    color = if (selectedLoader == "Quilt") Color(0xFFFFE8B3) else Color(0xFFFFF2DF),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (selectedLoader == "Quilt") Gold else Border),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Q", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Ink)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Quilt", fontWeight = FontWeight.Bold, color = Ink)
                            Text("Community mod platform", fontSize = 10.sp, color = Muted)
                        }
                        Spacer(Modifier.weight(1f))
                        if (selectedLoader == "Quilt") Text("SELECTED", fontSize = 9.sp, color = Color(0xFF9B6A35), fontWeight = FontWeight.Black)
                    }
                }
                if (selectedLoader != "Vanilla") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box {
                            OutlinedButton(onClick = { loaderVersionMenu = true }, enabled = !loaderVersionsLoading && loaderVersions.isNotEmpty()) {
                                Text(selectedLoaderVersion ?: if (loaderVersionsLoading) "Loading versions…" else "No compatible versions")
                            }
                            DropdownMenu(expanded = loaderVersionMenu, onDismissRequest = { loaderVersionMenu = false }) {
                                loaderVersions.forEach { item ->
                                    DropdownMenuItem(
                                        text = { Text(item.version + if (item.stable) "  •  Recommended" else "") },
                                        onClick = {
                                            selectedLoaderVersion = item.version
                                            profilePreferences.edit().putString("loader_version.$selectedProfile", item.version).apply()
                                            loaderVersionMenu = false
                                            installedLoaderProfileId = null
                                            profilePreferences.edit().remove("loader_profile.$selectedProfile").apply()
                                        }
                                    )
                                }
                            }
                        }
                        Button(
                            onClick = {
                                val release = selectedRelease
                                val version = selectedLoaderVersion
                                if (release == null || version == null) {
                                    loaderInstallStatus = "Choose a Minecraft version and a compatible loader release first."
                                } else {
                                    scope.launch {
                                        installingLoader = true
                                        try {
                                            val loader = when (selectedLoader) {
                                                "Fabric" -> OEngineModLoader.FABRIC
                                                "Quilt" -> OEngineModLoader.QUILT
                                                "Forge" -> OEngineModLoader.FORGE
                                                "NeoForge" -> OEngineModLoader.NEOFORGE
                                                else -> OEngineModLoader.OPTIFINE
                                            }
                                            if (loader == OEngineModLoader.FABRIC || loader == OEngineModLoader.QUILT) {
                                                val result = OEngineLoaderInstaller.install(
                                                    loader, release.id, version, activeInstance.root
                                                ) { done, total, label ->
                                                    loaderInstallStatus = "$label ($done/$total)"
                                                }
                                                installedLoaderProfileId = result.profileId
                                                profilePreferences.edit()
                                                    .putString("loader_profile.$selectedProfile", result.profileId)
                                                    .putString("loader_version.$selectedProfile", version)
                                                    .putString("loader_profile_loader.$selectedProfile", selectedLoader)
                                                    .putString("loader_profile_mc.$selectedProfile", release.id)
                                                    .apply()
                                                preparedLaunchPlan = null
                                                loaderInstallStatus = "Installed $selectedLoader $version • ${result.librariesInstalled} libraries • ${result.bytesDownloaded / 1024} KB downloaded. Profile metadata saved."
                                            } else {
                                                val staged = OEngineInstallerJarStager.stage(
                                                    loader, release.id, version, activeInstance.root
                                                ) { received, total ->
                                                    loaderInstallStatus = if (total > 0) {
                                                        "Downloading $selectedLoader installer: ${received * 100 / total}%"
                                                    } else "Downloading $selectedLoader installer: ${received / 1024} KB"
                                                }
                                                val agent = OEngineInstallerAgent.stage(context, activeInstance.root)
                                                val invocation = OEngineInstallerAgent.buildAwtInvocation(agent, staged.file)
                                                // Capture profile JSON state before the AWT installer runs. The completion
                                                // handler can then distinguish newly-created and rewritten profile IDs.
                                                val profileSnapshot = OEngineInstallerProfileWatcher.snapshot(activeInstance.root)
                                                val profileSnapshotJson = org.json.JSONObject().apply {
                                                    profileSnapshot.fingerprints.forEach { (id, fingerprint) -> put(id, fingerprint) }
                                                }
                                                check(profilePreferences.edit()
                                                    .putString("pending_installer_profile_snapshot.$selectedProfile", profileSnapshotJson.toString())
                                                    .putString("pending_installer_agent.$selectedProfile", agent.absolutePath)
                                                    .putString("pending_installer_args.$selectedProfile", org.json.JSONArray(invocation.javaArgs).toString())
                                                    .putString("pending_installer_jar.$selectedProfile", invocation.installerPath)
                                                    .putString("pending_installer_instance.$selectedProfile", activeInstance.root.canonicalPath)
                                                    .putString("pending_installer_loader.$selectedProfile", selectedLoader)
                                                    .putString("pending_installer_version.$selectedProfile", version)
                                                    .putString("pending_installer_mc.$selectedProfile", release.id)
                                                    .commit()) { "Could not save the pending Mojo AWT installer request." }
                                                val handoff = OEngineMojoAwtInstallerHandoff.readPending(
                                                    profilePreferences, selectedProfile
                                                )
                                                loaderInstallStatus = "$selectedLoader installer SHA-1 verified (${staged.bytes / 1024} KB). " +
                                                    "The saved Mojo AWT handoff was validated for ${handoff.instanceRoot.name}; " +
                                                    "native AWT/Cacio packaging is configured, but the AWT Java activity is not linked into the APK yet; installation has not started."
                                            }
                                        } catch (error: Exception) {
                                            loaderInstallStatus = error.message ?: "Could not install $selectedLoader."
                                        } finally {
                                            installingLoader = false
                                        }
                                    }
                                }
                            },
                            enabled = !loaderVersionsLoading && !installingLoader && selectedLoaderVersion != null &&
                                selectedLoader != "OptiFine",
                            colors = ButtonDefaults.buttonColors(containerColor = Ink)
                        ) {
                            Text(if (installingLoader) "WORKING…" else if (selectedLoader == "Forge" || selectedLoader == "NeoForge") "DOWNLOAD INSTALLER" else "INSTALL LOADER")
                        }
                    }
                    Text(loaderInstallStatus, color = Muted, fontSize = 11.sp)
                    if (installedLoaderProfileId != null) {
                        Text("Installed profile: $installedLoaderProfileId", color = Color(0xFF38764B), fontSize = 11.sp)
                    }
                    if (selectedLoader == "OptiFine") {
                        Text("OptiFine must be downloaded from its official site and installed with the compatible Minecraft profile.", color = Muted, fontSize = 10.sp)
                        OutlinedButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                                OEngineLoaderVersionCatalog.manualInstallerSource(OEngineModLoader.OPTIFINE)
                            )))
                        }) { Text("OPEN OFFICIAL OPTIFINE DOWNLOADS") }
                    }
                } else {
                    Text("Vanilla Minecraft profile selected.", color = Muted, fontSize = 11.sp)
                }
                if (selectedLoader == "OptiFine") {
                    Text("OptiFine compatibility depends on the Minecraft version; many releases use Forge.", color = Muted, fontSize = 10.sp)
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
                    Surface(
                        color = if (selectedProfile == name) Color(0xFFFFE7B8) else Color(0xFFFFF2DF),
                        shape = RoundedCornerShape(15.dp),
                        modifier = Modifier.fillMaxWidth().clickable {
                            restoreProfile(name)
                        }
                    ) {
                        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                images.avatar,
                                contentDescription = "Profile avatar",
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text(name, fontWeight = FontWeight.Black, fontSize = 14.sp)
                                Text(
                                    "Minecraft ${profilePreferences.getString("version.$name", if (name == selectedProfile) selectedVersion else "Not selected") ?: "Not selected"}  •  ${profilePreferences.getString("loader.$name", "Vanilla") ?: "Vanilla"}",
                                    color = Muted,
                                    fontSize = 11.sp
                                )
                            }
                            if (index == 0) {
                                Surface(color = Color(0xFFFFE5A5), shape = RoundedCornerShape(8.dp)) {
                                    Text("DEFAULT", modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp), fontSize = 9.sp, fontWeight = FontWeight.Black)
                                }
                            } else {
                                TextButton(onClick = {
                                    val removedName = profiles[index]
                                    profiles.removeAt(index)
                                    profilePreferences.edit().remove("version.$removedName").remove("loader.$removedName").apply()
                                    if (selectedProfile == removedName) {
                                        restoreProfile("Default")
                                    }
                                    persistProfiles()
                                }) { Text("REMOVE", color = Muted, fontSize = 10.sp) }
                            }
                        }
                    }
                }
                Text("Selected profile: $selectedProfile. Each profile remembers its Minecraft version and preferred loader; loader installation is still in progress.", color = Muted, fontSize = 11.sp)
            }
        }
    }

    if (showGameSurface) {
        Dialog(
            onDismissRequest = {
                gameInputForwarder.releaseAll()
                gameSurfaceReady = false
                gameProcessClient?.detachGameSurface()
                showGameSurface = false
            },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AndroidView(
                    factory = { viewContext ->
                        object : SurfaceView(viewContext) {
                            override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
                                super.onWindowFocusChanged(hasWindowFocus)
                                if (!hasWindowFocus) gameInputForwarder.releaseAll()
                                GLFW.onHostWindowFocusChanged(this, hasWindowFocus)
                            }

                            override fun onCapturedPointerEvent(event: MotionEvent): Boolean {
                                val translated = OEngineAndroidInputAdapter.fromMotionEvent(event)
                                if (translated.isNotEmpty()) {
                                    gameInputForwarder.forward(translated)
                                    return true
                                }
                                return super.onCapturedPointerEvent(event)
                            }

                            override fun onDetachedFromWindow() {
                                GLFW.detachHostView(this)
                                super.onDetachedFromWindow()
                            }
                        }.apply {
                            GLFW.attachHostView(this)
                            isFocusable = true
                            isFocusableInTouchMode = true
                            setOnKeyListener { _, keyCode, event ->
                                val translated = OEngineAndroidInputAdapter.fromKeyEvent(event)
                                if (translated == null) false else {
                                    gameInputForwarder.forward(listOf(translated))
                                    true
                                }
                            }
                            setOnTouchListener { _, event ->
                                val translated = OEngineAndroidInputAdapter.fromMotionEvent(event)
                                if (translated.isNotEmpty()) gameInputForwarder.forward(translated)
                                true
                            }
                            setOnGenericMotionListener { _, event ->
                                val translated = OEngineAndroidInputAdapter.fromMotionEvent(event)
                                if (translated.isNotEmpty()) gameInputForwarder.forward(translated)
                                translated.isNotEmpty()
                            }
                            setOnFocusChangeListener { _, hasFocus ->
                                if (!hasFocus) gameInputForwarder.releaseAll()
                            }
                            requestFocus()
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) {
                                    OEngineSessionLog.append(viewContext, "ANDROID_GAME_SURFACE_CREATED", "SurfaceView created")
                                }

                                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                                    gameSurfaceReady = false
                                    val attached = gameProcessClient?.attachGameSurface(holder.surface, width, height) == true
                                    OEngineSessionLog.append(
                                        viewContext,
                                        if (attached) "ANDROID_GAME_SURFACE_SENT" else "ANDROID_GAME_SURFACE_SEND_FAILED",
                                        "SurfaceView size=" + width + "x" + height
                                    )
                                }

                                override fun surfaceDestroyed(holder: SurfaceHolder) {
                                    gameInputForwarder.releaseAll()
                                    gameSurfaceReady = false
                                    gameProcessClient?.detachGameSurface()
                                    OEngineSessionLog.append(viewContext, "ANDROID_GAME_SURFACE_DESTROYED", "SurfaceView destroyed")
                                }
                            })
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                OEngineTouchControlsOverlay(
                    initialControls = savedTouchControls,
                    onKey = { code, down ->
                        gameInputForwarder.forward(listOf(
                            OEngineInputEvent(
                                device = OEngineInputDevice.KEYBOARD,
                                action = if (down) OEngineInputAction.DOWN else OEngineInputAction.UP,
                                code = code,
                                timestampNanos = System.nanoTime()
                            )
                        ))
                    },
                    onLayoutChanged = { layout -> OEngineTouchControls.save(context, layout) }
                )
                Text(
                    "O ENGINE • GAME SURFACE",
                    modifier = Modifier.align(Alignment.BottomStart).padding(18.dp),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
                Button(
                    onClick = {
                        gameInputForwarder.releaseAll()
                        gameProcessClient?.detachGameSurface()
                        showGameSurface = false
                    },
                    modifier = Modifier.align(Alignment.TopEnd).padding(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF342D2B), contentColor = Color.White)
                ) {
                    Text("CLOSE GAME VIEW", fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
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
                    onClick = {
                        val newName = profileName.trim()
                        profiles.add(newName)
                        selectedProfile = newName
                        selectedVersion = selectedRelease?.id ?: "Unavailable"
                        selectedLoader = "Vanilla"
                        profilePreferences.edit()
                            .putString("selected_profile", newName)
                            .putString("version.$newName", selectedVersion)
                            .putString("loader.$newName", selectedLoader)
                            .apply()
                        persistProfiles()
                        showCreateProfile = false
                    }
                ) { Text("CREATE") }
            },
            dismissButton = { TextButton(onClick = { showCreateProfile = false }) { Text("CANCEL") } }
        )
    }
}

/**
 * Android launcher entry point. The manifest launches this Activity when the app icon is tapped.
 */
class MainActivity : ComponentActivity() {
    @Suppress("DEPRECATION")
    private fun enterImmersiveLandscape() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        enterImmersiveLandscape()
        setContent {
            OLauncherApp()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveLandscape()
    }
}
