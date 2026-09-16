# Data Notice and Attribution

## Application code and embedded samples

The application source code is licensed under the MIT License (`LICENSE`). The
MIT License does not automatically apply to downloadable dictionary packages.

The small vocabulary in `app/src/main/assets/seed_entries.json` was written for
prototype demonstration purposes. It is not extracted from Wiktionary or
FreeDict.

## `pt-pt` package, version `2026-09-02`

The Portuguese–Portuguese package is a derivative work based on content from
the Portuguese Wiktionary:

- authorship: Portuguese Wiktionary contributors;
- source project: https://pt.wiktionary.org/;
- structured extraction: Wiktextract/Kaikki.org;
- source file: https://kaikki.org/dictionary/downloads/pt/pt-extract.jsonl.gz;
- stated extraction date: September 2, 2026;
- source SHA-256:
  `9c333f933157afa21234d4ffd4a6b8fe6eb61b279f6a5debd1d43b612faa8d16`;
- redistribution license for the derivative package: CC BY-SA 4.0;
- license text: https://creativecommons.org/licenses/by-sa/4.0/.

LinguaWiki performed the following transformations:

1. selected records whose entry language is Portuguese;
2. normalized and reorganized fields into SQLite tables;
3. linked form-only records to their corresponding lemmas;
4. generated diacritic-insensitive keys and indexes for lemmas, prefixes, and
   forms;
5. retained definitions, examples, International Phonetic Alphabet (IPA),
   etymology, translations, and inflection paradigms when available in the
   extraction;
6. compressed the database deterministically for distribution.

Published artifact identity:

- file: `linguawiki-pt-pt-2026-09-02.sqlite.gz`;
- file size: 39,590,052 bytes;
- file SHA-256:
  `cb8dd745870c1c55f13b8af0d34cdb84a2297da6a8b27ed2a447d449cbbfbcd8`;
- uncompressed database size: 120,827,904 bytes;
- uncompressed database SHA-256:
  `bdb0550cbab742983dc5398f5f7f27d656b966d5fd30098ce2febbe704e40ac5`.

When redistributing a copy or adaptation of the package, retain this
attribution, identify any additional modifications, and comply with CC BY-SA
4.0. Audio files are not included in this package and may be subject to their
own licenses.
