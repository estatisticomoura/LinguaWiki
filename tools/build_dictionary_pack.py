#!/usr/bin/env python3
"""Build a compact LinguaWiki SQLite package from Wiktextract JSONL.

Only entries in the selected headword language are retained. Records that only
describe an inflected form become small links to their lemma instead of full
duplicate entries. The resulting database is read-only on Android.
"""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import shutil
import sqlite3
import sys
import time
import unicodedata
from collections import defaultdict
from pathlib import Path
from typing import Any, Iterable, Iterator


SCHEMA_VERSION = 2
DEFAULT_SOURCE_URL = "https://kaikki.org/dictionary/downloads/pt/pt-extract.jsonl.gz"
DATA_LICENSE = "CC BY-SA 4.0"
DATA_LICENSE_URL = "https://creativecommons.org/licenses/by-sa/4.0/"
WIKTIONARY_URL = "https://pt.wiktionary.org/"
TRANSFORMATION_NOTICE = (
    "Records in the selected headword language were selected from the Wiktextract extraction; "
    "fields normalized and reorganized into SQLite; form-only records collapsed "
    "into lemma indexes; package compressed by LinguaWiki."
)

TAG_LABELS_PT = {
    "gerund": "gerúndio",
    "participle": "particípio",
    "infinitive": "infinitivo",
    "personal": "pessoal",
    "singular": "singular",
    "plural": "plural",
    "first-person": "1.ª pessoa",
    "second-person": "2.ª pessoa",
    "third-person": "3.ª pessoa",
    "indicative": "indicativo",
    "subjunctive": "subjuntivo",
    "conjunctive": "subjuntivo",
    "imperative": "imperativo",
    "present": "presente",
    "past": "pretérito",
    "continuative": "imperfeito",
    "imperfect": "imperfeito",
    "preterite": "pretérito",
    "pluperfect": "mais-que-perfeito",
    "future": "futuro",
    "conditional": "condicional",
    "affirmative": "afirmativo",
    "negative": "negativo",
    "masculine": "masculino",
    "feminine": "feminino",
    "common-gender": "dois gêneros",
    "comparative": "comparativo",
    "superlative": "superlativo",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--edition", default="pt")
    parser.add_argument("--language", default="pt")
    parser.add_argument("--definition-language", default="pt")
    parser.add_argument("--pack-id", default="pt-pt")
    parser.add_argument("--version", default="2026-09-02")
    parser.add_argument("--dump-date", default="")
    parser.add_argument("--display-name", default="Português")
    parser.add_argument("--wiktionary-url", default=WIKTIONARY_URL)
    parser.add_argument("--attribution", default="Portuguese Wiktionary contributors")
    parser.add_argument("--source-url", default=DEFAULT_SOURCE_URL)
    parser.add_argument("--source-sha256", default="")
    parser.add_argument("--gzip-output", type=Path)
    parser.add_argument("--manifest", type=Path)
    parser.add_argument("--download-url", default="")
    parser.add_argument("--report-every", type=int, default=50_000)
    parser.add_argument("--skip-vacuum", action="store_true")
    return parser.parse_args()


def canonical(value: str) -> str:
    return unicodedata.normalize("NFC", " ".join(value.strip().split())).casefold()


def folded(value: str) -> str:
    value = (
        canonical(value)
        .replace("ł", "l")
        .replace("đ", "d")
        .replace("ß", "ss")
        .replace("æ", "ae")
        .replace("œ", "oe")
    )
    return "".join(
        char
        for char in unicodedata.normalize("NFD", value)
        if unicodedata.category(char) != "Mn"
    )


def open_jsonl(path: Path) -> Iterable[str]:
    if path.suffix == ".gz":
        return gzip.open(path, "rt", encoding="utf-8")
    return path.open("r", encoding="utf-8")


def records(path: Path, language: str) -> Iterator[dict[str, Any]]:
    with open_jsonl(path) as source:
        for line_number, line in enumerate(source, 1):
            try:
                record = json.loads(line)
            except json.JSONDecodeError as error:
                raise ValueError(f"Invalid JSON on line {line_number}: {error}") from error
            if record.get("lang_code") == language:
                yield record


def is_form_sense(sense: dict[str, Any]) -> bool:
    return bool(sense.get("form_of")) or "form-of" in (sense.get("tags") or [])


def real_senses(record: dict[str, Any]) -> list[tuple[int, dict[str, Any]]]:
    record_tags = record.get("tags") or []
    pos_title = str(record.get("pos_title") or "")
    if "form-of" in record_tags or pos_title.casefold().startswith("forma "):
        return []
    return [
        (index, sense)
        for index, sense in enumerate(record.get("senses") or [], 1)
        if not is_form_sense(sense) and sense.get("glosses")
    ]


def compact_text(value: Any) -> str:
    return " ".join(str(value or "").strip().split())


def sense_definition(sense: dict[str, Any]) -> str:
    return "; ".join(
        text for text in (compact_text(item) for item in sense.get("glosses") or []) if text
    )


def examples_json(sense: dict[str, Any]) -> str | None:
    examples: list[str] = []
    seen: set[str] = set()
    for item in sense.get("examples") or []:
        text = compact_text(item.get("text"))
        if text and text not in seen:
            seen.add(text)
            examples.append(text)
    return json.dumps(examples, ensure_ascii=False, separators=(",", ":")) if examples else None


def pronunciations(record: dict[str, Any]) -> list[tuple[str, list[str]]]:
    output: list[tuple[str, list[str]]] = []
    seen: set[tuple[str, tuple[str, ...]]] = set()
    for sound in record.get("sounds") or []:
        ipa = compact_text(sound.get("ipa"))
        tags = {str(tag).casefold() for tag in (sound.get("tags") or [])}
        raw_tags = {str(tag).casefold() for tag in (sound.get("raw_tags") or [])}
        if ipa and not any("sampa" in tag for tag in tags | raw_tags):
            labels = [compact_text(x) for x in [*(sound.get("tags") or []), *(sound.get("raw_tags") or [])] if compact_text(x) and "sampa" not in compact_text(x).casefold()]
            key = (ipa, tuple(labels))
            if key not in seen:
                seen.add(key)
                output.append((ipa, labels))
    return output


def json_array(values: Iterable[Any]) -> str:
    return json.dumps(list(values), ensure_ascii=False, separators=(",", ":"))


def grammatical_features(record: dict[str, Any]) -> list[str]:
    ignored = {"canonical", "table-tags", "romanization", "form-of"}
    return list(dict.fromkeys(compact_text(x) for x in [*(record.get("tags") or []), *(record.get("raw_tags") or [])] if compact_text(x) and compact_text(x) not in ignored))


def normalized_words(value: str) -> set[str]:
    words: set[str] = set()
    for raw in folded(value).replace("/", " ").replace(";", " ").split():
        word = raw.strip(".,:()[]{}!?")
        if len(word) > 3 and word.endswith("s"):
            word = word[:-1]
        if word:
            words.add(word)
    return words


def match_translation_sense(translation: dict[str, Any], sense_ids: dict[int, int], definitions: dict[int, str]) -> tuple[int | None, int | None, str | None]:
    raw = translation.get("sense_index")
    try:
        source_index = int(raw) if raw is not None else 0
    except (TypeError, ValueError):
        source_index = 0
    if source_index in sense_ids:
        return sense_ids[source_index], list(sense_ids).index(source_index) + 1, None
    label = compact_text(translation.get("sense")) or None
    if not label:
        return None, None, None
    needle = folded(label)
    label_words = normalized_words(label)
    candidates: list[tuple[float, int, int]] = []
    for order, (source_index, definition) in enumerate(definitions.items(), 1):
        haystack = folded(definition)
        score = 1.0 if needle == haystack or needle in haystack or haystack in needle else len(label_words & normalized_words(definition)) / max(1, len(label_words))
        if score >= 0.6:
            candidates.append((score, order, source_index))
    if candidates:
        _, order, source_index = max(candidates)
        return sense_ids.get(source_index), order, label
    return None, None, label


def etymology(record: dict[str, Any]) -> str | None:
    texts = [compact_text(text).lstrip(":") for text in record.get("etymology_texts") or []]
    texts = [text for text in texts if text]
    return "\n\n".join(texts) if texts else None


def tag_label(tags: list[Any], raw_tags: list[Any] | None = None) -> str:
    output: list[str] = []
    for tag in [*(tags or []), *(raw_tags or [])]:
        value = compact_text(tag)
        if not value or value in {"canonical", "table-tags", "romanization"}:
            continue
        translated = TAG_LABELS_PT.get(value, value.replace("-", " "))
        if translated not in output:
            output.append(translated)
    return ", ".join(output)[:600]


def stable_id(edition: str, language: str, lemma: str, pos: str, homograph: str) -> str:
    source = "\x1f".join([edition, language, canonical(lemma), pos, homograph])
    return hashlib.sha1(source.encode("utf-8")).hexdigest()[:24]


def file_checksum(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def create_schema(db: sqlite3.Connection) -> None:
    db.executescript(
        """
        PRAGMA page_size=4096;
        PRAGMA journal_mode=OFF;
        PRAGMA synchronous=OFF;
        PRAGMA temp_store=MEMORY;

        CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL) WITHOUT ROWID;
        CREATE TABLE entries (
            id INTEGER PRIMARY KEY,
            stable_id TEXT NOT NULL UNIQUE,
            lemma TEXT NOT NULL,
            lemma_key TEXT NOT NULL,
            folded_key TEXT NOT NULL,
            part_of_speech TEXT NOT NULL,
            ipa TEXT,
            etymology TEXT,
            inflection_kind TEXT,
            grammatical_features_json TEXT NOT NULL DEFAULT '[]',
            content_score INTEGER NOT NULL DEFAULT 0
        );
        CREATE TABLE pronunciations (id INTEGER PRIMARY KEY, entry_id INTEGER NOT NULL, ipa TEXT NOT NULL, labels_json TEXT NOT NULL, UNIQUE(entry_id, ipa, labels_json));
        CREATE TABLE senses (
            id INTEGER PRIMARY KEY,
            entry_id INTEGER NOT NULL,
            sense_order INTEGER NOT NULL,
            definition TEXT NOT NULL,
            examples_json TEXT
        );
        CREATE TABLE translations (
            id INTEGER PRIMARY KEY,
            entry_id INTEGER NOT NULL,
            sense_id INTEGER,
            sense_order INTEGER,
            sense_label TEXT,
            language_code TEXT NOT NULL,
            language_name TEXT NOT NULL,
            term TEXT NOT NULL,
            tags_json TEXT NOT NULL
        );
        CREATE TABLE forms (
            id INTEGER PRIMARY KEY,
            entry_id INTEGER NOT NULL,
            surface TEXT NOT NULL,
            surface_key TEXT NOT NULL,
            folded_key TEXT NOT NULL,
            label TEXT NOT NULL,
            tags_json TEXT NOT NULL,
            raw_tags_json TEXT NOT NULL,
            UNIQUE(entry_id, surface_key, tags_json, raw_tags_json)
        );
        """
    )


def insert_meta(db: sqlite3.Connection, values: dict[str, str]) -> None:
    db.executemany("INSERT INTO meta(key, value) VALUES (?, ?)", values.items())


def insert_form(
    db: sqlite3.Connection,
    entry_id: int,
    surface: str,
    label: str,
    tags: list[Any] | None = None,
    raw_tags: list[Any] | None = None,
) -> bool:
    surface = compact_text(surface)
    if not surface:
        return False
    cursor = db.execute(
        """
        INSERT OR IGNORE INTO forms(entry_id, surface, surface_key, folded_key, label, tags_json, raw_tags_json)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        (entry_id, surface, canonical(surface), folded(surface), label[:600], json_array(tags or []), json_array(raw_tags or [])),
    )
    return cursor.rowcount > 0


def build(args: argparse.Namespace) -> dict[str, Any]:
    started = time.time()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    if args.output.exists():
        args.output.unlink()

    source_sha256 = args.source_sha256 or file_checksum(args.input)
    db = sqlite3.connect(args.output)
    create_schema(db)
    insert_meta(
        db,
        {
            "schema_version": str(SCHEMA_VERSION),
            "pack_id": args.pack_id,
            "version": args.version,
            "edition": args.edition,
            "language": args.language,
            "definition_language": args.definition_language,
            "source_url": args.source_url,
            "source_sha256": source_sha256,
            "license": DATA_LICENSE,
            "license_url": DATA_LICENSE_URL,
            "attribution": f"{args.attribution}; extracted with Wiktextract ({args.dump_date or args.version})",
            "attribution_url": args.wiktionary_url,
            "changes": TRANSFORMATION_NOTICE,
        },
    )
    db.commit()

    lemma_entries: dict[str, list[tuple[int, str]]] = defaultdict(list)
    occurrence: dict[tuple[str, str], int] = defaultdict(int)
    counts: defaultdict[str, int] = defaultdict(int)

    db.execute("BEGIN")
    for record_index, record in enumerate(records(args.input, args.language), 1):
        senses = real_senses(record)
        if not senses:
            counts["form_only_records"] += 1
            continue

        lemma = compact_text(record.get("word"))
        pos = compact_text(record.get("pos")) or "other"
        if not lemma:
            continue
        lemma_key = canonical(lemma)
        occurrence_key = (lemma_key, pos)
        occurrence[occurrence_key] += 1
        homograph = str(record.get("etymology_number") or occurrence[occurrence_key])
        external_id = stable_id(args.edition, args.language, lemma, pos, homograph)
        record_forms = record.get("forms") or []
        inflection_kind = None
        if record_forms:
            inflection_kind = "conjugation" if pos == "verb" else "declension"

        all_pronunciations = pronunciations(record)
        cursor = db.execute(
            """
            INSERT INTO entries(
                stable_id, lemma, lemma_key, folded_key, part_of_speech,
                ipa, etymology, inflection_kind, grammatical_features_json
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                external_id,
                lemma,
                lemma_key,
                folded(lemma),
                pos,
                all_pronunciations[0][0] if all_pronunciations else None,
                etymology(record),
                inflection_kind,
                json_array(grammatical_features(record)),
            ),
        )
        entry_id = int(cursor.lastrowid)
        lemma_entries[lemma_key].append((entry_id, pos))
        counts["entries"] += 1
        for ipa, labels in all_pronunciations:
            db.execute("INSERT OR IGNORE INTO pronunciations(entry_id, ipa, labels_json) VALUES (?, ?, ?)", (entry_id, ipa, json_array(labels)))

        sense_ids: dict[int, int] = {}
        sense_definitions: dict[int, str] = {}
        for output_order, (source_index, sense) in enumerate(senses, 1):
            definition = sense_definition(sense)
            if not definition:
                continue
            sense_cursor = db.execute(
                """
                INSERT INTO senses(entry_id, sense_order, definition, examples_json)
                VALUES (?, ?, ?, ?)
                """,
                (entry_id, output_order, definition, examples_json(sense)),
            )
            sense_ids[source_index] = int(sense_cursor.lastrowid)
            sense_definitions[source_index] = definition
            counts["senses"] += 1

        for translation in record.get("translations") or []:
            term = compact_text(translation.get("word"))
            language = compact_text(translation.get("lang_code"))
            language_name = compact_text(translation.get("lang")) or language
            if not term or not language:
                continue
            sense_id, sense_order, sense_label = match_translation_sense(translation, sense_ids, sense_definitions)
            db.execute(
                "INSERT INTO translations(entry_id, sense_id, sense_order, sense_label, language_code, language_name, term, tags_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                (entry_id, sense_id, sense_order, sense_label, language, language_name, term, json_array([*(translation.get("tags") or []), *(translation.get("raw_tags") or [])])),
            )
            counts["translations"] += 1

        for form in record_forms:
            surface = compact_text(form.get("form"))
            if insert_form(db, entry_id, surface, tag_label(form.get("tags") or [], form.get("raw_tags")), form.get("tags") or [], form.get("raw_tags") or []):
                counts["forms_from_lemmas"] += 1

        if record_index % args.report_every == 0:
            print(
                f"pass 1: {record_index:,} selected-language records; {counts['entries']:,} entries",
                file=sys.stderr,
            )
    db.commit()

    db.execute("BEGIN")
    for record_index, record in enumerate(records(args.input, args.language), 1):
        if real_senses(record):
            continue
        surface = compact_text(record.get("word"))
        if not surface:
            continue
        targets: list[str] = []
        for sense in record.get("senses") or []:
            for target in sense.get("form_of") or []:
                lemma = compact_text(target.get("word"))
                if lemma and lemma not in targets:
                    targets.append(lemma)
        if not targets:
            continue
        label = tag_label(record.get("tags") or [])
        if not label:
            sense_tags: list[Any] = []
            for sense in record.get("senses") or []:
                sense_tags.extend(sense.get("tags") or [])
            label = tag_label(sense_tags)
        source_pos = compact_text(record.get("pos"))
        for target in targets:
            candidates = lemma_entries.get(canonical(target), [])
            preferred = [candidate for candidate in candidates if candidate[1] == source_pos]
            for entry_id, _ in preferred or candidates:
                if insert_form(db, entry_id, surface, label, record.get("tags") or [], []):
                    counts["forms_from_records"] += 1
        if record_index % args.report_every == 0:
            print(
                f"pass 2: {record_index:,} selected-language records; {counts['forms_from_records']:,} form links",
                file=sys.stderr,
            )
    db.commit()

    print("creating indexes and compacting database", file=sys.stderr)
    db.executescript(
        """
        CREATE INDEX entries_lemma ON entries(lemma_key);
        CREATE INDEX entries_folded ON entries(folded_key);
        CREATE INDEX forms_surface ON forms(surface_key);
        CREATE INDEX forms_folded ON forms(folded_key);
        CREATE INDEX forms_entry ON forms(entry_id);
        CREATE INDEX senses_entry ON senses(entry_id, sense_order);
        CREATE INDEX translations_sense ON translations(sense_id);
        CREATE INDEX translations_entry ON translations(entry_id);
        CREATE INDEX pronunciations_entry ON pronunciations(entry_id);
        ANALYZE;
        """
    )
    db.execute("""UPDATE entries SET content_score =
        (SELECT COUNT(*) * 10000 FROM senses WHERE senses.entry_id=entries.id) +
        (SELECT COUNT(*) * 100 FROM translations WHERE translations.entry_id=entries.id) +
        (SELECT COUNT(*) FROM forms WHERE forms.entry_id=entries.id)""")
    db.commit()
    if not args.skip_vacuum:
        db.execute("VACUUM")
    for table in ("entries", "senses", "translations", "forms"):
        counts[table] = int(db.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0])
    assert db.execute("PRAGMA integrity_check").fetchone()[0] == "ok"
    db.close()

    sqlite_sha256 = file_checksum(args.output)
    gzip_path = args.gzip_output
    if gzip_path:
        gzip_path.parent.mkdir(parents=True, exist_ok=True)
        if gzip_path.exists():
            gzip_path.unlink()
        with args.output.open("rb") as source, gzip_path.open("wb") as compressed_file:
            with gzip.GzipFile(
                filename="",
                mode="wb",
                fileobj=compressed_file,
                compresslevel=9,
                mtime=0,
            ) as destination:
                shutil.copyfileobj(source, destination, length=1024 * 1024)

    manifest = {
        "schemaVersion": SCHEMA_VERSION,
        "packId": args.pack_id,
        "version": args.version,
        "headwordLanguage": args.language,
        "definitionLanguage": args.definition_language,
        "primaryEdition": args.edition,
        "displayName": args.display_name,
        "downloadBytes": gzip_path.stat().st_size if gzip_path else None,
        "installedBytes": args.output.stat().st_size,
        "entryCount": counts["entries"],
        "senseCount": counts["senses"],
        "translationCount": counts["translations"],
        "formCount": counts["forms"],
        "sha256": file_checksum(gzip_path) if gzip_path else sqlite_sha256,
        "sqliteSha256": sqlite_sha256,
        "url": args.download_url,
        "source": {
            "name": "Wiktionary",
            "edition": args.edition,
            "dumpDate": args.dump_date or args.version,
            "url": args.source_url,
            "sha256": source_sha256,
        },
        "licenses": [DATA_LICENSE],
        "licenseUrls": [DATA_LICENSE_URL],
        "attribution": f"{args.attribution}; extraction by Wiktextract/Kaikki.org",
        "attributionUrl": args.wiktionary_url,
        "changes": TRANSFORMATION_NOTICE,
    }
    if args.manifest:
        args.manifest.parent.mkdir(parents=True, exist_ok=True)
        args.manifest.write_text(
            json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
    return {
        **manifest,
        "elapsedSeconds": round(time.time() - started, 2),
        "formOnlyRecordsCollapsed": counts["form_only_records"],
        "formsFromLemmaTables": counts["forms_from_lemmas"],
        "formsFromFormRecords": counts["forms_from_records"],
    }


def main() -> None:
    report = build(parse_args())
    json.dump(report, sys.stdout, ensure_ascii=False, indent=2)
    print()


if __name__ == "__main__":
    main()
