# Data Notice and Attribution

## Application code and embedded samples

The application source code is licensed under the MIT License (`LICENSE`). The
MIT License does not automatically apply to downloadable dictionary packages.

The small vocabulary in `app/src/main/assets/seed_entries.json` was written for
prototype demonstration purposes. It is not extracted from Wiktionary or
FreeDict.

## `pt-pt` package, version `2026-09-17`

The Portuguese–Portuguese package is a derivative work based on content from
the Portuguese Wiktionary:

- authorship: Portuguese Wiktionary contributors;
- source project: https://pt.wiktionary.org/;
- structured extraction: Wiktextract/Kaikki.org;
- source file: https://kaikki.org/dictionary/downloads/pt/pt-extract.jsonl.gz;
- stated extraction date: September 17, 2026;
- source SHA-256:
  `c51c584c3b39e6c33165848b69cd7ac135cd4fa24c0e1d4d3cfa1dab93efb15e`;
- redistribution license for the derivative package: CC BY-SA 4.0;
- license text: https://creativecommons.org/licenses/by-sa/4.0/.

LinguaWiki performed the following transformations:

1. selected records whose entry language is Portuguese;
2. normalized and reorganized fields into SQLite tables;
3. linked form-only records to their corresponding lemmas;
4. generated diacritic-insensitive keys and indexes for lemmas, prefixes, and
   forms;
5. retained definitions, examples, all available International Phonetic
   Alphabet (IPA) records, etymology, translations linked to their senses, and
   structured inflection paradigms when available in the extraction;
6. compressed the database deterministically for distribution.

Published artifact identity:

- file: `linguawiki-pt-pt-2026-09-17.sqlite.gz`;
- file size: 60,755,090 bytes;
- file SHA-256:
  `f590afd2238a65b469675d5bcd336e074b34bd515a3ca503179761381885f0fe`;
- uncompressed database size: 268,677,120 bytes;
- uncompressed database SHA-256:
  `0379e7fea5b0e614dfe432e38c197ebc3d3839c3c640e9e25767dbec8b1f7623`.

When redistributing a copy or adaptation of the package, retain this
attribution, identify any additional modifications, and comply with CC BY-SA
4.0. Audio files are not included in this package and may be subject to their
own licenses.
