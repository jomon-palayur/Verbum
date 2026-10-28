# Verbum

**Catholic Scripture, close at hand.**

Verbum brings Catholic Scripture and tradition into a calm, thoughtful reading experience, with a growing collection of translations and a focus on prayer, reflection, and the Church's liturgical life.

## Highlights

- A growing library of Bible translations, with language, source, and canon coverage clearly identified.
- A focused reader for prayer and personal study.
- Daily Scripture, liturgical calendar, and home-screen widgets.
- Light, dark, and system appearance.

## Get Verbum

The planned first release is **0.1.4**. Downloads and release notes will be posted on the repository's [Releases](https://github.com/jomon-palayur/Verbum/releases) page.

## Build from source

Requires JDK 17 and Android SDK Platform 35.

```sh
./gradlew :app:assembleDebug
```

The debug APK is created at `app/build/outputs/apk/debug/app-debug.apk`.

## Development checks

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug
```

## Bible data

Scripture editions have their own sources, licenses, and attribution. See [Bible data sources](docs/BIBLE_DATA_SOURCES.md). Daily Mass reading coverage and some editions are still in development.

## Contributing

Bug reports and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

No license has yet been assigned to the Verbum application source. Bible texts and other third-party data are governed by their own terms.
