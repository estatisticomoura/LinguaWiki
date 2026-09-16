# LinguaWiki Offline Packages

## Architecture

The APK contains the user interface, search engine, and a small demonstration
dataset. Each complete dictionary collection is distributed as a compressed,
read-only SQLite database and downloaded on demand. Users may install or remove
languages independently. Available device storage, rather than a fixed list in
the app, is the practical limit on the number of installed packages.

Each package is monolingual:

- headwords in the selected language;
- definitions from the corresponding Wiktionary edition;
- examples, International Phonetic Alphabet (IPA), and etymology when
  available;
- inflected forms, conjugations, and declensions;
- simple translations into other languages when recorded by that edition.

A second complete English collection is not duplicated inside each package.
This keeps download sizes predictable. Other editions remain available through
online lookup, and optional reference layers may be offered as separate
packages in the future.

## First measured package

Package `pt-pt`, version `2026-09-02`, has the following measured values:

| Field | Value |
|---|---:|
| `downloadBytes` | 39,590,052 |
| `installedBytes` | 120,827,904 |
| `entryCount` | 93,848 |
| `senseCount` | 151,369 |
| `translationCount` | 417,989 |
| `formCount` | 449,746 |

These values come from the actual distribution artifact; they are not
estimates derived from a raw dump.

## Off-device production

The phone does not process XML or JSON Lines dumps. Package generation takes
place before publication:

1. download the structured extraction and verify its checksum;
2. select records in the target language;
3. normalize fields and collapse form-only pages;
4. create an indexed SQLite database;
5. run `PRAGMA integrity_check` and regression searches;
6. compress the database;
7. generate the manifest with sizes, record counts, URL, and SHA-256;
8. publish the archive and manifest in a versioned GitHub Release.

The current builder is `tools/build_dictionary_pack.py`.

## Database format

Schema version 1 contains:

- `meta`: schema version, package, Wiktionary edition, language, and data
  version;
- `entries`: lemma, normalized form, diacritic-insensitive form, part of
  speech, IPA, etymology, and inflection type;
- `senses`: definition and a JSON list of examples;
- `translations`: language and term linked to a sense;
- `forms`: surface form, search keys, and morphological label.

Entry IDs exposed to the app use the format `pack/<packId>/<stableId>`.
Favorites and history also store a snapshot of essential metadata, so they do
not disappear when a package is removed.

## Safe installation

Before confirmation, the app displays:

- download size;
- final installed size;
- maximum temporary-space requirement;
- currently available space.

Peak required storage is calculated as:

```text
downloadBytes + installedBytes + 16 MiB safety margin
```

The implemented installation sequence is:

1. resume a partial download with HTTP `Range` when the server supports it;
2. restrict the source and final redirect destination to HTTPS;
3. verify the gzip size and SHA-256;
4. decompress into a temporary file;
5. verify size, metadata, and `PRAGMA integrity_check`;
6. rename the previous version as a backup;
7. activate the new database with an atomic rename;
8. restore the backup if activation fails;
9. delete the gzip only after successful installation.

An interruption never replaces an installed dictionary with a partial file.

## Search order

Search results are ranked in this order:

1. exact lemma;
2. exact inflected form;
3. diacritic-insensitive lemma or form;
4. prefix;
5. bounded edit distance.

Results for inflected forms prominently show the relationship between the
searched surface form and its lemma. For substantially larger packages, the
approximate-matching stage should migrate to a trigram or SymSpell index rather
than scanning candidates by length.

## Pronunciation

Android text-to-speech is the default pronunciation path; no audio is bundled
with the dictionary package. A future version may download human recordings
from Wikimedia Commons on demand, with removable caching and per-file
authorship and license metadata.

## License and provenance

Complete metadata for the first package and a record of the transformations
performed are available in `NOTICE-DATA.md`. The source-code license and data
license remain separate.
