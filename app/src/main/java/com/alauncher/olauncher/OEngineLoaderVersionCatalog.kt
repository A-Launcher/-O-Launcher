package com.alauncher.olauncher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Version discovery for supported Minecraft mod-loader ecosystems. */
object OEngineLoaderVersionCatalog {
    data class LoaderVersion(
        val loader: OEngineModLoader,
        val minecraftVersion: String,
        val version: String,
        val stable: Boolean,
        val installerUrl: String?,
        val source: String
    )

    /** Numeric-aware ordering: 47.10.0 must sort after 47.9.0, not before it. */
    internal fun compareVersionsDescending(left: String, right: String): Int {
        fun parts(value: String): List<Pair<Int, String>> =
            Regex("\\d+|[A-Za-z]+").findAll(value).map { token ->
                val raw = token.value
                raw.toIntOrNull()?.let { it to "" } ?: (0 to raw.lowercase())
            }.toList()
        val a = parts(left)
        val b = parts(right)
        for (index in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrNull(index) ?: (0 to "")
            val y = b.getOrNull(index) ?: (0 to "")
            val number = y.first.compareTo(x.first)
            if (number != 0) return number
            val text = y.second.compareTo(x.second)
            if (text != 0) return text
        }
        return right.compareTo(left)
    }

    private fun newestFirst(values: List<LoaderVersion>): List<LoaderVersion> =
        values.sortedWith(
            compareByDescending<LoaderVersion> { it.stable }
                .thenComparator { a, b -> compareVersionsDescending(a.version, b.version) }
        )

    suspend fun fetch(
        loader: OEngineModLoader,
        minecraftVersion: String
    ): List<LoaderVersion> = withContext(Dispatchers.IO) {
        val game = minecraftVersion.trim()
        require(game.matches(Regex("[0-9]+\\.[0-9]+(?:\\.[0-9]+)?"))) {
            "A valid Minecraft version is required."
        }
        when (loader) {
            OEngineModLoader.VANILLA -> emptyList()
            OEngineModLoader.FABRIC -> parseFabric(game, requestText(
                "https://meta.fabricmc.net/v2/versions/loader/" + game
            ))
            OEngineModLoader.QUILT -> parseQuilt(game, requestText(
                "https://meta.quiltmc.org/v3/versions/loader/" + game
            ))
            OEngineModLoader.FORGE -> parseForgeMaven(
                game,
                requestText("https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml"),
                requestText("https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json")
            )
            OEngineModLoader.NEOFORGE -> parseNeoForge(game, requestText(
                "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml"
            ))
            OEngineModLoader.OPTIFINE -> emptyList()
        }
    }

    fun parseFabric(game: String, body: String): List<LoaderVersion> {
        val rows = JSONArray(body)
        return buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val loader = row.optJSONObject("loader") ?: continue
                val version = loader.optString("version")
                if (version.isBlank()) continue
                add(LoaderVersion(
                    OEngineModLoader.FABRIC, game, version, loader.optBoolean("stable", false),
                    null, "Fabric Meta"
                ))
            }
        }.distinctBy { it.version }.let(::newestFirst)
    }

    fun parseQuilt(game: String, body: String): List<LoaderVersion> {
        val rows = JSONArray(body)
        return buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val loader = row.optJSONObject("loader") ?: continue
                val version = loader.optString("version")
                if (version.isBlank()) continue
                add(LoaderVersion(
                    OEngineModLoader.QUILT, game, version, loader.optBoolean("stable", false),
                    null, "Quilt Meta"
                ))
            }
        }.distinctBy { it.version }.let(::newestFirst)
    }

    /** Full Forge version list, matching MojoLauncher's ForgelikeUtils Maven metadata flow. */
    fun parseForgeMaven(game: String, metadataXml: String, promotionsJson: String = "{}"): List<LoaderVersion> {
        val recommended = runCatching {
            JSONObject(promotionsJson).optJSONObject("promos")?.optString("$game-recommended").orEmpty()
        }.getOrDefault("")
        val versions = Regex("<version>\\s*([^<]+)\\s*</version>")
            .findAll(metadataXml)
            .map { it.groupValues[1].trim() }
            .filter { it.startsWith("$game-") }
            .map { it.removePrefix("$game-") }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
        return versions.map { version ->
            LoaderVersion(
                OEngineModLoader.FORGE, game, version, version == recommended,
                "https://maven.minecraftforge.net/net/minecraftforge/forge/$game-$version/forge-$game-$version-installer.jar",
                "Forge Maven"
            )
        }.let(::newestFirst)
    }

    fun parseForge(game: String, body: String): List<LoaderVersion> {
        val promos = JSONObject(body).optJSONObject("promos") ?: return emptyList()
        val prefix = game + "-"
        val values = mutableListOf<LoaderVersion>()
        val keys = promos.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (!key.startsWith(prefix)) continue
            val version = promos.optString(key)
            if (version.isBlank()) continue
            val stable = key.endsWith("-recommended")
            values.add(LoaderVersion(
                OEngineModLoader.FORGE, game, version, stable,
                "https://maven.minecraftforge.net/net/minecraftforge/forge/" + game + "-" + version +
                    "/forge-" + game + "-" + version + "-installer.jar",
                "Forge promotions"
            ))
        }
        return newestFirst(values)
    }

    fun parseNeoForge(game: String, body: String): List<LoaderVersion> {
        // NeoForge uses a compact MC-major/minor prefix: 1.21.1 -> 21.1, 1.20.4 -> 20.4.
        val parts = game.split('.')
        if (parts.size < 2 || parts[0] != "1") return emptyList()
        val prefix = parts[1] + "." + (parts.getOrNull(2) ?: "0") + "."
        val versions = Regex("<version>\\s*([^<]+)\\s*</version>")
            .findAll(body).map { it.groupValues[1].trim() }
            .filter { it.startsWith(prefix) }
            .distinct()
            .toList()
        return versions.map {
            LoaderVersion(
                OEngineModLoader.NEOFORGE, game, it, true,
                "https://maven.neoforged.net/releases/net/neoforged/neoforge/" + it +
                    "/neoforge-" + it + "-installer.jar",
                "NeoForge Maven"
            )
        }.let(::newestFirst)
    }

    /** OptiFine distributes version-specific installers through its official downloads page. */
    fun manualInstallerSource(loader: OEngineModLoader): String? =
        if (loader == OEngineModLoader.OPTIFINE) "https://optifine.net/downloads" else null

    private fun requestText(address: String): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "O-Launcher/0.1.0")
            setRequestProperty("Accept", "application/json, application/xml, text/xml")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IllegalStateException("Loader metadata request failed (HTTP " + status + ").")
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
