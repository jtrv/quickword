#!/usr/bin/env python3
"""Build the QuickWord dictionary SQLite from a kaikki.org wiktextract JSONL.

Stdlib only. Streaming: constant memory over the ~3 GB English dump.

  uv run build_db.py --input data/kaikki-en.jsonl.gz --output data/quickword-en.db
  uv run build_db.py --input sample.jsonl --output ../app/src/main/assets/dictionary/quickword-en.db --dev

Schema (mirrors PLAN.md): words(word,pos,ipa) 1:1 with kaikki entries,
senses(gloss,example) ordered, synonyms(word_id,synonym) from entry- and
sense-level synonym lists. meta table carries source/license/counts.
"""

import argparse
import gzip
import json
import sqlite3
import sys
import time
from pathlib import Path

SCHEMA = """
CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE TABLE words(
  id INTEGER PRIMARY KEY,
  word TEXT NOT NULL,
  pos TEXT NOT NULL,
  ipa TEXT
);
CREATE TABLE senses(
  id INTEGER PRIMARY KEY,
  word_id INTEGER NOT NULL REFERENCES words(id),
  sense_no INTEGER NOT NULL,
  gloss TEXT NOT NULL,
  example TEXT
);
CREATE TABLE synonyms(
  word_id INTEGER NOT NULL REFERENCES words(id),
  synonym TEXT NOT NULL
);
"""

INDEXES = """
CREATE INDEX idx_words_word ON words(word COLLATE NOCASE);
CREATE INDEX idx_senses_word ON senses(word_id, sense_no);
CREATE INDEX idx_syn_word ON synonyms(word_id);
"""

# Senses carrying only these tag combinations add noise, not meaning.
SKIP_TAGS = {"obsolete", "misspelling"}
# Affix entries pollute prefix-search suggestions ("un-", "-ness"). Proper
# nouns (pos=name) are deliberately KEPT: PRODUCT.md principle 4 — offline is
# the default; the Wikipedia fallback is garnish, not a dependency.
SKIP_POS = {"prefix", "suffix", "infix", "interfix", "circumfix", "affix"}


def entry_rows(entry):
    """Yield (gloss, example) for usable senses of one kaikki entry."""
    for sense in entry.get("senses", ()):
        glosses = sense.get("glosses") or sense.get("raw_glosses")
        if not glosses:
            continue
        if SKIP_TAGS & set(sense.get("tags", ())):
            continue
        example = None
        for ex in sense.get("examples", ()):
            if ex.get("text"):
                example = ex["text"]
                break
        yield glosses[-1], example, [s["word"] for s in sense.get("synonyms", ()) if s.get("word")]


def first_ipa(entry):
    for sound in entry.get("sounds", ()):
        if sound.get("ipa"):
            return sound["ipa"]
    return None


def build(input_path: Path, output_path: Path, dev: bool) -> dict:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.unlink(missing_ok=True)
    db = sqlite3.connect(output_path)
    db.executescript(SCHEMA)
    opener = gzip.open if input_path.suffix == ".gz" else open

    n_words = n_senses = n_syn = n_lines = 0
    t0 = time.time()
    with opener(input_path, "rt", encoding="utf-8") as f:
        for line in f:
            n_lines += 1
            if n_lines % 200_000 == 0:
                print(f"  {n_lines:,} lines, {n_words:,} words, {n_senses:,} senses ({time.time()-t0:.0f}s)")
            try:
                e = json.loads(line)
            except json.JSONDecodeError:
                continue
            word, pos = e.get("word"), e.get("pos")
            if not word or not pos or len(word) > 64 or pos in SKIP_POS:
                continue
            rows = list(entry_rows(e))
            if not rows:
                continue
            cur = db.execute(
                "INSERT INTO words(word,pos,ipa) VALUES(?,?,?)", (word, pos, first_ipa(e))
            )
            wid = cur.lastrowid
            n_words += 1
            syns = {s["word"] for s in e.get("synonyms", ()) if s.get("word")}
            for i, (gloss, example, sense_syns) in enumerate(rows, 1):
                db.execute(
                    "INSERT INTO senses(word_id,sense_no,gloss,example) VALUES(?,?,?,?)",
                    (wid, i, gloss, example),
                )
                n_senses += 1
                syns.update(sense_syns)
            syns.discard(word)
            for s in sorted(syns):
                db.execute("INSERT INTO synonyms(word_id,synonym) VALUES(?,?)", (wid, s))
                n_syn += 1

    db.executescript(INDEXES)
    for k, v in {
        "source": "kaikki.org wiktextract (English Wiktionary)",
        "license": "CC BY-SA 4.0",
        "schema_version": "1",
        "words": str(n_words),
        "senses": str(n_senses),
        "synonyms": str(n_syn),
        "built_from": input_path.name,
    }.items():
        db.execute("INSERT INTO meta(key,value) VALUES(?,?)", (k, v))
    db.commit()
    db.execute("VACUUM")
    db.close()

    stats = {"words": n_words, "senses": n_senses, "synonyms": n_syn,
             "mb": output_path.stat().st_size / 1e6}
    print(f"OK {output_path}: {n_words:,} words, {n_senses:,} senses, "
          f"{n_syn:,} synonyms, {stats['mb']:.1f} MB")

    # Pinned expectations (PLAN.md refutation round 2, claim 5): the full dump
    # must land in the researched ballpark; drift means the filter broke.
    if not dev:
        assert n_words > 700_000, f"full build produced only {n_words:,} words"
        assert n_senses > 1_000_000, f"full build produced only {n_senses:,} senses"
        assert 60 < stats["mb"] < 400, f"size {stats['mb']:.0f} MB outside sane range"
    else:
        assert n_words >= 10 and n_senses >= n_words, "dev fixture too small to exercise the app"
    return stats


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", required=True, type=Path)
    ap.add_argument("--output", required=True, type=Path)
    ap.add_argument("--dev", action="store_true", help="dev fixture: relaxed size assertions")
    args = ap.parse_args()
    sys.exit(0 if build(args.input, args.output, args.dev) else 1)
