package com.alauncher.olauncher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Public Modrinth v2 catalogue client. No API key is required for public search.
 * This is a discovery layer only: installing a project is intentionally handled
 * by a separate, loader-aware installer so incompatible files are never dropped
 * blindly into a Minecraft instance.
 */
object OEngineModrinthCatalog {
    private const val API = "https://api.modrinth.com/v2"
    private const val USER_AGENT = "O-Launcher/1.0 (Android; https://github.com/A-Launcher/-O-Launcher)"

    enum class ProjectType(val apiValue: String, val label: String) {
        ALL("", "All"),
        MOD("mod", "Mods"),
        MODPACK("modpack", "Modpacks"),
        RESOURCE_PACK("resourcepack", "Resource Packs"),
        SHADER("shader", "Shaders"),
        DATA_PACK("datapack", "Data Packs");

        companion object {
            fun fromLabel(label: String): ProjectType =
                entries.firstOrNull { it.label.equals(label, ignoreCase = true) } ?: ALL
        }
    }

    data class Project(
        val id: String,
        val slug: String,
        val title: String,
        val description: String,
        val author: String,
        val downloads: Long,
        val follows: Long,
        val projectType: ProjectType,
        val iconUrl: String?,
        val categories: List<String>,
        val versions: List<String>
    )

    data class SearchPage(
        val hits: List<Project>,
        val totalHits: Int,
        val offset: Int,
        val limit: Int
    )

    suspend fun search(
        query: String,
        type: ProjectType = ProjectType.ALL,
        gameVersion: String? = null,
        loader: String? = null,
        offset: Int = 0,
        limit: Int = 20
    ): SearchPage = withContext(Dispatchers.IO) {
        require(offset >= 0) { "Search offset cannot be negative." }
        require(limit in 1..100) { "Search page size must be between 1 and 100." }
        val facets = JSONArray()
        if (type != ProjectType.ALL) facets.put(JSONArray().put("project_type:${type.apiValue}"))
        if (!gameVersion.isNullOrBlank()) facets.put(JSONArray().put("versions:${gameVersion.trim()}"))
        if (!loader.isNullOrBlank() && type == ProjectType.MOD) facets.put(JSONArray().put("categories:${loader.trim().lowercase()}"))
        val encodedFacets = URLEncoder.encode(facets.toString(), Charsets.UTF_8.name())
        val encodedQuery = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
        val url = "$API/search?query=$encodedQuery&facets=$encodedFacets&offset=$offset&limit=$limit&index=relevance"
        val json = JSONObject(requestText(url))
        val hits = json.optJSONArray("hits") ?: JSONArray()
        val projects = buildList {
            for (index in 0 until hits.length()) {
                val item = hits.optJSONObject(index) ?: continue
                val id = item.optString("project_id")
                if (id.isBlank()) continue
                val projectType = ProjectType.entries.firstOrNull {
                    it.apiValue.isNotEmpty() && it.apiValue == item.optString("project_type")
                } ?: ProjectType.ALL
                val categories = item.optJSONArray("categories").toStringList()
                val versions = item.optJSONArray("versions").toStringList()
                add(Project(
                    id = id,
                    slug = item.optString("slug"),
                    title = item.optString("title").ifBlank { item.optString("slug", "Untitled project") },
                    description = item.optString("description"),
                    author = item.optString("author").ifBlank { "Unknown creator" },
                    downloads = item.optLong("downloads"),
                    follows = item.optLong("follows"),
                    projectType = projectType,
                    iconUrl = item.optString("icon_url").takeIf { it.startsWith("https://") },
                    categories = categories,
                    versions = versions
                ))
            }
        }
        SearchPage(projects, json.optInt("total_hits"), offset, limit)
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val value = optString(index).trim()
                if (value.isNotEmpty()) add(value)
            }
        }
    }

    private fun requestText(address: String): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val detail = runCatching { JSONObject(body).optString("description") }.getOrNull()
                throw IllegalStateException(detail?.takeIf { it.isNotBlank() } ?: "Modrinth returned HTTP $status.")
            }
            return body
        } finally {
            connection.disconnect()
        }
    }
}
