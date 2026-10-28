package com.verbum.app.audio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.content.pm.PackageManager
import android.Manifest
import android.os.Bundle
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.verbum.app.MainActivity
import com.verbum.app.R
import com.verbum.app.data.VerbumDatabase
import com.verbum.app.data.model.VerseEntity
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PlaybackSnapshot(
    val active: Boolean = false,
    val playing: Boolean = false,
    val translationId: String = "",
    val languageCode: String = "en-US",
    val bookId: Int = 1,
    val chapter: Int = 1,
    val currentVerse: Int = 1,
    val reference: String = "",
    val speed: Float = 1f,
    val error: String? = null,
)

object BiblePlaybackState {
    private val mutableState = MutableStateFlow(PlaybackSnapshot())
    val state = mutableState.asStateFlow()
    internal fun update(transform: (PlaybackSnapshot) -> PlaybackSnapshot) = mutableState.update(transform)
}

class BiblePlaybackService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tts: TextToSpeech? = null
    private var mediaSession: MediaSessionCompat? = null
    private var verses: List<VerseEntity> = emptyList()
    private var currentIndex = 0
    private var translationId = ""
    private var languageCode = "en-US"
    private var currentBookName = ""
    private var bookId = 1
    private var chapter = 1
    private var autoAdvance = true
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaSession = MediaSessionCompat(this, "VerbumBiblePlayback").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = resumePlayback()
                override fun onPause() = pausePlayback()
                override fun onSkipToNext() = skip(1)
                override fun onSkipToPrevious() = skip(-1)
                override fun onStop() = stopPlayback()
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_STOP && intent?.action != null) ensureForeground()
        when (intent?.action) {
            ACTION_START -> {
                translationId = intent.getStringExtra(EXTRA_TRANSLATION_ID).orEmpty()
                languageCode = intent.getStringExtra(EXTRA_LANGUAGE_CODE) ?: "en-US"
                bookId = intent.getIntExtra(EXTRA_BOOK_ID, 1)
                chapter = intent.getIntExtra(EXTRA_CHAPTER, 1)
                autoAdvance = intent.getBooleanExtra(EXTRA_AUTO_ADVANCE, true)
                loadChapter(bookId, chapter, autoStart = true)
            }
            ACTION_PLAY -> resumePlayback()
            ACTION_PAUSE -> pausePlayback()
            ACTION_NEXT -> skip(1)
            ACTION_PREVIOUS -> skip(-1)
            ACTION_STOP -> stopPlayback()
            ACTION_SPEED -> setSpeed(intent.getFloatExtra(EXTRA_SPEED, 1f))
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureForeground() {
        if (foregroundStarted) return
        startForeground(NOTIFICATION_ID, buildNotification())
        foregroundStarted = true
    }

    private fun loadChapter(targetBook: Int, targetChapter: Int, autoStart: Boolean) {
        serviceScope.launch {
            val data = withContext(Dispatchers.IO) {
                val db = VerbumDatabase.getInstance(this@BiblePlaybackService)
                val items = db.verseDao().getChapter(translationId, targetBook, targetChapter)
                val book = db.bookDao().getById(targetBook)
                items to book?.shortName
            }
            if (data.first.isEmpty()) {
                BiblePlaybackState.update { it.copy(active = false, playing = false, error = "NO_VERSES") }
                stopPlayback()
                return@launch
            }
            verses = data.first
            bookId = targetBook
            chapter = targetChapter
            currentBookName = data.second ?: "Book $targetBook"
            currentIndex = 0
            val reference = "$currentBookName $targetChapter"
            BiblePlaybackState.update {
                it.copy(
                    active = true,
                    playing = autoStart,
                    translationId = translationId,
                    languageCode = languageCode,
                    bookId = bookId,
                    chapter = chapter,
                    currentVerse = verses.first().verse,
                    reference = "$reference:${verses.first().verse}",
                    error = null,
                )
            }
            updateSessionMetadata()
            if (autoStart) initializeTtsAndSpeak() else publishPlaybackState()
        }
    }

    private fun initializeTtsAndSpeak() {
        if (tts != null) {
            speakCurrent()
            return
        }
        tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.SUCCESS) {
                BiblePlaybackState.update { it.copy(active = false, playing = false, error = "TTS_INITIALIZATION_FAILED") }
                stopPlayback()
                return@TextToSpeech
            }
            val engine = tts ?: return@TextToSpeech
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    val spokenVerse = utteranceId?.removePrefix(UTTERANCE_PREFIX)?.toIntOrNull() ?: return
                    BiblePlaybackState.update {
                        it.copy(currentVerse = spokenVerse, reference = "$currentBookName $chapter:$spokenVerse")
                    }
                    updateSessionMetadata()
                    updateNotification()
                }

                override fun onDone(utteranceId: String?) {
                    serviceScope.launch { advanceAfterUtterance() }
                }

                @Deprecated("Deprecated in Android API")
                override fun onError(utteranceId: String?) {
                    BiblePlaybackState.update { it.copy(error = "TTS_SPEECH_ERROR", playing = false) }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    BiblePlaybackState.update { it.copy(error = "TTS_ERROR_$errorCode", playing = false) }
                }
            })
            val locale = Locale.forLanguageTag(languageCode)
            val result = engine.setLanguage(locale)
            when (result) {
                TextToSpeech.LANG_MISSING_DATA -> {
                    BiblePlaybackState.update { it.copy(error = "LANG_MISSING_DATA", playing = false) }
                    updateNotification()
                }
                TextToSpeech.LANG_NOT_SUPPORTED -> {
                    BiblePlaybackState.update { it.copy(error = "LANG_NOT_SUPPORTED", playing = false) }
                    updateNotification()
                }
                else -> {
                    engine.setSpeechRate(BiblePlaybackState.state.value.speed)
                    speakCurrent()
                }
            }
        }
    }

    private fun speakCurrent() {
        if (verses.isEmpty() || currentIndex !in verses.indices) return
        val verse = verses[currentIndex]
        val parameters = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f) }
        val result = tts?.speak(verse.text, TextToSpeech.QUEUE_FLUSH, parameters, "$UTTERANCE_PREFIX${verse.verse}")
        if (result == TextToSpeech.ERROR) {
            BiblePlaybackState.update { it.copy(playing = false, error = "TTS_SPEECH_ERROR") }
        } else {
            BiblePlaybackState.update { it.copy(playing = true, currentVerse = verse.verse, error = null) }
            publishPlaybackState()
            updateNotification()
        }
    }

    private suspend fun advanceAfterUtterance() {
        if (!BiblePlaybackState.state.value.playing) return
        if (currentIndex + 1 < verses.size) {
            currentIndex++
            speakCurrent()
            return
        }
        if (!autoAdvance) {
            BiblePlaybackState.update { it.copy(playing = false) }
            publishPlaybackState()
            updateNotification()
            return
        }
        val next = withContext(Dispatchers.IO) {
            val db = VerbumDatabase.getInstance(this@BiblePlaybackService)
            val nextInBook = db.verseDao().getNextChapter(translationId, bookId, chapter)
            if (nextInBook != null) return@withContext (bookId to nextInBook)
            val books = db.bookDao().observeCanonicalBooks().first()
            for (book in books.filter { it.id > bookId }) {
                val chapters = db.verseDao().getChapterNumbers(translationId, book.id)
                if (chapters.isNotEmpty()) return@withContext (book.id to chapters.first())
            }
            null
        }
        if (next == null) stopPlayback() else loadChapter(next.first, next.second, autoStart = true)
    }

    private fun pausePlayback() {
        tts?.stop()
        BiblePlaybackState.update { it.copy(playing = false) }
        publishPlaybackState()
        updateNotification()
    }

    private fun resumePlayback() {
        if (verses.isEmpty()) return
        BiblePlaybackState.update { it.copy(active = true, playing = true, error = null) }
        if (tts == null) initializeTtsAndSpeak() else speakCurrent()
    }

    private fun skip(direction: Int) {
        if (verses.isEmpty()) return
        currentIndex = (currentIndex + direction).coerceIn(0, verses.lastIndex)
        if (BiblePlaybackState.state.value.playing) speakCurrent() else {
            BiblePlaybackState.update {
                val selectedVerse = verses[currentIndex].verse
                it.copy(currentVerse = selectedVerse, reference = "$currentBookName $chapter:$selectedVerse")
            }
            updateSessionMetadata()
        }
    }

    private fun setSpeed(value: Float) {
        val speed = value.coerceIn(0.75f, 2f)
        tts?.setSpeechRate(speed)
        BiblePlaybackState.update { it.copy(speed = speed) }
        updateNotification()
    }

    private fun updateSessionMetadata() {
        val snapshot = BiblePlaybackState.state.value
        mediaSession?.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, snapshot.reference)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Verbum Bible Audio")
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, snapshot.translationId)
                .build(),
        )
        publishPlaybackState()
    }

    private fun publishPlaybackState() {
        val snapshot = BiblePlaybackState.state.value
        val playbackState = if (snapshot.playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_STOP,
                )
                .setState(playbackState, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, snapshot.speed)
                .build(),
        )
    }

    private fun updateNotification() {
        val notificationsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (foregroundStarted && notificationsAllowed) {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setContentTitle(BiblePlaybackState.state.value.reference.ifBlank { "Bible audio" })
        .setContentText(if (BiblePlaybackState.state.value.playing) "Verbum · ${BiblePlaybackState.state.value.speed}×" else "Paused")
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setOngoing(BiblePlaybackState.state.value.playing)
        .addAction(android.R.drawable.ic_media_previous, "Previous verse", servicePendingIntent(ACTION_PREVIOUS, 1))
        .addAction(if (BiblePlaybackState.state.value.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (BiblePlaybackState.state.value.playing) "Pause" else "Play",
            servicePendingIntent(if (BiblePlaybackState.state.value.playing) ACTION_PAUSE else ACTION_PLAY, 2))
        .addAction(android.R.drawable.ic_media_next, "Next verse", servicePendingIntent(ACTION_NEXT, 3))
        .setStyle(MediaStyle().setMediaSession(mediaSession?.sessionToken).setShowActionsInCompactView(0, 1, 2))
        .build()

    private fun servicePendingIntent(action: String, requestCode: Int) = PendingIntent.getService(
        this,
        requestCode,
        Intent(this, BiblePlaybackService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Bible audio", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun stopPlayback() {
        tts?.stop()
        BiblePlaybackState.update { PlaybackSnapshot(speed = it.speed) }
        if (foregroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
        }
        stopSelf()
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        mediaSession?.isActive = false
        mediaSession?.release()
        serviceScope.cancel()
        BiblePlaybackState.update { PlaybackSnapshot(speed = it.speed) }
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.verbum.app.audio.START"
        const val ACTION_PLAY = "com.verbum.app.audio.PLAY"
        const val ACTION_PAUSE = "com.verbum.app.audio.PAUSE"
        const val ACTION_PREVIOUS = "com.verbum.app.audio.PREVIOUS"
        const val ACTION_NEXT = "com.verbum.app.audio.NEXT"
        const val ACTION_SPEED = "com.verbum.app.audio.SPEED"
        const val ACTION_STOP = "com.verbum.app.audio.STOP"
        const val EXTRA_TRANSLATION_ID = "translation_id"
        const val EXTRA_LANGUAGE_CODE = "language_code"
        const val EXTRA_BOOK_ID = "book_id"
        const val EXTRA_CHAPTER = "chapter"
        const val EXTRA_AUTO_ADVANCE = "auto_advance"
        const val EXTRA_SPEED = "speed"
        private const val CHANNEL_ID = "verbum_bible_audio"
        private const val NOTIFICATION_ID = 7301
        private const val UTTERANCE_PREFIX = "verse_"
    }
}
