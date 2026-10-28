# Bible data sources and licensing

Verbum keeps the Bible text separate from its application code. Each edition has its own source, license, language, and book-count metadata in `app/src/main/assets/database/translation_catalog.json`. Check the source terms again before redistributing data; upstream repositories and licenses can change.

## Bundled editions

| Edition | Source | Catalog status |
| --- | --- | --- |
| World English Bible Catholic Edition (WEBC) | [eBible.org edition page](https://ebible.org/eng-web-c/) | Public domain; 73 books; bundled |
| Douay-Rheims American Edition (DRA) | [eBible.org edition page](https://ebible.org/find/details.php?id=engDRA) | Public domain; 73 books; bundled |
| Clementine Vulgate | [eBible.org edition page](https://ebible.org/find/details.php?id=latVUC) | Public domain; 73 books; bundled |

The source USFM archives and the seed database generator are in `tools/seed_sources/` and `tools/create_seed_db.py`. Regenerate the database after changing source data or its schema:

```sh
python3 tools/create_seed_db.py
```

The generated seed database is an app asset and is intentionally included in the source tree. Do not delete it from a source checkout unless you also change the database initialization flow.

## Download catalog

The download catalog includes editions from eBible.org and community repositories, including [Free Bibles India](https://github.com/FreeBiblesIndia). Entries include a declared license and book count. Some regional entries contain 66 books and are marked incomplete for the Catholic canon. Other sources remain marked Coming soon when import support or distribution terms have not been established.

Third-party sources currently represented in the catalog include:

- [NABRE JSON dataset](https://github.com/nirmalben/bible-nabre-json-dataset)
- [Tamil Bible OSIS](https://github.com/jayarathina/Tamil-Bible-OSIS)
- [Catholic Public Domain Version PSFM source](https://github.com/BibleCorps/ENG-B-CPDV2009-pd-PSFM)
- [Free Bibles India language repositories](https://github.com/FreeBiblesIndia)

Their catalog availability, importer support, and license metadata are separate questions. Do not treat a source being listed as proof that it is bundled, downloadable, complete, or approved. Preserve each upstream copyright notice and attribution. Written rights-holder permission should be retained by the maintainers when a catalog entry relies on direct authorization.

## Canon and approval labels

Verbum uses a 73-book Catholic canonical order. A source's declared book count is not by itself proof of text completeness or ecclesial approval. An approval label must identify the approving authority. When coverage or terms are uncertain, keep the edition unavailable or label it incomplete/Coming soon rather than implying approval.

## Catalog source configuration

The app's built-in catalog is `app/src/main/assets/database/translation_catalog.json`. A build may point at a hosted HTTPS catalog:

```sh
./gradlew :app:assembleDebug \
  -PverbumTranslationCatalogUrl=https://example.org/catalog.json
```

The importer supports USFM ZIP, Zefania XML, nested or flat JSON, and compatible SQLite sources where the adapter handles the source structure. An unsupported format should remain marked Coming soon until it can be tested against that source.
