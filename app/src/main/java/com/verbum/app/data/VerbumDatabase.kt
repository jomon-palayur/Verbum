package com.verbum.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.verbum.app.data.dao.BookDao
import com.verbum.app.data.dao.CuratedVerseDao
import com.verbum.app.data.dao.LectionaryReadingDao
import com.verbum.app.data.dao.TranslationDao
import com.verbum.app.data.dao.VerseDao
import com.verbum.app.data.dao.WidgetConfigDao
import com.verbum.app.data.dao.VerseAnnotationDao
import com.verbum.app.data.dao.ReadingHistoryDao
import com.verbum.app.data.dao.LiturgicalProfileDao
import com.verbum.app.data.model.BookEntity
import com.verbum.app.data.model.CuratedVerseEntity
import com.verbum.app.data.model.LectionaryReadingEntity
import com.verbum.app.data.model.TranslationEntity
import com.verbum.app.data.model.VerseEntity
import com.verbum.app.data.model.WidgetConfigEntity
import com.verbum.app.data.model.VerseAnnotationEntity
import com.verbum.app.data.model.ReadingHistoryEntity
import com.verbum.app.data.model.LiturgicalProfileEntity

@Database(
    entities = [
        BookEntity::class,
        TranslationEntity::class,
        VerseEntity::class,
        CuratedVerseEntity::class,
        LectionaryReadingEntity::class,
        WidgetConfigEntity::class,
        VerseAnnotationEntity::class,
        ReadingHistoryEntity::class,
        LiturgicalProfileEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class VerbumDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun translationDao(): TranslationDao
    abstract fun verseDao(): VerseDao
    abstract fun curatedVerseDao(): CuratedVerseDao
    abstract fun lectionaryReadingDao(): LectionaryReadingDao
    abstract fun widgetConfigDao(): WidgetConfigDao
    abstract fun verseAnnotationDao(): VerseAnnotationDao
    abstract fun readingHistoryDao(): ReadingHistoryDao
    abstract fun liturgicalProfileDao(): LiturgicalProfileDao

    companion object {
        @Volatile private var instance: VerbumDatabase? = null
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE lectionary_readings ADD COLUMN gospelAcclamationRef TEXT NOT NULL DEFAULT ''")
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE translations ADD COLUMN canonBookCount INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE translations ADD COLUMN catholicApproved INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE translations ADD COLUMN attribution TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE translations ADD COLUMN approvalAuthority TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE widget_configs ADD COLUMN liturgicalProfileId TEXT NOT NULL DEFAULT 'roman'")
                database.execSQL("UPDATE translations SET canonBookCount = 73 WHERE id IN ('eng_webc','eng_dra','lat_vuc')")
                database.execSQL("UPDATE translations SET canonBookCount = 66 WHERE id IN ('hincv','kanirv','mal2015','marc','tamtcv','tel2017')")
                database.execSQL("""CREATE TABLE IF NOT EXISTS verse_annotations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    bookId INTEGER NOT NULL, chapter INTEGER NOT NULL,
                    verseStart INTEGER NOT NULL, verseEnd INTEGER NOT NULL,
                    translationId TEXT NOT NULL, kind TEXT NOT NULL,
                    colorArgb INTEGER NOT NULL, note TEXT NOT NULL, updatedAt INTEGER NOT NULL)""")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_verse_annotations_bookId_chapter_verseStart_verseEnd ON verse_annotations(bookId, chapter, verseStart, verseEnd)")
                database.execSQL("""CREATE TABLE IF NOT EXISTS reading_history (
                    bookId INTEGER NOT NULL PRIMARY KEY, chapter INTEGER NOT NULL,
                    translationId TEXT NOT NULL, updatedAt INTEGER NOT NULL)""")
                database.execSQL("""CREATE TABLE IF NOT EXISTS liturgical_profiles (
                    id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, tradition TEXT NOT NULL,
                    regionCode TEXT NOT NULL, sourceName TEXT NOT NULL, sourceUrl TEXT NOT NULL,
                    license TEXT NOT NULL, isBundled INTEGER NOT NULL)""")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_liturgical_profiles_tradition_regionCode ON liturgical_profiles(tradition, regionCode)")
                database.execSQL("""CREATE TABLE lectionary_readings_v3 (
                    liturgicalKey TEXT NOT NULL, title TEXT NOT NULL, color TEXT NOT NULL,
                    firstReadingRef TEXT NOT NULL, psalmRef TEXT NOT NULL, secondReadingRef TEXT,
                    gospelAcclamationRef TEXT NOT NULL DEFAULT '', gospelRef TEXT NOT NULL,
                    gospelKeyVerseCoord TEXT NOT NULL, profileId TEXT NOT NULL,
                    PRIMARY KEY(profileId, liturgicalKey))""")
                database.execSQL("""INSERT INTO lectionary_readings_v3
                    (liturgicalKey,title,color,firstReadingRef,psalmRef,secondReadingRef,gospelAcclamationRef,gospelRef,gospelKeyVerseCoord,profileId)
                    SELECT liturgicalKey,title,color,firstReadingRef,psalmRef,secondReadingRef,gospelAcclamationRef,gospelRef,gospelKeyVerseCoord,'roman'
                    FROM lectionary_readings""")
                database.execSQL("DROP TABLE lectionary_readings")
                database.execSQL("ALTER TABLE lectionary_readings_v3 RENAME TO lectionary_readings")
                database.execSQL("""INSERT OR IGNORE INTO liturgical_profiles
                    (id,name,tradition,regionCode,sourceName,sourceUrl,license,isBundled)
                    VALUES ('roman','Roman Rite','Roman','001','General Roman Calendar','','Existing Verbum data',1)""")
            }
        }
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

        fun getInstance(context: Context): VerbumDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                VerbumDatabase::class.java,
                "verbum.db",
            )
                .createFromAsset("database/verbum_seed.db")
                .addMigrations(*MIGRATIONS)
                .build()
                .also { instance = it }
        }
    }
}
