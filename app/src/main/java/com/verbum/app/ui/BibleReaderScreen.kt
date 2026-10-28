package com.verbum.app.ui

import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Palette
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verbum.app.audio.BiblePlaybackService
import com.verbum.app.audio.BiblePlaybackState
import com.verbum.app.R
import com.verbum.app.data.VerbumDatabase
import com.verbum.app.data.model.BookEntity
import com.verbum.app.data.model.VerseEntity
import com.verbum.app.data.model.VerseAnnotationEntity
import com.verbum.app.data.model.ReadingHistoryEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BibleReaderScreen(
    database: VerbumDatabase,
    initialTranslationId: String,
    initialTarget: Pair<Int, Int>?,
    onTargetConsumed: () -> Unit,
    onTranslationChanged: (String, String) -> Unit,
    readerTextScale: Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val books by database.bookDao().observeCanonicalBooks().collectAsState(initial = emptyList())
    val translations by database.translationDao().observeAll().collectAsState(initial = emptyList())
    val playback by BiblePlaybackState.state.collectAsState()
    var translationId by remember(initialTranslationId) { mutableStateOf(initialTranslationId) }
    var bookId by remember { mutableIntStateOf(1) }
    var chapter by remember { mutableIntStateOf(1) }
    var chapterNumbers by remember { mutableStateOf(emptyList<Int>()) }
    var chaptersLoading by remember { mutableStateOf(true) }
    var continuousPlayback by remember { mutableStateOf(true) }
    var lyricsMode by remember { mutableStateOf(false) }
    var selectedVerse by remember { mutableIntStateOf(0) }
    var showSearch by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf(emptyList<com.verbum.app.data.dao.VerseDao.SearchHit>()) }
    var noteDialog by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }
    val book = books.firstOrNull { it.id == bookId }
    val translation = translations.firstOrNull { it.id == translationId }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        startPlayback(context, translationId, translation?.languageCode ?: "en-US", bookId, chapter, continuousPlayback)
    }
    val verses by remember(translationId, bookId, chapter) {
        database.verseDao().observeChapter(translationId, bookId, chapter)
    }.collectAsState(initial = emptyList())
    val listState = rememberLazyListState()
    val annotations by database.verseAnnotationDao().observeChapter(bookId, chapter).collectAsState(initial = emptyList())
    val bookmarks by database.verseAnnotationDao().observeBookmarks().collectAsState(initial = emptyList())
    val history by database.readingHistoryDao().observeAll().collectAsState(initial = emptyList())

    LaunchedEffect(translations, initialTranslationId) {
        val selected = translations.firstOrNull { it.id == initialTranslationId }
            ?: translations.firstOrNull { it.id == translationId }
            ?: translations.firstOrNull()
        if (selected != null && selected.id != translationId) {
            translationId = selected.id
            onTranslationChanged(selected.id, selected.languageCode)
        }
    }
    LaunchedEffect(initialTarget) {
        initialTarget?.let { (targetBook, targetChapter) ->
            selectedVerse = 0
            bookId = targetBook.coerceIn(1, 73)
            chapter = targetChapter.coerceAtLeast(1)
            onTargetConsumed()
        }
    }
    LaunchedEffect(translationId, bookId) {
        chaptersLoading = true
        chapterNumbers = emptyList()
        chapterNumbers = withContext(Dispatchers.IO) { database.verseDao().getChapterNumbers(translationId, bookId) }
        if (chapterNumbers.isNotEmpty() && chapter !in chapterNumbers) chapter = chapterNumbers.first()
        chaptersLoading = false
    }
    LaunchedEffect(translationId, bookId, chapter) {
        withContext(Dispatchers.IO) {
            database.readingHistoryDao().upsert(ReadingHistoryEntity(bookId, chapter, translationId))
        }
    }
    LaunchedEffect(searchQuery, translationId, showSearch) {
        if (!showSearch || searchQuery.isBlank()) {
            searchResults = emptyList()
        } else {
            delay(250)
            searchResults = withContext(Dispatchers.IO) {
                database.verseDao().search(translationId, searchQuery.trim(), 60)
            }
        }
    }
    LaunchedEffect(playback.currentVerse, playback.bookId, playback.chapter, bookId, chapter, verses) {
        if (playback.active && playback.bookId == bookId && playback.chapter == chapter) {
            val rowIndex = verses.indexOfFirst { it.verse == playback.currentVerse }
            if (rowIndex >= 0) listState.animateScrollToItem(rowIndex)
        }
    }
    LaunchedEffect(selectedVerse, verses) {
        val selectedIndex = verses.indexOfFirst { it.verse == selectedVerse }
        if (selectedIndex >= 0) listState.animateScrollToItem(selectedIndex)
        else if (selectedVerse == 0 && verses.isNotEmpty()) listState.scrollToItem(0)
    }
    LaunchedEffect(playback.active) {
        if (playback.active) lyricsMode = true
    }
    LaunchedEffect(playback.error) {
        if (playback.error == "LANG_MISSING_DATA") {
            runCatching { context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tab_bible), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f).padding(top = 12.dp, bottom = 8.dp))
            IconButton(onClick = { showSearch = true }) {
                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search_bible))
            }
            IconButton(onClick = { showBookmarks = true }) {
                Icon(Icons.Filled.Bookmarks, contentDescription = stringResource(R.string.saved_verses))
            }
            IconButton(onClick = { showHistory = true }) {
                Icon(Icons.Filled.History, contentDescription = stringResource(R.string.reading_history))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            SelectionMenu("${book?.name ?: "Book"} ▾", books, { it.name }, { bookId = it.id; chapter = 1; selectedVerse = 0 }, Modifier.weight(1.4f))
            SelectionMenu("Ch. $chapter ▾", chapterNumbers, Int::toString, { chapter = it; selectedVerse = 0 }, Modifier.weight(0.7f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            SelectionMenu("${translation?.shortName ?: "Translation"} ▾", translations, { it.name }, {
                translationId = it.id
                selectedVerse = 0
                onTranslationChanged(it.id, it.languageCode)
            }, Modifier.weight(1f))
            TextButton(onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                continuousPlayback = !continuousPlayback
            }) {
                Text(if (continuousPlayback) "Continuous ✓" else "Continuous")
            }
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        startPlayback(context, translationId, translation?.languageCode ?: "en-US", bookId, chapter, continuousPlayback)
                    }
                },
                enabled = verses.isNotEmpty(),
            ) { Text(stringResource(R.string.listen)) }
        }
        if (translation != null) {
            Text(
                "${translation.languageCode} · ${if (translation.canonBookCount > 0) "${translation.canonBookCount} books" else "canon coverage unknown"}${if (translation.catholicApproved) " · Catholic-recognized" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
            )
        }
        if (playback.active) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                FilledTonalButton(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    lyricsMode = !lyricsMode
                }) {
                    Text(if (lyricsMode) "Show all verses" else "Follow aloud")
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { scope.launch { moveChapter(-1, books, bookId, chapter, chapterNumbers, { bookId = it; selectedVerse = 0 }, { chapter = it; selectedVerse = 0 }, database, translationId) } }) { Text(stringResource(R.string.previous_chapter)) }
            TextButton(onClick = { scope.launch { moveChapter(1, books, bookId, chapter, chapterNumbers, { bookId = it; selectedVerse = 0 }, { chapter = it; selectedVerse = 0 }, database, translationId) } }) { Text(stringResource(R.string.next_chapter)) }
        }
        when {
            translations.isEmpty() -> Text(stringResource(R.string.offline_bible_missing), Modifier.padding(16.dp))
            chaptersLoading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(20.dp))
            chapterNumbers.isEmpty() && book != null -> Card(Modifier.fillMaxWidth().padding(top = 20.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${book.name} isn’t included in ${translation?.shortName ?: "this edition"}.", style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.region_66_warning), style = MaterialTheme.typography.bodyMedium)
                    translations.firstOrNull { it.id == "eng_webc" }?.let { completeEdition ->
                        Button(onClick = {
                            translationId = completeEdition.id
                            onTranslationChanged(completeEdition.id, completeEdition.languageCode)
                        }) { Text(stringResource(R.string.read_complete_canon, completeEdition.shortName)) }
                    }
                }
            }
            verses.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(20.dp))
            lyricsMode && playback.active -> LyricsReadingMode(
                verses = verses,
                currentVerse = playback.currentVerse,
                reference = playback.reference,
                modifier = Modifier.weight(1f),
            )
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(verses, key = { it.verse }) { verse ->
                    VerseRow(
                        verse,
                        playback.active && playback.bookId == bookId && playback.chapter == chapter && playback.currentVerse == verse.verse,
                        annotations.any { it.verseStart <= verse.verse && it.verseEnd >= verse.verse && it.kind != "BOOKMARK" },
                        selectedVerse == verse.verse,
                        readerTextScale,
                    ) { selectedVerse = if (selectedVerse == verse.verse) 0 else verse.verse }
                }
            }
        }
        if (selectedVerse > 0 && verses.any { it.verse == selectedVerse }) {
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Verse $selectedVerse", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            val old = database.verseAnnotationDao().findVerseAnnotation(bookId, chapter, selectedVerse, "BOOKMARK")
                            if (old == null) database.verseAnnotationDao().upsert(VerseAnnotationEntity(bookId = bookId, chapter = chapter, verseStart = selectedVerse, verseEnd = selectedVerse, translationId = translationId, kind = "BOOKMARK"))
                            else database.verseAnnotationDao().delete(old.id)
                        }
                    }) { Icon(Icons.Filled.Bookmark, contentDescription = stringResource(R.string.toggle_bookmark)) }
                    IconButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            val old = database.verseAnnotationDao().findVerseAnnotation(bookId, chapter, selectedVerse, "HIGHLIGHT")
                            if (old == null) database.verseAnnotationDao().upsert(VerseAnnotationEntity(bookId = bookId, chapter = chapter, verseStart = selectedVerse, verseEnd = selectedVerse, translationId = translationId, kind = "HIGHLIGHT"))
                            else database.verseAnnotationDao().delete(old.id)
                        }
                    }) { Icon(Icons.Filled.Palette, contentDescription = stringResource(R.string.toggle_highlight)) }
                    IconButton(onClick = {
                        noteText = annotations.firstOrNull { it.verseStart == selectedVerse && it.kind == "NOTE" }?.note.orEmpty()
                        noteDialog = true
                    }) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.add_or_edit_note)) }
                }
            }
        }
    }

    if (showSearch) {
        AlertDialog(
            onDismissRequest = { showSearch = false },
            title = { Text(stringResource(R.string.search_this_bible)) },
            text = {
                Column {
                    OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, label = { Text(stringResource(R.string.search_word_or_phrase)) }, singleLine = true)
                    LazyColumn(Modifier.padding(top = 8.dp).heightIn(max = 420.dp)) {
                        items(searchResults, key = { "${it.bookId}-${it.chapter}-${it.verse}" }) { hit ->
                            Column(Modifier.fillMaxWidth().clickable {
                                bookId = hit.bookId
                                chapter = hit.chapter
                                selectedVerse = hit.verse
                                showSearch = false
                            }.padding(vertical = 8.dp)) {
                                Text("${hit.shortName} ${hit.chapter}:${hit.verse}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                Text(hit.text, maxLines = 3, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        if (searchQuery.isNotBlank() && searchResults.isEmpty()) item { Text(stringResource(R.string.no_matches), Modifier.padding(8.dp)) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSearch = false }) { Text(stringResource(R.string.close)) } },
        )
    }
    if (showBookmarks) {
        AlertDialog(
            onDismissRequest = { showBookmarks = false },
            title = { Text(stringResource(R.string.saved_verses)) },
            text = {
                if (bookmarks.isEmpty()) Text(stringResource(R.string.bookmarks_empty))
                else LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(bookmarks, key = VerseAnnotationEntity::id) { item ->
                        val savedBook = books.firstOrNull { it.id == item.bookId }
                        Column(Modifier.fillMaxWidth().clickable {
                            translationId = item.translationId
                            bookId = item.bookId
                            chapter = item.chapter
                            selectedVerse = item.verseStart
                            showBookmarks = false
                        }.padding(vertical = 8.dp)) {
                            Text("${savedBook?.shortName ?: item.bookId} ${item.chapter}:${item.verseStart}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                            Text(verses.firstOrNull { item.bookId == bookId && item.chapter == chapter && it.verse == item.verseStart }?.text.orEmpty(), maxLines = 2)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBookmarks = false }) { Text(stringResource(R.string.close)) } },
        )
    }
    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text(stringResource(R.string.continue_reading)) },
            text = {
                if (history.isEmpty()) Text(stringResource(R.string.history_empty))
                else LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(history.take(30), key = { it.bookId }) { item ->
                        val savedBook = books.firstOrNull { it.id == item.bookId }
                        TextButton(onClick = {
                            translationId = item.translationId
                            bookId = item.bookId
                            chapter = item.chapter
                            showHistory = false
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("${savedBook?.name ?: "Book ${item.bookId}"} ${item.chapter}", Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHistory = false }) { Text(stringResource(R.string.close)) } },
        )
    }
    if (noteDialog) {
        AlertDialog(
            onDismissRequest = { noteDialog = false },
            title = { Text(stringResource(R.string.note_on_verse, selectedVerse)) },
            text = { OutlinedTextField(value = noteText, onValueChange = { noteText = it }, label = { Text(stringResource(R.string.personal_note)) }, minLines = 3) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        val old = database.verseAnnotationDao().findVerseAnnotation(bookId, chapter, selectedVerse, "NOTE")
                        if (noteText.isBlank()) old?.let { database.verseAnnotationDao().delete(it.id) }
                        else database.verseAnnotationDao().upsert(VerseAnnotationEntity(id = old?.id ?: 0, bookId = bookId, chapter = chapter, verseStart = selectedVerse, verseEnd = selectedVerse, translationId = translationId, kind = "NOTE", note = noteText))
                    }
                    noteDialog = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { noteDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun LyricsReadingMode(
    verses: List<VerseEntity>,
    currentVerse: Int,
    reference: String,
    modifier: Modifier = Modifier,
) {
    val index = verses.indexOfFirst { it.verse == currentVerse }.coerceAtLeast(0)
    val previous = verses.getOrNull(index - 1)?.text
    val current = verses.getOrNull(index)?.text.orEmpty()
    val next = verses.getOrNull(index + 1)?.text
    Column(
        modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(reference, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        previous?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        AnimatedContent(targetState = currentVerse, label = "spoken-verse") { verseNumber ->
            val spoken = verses.firstOrNull { it.verse == verseNumber }?.text.orEmpty()
            Card(
                Modifier.fillMaxWidth().animateContentSize(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Text(
                    spoken,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                        lineHeight = 35.sp,
                    ),
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 26.dp),
                )
            }
        }
        next?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        LinearProgressIndicator(
            progress = { (index + 1f) / verses.size.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth(0.7f),
        )
        Text("${index + 1} of ${verses.size} verses", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun startPlayback(
    context: android.content.Context,
    translationId: String,
    languageCode: String,
    bookId: Int,
    chapter: Int,
    autoAdvance: Boolean,
) {
    val intent = Intent(context, BiblePlaybackService::class.java)
        .setAction(BiblePlaybackService.ACTION_START)
        .putExtra(BiblePlaybackService.EXTRA_TRANSLATION_ID, translationId)
        .putExtra(BiblePlaybackService.EXTRA_LANGUAGE_CODE, languageCode)
        .putExtra(BiblePlaybackService.EXTRA_BOOK_ID, bookId)
        .putExtra(BiblePlaybackService.EXTRA_CHAPTER, chapter)
        .putExtra(BiblePlaybackService.EXTRA_AUTO_ADVANCE, autoAdvance)
    ContextCompat.startForegroundService(context, intent)
}

@Composable
private fun VerseRow(
    verse: VerseEntity,
    highlighted: Boolean,
    annotated: Boolean,
    selected: Boolean,
    readerTextScale: Float,
    onClick: () -> Unit,
) {
    val isTinted = highlighted || annotated || selected
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .background(
                if (isTinted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        androidx.compose.material3.Surface(
            color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            shape = CircleShape,
            modifier = Modifier.padding(top = 2.dp),
        ) {
            Text(
                verse.verse.toString(),
                color = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            )
        }
        Text(
            verse.text,
            color = if (isTinted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 18.sp * readerTextScale,
                lineHeight = 29.sp * readerTextScale,
            ),
        )
    }
}

@Composable
private fun <T> SelectionMenu(
    label: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box(modifier) {
        FilledTonalButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(label, maxLines = 1, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ExpandMore, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(optionLabel(option)) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}

private suspend fun moveChapter(
    direction: Int,
    books: List<BookEntity>,
    bookId: Int,
    chapter: Int,
    chapters: List<Int>,
    setBook: (Int) -> Unit,
    setChapter: (Int) -> Unit,
    database: VerbumDatabase,
    translationId: String,
) {
    val available = books.sortedBy(BookEntity::canonicalOrder)
    val currentChapterIndex = chapters.indexOf(chapter)
    val currentBookIndex = available.indexOfFirst { it.id == bookId }
    if (currentBookIndex < 0 || chapters.isEmpty()) return
    val nextChapter = chapters.getOrNull(currentChapterIndex + direction)
    if (nextChapter != null) setChapter(nextChapter)
    else findAdjacentBook(available, currentBookIndex, direction, database, translationId, setBook, setChapter)
}

private suspend fun findAdjacentBook(
    books: List<BookEntity>,
    startIndex: Int,
    direction: Int,
    database: VerbumDatabase,
    translationId: String,
    setBook: (Int) -> Unit,
    setChapter: (Int) -> Unit,
) {
    var index = startIndex + direction
    while (index in books.indices) {
        val nextBook = books[index]
        val chapters = database.verseDao().getChapterNumbers(translationId, nextBook.id)
        if (chapters.isNotEmpty()) {
            setBook(nextBook.id)
            setChapter(if (direction > 0) chapters.first() else chapters.last())
            return
        }
        index += direction
    }
}
