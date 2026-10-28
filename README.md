# Verbum

**A Catholic Bible reader for Android, built for Scripture, prayer, and daily reading.**

Verbum is a native Android app with an offline Bible library, a focused Scripture reader, daily verse and liturgical views, and home-screen widgets. It follows the Catholic 73-book canon while clearly identifying editions that contain fewer books.

> **Release status:** Verbum 0.1.4 is the initial public release. The app is usable for offline Bible reading, but daily Mass reading coverage and some Catholic and regional editions are still being developed. See [Current coverage](#current-coverage).

## What you can do

- Read bundled Bible editions offline, with chapter navigation, search, bookmarks, highlights, notes, and reading history.
- Choose a Bible edition independently of the app's display language. Scripture stays in the language of the selected edition.
- Download additional editions from the in-app library. The catalog identifies language, source, license, book coverage, and import status.
- Follow a daily verse and explore the liturgical calendar.
- Listen to Scripture using Android text-to-speech.
- Add daily verse, daily lectionary, and compact short-verse widgets. The short-verse widget is designed for constrained spaces such as lock-screen widget areas on Android versions and launchers that support them.
- Set the app to System, Light, or Dark appearance. System appearance uses Material You dynamic colors on Android 12 and later when available.
- Use translated interface resources in English, Malayalam, Tamil, Hindi, Telugu, Kannada, and Marathi.

## Screenshots

Screenshots will be added after the first device review.

## Current coverage

### Bible editions

The bundled offline library contains:

| Edition | Language | Canon coverage | Availability |
| --- | --- | ---: | --- |
| World English Bible Catholic Edition (WEBC) | English | 73 books | Bundled; default edition |
| Douay-Rheims American Edition (DRA) | English | 73 books | Bundled |
| Clementine Vulgate | Latin | 73 books | Bundled |

Additional editions appear in the in-app catalog with their source and coverage. Several regional downloads currently contain 66 books; they are labeled incomplete for Catholic canon coverage. Other editions are marked **Coming soon** when the source format or distribution terms still need work. Do not assume that every listed translation is currently downloadable or selectable.

### Daily readings

The calendar engine calculates liturgical seasons, cycles, feast titles, and colors offline. The bundled database currently includes a starter reading citation entry; it does **not** yet contain a complete daily Mass lectionary. Other rites and full year-round reading coverage are in development. The app labels unavailable readings rather than presenting calendar calculations as complete reading content.

## Get the app

Download the Android package from the repository's **Releases** page when version 0.1.4 is published. Verify that the release asset is signed before installing. If you are building from source, see [Development](#development).

## Development

### Requirements

- JDK 17
- Android SDK Platform 35 and Android Build Tools 35.0.0
- Android Studio or the included Gradle wrapper
- Android 8.0 (API 26) or later to run the app

Set `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) to your SDK directory. If needed, create `local.properties` with the local `sdk.dir` path; this machine-specific file is ignored by Git.

Build and run the debug app:

```sh
./gradlew :app:assembleDebug
```

Run JVM unit tests and lint:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug
```

Run instrumentation tests on an attached API 26+ device or emulator:

```sh
./gradlew :app:connectedDebugAndroidTest
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### Project structure

```text
app/src/main/java/com/verbum/app/
  audio/        Scripture playback service
  data/         Room database, entities, DAOs, and repository
  liturgy/      Offline calendar and reading-data installation
  translations/ Translation catalog, importers, and download worker
  ui/           Compose screens and theme
  widget/       App widgets and per-widget configuration
app/src/main/assets/database/  Bundled catalog and offline seed database
app/src/test/                  JVM tests
app/src/androidTest/           Android instrumentation tests
tools/                         Reproducible seed database generator and source archives
```

### Architecture

- Kotlin, Jetpack Compose, and Material 3 for the app interface.
- Room and SQLite for offline Scripture, reading state, and bundled content.
- WorkManager for background downloads and widget refreshes.
- Android `RemoteViews` for launcher widgets.
- Android text-to-speech for spoken reading.

The app's minimum supported Android version is API 26. The current build targets API 35. See `app/build.gradle.kts` for the authoritative version and dependency configuration.

## Bible data and attribution

The bundled database is generated from the source archives in `tools/seed_sources/` by `tools/create_seed_db.py`. The bundled Bible texts are identified as public domain in the catalog and data documentation. Downloadable text retains its own license and attribution; translation rights are separate from the Verbum app's source-code rights.

The catalog and importer support USFM ZIP, Zefania XML, JSON, and compatible SQLite sources where implemented. Edition-specific metadata records source URLs, declared book count, language, and license. Review each source's terms before redistributing it or changing its catalog status.

See [Bible data sources and licensing](docs/BIBLE_DATA_SOURCES.md) for details and [app/src/main/assets/database/README.md](app/src/main/assets/database/README.md) for seed generation.

## Privacy

Verbum does not require an account. Reading notes, bookmarks, highlights, and history are stored in the app's local database. Translation downloads use the source URLs listed in the catalog. The app requests internet access for those downloads; this repository does not include an analytics SDK.

## Contributing

Bug reports and focused pull requests are welcome. Please include reproduction steps for bugs and identify the Android version and device or emulator where relevant. Before changing scripture datasets, include the source, license, attribution, language, and book coverage.

See [CONTRIBUTING.md](CONTRIBUTING.md). Please do not add private keys, signing files, personal data, or unlicensed scripture content.

## Release and license

Release preparation and versioning are documented in [docs/RELEASING.md](docs/RELEASING.md). Version 0.1.4 release notes are in [CHANGELOG.md](CHANGELOG.md).

No software license has been assigned to the Verbum application source yet. Until a `LICENSE` file is added, public visibility of this repository does not grant permission to copy, modify, or redistribute its application code. Bible editions and other data may have separate terms as described in their source metadata.
