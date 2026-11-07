package com.alauncher.olauncher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** CurseForge Minecraft catalogue client. Requires the user's own API key. */
object OEngineCurseForgeCatalog {
    private const val API = "https://api.curseforge.com/v1"
    private const val MINECRAFT_GAME_ID = 432

    data class Project(
        val id: Long, val name: String, val summary: String, val authors: List<String>,
        val downloads: Long, val logoUrl: String?, val websiteUrl: String?,
        val classId: Int, val latestFiles: List<String>
    )
    data class SearchPage(val projects: List<Project>, val totalCount: Int, val index: Int, val pageSize: Int)

    suspend fun search(apiKey: String, query: String, category: String, index: Int = 0, pageSize: Int = 20): SearchPage =
        withContext(Dispatchers.IO) {
            require(apiKey.isNotBlank()) { "Add your CurseForge API key in Marketplace settings first." }
            require(index >= 0 && pageSize in 1..50)
            val classId = when (category) {
                "Mods" -> 6
                "Modpacks" -> 4471
                "Resource Packs" -> 12
                "Worlds" -> 17
                "Shaders" -> 6552
                else -> null
            }
            val params = mutableListOf("gameId=$MINECRAFT_GAME_ID", "index=$index", "pageSize=$pageSize", "sortField=2", "sortOrder=desc")
            if (classId != null) params += "classId=$classId"
            if (query.isNotBlank()) params += "searchFilter=" + URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
            val connection = (URL("$API/mods/search?${params.joinToString("&")}").openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000; readTimeout = 20_000; requestMethod = "GET"
                setRequestProperty("x-api-key", apiKey)
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "O-Launcher/1.0 (Android)")
            }
            try {
                val status = connection.responseCode
                val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (status !in 200..299) {
                    throw IllegalStateException(when (status) {
                        401, 403 -> "CurseForge rejected the API key. Check that it is valid and enabled."
                        429 -> "CurseForge rate limit reached. Please wait and retry."
                        else -> "CurseForge returned HTTP $status."
                    })
                }
                val json = JSONObject(body)
                val data = json.optJSONArray("data")
                val projects = buildList {
                    if (data != null) for (i in 0 until data.length()) {
                        val item = data.optJSONObject(i) ?: continue
                        val id = item.optLong("id")
                        if (id <= 0) continue
                        val authorsJson = item.optJSONArray("authors")
                        val authors = buildList {
                            if (authorsJson != null) for (a in 0 until authorsJson.length()) {
                                val author = authorsJson.optJSONObject(a)?.optString("name").orEmpty()
                                if (author.isNotBlank()) add(author)
                            }
                        }
                        val logo = item.optJSONObject("logo")?.optString("url")
                        val links = item.optJSONObject("links")
                        val files = item.optJSONArray("latestFiles")
                        val latest = buildList {
                            if (files != null) for (f in 0 until minOf(files.length(), 5)) {
                                val file = files.optJSONObject(f) ?: continue
                                val display = file.optString("displayName").ifBlank { file.optString("fileName") }
                                if (display.isNotBlank()) add(display)
                            }
                        }
                        add(Project(id, item.optString("name").ifBlank { "Unnamed project" },
                            item.optString("summary"), authors, item.optLong("downloadCount"),
                            logo?.takeIf { it.startsWith("https://") },
                            links?.optString("websiteUrl")?.takeIf { it.startsWith("https://") },
                            item.optInt("classId"), latest))
                    }
                }
                val pagination = json.optJSONObject("pagination")
                SearchPage(projects, pagination?.optInt("totalCount") ?: projects.size,
                    pagination?.optInt("index") ?: index, pagination?.optInt("pageSize") ?: pageSize)
            } finally { connection.disconnect() }
        }
}
