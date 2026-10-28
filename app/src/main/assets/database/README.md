# Bundled seed database

`verbum_seed.db` is opened by `VerbumDatabase` through Room's `createFromAsset` support. It contains the 73 canonical books, three complete offline editions, a starter set of daily reflections, and the October 28 Saints Simon and Jude reading map. The default is the modern World English Bible Catholic Edition (WEBC); the Douay-Rheims and Latin Clementine Vulgate are available in the reader and Library.

The seed source archives are `tools/seed_sources/engDRA_usfm.zip`, `tools/seed_sources/eng-web-c_usfm.zip`, and `tools/seed_sources/latVUC_usfm.zip`. eBible.org identifies all three as public domain. Regenerate the binary after changing the schema, canonical book metadata, or seed rows with:

```sh
python3 tools/create_seed_db.py
```

The USFM book IDs are normalized into the app's canonical 1–73 book order during seed generation. Esther and Daniel appendix IDs used by the Catholic edition are folded into the canonical Esther and Daniel rows.
