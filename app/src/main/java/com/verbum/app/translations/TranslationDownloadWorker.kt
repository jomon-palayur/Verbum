package com.verbum.app.translations

import android.content.Context
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import com.verbum.app.data.VerbumDatabase
import com.verbum.app.data.model.TranslationEntity
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.UUID

class TranslationDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString(KEY_ID)?.trim().orEmpty()
        val name = inputData.getString(KEY_NAME)?.trim().orEmpty()
        val shortName = inputData.getString(KEY_SHORT_NAME)?.trim().orEmpty()
        val languageCode = inputData.getString(KEY_LANGUAGE)?.trim().orEmpty()
        val url = inputData.getString(KEY_URL)?.trim().orEmpty()
        val bookCount = inputData.getInt(KEY_BOOK_COUNT, 73)
        val catholicApproved = inputData.getBoolean(KEY_CATHOLIC_APPROVED, false)
        val approvalAuthority = inputData.getString(KEY_APPROVAL_AUTHORITY).orEmpty()
        val attribution = inputData.getString(KEY_ATTRIBUTION).orEmpty()
        val format = runCatching { TranslationFormat.valueOf(inputData.getString(KEY_FORMAT).orEmpty()) }.getOrNull()
        if (id.isBlank() || name.isBlank() || shortName.isBlank() || languageCode.isBlank() ||
            !url.startsWith("https://") || format == null || bookCount !in setOf(66, 73)
        ) {
            return@withContext Result.failure(Data.Builder().putString(KEY_ERROR, "Invalid translation catalog entry").build())
        }
        val tempFile = File(applicationContext.cacheDir, "translation-${id}-${UUID.randomUUID()}.download")
        try {
            download(url, tempFile)
            val imported = when (format) {
                TranslationFormat.USFM_ZIP -> tempFile.inputStream().use { UsfmZipParser.parse(it, id) }
                TranslationFormat.ZEFANIA_XML -> tempFile.inputStream().use { ZefaniaXmlParser.parse(it, id) }
                TranslationFormat.JSON -> JsonSqliteImporter.parseJson(tempFile, id)
                TranslationFormat.SQLITE -> JsonSqliteImporter.parseSqlite(tempFile, id)
                TranslationFormat.OSIS_ZIP, TranslationFormat.PSFM -> throw IllegalArgumentException("This source format is marked coming soon")
            }
            validateCoverage(imported, bookCount)
            require(!catholicApproved || (bookCount == 73 && approvalAuthority.isNotBlank())) {
                "Catholic-recognized editions need all 73 books and an approving authority"
            }
            val database = VerbumDatabase.getInstance(applicationContext)
            database.withTransaction {
                database.verseDao().deleteTranslation(id)
                imported.chunked(750).forEach { database.verseDao().insertAll(it) }
                database.translationDao().upsert(
                    TranslationEntity(
                        id = id,
                        name = name,
                        shortName = shortName,
                        languageCode = languageCode,
                        isBundled = false,
                        installedAt = System.currentTimeMillis(),
                        canonBookCount = bookCount,
                        catholicApproved = catholicApproved,
                        attribution = attribution,
                        approvalAuthority = approvalAuthority,
                    ),
                )
            }
            Result.success(Data.Builder().putString(KEY_ID, id).putInt(KEY_COUNT, imported.size).build())
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            Result.failure(Data.Builder().putString(KEY_ERROR, error.message ?: "Translation import failed").build())
        } finally {
            tempFile.delete()
        }
    }

    private suspend fun download(address: String, output: File) {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 60_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Verbum-Android/1.0")
        connection.connect()
        try {
            val status = connection.responseCode
            require(status in 200..299) { "Download request returned HTTP $status" }
            val length = connection.contentLengthLong
            require(length <= MAX_DOWNLOAD_BYTES) { "Translation archive exceeds the 120 MiB download limit" }
            val total = length.takeIf { it > 0L } ?: -1L
            var copied = 0L
            connection.inputStream.use { input ->
                FileOutputStream(output).use { destination ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var lastProgress = -1
                    while (true) {
                        ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        copied += count
                        require(copied <= MAX_DOWNLOAD_BYTES) { "Translation archive exceeds the 120 MiB download limit" }
                        destination.write(buffer, 0, count)
                            if (total > 0L) {
                                val progress = (copied * 100 / total).toInt().coerceIn(0, 100)
                                if (progress > lastProgress) {
                                    setProgress(Data.Builder().putInt(KEY_PROGRESS, progress).build())
                                    lastProgress = progress
                                }
                            }
                    }
                    destination.fd.sync()
                }
            }
            require(copied > 0L) { "The translation source returned an empty file" }
        } finally {
            connection.disconnect()
        }
    }

    private fun validateCoverage(verses: List<com.verbum.app.data.model.VerseEntity>, expectedBookCount: Int) {
        require(verses.isNotEmpty()) { "The source contains no verse text" }
        val books = verses.mapTo(HashSet()) { it.bookId }
        val expectedBooks = if (expectedBookCount == 73) (1..73).toSet() else (1..73).toSet() - setOf(17, 18, 20, 21, 27, 28, 32)
        require(books == expectedBooks) {
            "The source must contain exactly $expectedBookCount books in Verbum's canonical order; found ${books.size}"
        }
        require(verses.all { it.chapter > 0 && it.verse > 0 && it.text.isNotBlank() }) { "The source contains invalid verse coordinates" }
    }

    private suspend fun ensureActive() = currentCoroutineContext().ensureActive()

    companion object {
        const val KEY_ID = "translation_id"
        const val KEY_NAME = "translation_name"
        const val KEY_SHORT_NAME = "translation_short_name"
        const val KEY_LANGUAGE = "translation_language"
        const val KEY_URL = "translation_url"
        const val KEY_BOOK_COUNT = "translation_book_count"
        const val KEY_FORMAT = "translation_format"
        const val KEY_PROGRESS = "download_progress"
        const val KEY_COUNT = "verse_count"
        const val KEY_CATHOLIC_APPROVED = "catholic_approved"
        const val KEY_APPROVAL_AUTHORITY = "approval_authority"
        const val KEY_ATTRIBUTION = "attribution"
        const val KEY_ERROR = "error_message"
        const val WORK_TAG = "translation_download"
        private const val MAX_DOWNLOAD_BYTES = 120L * 1024 * 1024

        fun workData(entry: TranslationCatalogEntry) = Data.Builder()
            .putString(KEY_ID, entry.id)
            .putString(KEY_NAME, entry.name)
            .putString(KEY_SHORT_NAME, entry.shortName)
            .putString(KEY_LANGUAGE, entry.languageCode)
            .putString(KEY_URL, entry.downloadUrl)
            .putInt(KEY_BOOK_COUNT, entry.bookCount)
            .putString(KEY_FORMAT, entry.format.name)
            .putBoolean(KEY_CATHOLIC_APPROVED, entry.catholicApproved)
            .putString(KEY_APPROVAL_AUTHORITY, entry.approvalAuthority)
            .putString(KEY_ATTRIBUTION, entry.attribution)
            .build()

        fun networkConstraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
