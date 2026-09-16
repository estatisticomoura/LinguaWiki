#!/usr/bin/env python3
"""Convert a Wiktextract JSONL sample into LinguaWiki's prototype JSON schema.

This development utility deliberately stays conservative. A production pack
should be compiled directly to indexed SQLite and retain complete provenance,
attribution, and license metadata.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import unicodedata
from collections.abc import Iterable
from pathlib import Path
from typing import Any, TextIO


DECLINED_PARTS = {"noun", "proper-noun", "adjective", "pronoun", "numeral"}
SKIPPED_FORM_TAGS = {"canonical", "romanization", "alternative", "alt-of"}


def parser() -> argparse.ArgumentParser:
    command = argparse.ArgumentParser(description=__doc__)
    command.add_argument("input", help="Wiktextract JSONL file, optionally '-' for stdin")
    command.add_argument("output", help="LinguaWiki JSON file, optionally '-' for stdout")
    command.add_argument(
        "--edition",
        required=True,
        help="Wiktionary edition that supplied the definitions, for example pl or en",
    )
    command.add_argument(
        "--language",
        required=True,
        help="language of the headwords to retain, for example pl",
    )
    command.add_argument(
        "--translations",
        nargs="*",
        default=[],
        metavar="CODE",
        help="translation language codes to retain; omit to retain every language",
    )
    command.add_argument("--limit", type=int, default=0, help="maximum entries; 0 means unlimited")
    return command


def compact(value: str) -> str:
    return " ".join(value.split())


def normalized(value: str) -> str:
    decomposed = unicodedata.normalize("NFKD", value.casefold())
    return "".join(character for character in decomposed if not unicodedata.combining(character))


def slug(value: str) -> str:
    value = normalized(value)
    value = re.sub(r"[^a-z0-9]+", "-", value).strip("-")
    return value[:45] or "entry"


def stable_id(item: dict[str, Any], edition: str) -> str:
    language = str(item.get("lang_code", "und"))
    word = str(item.get("word", ""))
    part = str(item.get("pos", "other"))
    etymology = str(item.get("etymology_number", ""))
    fingerprint = hashlib.sha1(
        f"{language}\0{word}\0{part}\0{etymology}".encode("utf-8"),
        usedforsecurity=False,
    ).hexdigest()[:10]
    return f"{edition}-{language}-{slug(word)}-{slug(part)}-{fingerprint}"


def first_ipa(sounds: Iterable[dict[str, Any]]) -> str | None:
    for sound in sounds:
        value = sound.get("ipa")
        if isinstance(value, str) and value.strip():
            return compact(value)
    return None


def first_example(sense: dict[str, Any]) -> str | None:
    for example in sense.get("examples", []):
        if isinstance(example, dict):
            value = example.get("text")
        else:
            value = example
        if isinstance(value, str) and value.strip():
            return compact(value)
    return None


def definition_for(sense: dict[str, Any]) -> str | None:
    glosses = sense.get("glosses") or sense.get("raw_glosses") or []
    definitions = [compact(value) for value in glosses if isinstance(value, str) and value.strip()]
    return "; ".join(definitions) or None


def translation_language(item: dict[str, Any]) -> str | None:
    value = item.get("lang_code") or item.get("code")
    return compact(value) if isinstance(value, str) and value.strip() else None


def translations_for(
    source: Iterable[dict[str, Any]],
    allowed: set[str],
) -> list[dict[str, str]]:
    result: list[dict[str, str]] = []
    seen: set[tuple[str, str]] = set()
    for item in source:
        language = translation_language(item)
        term = item.get("word")
        if not language or not isinstance(term, str) or not term.strip():
            continue
        if allowed and language not in allowed:
            continue
        term = compact(term)
        key = (language, term.casefold())
        if key in seen:
            continue
        seen.add(key)
        result.append({"language": language, "term": term, "targetLemma": term})
    return result


def forms_for(source: Iterable[dict[str, Any]], lemma: str) -> list[dict[str, str]]:
    result: list[dict[str, str]] = []
    seen: set[tuple[str, str]] = set()
    for item in source:
        form = item.get("form")
        tags = [str(tag) for tag in item.get("tags", [])]
        if not isinstance(form, str) or not form.strip() or form == lemma:
            continue
        if set(tags) & SKIPPED_FORM_TAGS:
            continue
        label = ", ".join(tags) or "form"
        key = (form.casefold(), label)
        if key in seen:
            continue
        seen.add(key)
        result.append({"surface": compact(form), "label": label})
    return result


def inflection_kind(part: str) -> str | None:
    if part == "verb":
        return "conjugation"
    if part in DECLINED_PARTS:
        return "declension"
    return None


def convert(
    item: dict[str, Any],
    edition: str,
    allowed_translations: set[str],
) -> dict[str, Any] | None:
    word = item.get("word")
    language = item.get("lang_code")
    if not isinstance(word, str) or not word.strip() or not isinstance(language, str):
        return None

    senses: list[dict[str, Any]] = []
    for source_sense in item.get("senses", []):
        definition = definition_for(source_sense)
        if not definition:
            continue
        sense: dict[str, Any] = {"definition": definition}
        example = first_example(source_sense)
        if example:
            sense["example"] = example
        local_translations = translations_for(source_sense.get("translations", []), allowed_translations)
        if local_translations:
            sense["translations"] = local_translations
        senses.append(sense)

    if not senses:
        return None

    # Many Wiktextract releases place translations at entry level. Without a
    # reliable sense link, keep them on the first sense and require review.
    top_level = translations_for(item.get("translations", []), allowed_translations)
    if top_level:
        present = {
            (translation["language"], translation["term"].casefold())
            for translation in senses[0].get("translations", [])
        }
        senses[0].setdefault("translations", []).extend(
            translation
            for translation in top_level
            if (translation["language"], translation["term"].casefold()) not in present
        )

    part = str(item.get("pos") or "other")
    entry: dict[str, Any] = {
        "id": stable_id(item, edition),
        "edition": edition,
        "language": language,
        "lemma": compact(word),
        "partOfSpeech": part,
        "senses": senses,
        "forms": forms_for(item.get("forms", []), compact(word)),
    }
    ipa = first_ipa(item.get("sounds", []))
    if ipa:
        entry["ipa"] = ipa
    etymology = item.get("etymology_text")
    if isinstance(etymology, str) and etymology.strip():
        entry["etymology"] = compact(etymology)
    kind = inflection_kind(part)
    if kind:
        entry["inflectionKind"] = kind
    return entry


def open_input(name: str) -> tuple[TextIO, bool]:
    if name == "-":
        return sys.stdin, False
    return Path(name).open("r", encoding="utf-8"), True


def read_entries(
    stream: TextIO,
    edition: str,
    language: str,
    translations: set[str],
    limit: int,
) -> list[dict[str, Any]]:
    entries: list[dict[str, Any]] = []
    for line_number, line in enumerate(stream, start=1):
        if not line.strip():
            continue
        try:
            item = json.loads(line)
        except json.JSONDecodeError as error:
            raise ValueError(f"invalid JSON on line {line_number}: {error}") from error
        if item.get("lang_code") != language:
            continue
        converted = convert(item, edition, translations)
        if converted:
            entries.append(converted)
        if limit and len(entries) >= limit:
            break
    return entries


def write_output(name: str, payload: dict[str, Any]) -> None:
    if name == "-":
        json.dump(payload, sys.stdout, ensure_ascii=False, indent=2)
        sys.stdout.write("\n")
        return
    with Path(name).open("w", encoding="utf-8") as stream:
        json.dump(payload, stream, ensure_ascii=False, indent=2)
        stream.write("\n")


def main() -> int:
    arguments = parser().parse_args()
    if arguments.limit < 0:
        parser().error("--limit cannot be negative")
    allowed = set(arguments.translations)
    stream, should_close = open_input(arguments.input)
    try:
        entries = read_entries(
            stream,
            arguments.edition,
            arguments.language,
            allowed,
            arguments.limit,
        )
    finally:
        if should_close:
            stream.close()
    write_output(arguments.output, {"entries": entries})
    print(f"converted {len(entries)} entries", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
