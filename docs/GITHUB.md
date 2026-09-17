# LinguaWiki GitHub Workflow

The public repository is:
https://github.com/estatisticomoura/LinguaWiki

English is the canonical language for repository documentation, metadata,
commit messages, issues, pull requests, release notes, and other public project
communication. Dictionary entries and localized application resources remain
in their respective target languages.

## 1. Main repository areas

- **Code** contains source code and documentation.
- **Actions** runs automated builds and tests after each update.
- **Releases** distributes large files such as APKs and dictionary packages.

The dictionary package must not be committed to Git history. Attach
it to a Release instead.

## 2. Repository About metadata

Keep the public **About** panel in English. Use this description:

> Offline-first Android dictionary with downloadable monolingual Wiktionary
> packages, inflection-aware search, and optional online lookup.

Use these topics:

- `android`
- `dictionary`
- `wiktionary`
- `offline-first`
- `linguistics`
- `kotlin`
- `jetpack-compose`

To update the panel, open the repository, select the gear icon beside
**About**, edit the description and topics, and select **Save changes**.

## 3. Check an update

1. Open the repository.
2. Select **Actions**.
3. Open the latest **Android CI** run.
4. Wait until every step is green.
5. To download the automatically built APK, open the run and download the
   **LinguaWiki-debug-apk** artifact near the bottom of the page.

GitHub Actions artifacts are temporary. Use a Release for a stable public
download.

## 4. Create Release `v0.5.0-prototype`

1. Open **Releases** on the repository page.
2. Select **Draft a new release**.
3. Under **Choose a tag**, enter `v0.5.0-prototype`.
4. Select **Create new tag: v0.5.0-prototype on publish**.
5. Confirm that the target branch is `main`.
6. Set the release title to `LinguaWiki 0.5.0 prototype`.
7. Paste the English release notes provided below.
8. Select **Set as a pre-release** because this build is still experimental.
9. Attach these four files without changing their names:

   - `LinguaWiki-prototype-0.5.0-debug.apk`
   - `linguawiki-pt-pt-2026-09-17.sqlite.gz`
   - `pt-pt-2026-09-17.json`
   - `NOTICE-DATA.md`

10. Wait for every upload to finish.
11. Select **Publish release**.

The tag and dictionary filename are part of the URL bundled with the APK. A
different tag or filename will break in-app package downloads.

### Release notes for `v0.5.0-prototype`

```markdown
Second LinguaWiki prototype with a rebuilt Portuguese monolingual package and
improved offline and online lookup.

## Highlights

- Portuguese–Portuguese offline dictionary downloaded on demand
- translations grouped by sense, with full language names and all source terms
- all available IPA records and structured inflection metadata
- conjugation groups with explicit personal pronouns
- grouped homographs ordered by entry content
- online suggestions collapse after selection and remain closed when editions change
- online Wiktionary audio controls are enabled
- English Wiktionary links for Polish open the modern Polish section
- the home search screen does not expose recent queries
- search by lemma or inflected form
- ambiguous-form support: `fui` resolves to both `ir` and `ser`
- diacritic-insensitive matching: `coracoes` finds `coração`
- definitions, examples, etymology, IPA, translations, and inflection data
- resumable download with SHA-256 and SQLite integrity verification
- atomic package installation and independent removal
- configurable font size, line spacing, color palette, and high contrast

## Portuguese package

- 93,848 entries
- 151,369 senses
- 417,989 translations
- 852,150 indexed forms
- 60,755,090-byte compressed download
- 268,677,120 bytes installed

This is an experimental build signed with a development key. Dictionary data
is derived from the Portuguese Wiktionary and distributed under CC BY-SA 4.0.
See `NOTICE-DATA.md` for source details, attribution, checksums, and declared
transformations.
```

## 5. Verify the published download

1. Open the Release in a phone browser.
2. Download and install the APK.
3. In the app, open **Settings → Offline dictionaries**. The label may appear
   translated according to the device language.
4. Download **Portuguese**.
5. If the app reports an invalid file, verify that the asset name is unchanged
   and that it was attached to the Release with the correct tag.

## 6. Make a small edit on GitHub

For a documentation-only edit:

1. open the file under **Code**;
2. select the pencil icon, **Edit this file**;
3. make the change in English;
4. select **Commit changes**;
5. write a short English commit message;
6. use a new branch and pull request for code changes. A direct commit to
   `main` is acceptable for a minor documentation correction.

Before merging code changes, confirm that **Android CI** is green.

## 7. Publish future versions

Every package must use an immutable URL containing its data version and Release
tag. When updating data from Wiktionary:

1. generate a new package and manifest;
2. run tests and verify checksums;
3. update the app catalog;
4. increment `versionCode` and `versionName`;
5. create a new tag and Release with English release notes;
6. never replace an older version's asset silently.
