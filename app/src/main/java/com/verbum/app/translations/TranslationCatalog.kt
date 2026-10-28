package com.verbum.app.translations

import android.content.Context
import com.verbum.app.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

data class TranslationCatalogEntry(
    val id: String,
    val name: String,
    val shortName: String,
    val languageCode: String,
    val format: TranslationFormat,
    val downloadUrl: String,
    val license: String,
    val bundled: Boolean,
    val bookCount: Int,
    val attribution: String,
    val sourceUrl: String,
    val catholicApproved: Boolean,
    val approvalAuthority: String,
    val comingSoon: Boolean,
)

object TranslationCatalogRepository {
    fun load(context: Context): List<TranslationCatalogEntry> {
        val remote = BuildConfig.TRANSLATION_CATALOG_URL.trim()
        val json = if (remote.isNotEmpty()) {
            runCatching { fetchCatalog(remote) }.getOrNull()
        } else null
        val source = json ?: context.assets.open("database/translation_catalog.json").bufferedReader().use { it.readText() }
        val root = JSONObject(source)
        require(root.optInt("schemaVersion", -1) == 1) { "Unsupported translation catalog schema" }
        val entries = root.getJSONArray("translations")
        return buildList {
            for (index in 0 until entries.length()) {
                val item = entries.getJSONObject(index)
                val format = runCatching { TranslationFormat.valueOf(item.getString("format").uppercase()) }
                    .getOrElse { throw IllegalArgumentException("Unsupported translation format in catalog", it) }
                val url = item.getString("url")
                require(url.startsWith("https://")) { "Translation sources must use HTTPS" }
                add(
                    TranslationCatalogEntry(
                        id = item.getString("id"),
                        name = item.getString("name"),
                        shortName = item.optString("shortName", item.getString("id")),
                        languageCode = item.optString("languageCode", "en"),
                        format = format,
                        downloadUrl = url,
                        license = item.optString("license", "License not specified"),
                        bundled = item.optBoolean("bundled", false),
                        bookCount = item.optInt("bookCount", 73),
                        attribution = item.optString("attribution", ""),
                        sourceUrl = item.optString("sourceUrl", url),
                        catholicApproved = item.optBoolean("catholicApproved", false),
                        approvalAuthority = item.optString("approvalAuthority", ""),
                        comingSoon = item.optBoolean("comingSoon", false),
                    ),
                )
            }
        }
    }

    private fun fetchCatalog(address: String): String {
        require(address.startsWith("https://")) { "Catalog URL must use HTTPS" }
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.requestMethod = "GET"
        return try {
            val response = connection
            require(response.responseCode in 200..299) { "Catalog request returned ${response.responseCode}" }
            response.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
