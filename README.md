# LinguaWiki — offline dictionaries and online Wiktionary

LinguaWiki is an Android dictionary app with no account requirement, ads,
telemetry, or tracking. Its interface follows the device language and is
has complete English, Italian, and Portuguese resources; its initial search
copy is also localized for the 17 other Kaikki source languages supported in
this phase.

Version `0.5.0-prototype` introduces the first complete downloadable
monolingual package: Portuguese words defined in Portuguese. Dictionary data is
downloaded on demand, so the APK remains small and each language can be
installed or removed independently.

English is the canonical language for project documentation, repository
metadata, release notes, and public project communication. Dictionary content
and localized app strings remain in their respective languages.

## Current features

- incremental offline search by lemma and inflected form;
- diacritic-insensitive and approximate matching;
- preservation of ambiguous forms (`fui` resolves to both `ir` and `ser`);
- definitions, examples, International Phonetic Alphabet (IPA), etymology, and
  translations grouped by sense with full language names and all source terms;
- structured conjugation and declension views with explicit pronouns;
- pronunciation through the Android text-to-speech engine;
- local favorites and history, including entries from downloaded packages;
- resumable downloads, SHA-256 verification, SQLite validation, and atomic
  installation;
- independent package removal without deleting favorites or history;
- online lookup across 173 active Wiktionary editions;
- online suggestions using prefix search, approximate search, and diacritic
  variants;
- system, light, dark, and high-contrast themes;
- green, blue, and amber color palettes;
- five font sizes and three line-spacing options.

## Portuguese–Portuguese package

The first package was built from the Portuguese Wiktionary extraction
published by Wiktextract/Kaikki.org on September 17, 2026.

| Metric | Value |
|---|---:|
| Compressed download | 60,755,090 bytes (58.0 MiB) |
| Installed database | 268,677,120 bytes (256.2 MiB) |
| Entries | 93,848 |
| Senses | 151,369 |
| Translations | 417,989 |
| Indexed forms | 852,150 |

The package contains Portuguese headwords with definitions in Portuguese.
Simple translations into other languages, examples, IPA, etymology, and
inflection data are retained whenever they are available in the source.
Form-only records point to their lemmas instead of duplicating complete entry
pages.

The package is not stored in Git because of its size. It must be attached to
Release `v0.5.0-prototype` as
`linguawiki-pt-pt-2026-09-17.sqlite.gz`. The catalog bundled with the APK points
to that immutable release URL.

## Install and test

1. Download `LinguaWiki-prototype-0.5.0-debug.apk` from Release
   `v0.5.0-prototype`.
2. Open the APK on Android and allow installation from that source if Android
   asks for permission.
3. In LinguaWiki, open **Settings → Offline dictionaries**. Labels are
   localized according to the device language.
4. Review the download size, installed size, temporary-space requirement, and
   currently available storage.
5. Download **Portuguese**.
6. Return to search, select Portuguese, and try the following queries:

| Query | Expected result |
|---|---|
| `poder` | entries and senses for the lemma `poder` |
| `pudesse` | the searched form highlighted and linked to `poder` |
| `fui` | both `ir` and `ser` |
| `coracoes` | `coração` or `corações`, despite the missing diacritic |
| `fazer` | definitions, examples, etymology, translations, and conjugation |

The prototype APK is signed with a development key. It is intended for direct
testing and is not signed for final Google Play distribution.

## Online lookup

Under **Settings → Online editions**, choose which Wiktionary editions may
appear in online search. Only the active edition receives the current query for
suggestion generation. The client retries once after a transient failure,
keeps a small in-memory cache, and exposes an explicit retry action.

Entry pages are displayed directly from the official website of the selected
edition. JavaScript is enabled so Wiktionary audio controls work; DOM storage
remains disabled and top-level navigation is restricted to Wiktionary hosts.

## Build the Android app

Requirements: JDK 17 and the Android SDK with platform 35.

```bash
./gradlew testDebugUnitTest assembleDebug lintDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Rebuild the dictionary package

The package builder reads a compressed JSON Lines extraction and produces the
SQLite database, gzip archive, and manifest:

```bash
python3 tools/build_dictionary_pack.py \
  pt-extract.jsonl.gz \
  linguawiki-pt-pt-2026-09-17.sqlite \
  --gzip-output linguawiki-pt-pt-2026-09-17.sqlite.gz \
  --manifest pt-pt-2026-09-17.json \
  --pack-id pt-pt \
  --version 2026-09-17 \
  --language pt \
  --edition pt \
  --download-url https://github.com/estatisticomoura/LinguaWiki/releases/download/v0.5.0-prototype/linguawiki-pt-pt-2026-09-17.sqlite.gz \
  --source-url https://kaikki.org/dictionary/downloads/pt/pt-extract.jsonl.gz \
  --source-sha256 c51c584c3b39e6c33165848b69cd7ac135cd4fa24c0e1d4d3cfa1dab93efb15e
```

After generation, verify `PRAGMA integrity_check`, the manifest totals, and the
regression queries before publishing. See
[`docs/OFFLINE-PACKS.md`](docs/OFFLINE-PACKS.md) for the complete technical
design.

## Repository and release workflow

Commit, GitHub Actions, and Release procedures are documented step by step in
[`docs/GITHUB.md`](docs/GITHUB.md).

## Licenses and attribution

- application source code: MIT (`LICENSE`);
- embedded demonstration entries: original prototype content;
- Portuguese dictionary package: CC BY-SA 4.0, with attribution to Portuguese
  Wiktionary contributors and a record of the transformations performed.

Read [`NOTICE-DATA.md`](NOTICE-DATA.md) before redistributing dictionary data.
