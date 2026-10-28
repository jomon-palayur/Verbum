package com.verbum.app.liturgy

import androidx.room.withTransaction
import com.verbum.app.data.ScriptureCoordinate
import com.verbum.app.data.VerbumDatabase
import com.verbum.app.data.model.LectionaryReadingEntity
import com.verbum.app.data.model.LiturgicalProfileEntity
import org.json.JSONObject

data class LiturgicalDataPack(
    val profiles: List<LiturgicalProfileEntity>,
    val readings: List<LectionaryReadingEntity>,
)

/** Parses rights-cleared, source-attributed calendar and citation data supplied as a versioned JSON pack. */
object LiturgicalDataPackInstaller {
    fun parse(json: String): LiturgicalDataPack {
        val root = JSONObject(json)
        require(root.optInt("schemaVersion", -1) == 1) { "Unsupported liturgical pack schema" }
        val profileArray = root.optJSONArray("profiles") ?: error("Liturgical pack has no profiles")
        val profiles = ArrayList<LiturgicalProfileEntity>()
        val readings = ArrayList<LectionaryReadingEntity>()
        val seenProfileIds = HashSet<String>()
        for (profileIndex in 0 until profileArray.length()) {
            val profile = profileArray.getJSONObject(profileIndex)
            val id = profile.getString("id").trim()
            require(id.matches(Regex("[a-z0-9_-]{2,48}")) && seenProfileIds.add(id)) { "Invalid or duplicate liturgical profile id" }
            val sourceUrl = profile.getString("sourceUrl").trim()
            require(sourceUrl.startsWith("https://")) { "Liturgical data source must use HTTPS" }
            val sourceName = profile.optString("sourceName").trim()
            val license = profile.optString("license").trim()
            require(sourceName.isNotBlank() && license.isNotBlank()) { "Liturgical pack must include source attribution and license" }
            profiles += LiturgicalProfileEntity(
                id = id,
                name = profile.getString("name").trim(),
                tradition = profile.getString("tradition").trim(),
                regionCode = profile.optString("regionCode", "001"),
                sourceName = sourceName,
                sourceUrl = sourceUrl,
                license = license,
                isBundled = false,
            )
            val readingArray = profile.optJSONArray("readings") ?: error("Profile $id has no readings")
            require(readingArray.length() > 0) { "Profile $id contains no readings" }
            for (readingIndex in 0 until readingArray.length()) {
                val reading = readingArray.getJSONObject(readingIndex)
                val key = reading.getString("key").trim()
                require(key.isNotBlank()) { "Reading key cannot be blank" }
                val first = reading.getString("firstReadingRef")
                val psalm = reading.getString("psalmRef")
                val gospel = reading.getString("gospelRef")
                require(ScriptureCoordinate.parse(first) != null && ScriptureCoordinate.parse(psalm) != null && ScriptureCoordinate.parse(gospel) != null) {
                    "Invalid scripture coordinate in $id/$key"
                }
                val second = reading.optString("secondReadingRef").takeIf(String::isNotBlank)
                val acclamation = reading.optString("gospelAcclamationRef")
                val keyVerse = reading.optString("gospelKeyVerseCoord").ifBlank { gospel.substringBefore('-') }
                require(second == null || ScriptureCoordinate.parse(second) != null) { "Invalid second reading coordinate in $id/$key" }
                require(acclamation.isBlank() || ScriptureCoordinate.parse(acclamation) != null) { "Invalid Gospel acclamation coordinate in $id/$key" }
                require(ScriptureCoordinate.parse(keyVerse) != null) { "Invalid Gospel key verse coordinate in $id/$key" }
                readings += LectionaryReadingEntity(
                    liturgicalKey = key,
                    title = reading.getString("title").trim(),
                    color = reading.getString("color").uppercase(),
                    firstReadingRef = first,
                    psalmRef = psalm,
                    secondReadingRef = second,
                    gospelAcclamationRef = acclamation,
                    gospelRef = gospel,
                    gospelKeyVerseCoord = keyVerse,
                    profileId = id,
                )
            }
        }
        require(profiles.isNotEmpty() && readings.isNotEmpty()) { "Liturgical data pack is empty" }
        require(readings.map { it.profileId to it.liturgicalKey }.distinct().size == readings.size) { "Duplicate reading key" }
        return LiturgicalDataPack(profiles, readings)
    }

    suspend fun install(database: VerbumDatabase, json: String): LiturgicalDataPack {
        val pack = parse(json)
        database.withTransaction {
            database.liturgicalProfileDao().upsertAll(pack.profiles)
            database.lectionaryReadingDao().insertAll(pack.readings)
        }
        return pack
    }
}
