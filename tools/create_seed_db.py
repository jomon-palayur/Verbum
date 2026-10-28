#!/usr/bin/env python3
"""Create the bundled Verbum Room-compatible seed database."""
import sqlite3
import re
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
DB_PATH = ROOT / "app/src/main/assets/database/verbum_seed.db"
SOURCE_ARCHIVES = [
    ("eng_dra", "Douay-Rheims American Edition (1899)", "DRA", "en-US", ROOT / "tools/seed_sources/engDRA_usfm.zip"),
    ("eng_webc", "World English Bible Catholic Edition (Modern English)", "WEBC", "en-US", ROOT / "tools/seed_sources/eng-web-c_usfm.zip"),
    ("lat_vuc", "Clementine Vulgate (Latin, 1598)", "VULC", "la", ROOT / "tools/seed_sources/latVUC_usfm.zip"),
]
BOOKS = [
    ("Genesis", "Gen", "OT"), ("Exodus", "Ex", "OT"), ("Leviticus", "Lev", "OT"),
    ("Numbers", "Num", "OT"), ("Deuteronomy", "Deut", "OT"), ("Joshua", "Josh", "OT"),
    ("Judges", "Judg", "OT"), ("Ruth", "Ruth", "OT"), ("1 Samuel", "1 Sam", "OT"),
    ("2 Samuel", "2 Sam", "OT"), ("1 Kings", "1 Kgs", "OT"), ("2 Kings", "2 Kgs", "OT"),
    ("1 Chronicles", "1 Chr", "OT"), ("2 Chronicles", "2 Chr", "OT"), ("Ezra", "Ezra", "OT"),
    ("Nehemiah", "Neh", "OT"), ("Tobit", "Tob", "OT"), ("Judith", "Jdt", "OT"),
    ("Esther", "Esth", "OT"), ("1 Maccabees", "1 Macc", "OT"), ("2 Maccabees", "2 Macc", "OT"),
    ("Job", "Job", "OT"), ("Psalms", "Ps", "OT"), ("Proverbs", "Prov", "OT"),
    ("Ecclesiastes", "Eccl", "OT"), ("Song of Solomon", "Song", "OT"), ("Wisdom", "Wis", "OT"),
    ("Sirach", "Sir", "OT"), ("Isaiah", "Isa", "OT"), ("Jeremiah", "Jer", "OT"),
    ("Lamentations", "Lam", "OT"), ("Baruch", "Bar", "OT"), ("Ezekiel", "Ezek", "OT"),
    ("Daniel", "Dan", "OT"), ("Hosea", "Hos", "OT"), ("Joel", "Joel", "OT"),
    ("Amos", "Amos", "OT"), ("Obadiah", "Obad", "OT"), ("Jonah", "Jon", "OT"),
    ("Micah", "Mic", "OT"), ("Nahum", "Nah", "OT"), ("Habakkuk", "Hab", "OT"),
    ("Zephaniah", "Zeph", "OT"), ("Haggai", "Hag", "OT"), ("Zechariah", "Zech", "OT"),
    ("Malachi", "Mal", "OT"), ("Matthew", "Matt", "NT"), ("Mark", "Mark", "NT"),
    ("Luke", "Luke", "NT"), ("John", "John", "NT"), ("Acts", "Acts", "NT"),
    ("Romans", "Rom", "NT"), ("1 Corinthians", "1 Cor", "NT"),
    ("2 Corinthians", "2 Cor", "NT"), ("Galatians", "Gal", "NT"), ("Ephesians", "Eph", "NT"),
    ("Philippians", "Phil", "NT"), ("Colossians", "Col", "NT"), ("1 Thessalonians", "1 Thess", "NT"),
    ("2 Thessalonians", "2 Thess", "NT"), ("1 Timothy", "1 Tim", "NT"),
    ("2 Timothy", "2 Tim", "NT"), ("Titus", "Titus", "NT"), ("Philemon", "Phlm", "NT"),
    ("Hebrews", "Heb", "NT"), ("James", "Jas", "NT"), ("1 Peter", "1 Pet", "NT"),
    ("2 Peter", "2 Pet", "NT"), ("1 John", "1 John", "NT"), ("2 John", "2 John", "NT"),
    ("3 John", "3 John", "NT"), ("Jude", "Jude", "NT"), ("Revelation", "Rev", "NT"),
]
USFM_BOOK_IDS = {
    "GEN": 1, "EXO": 2, "LEV": 3, "NUM": 4, "DEU": 5, "JOS": 6, "JDG": 7,
    "RUT": 8, "1SA": 9, "2SA": 10, "1KI": 11, "2KI": 12, "1CH": 13, "2CH": 14,
    "EZR": 15, "NEH": 16, "TOB": 17, "JDT": 18, "EST": 19, "ESG": 19, "1MA": 20, "2MA": 21,
    "JOB": 22, "PSA": 23, "PRO": 24, "ECC": 25, "SNG": 26, "WIS": 27, "SIR": 28,
    "ISA": 29, "JER": 30, "LAM": 31, "BAR": 32, "EZK": 33, "DAN": 34, "DAG": 34, "HOS": 35,
    "JOL": 36, "AMO": 37, "OBA": 38, "JON": 39, "MIC": 40, "NAM": 41, "HAB": 42,
    "ZEP": 43, "HAG": 44, "ZEC": 45, "MAL": 46, "MAT": 47, "MRK": 48, "LUK": 49,
    "JHN": 50, "ACT": 51, "ROM": 52, "1CO": 53, "2CO": 54, "GAL": 55, "EPH": 56,
    "PHP": 57, "COL": 58, "1TH": 59, "2TH": 60, "1TI": 61, "2TI": 62, "TIT": 63,
    "PHM": 64, "HEB": 65, "JAS": 66, "1PE": 67, "2PE": 68, "1JN": 69, "2JN": 70,
    "3JN": 71, "JUD": 72, "REV": 73,
}

DB_PATH.parent.mkdir(parents=True, exist_ok=True)
DB_PATH.unlink(missing_ok=True)
connection = sqlite3.connect(DB_PATH)
connection.executescript("""
CREATE TABLE books (
    id INTEGER NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    shortName TEXT NOT NULL,
    testament TEXT NOT NULL,
    canonicalOrder INTEGER NOT NULL
);
CREATE TABLE translations (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    shortName TEXT NOT NULL,
    languageCode TEXT NOT NULL,
    isBundled INTEGER NOT NULL,
    installedAt INTEGER NOT NULL
);
CREATE TABLE verses (
    translationId TEXT NOT NULL,
    bookId INTEGER NOT NULL,
    chapter INTEGER NOT NULL,
    verse INTEGER NOT NULL,
    text TEXT NOT NULL,
    PRIMARY KEY(translationId, bookId, chapter, verse)
);
CREATE INDEX index_verses_translationId_bookId_chapter
    ON verses(translationId, bookId, chapter);
CREATE TABLE curated_verses (
    id INTEGER NOT NULL PRIMARY KEY,
    bookId INTEGER NOT NULL,
    chapter INTEGER NOT NULL,
    verseStart INTEGER NOT NULL,
    verseEnd INTEGER NOT NULL,
    charLengthEstimate INTEGER NOT NULL,
    stateTags TEXT NOT NULL,
    tone TEXT NOT NULL,
    season TEXT NOT NULL,
    contextSummary TEXT NOT NULL,
    patristicQuote TEXT NOT NULL,
    patristicAuthor TEXT NOT NULL,
    practicalTakeaway TEXT NOT NULL
);
CREATE TABLE lectionary_readings (
    liturgicalKey TEXT NOT NULL PRIMARY KEY,
    title TEXT NOT NULL,
    color TEXT NOT NULL,
    firstReadingRef TEXT NOT NULL,
    psalmRef TEXT NOT NULL,
    secondReadingRef TEXT,
    gospelAcclamationRef TEXT NOT NULL DEFAULT '',
    gospelRef TEXT NOT NULL,
    gospelKeyVerseCoord TEXT NOT NULL
);
CREATE TABLE widget_configs (
    appWidgetId INTEGER NOT NULL PRIMARY KEY,
    widgetType TEXT NOT NULL,
    translationId TEXT NOT NULL,
    bgOpacityPercent INTEGER NOT NULL,
    bgTintMode TEXT NOT NULL,
    textColorMode TEXT NOT NULL,
    useDropShadow INTEGER NOT NULL,
    fontFamily TEXT NOT NULL,
    refreshHours INTEGER NOT NULL,
    activeCuratedVerseId INTEGER
);
""")
if len(BOOKS) != 73:
    raise SystemExit(f"Expected 73 canonical books, found {len(BOOKS)}")
connection.executemany(
    "INSERT INTO books(id, name, shortName, testament, canonicalOrder) VALUES (?, ?, ?, ?, ?)",
    [(index, name, short_name, testament, index) for index, (name, short_name, testament) in enumerate(BOOKS, 1)],
)
marker_pattern = re.compile(r"\\(c|v)\s+(\d+)(?:\s+)?")

def parse_archive(translation_id, archive_path):
    if not archive_path.is_file():
        raise SystemExit(f"Missing licensed translation source: {archive_path}")
    rows = []
    seen_books = set()
    with ZipFile(archive_path) as archive:
        for filename in archive.namelist():
            if not filename.lower().endswith(".usfm"):
                continue
            source = archive.read(filename).decode("utf-8-sig")
            id_match = re.search(r"^\\id\s+(\S+)", source, re.MULTILINE)
            if id_match is None:
                raise SystemExit(f"USFM file has no book ID: {filename}")
            usfm_id = id_match.group(1).upper()
            book_id = USFM_BOOK_IDS.get(usfm_id)
            if book_id is None or book_id in seen_books:
                raise SystemExit(f"Unexpected or duplicate USFM book ID: {usfm_id}")
            seen_books.add(book_id)
            markers = list(marker_pattern.finditer(source))
            chapter = 0
            for index, marker in enumerate(markers):
                kind, number = marker.group(1), int(marker.group(2))
                end = markers[index + 1].start() if index + 1 < len(markers) else len(source)
                if kind == "c":
                    chapter = number
                    continue
                if chapter <= 0:
                    raise SystemExit(f"Verse occurs before a chapter in {filename}")
                verse_text = source[marker.end():end]
                verse_text = re.sub(r"\\w\s+([^|]+)\|[^\\]*\\w\*", r"\1", verse_text)
                verse_text = re.sub(r"\\[+a-zA-Z0-9]+\*?", " ", verse_text)
                verse_text = re.sub(r"\s+", " ", verse_text).strip()
                if verse_text:
                    rows.append((translation_id, book_id, chapter, number, verse_text))
    if len(seen_books) != 73:
        raise SystemExit(f"{archive_path.name}: expected 73 canonical books, found {len(seen_books)}")
    return rows

verse_counts = {}
for translation_id, name, short_name, language_code, archive_path in SOURCE_ARCHIVES:
    connection.execute(
        "INSERT INTO translations(id, name, shortName, languageCode, isBundled, installedAt) VALUES (?, ?, ?, ?, ?, ?)",
        (translation_id, name, short_name, language_code, 1, 0),
    )
    verse_rows = parse_archive(translation_id, archive_path)
    connection.executemany(
        "INSERT INTO verses(translationId, bookId, chapter, verse, text) VALUES (?, ?, ?, ?, ?)",
        verse_rows,
    )
    verse_counts[translation_id] = len(verse_rows)
curated_rows = [
    (1, 23, 23, 1, 4, 125, "anxiety,trust,comfort", "comforting", "any", "A psalm of confidence uses the image of a shepherd to describe God's care through danger and uncertainty.", "You have made us for yourself, and our heart is restless until it rests in you.", "St. Augustine", "Name one place where you need guidance today, and entrust it to God in prayer."),
    (2, 23, 46, 2, 4, 93, "anxiety,peace,trust", "comforting", "any", "The psalmist calls the people to trust God even when the world around them feels unstable.", "", "", "Pause for one quiet minute and repeat the words, 'God is our refuge and strength.'"),
    (3, 29, 41, 10, 10, 94, "anxiety,courage,hope", "comforting", "any", "Isaiah reassures God's people that they are not abandoned in a time of fear.", "Ignorance of Scripture is ignorance of Christ.", "St. Jerome", "Ask someone you trust for support with one difficult task."),
    (4, 47, 11, 28, 30, 145, "weary,peace,hope", "comforting", "any", "Jesus invites the burdened to learn his gentle way of life.", "", "", "Set down one unnecessary burden and make room for rest."),
    (5, 52, 8, 28, 28, 111, "grief,hope,trust", "comforting", "any", "Paul speaks of God's faithful purpose amid suffering and the hope of sharing in Christ's glory.", "", "", "Look for one small good that can grow from today's difficulty."),
    (6, 52, 8, 31, 39, 150, "fear,hope,trust", "challenging", "any", "Paul proclaims that nothing in creation can separate believers from the love of God in Christ.", "", "", "When fear rises, name the love that remains with you."),
    (7, 57, 4, 6, 7, 118, "anxiety,peace,prayer", "comforting", "any", "Paul encourages the Philippians to bring their needs to God with gratitude and prayer.", "", "", "Turn one worry into a short, honest prayer."),
    (8, 67, 5, 7, 7, 62, "anxiety,trust,comfort", "comforting", "any", "Peter reminds the community that they can entrust their anxieties to God's care.", "", "", "Write down a worry and consciously hand it over in prayer."),
    (9, 27, 3, 1, 9, 136, "grief,hope,trust", "contemplative", "any", "Wisdom contrasts the apparent defeat of the just with their hope in God's keeping.", "", "", "Remember a person who has died and pray for them today."),
    (10, 28, 2, 1, 11, 158, "hardship,trust,courage", "challenging", "any", "Sirach encourages perseverance and fidelity when a person begins serving the Lord.", "", "", "Choose one faithful next step instead of demanding to see the whole path."),
    (11, 50, 14, 1, 3, 123, "grief,hope,comfort", "comforting", "any", "Jesus consoles his disciples and promises that they will remain with him.", "", "", "Offer comfort to someone facing a goodbye or a season of change."),
    (12, 49, 15, 4, 7, 137, "mercy,hope,belonging", "comforting", "any", "Jesus tells of a shepherd who searches for the one sheep that has gone missing.", "", "", "Take the first step toward someone who may feel forgotten."),
    (13, 17, 4, 15, 15, 108, "kindness,guidance,justice", "challenging", "any", "Tobit gives his son practical counsel about how to treat others.", "", "", "Treat the next person you meet with the fairness you hope to receive."),
    (14, 66, 1, 2, 4, 130, "patience,hardship,hope", "challenging", "any", "James reframes trials as a place where endurance and mature faith can grow.", "", "", "Practice patience in one situation you cannot resolve immediately."),
    (15, 50, 11, 35, 35, 16, "grief,comfort,hope", "comforting", "any", "At the tomb of Lazarus, Jesus shares in the grief of those he loves.", "", "", "Bring your grief honestly to Christ today."),
    (16, 69, 4, 8, 8, 16, "love,hope,belonging", "contemplative", "any", "John describes love as the very nature of God.", "", "", "Offer one small act of love without expecting a return."),
    (17, 59, 5, 17, 17, 24, "anxiety,prayer,trust", "comforting", "any", "Paul asks the Thessalonians to remain close to God in every moment.", "", "", "Take one quiet breath and turn it into a prayer."),
    (18, 59, 5, 18, 18, 32, "gratitude,hope,trust", "contemplative", "any", "Paul invites gratitude in every circumstance.", "", "", "Name one gift you received today."),
]
connection.executemany(
    "INSERT INTO curated_verses(id, bookId, chapter, verseStart, verseEnd, charLengthEstimate, stateTags, tone, season, contextSummary, patristicQuote, patristicAuthor, practicalTakeaway) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
    curated_rows,
)
connection.execute(
    "INSERT INTO lectionary_readings(liturgicalKey, title, color, firstReadingRef, psalmRef, secondReadingRef, gospelAcclamationRef, gospelRef, gospelKeyVerseCoord) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
    ("FEAST_10_28", "Feast of Saints Simon and Jude, Apostles", "RED", "56:2:19-22", "23:19:2-5", None, "50:15:16", "49:6:12-16", "49:6:12"),
)
connection.commit()
connection.execute("PRAGMA user_version = 2")
connection.commit()
connection.close()
print(f"Wrote {DB_PATH} with {len(BOOKS)} books and {verse_counts}")
