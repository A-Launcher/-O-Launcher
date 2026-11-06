package com.alauncher.olauncher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val root = remember { (context.getExternalFilesDir(null) ?: context.filesDir).resolve("launcher").apply { mkdirs() } }
    var current by remember { mutableStateOf(root) }
    var refresh by remember { mutableIntStateOf(0) }
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var editingFile by remember { mutableStateOf<File?>(null) }
    var editorText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            try {
                val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val column = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && column >= 0) cursor.getString(column) else null
                } ?: "imported_file"
                context.contentResolver.openInputStream(uri)?.use { input ->
                    current.resolve(File(name).name).outputStream().use { output -> input.copyTo(output) }
                }
            } catch (_: Exception) { message = "Could not import one of the selected files." }
        }
        refresh++
    }
    val entries = remember(current, refresh) {
        current.listFiles()?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
    }

    Column(Modifier.fillMaxWidth().fillMaxHeight().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DestinationHero(images, "Launcher Files", "Manage your launcher folders and files", "FILE MANAGEMENT")
        Surface(color = Panel, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("LAUNCHER FILES", fontSize = 23.sp, fontWeight = FontWeight.Black)
                        Text("Manage your launcher folder without opening a separate file manager.", color = Muted, fontSize = 12.sp)
                    }
                    Button(onClick = { newName = ""; showCreate = true }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = null)
                        Spacer(Modifier.width(5.dp))
                        Text("NEW FOLDER", fontWeight = FontWeight.Black, fontSize = 10.sp)
                    }
                    Spacer(Modifier.width(7.dp))
                    Button(onClick = { importPicker.launch(arrayOf("*/*")) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFE5A5), contentColor = Ink)) {
                        Text("IMPORT", fontWeight = FontWeight.Black, fontSize = 10.sp)
                    }
                }
                Surface(color = Color(0xFFFFF2DF), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = Color(0xFF4775C8))
                        Spacer(Modifier.width(8.dp))
                        val relative = current.relativeTo(root).path
                        Text(if (relative == ".") "launcher/" else "launcher/" + relative + "/", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Spacer(Modifier.weight(1f))
                        TextButton(enabled = current != root, onClick = { current.parentFile?.let { current = it } }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Go up")
                            Text("UP")
                        }
                    }
                }
            }
        }
        Surface(color = Panel, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 520.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (entries.isEmpty()) Text("This folder is empty. Create a folder or import files to get started.", color = Muted, modifier = Modifier.padding(18.dp), fontSize = 13.sp)
                entries.forEach { file ->
                    Surface(color = Color(0xFFFFF2DF), shape = RoundedCornerShape(13.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().clickable {
                            if (file.isDirectory) current = file
                            else if (file.extension.lowercase() in listOf("txt", "json", "properties", "cfg", "log", "yml", "yaml")) {
                                editingFile = file
                                editorText = runCatching { file.readText() }.getOrDefault("")
                            } else message = "This file type can be stored and removed here, but not edited as text."
                        }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (file.isDirectory) Icons.Filled.FolderOpen else Icons.Filled.InsertDriveFile, contentDescription = null, tint = if (file.isDirectory) Color(0xFF4775C8) else Muted)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(file.name, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (file.isDirectory) "Folder" else file.length().toString() + " bytes · Tap text files to edit", color = Muted, fontSize = 10.sp)
                            }
                            IconButton(onClick = {
                                runCatching { if (file.isDirectory) file.deleteRecursively() else file.delete() }
                                refresh++
                            }) { Icon(Icons.Filled.Delete, contentDescription = "Delete " + file.name, tint = Color(0xFFB44B3F)) }
                        }
                    }
                }
            }
        }
        if (message.isNotBlank()) Text(message, color = Muted, fontSize = 11.sp)
        Text("Storage: app-private launcher directory. Files here are managed by O Launcher and are not visible to other apps by default.", color = Muted, fontSize = 10.sp)
    }
    if (showCreate) AlertDialog(
        onDismissRequest = { showCreate = false },
        title = { Text("CREATE FOLDER") },
        text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true, label = { Text("Folder name") }) },
        confirmButton = { TextButton(enabled = newName.isNotBlank() && !newName.contains("/") && !newName.contains("\\"), onClick = {
            val folder = current.resolve(newName.trim())
            if (folder.mkdirs()) refresh++ else message = "Could not create that folder."
            showCreate = false
        }) { Text("CREATE") } },
        dismissButton = { TextButton(onClick = { showCreate = false }) { Text("CANCEL") } }
    )
    editingFile?.let { file ->
        AlertDialog(
            onDismissRequest = { editingFile = null },
            title = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = { OutlinedTextField(value = editorText, onValueChange = { editorText = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 420.dp), maxLines = 18) },
            confirmButton = { TextButton(onClick = { runCatching { file.writeText(editorText) }; editingFile = null; refresh++ }) { Text("SAVE") } },
            dismissButton = { TextButton(onClick = { editingFile = null }) { Text("CANCEL") } }
        )
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
                    message = "Imported successfully. In-game skin applying will be connected with the game runtime."
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
                modifier = Modifier.weight(1.25f).height(300.dp),
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
            Surface(modifier = Modifier.weight(1f).height(300.dp), color = Panel, shape = RoundedCornerShape(24.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
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
    var performance by remember { mutableStateOf(true) }
    var animations by remember { mutableStateOf(true) }
    var compactUi by remember { mutableStateOf(false) }
    var notifications by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DestinationHero(images, "Settings", "Tune O Launcher to match the way you play", "PREFERENCES")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsGroup("APPEARANCE", "Customize the launcher interface") {
                    SettingToggle("Smooth animations", "Use subtle transitions between screens", animations) { animations = it }
                    SettingToggle("Compact interface", "Reduce spacing in content panels", compactUi) { compactUi = it }
                }
                SettingsGroup("PERFORMANCE", "Control launcher resource usage") {
                    SettingToggle("Performance mode", "Prefer a lighter interface on your device", performance) { performance = it }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsGroup("NOTIFICATIONS", "Choose what O Launcher can tell you") {
                    SettingToggle("Launcher notifications", "Updates and important launcher notices", notifications) { notifications = it }
                }
                SettingsGroup("ABOUT THIS DEVICE", "Launcher environment") {
                    SettingInfo("Platform", "Android · Landscape")
                    SettingInfo("Edition", "Minecraft Java Edition")
                    SettingInfo("Build", "O Launcher · Development")
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
    var playerName by remember { mutableStateOf(prefs.getString("offline_name", "Not signed in") ?: "Not signed in") }
    var playerUuid by remember { mutableStateOf(prefs.getString("offline_uuid", "") ?: "") }
    var offlineName by remember { mutableStateOf("") }
    var isOffline by remember { mutableStateOf(prefs.getBoolean("is_offline", false)) }
    var device by remember { mutableStateOf<MicrosoftDeviceCode?>(null) }
    var status by remember { mutableStateOf(if (isOffline) "Offline profile saved on this device." else "Connect a Microsoft account that owns Minecraft: Java Edition.") }
    var busy by remember { mutableStateOf(false) }
    var signedIn by remember { mutableStateOf(false) }
    var showOfflineForm by remember { mutableStateOf(false) }
    var offlineError by remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DestinationHero(images, "Player account", "Connect a Microsoft account or create an offline profile", "YOUR PROFILE")
        Surface(Modifier.fillMaxWidth(), color = Panel, shape = RoundedCornerShape(24.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
            Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                Image(images.avatar, contentDescription = "Profile avatar", modifier = Modifier.size(112.dp).clip(RoundedCornerShape(22.dp)), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (signedIn) "MICROSOFT ACCOUNT" else if (isOffline) "OFFLINE ACCOUNT" else "NO ACCOUNT SELECTED", color = Color(0xFF9B6A35), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                    Text(playerName, color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    if (playerUuid.isNotBlank()) Text("UUID: $playerUuid", color = Muted, fontSize = 10.sp)
                    Text(status, color = Muted, fontSize = 12.sp)
                }
                Surface(color = if (signedIn) Color(0xFFDDF1D8) else Color(0xFFFFE7B6), shape = RoundedCornerShape(12.dp)) {
                    Text(if (signedIn) "CONNECTED" else if (isOffline) "OFFLINE" else "NOT SIGNED IN", Modifier.padding(horizontal = 14.dp, vertical = 9.dp), color = if (signedIn) Color(0xFF356B31) else Color(0xFF79512A), fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        Surface(Modifier.fillMaxWidth(), color = Panel, shape = RoundedCornerShape(22.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AccountTypeBanner(title = "Microsoft Account", subtitle = "Secure sign-in for Minecraft: Java Edition", tag = "ONLINE", mark = "M", colors = listOf(Color(0xFF174A82), Color(0xFF2679B9)))
                Text("Your password is entered only on Microsoft's website. O Launcher never asks you to type it here.", color = Muted, fontSize = 12.sp)
                device?.let { pending ->
                    Text("Open ${pending.verificationUri} and enter this code:", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Surface(color = Color(0xFFFFF2DF), shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
                        Text(pending.userCode, Modifier.fillMaxWidth().padding(18.dp), color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Black, textAlign = androidx.compose.ui.text.style.TextAlign.Center, letterSpacing = 3.sp)
                    }
                    Text("Keep O Launcher open. It will finish sign-in after you approve the request.", color = Muted, fontSize = 11.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                playerName = account.name
                                playerUuid = account.uuid
                                signedIn = true
                                isOffline = false
                                prefs.edit().putBoolean("is_offline", false).apply()
                                device = null
                                status = "Minecraft Java profile verified."
                            } catch (e: Exception) {
                                status = e.message ?: "Sign-in failed. Please try again."
                                device = null
                            } finally { busy = false }
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink), shape = RoundedCornerShape(12.dp)) {
                        Text(if (busy) "WAITING FOR MICROSOFT…" else "SIGN IN WITH MICROSOFT", fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                    if (signedIn) OutlinedButton(onClick = {
                        MicrosoftTokenVault.clear(context); signedIn = false
                        playerName = "Not signed in"; playerUuid = ""; isOffline = false
                        status = "Microsoft account disconnected."
                    }) { Text("SIGN OUT", fontSize = 11.sp) }
                }
            }
        }

        Surface(Modifier.fillMaxWidth(), color = Panel, shape = RoundedCornerShape(22.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AccountTypeBanner(title = "Offline Account", subtitle = "A local player profile on this device", tag = "LOCAL", mark = "OFF", colors = listOf(Color(0xFF315B37), Color(0xFF6B8C45)))
                Text("Play with a local player profile without signing in to Microsoft. Some online servers require an authenticated account and won't accept offline profiles.", color = Muted, fontSize = 12.sp)
                if (isOffline && !showOfflineForm) {
                    Text("Saved offline profile: $playerName", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { showOfflineForm = true; offlineName = playerName }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) { Text("EDIT PROFILE") }
                        OutlinedButton(onClick = {
                            prefs.edit().remove("offline_name").remove("offline_uuid").putBoolean("is_offline", false).apply()
                            isOffline = false; playerName = "Not signed in"; playerUuid = ""; status = "Offline profile removed from this device."
                        }) { Text("REMOVE") }
                    }
                } else {
                    OutlinedTextField(
                        value = offlineName,
                        onValueChange = { offlineName = it; offlineError = "" },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Player name") },
                        placeholder = { Text("Enter your Minecraft name") }
                    )
                    if (offlineError.isNotBlank()) Text(offlineError, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = {
                            val clean = offlineName.trim()
                            when {
                                clean.length !in 3..16 -> offlineError = "Name must be 3–16 characters."
                                !clean.matches(Regex("[A-Za-z0-9_]+")) -> offlineError = "Use only letters, numbers, and underscores."
                                else -> {
                                    val offlineUuid = java.util.UUID.nameUUIDFromBytes("OfflinePlayer:$clean".toByteArray(Charsets.UTF_8)).toString().replace("-", "")
                                    prefs.edit().putString("offline_name", clean).putString("offline_uuid", offlineUuid).putBoolean("is_offline", true).apply()
                                    playerName = clean; playerUuid = offlineUuid; isOffline = true; signedIn = false
                                    showOfflineForm = false; status = "Offline profile saved on this device."
                                }
                            }
                        }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) { Text("SAVE OFFLINE ACCOUNT") }
                        if (showOfflineForm) OutlinedButton(onClick = { showOfflineForm = false; offlineError = "" }) { Text("CANCEL") }
                    }
                }
                Text("Offline profiles are local only. O Launcher will use this identity when offline session support is added to the game runtime.", color = Muted, fontSize = 10.sp)
            }
        }
    }
}
@Composable
private fun MarketplaceDestination(images: LauncherImages, availableHeight: Dp) {
    val categories = listOf("All", "Mods", "Resource Packs", "Data Packs", "Shaders", "Maps", "Worlds", "More")
    var selectedCategory by remember { mutableStateOf("All") }
    var search by remember { mutableStateOf("") }

    BoxWithConstraints(Modifier.fillMaxWidth().height(availableHeight)) {
        val contentHeight = this.maxHeight
        val scale = (contentHeight / 700.dp).coerceIn(0.72f, 1.12f)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp * scale)) {
        DestinationHero(images, "Marketplace", "Discover mods, worlds, resource packs and more", "COMMUNITY CONTENT")
        Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.Search, contentDescription = "Search", tint = Muted, modifier = Modifier.size(19.dp))
            Box(Modifier.weight(1f)) {
                if (search.isEmpty()) Text("Search for mods, worlds, and more...", color = Muted, fontSize = 12.sp)
                BasicTextField(value = search, onValueChange = { search = it }, singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(color = Ink, fontSize = 12.sp))
            }
            Icon(Icons.Filled.ExpandMore, contentDescription = "Filters", tint = Ink, modifier = Modifier.size(20.dp))
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { category ->
                val selected = selectedCategory == category
                Surface(onClick = { selectedCategory = category }, color = if (selected) Color(0xFF704329) else Panel, shape = RoundedCornerShape(30.dp), border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Color(0xFF342139) else Border)) {
                    Text(category, Modifier.padding(horizontal = 15.dp, vertical = 9.dp), color = if (selected) Color.White else Ink, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (search.isBlank()) "Featured $selectedCategory" else "Search results", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Ink)
                Text("CurseForge community content", color = Muted, fontSize = 11.sp)
            }
            Text("VIEW ALL  →", color = Color(0xFF8B5731), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Surface(color = Panel, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border.copy(alpha = .8f)), modifier = Modifier.fillMaxWidth().height(contentHeight * 0.25f)) {
            Column(Modifier.fillMaxSize().padding(18.dp * scale), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Filled.Storefront, contentDescription = null, tint = Color(0xFF9A6A48), modifier = Modifier.size(31.dp))
                Spacer(Modifier.height(8.dp))
                Text("CurseForge connection pending", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Ink)
                Text("Your API application is under review. Live projects, creators, ratings and downloads will appear here as soon as the key is configured.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
            }
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
    var versions by remember { mutableStateOf<List<MinecraftRelease>>(emptyList()) }
    var selectedVersion by remember { mutableStateOf("Loading…") }
    var selectedRelease by remember { mutableStateOf<MinecraftRelease?>(null) }
    var versionMenu by remember { mutableStateOf(false) }
    var versionsLoading by remember { mutableStateOf(true) }
    var versionStatus by remember { mutableStateOf("Loading official Minecraft releases…") }
    var downloadingClient by remember { mutableStateOf(false) }
    var downloadBytes by remember { mutableStateOf(0L) }
    var downloadTotal by remember { mutableStateOf(0L) }
    var downloadStatus by remember { mutableStateOf("Choose a version to download its official client file.") }

    LaunchedEffect(Unit) {
        try {
            val (loaded, latest) = MinecraftVersionInstaller.fetchReleases()
            versions = loaded.take(80)
            selectedRelease = loaded.firstOrNull { it.id == latest } ?: loaded.firstOrNull()
            selectedVersion = selectedRelease?.id ?: "Unavailable"
            versionStatus = "Loaded ${loaded.size} official release versions. Latest: $latest"
        } catch (error: Exception) {
            versionStatus = error.message ?: "Could not load Minecraft versions."
        } finally {
            versionsLoading = false
        }
    }
    var showCreateProfile by remember { mutableStateOf(false) }
    var profileName by remember { mutableStateOf("") }
    val profiles = remember { mutableStateListOf("Default") }

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
                                        onClick = { selectedVersion = version.id; selectedRelease = version; versionMenu = false },
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
                                            val result = MinecraftVersionInstaller.downloadClient(release, File(context.filesDir, "minecraft")) { done, total ->
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
                        Text("Downloads the official client JAR and verifies its SHA-1 in app storage. Libraries, assets, Java runtime and launching are the next stages.", color = Muted, fontSize = 10.sp)
                    }
                }
            }
        }

        Surface(color = Panel, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                var selectedLoader by remember { mutableStateOf("Vanilla") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("MOD LOADERS", fontSize = 20.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                        Text("Choose the mod platform for this game profile.", color = Muted, fontSize = 12.sp)
                    }
                    Surface(color = Color(0xFFFFE5A5), shape = RoundedCornerShape(10.dp)) {
                        Text("4 LOADERS", modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    LoaderOptionCard("Fabric", "Fast, lightweight", R.drawable.loader_fabric, selectedLoader == "Fabric", { selectedLoader = "Fabric" }, Modifier.weight(1f))
                    LoaderOptionCard("NeoForge", "Modern mod platform", R.drawable.loader_neoforge, selectedLoader == "NeoForge", { selectedLoader = "NeoForge" }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    LoaderOptionCard("Forge", "Classic mod platform", R.drawable.loader_forge, selectedLoader == "Forge", { selectedLoader = "Forge" }, Modifier.weight(1f))
                    LoaderOptionCard("OptiFine", "Graphics and performance", R.drawable.loader_optifine, selectedLoader == "OptiFine", { selectedLoader = "OptiFine" }, Modifier.weight(1f))
                }
                Text("Selected: $selectedLoader  •  Selection is visual for now; loader downloads and installation are not connected yet.", color = Muted, fontSize = 11.sp)
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
                    Surface(color = Color(0xFFFFF2DF), shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth()) {
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
