#!/usr/bin/env python3
"""Build the QuickWord offline Wikipedia corpus from a Kiwix mini ZIM.

  uv run --with libzim build_wiki.py --input data/wikipedia_en_top_mini.zim \
      --output data/quickword-wiki-top.db

Why a Kiwix ZIM rather than a Wikimedia dump: Kiwix has already solved the two
hard parts — which articles are worth keeping, and getting a clean lead section
out of wikitext. libzim is GPL and is used here as a build-time tool only, the
way a compiler is; nothing of it ships (PLAN.md refutation round 4).

The corpus is ~7x smaller than the ZIM it came from because everything a
notification does not need is dropped: HTML, infoboxes, CSS, images and the
Xapian search index. Lead paragraphs are stored per-row DEFLATE-compressed
against a shared preset dictionary, which buys whole-archive compression
ratios while keeping every row independently decodable.

The dictionary is stored IN the file. A corpus that cannot decode itself is
not a corpus.
"""

import argparse
import base64
import html
import re
import sqlite3
import sys
import time
import zlib
from pathlib import Path

SCHEMA = """
CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE TABLE article(
  id INTEGER PRIMARY KEY,
  title TEXT NOT NULL,
  intro BLOB NOT NULL
);
CREATE TABLE alias(name TEXT PRIMARY KEY, target INTEGER NOT NULL) WITHOUT ROWID;
"""

# Deliberately not UNIQUE: Wikipedia titles are case-sensitive past the first
# letter, so "pH" and "PH" are different articles that collide under NOCASE.
# The reader prefers an exact-case match and falls back to the first
# case-insensitive one, which is the same policy the dictionary lookup uses.
INDEXES = "CREATE INDEX idx_article_title ON article(title COLLATE NOCASE);"

TAG = re.compile(r"<[^>]+>")
PARA = re.compile(r"<p\b[^>]*>(.*?)</p>", re.S | re.I)
CITATION = re.compile(r"\[\d+\]")
# Kiwix renders a section redirect as a tiny meta-refresh page, and libzim does
# not report those as redirects. They are aliases, not articles.
REFRESH = re.compile(r"URL='\./([^'#]+)(?:#[^']*)?'")
STUB_BYTES = 2000

# The live path refuses disambiguation pages (WikipediaApi.parse), so the
# offline path must too — otherwise "Mercury" answers as though it were an
# article and the two paths disagree (refutation round 4).
DISAMBIG_TITLE = re.compile(r"\((disambiguation|disambig)\)\s*$", re.I)
DISAMBIG_LEAD = re.compile(
    r"\b(may|can) (also )?refer to\b|\bmay stand for\b|\bcommonly refers to\b", re.I
)

MIN_LEAD_CHARS = 80
DICT_SAMPLE_ARTICLES = 2000
DICT_MAX_BYTES = 32768  # DEFLATE only looks at the last 32 KiB of a dictionary


def lead_paragraph(body: str) -> str | None:
    """First paragraph with actual prose in it, as plain text."""
    for match in PARA.finditer(body):
        text = html.unescape(TAG.sub("", match.group(1)))
        text = CITATION.sub("", text)
        text = re.sub(r"\s+", " ", text).strip()
        if len(text) >= MIN_LEAD_CHARS:
            return text
    return None


def read_zim(path: Path):
    """(articles, aliases) — aliases cover both true and section redirects."""
    from libzim.reader import Archive

    archive = Archive(str(path))
    articles: list[tuple[str, str]] = []
    aliases: list[tuple[str, str]] = []
    skipped_disambig = 0

    for entry_id in range(archive.all_entry_count):
        entry = archive._get_entry_by_id(entry_id)
        if entry.is_redirect:
            try:
                aliases.append((entry.title, entry.get_redirect_entry().title))
            except RuntimeError:
                pass  # dangling redirect in the archive
            continue

        item = entry.get_item()
        if item.mimetype != "text/html":
            continue
        body = bytes(item.content).decode("utf-8", "replace")

        refresh = REFRESH.search(body)
        if refresh and len(body) < STUB_BYTES:
            aliases.append((entry.title, refresh.group(1).replace("_", " ")))
            continue

        if DISAMBIG_TITLE.search(entry.title):
            skipped_disambig += 1
            continue
        text = lead_paragraph(body)
        if text is None:
            continue
        if DISAMBIG_LEAD.search(text[:200]):
            skipped_disambig += 1
            continue
        articles.append((entry.title, text))

    print(f"  {len(articles):,} articles, {len(aliases):,} aliases, "
          f"{skipped_disambig:,} disambiguation pages skipped")
    return articles, aliases


def build_dictionary(articles: list[tuple[str, str]]) -> bytes:
    sample = " ".join(text for _, text in articles[:DICT_SAMPLE_ARTICLES])
    return sample.encode()[-DICT_MAX_BYTES:]


def compress(text: str, zdict: bytes) -> bytes:
    # Raw DEFLATE (wbits=-15): no zlib header, so the reader must apply the
    # dictionary before inflating — it is never told to. See WikiCorpus.kt.
    compressor = zlib.compressobj(9, zlib.DEFLATED, -15, 9, zlib.Z_DEFAULT_STRATEGY, zdict=zdict)
    return compressor.compress(text.encode()) + compressor.flush()


def write_db(out: Path, articles, aliases, zdict: bytes, source: str) -> dict:
    if out.exists():
        out.unlink()
    db = sqlite3.connect(out)
    db.executescript(SCHEMA)

    ids: dict[str, int] = {}
    for article_id, (title, text) in enumerate(articles, 1):
        ids[title] = article_id
        db.execute(
            "INSERT INTO article(id, title, intro) VALUES(?,?,?)",
            (article_id, title, compress(text, zdict)),
        )
    resolved = [(name, ids[target]) for name, target in aliases if target in ids and name not in ids]
    db.executemany("INSERT OR REPLACE INTO alias(name, target) VALUES(?,?)", resolved)
    db.executescript(INDEXES)

    for key, value in {
        "source": f"Kiwix mini ZIM ({source})",
        "license": "CC BY-SA 4.0",
        "license_url": "https://creativecommons.org/licenses/by-sa/4.0/",
        "schema_version": "1",
        "articles": str(len(articles)),
        "aliases": str(len(resolved)),
        # Base64 because meta.value is TEXT and this must survive any dump/restore.
        "zdict": base64.b64encode(zdict).decode(),
    }.items():
        db.execute("INSERT INTO meta(key, value) VALUES(?,?)", (key, value))

    db.commit()
    db.execute("VACUUM")
    db.close()
    return {"articles": len(articles), "aliases": len(resolved), "mb": out.stat().st_size / 1e6}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", type=Path, required=True, help="Kiwix mini .zim")
    ap.add_argument("--output", type=Path, required=True)
    ap.add_argument("--dev", action="store_true", help="dev fixture: relaxed assertions")
    args = ap.parse_args()

    started = time.time()
    print(f"reading {args.input.name}")
    articles, aliases = read_zim(args.input)
    if not articles:
        print("no articles extracted", file=sys.stderr)
        return 1

    zdict = build_dictionary(articles)
    stats = write_db(args.output, articles, aliases, zdict, args.input.name)
    print(f"  wrote {args.output} — {stats['mb']:.1f} MB in {time.time() - started:.0f}s")

    if args.dev:
        assert stats["articles"] >= 5, "dev fixture too small to exercise the app"
    else:
        assert stats["articles"] > 40_000, f"only {stats['articles']:,} articles"
        assert stats["aliases"] > stats["articles"], "aliases should outnumber articles"
        assert 20 < stats["mb"] < 120, f"size {stats['mb']:.0f} MB outside sane range"

    # A corpus that cannot decode itself is not a corpus: prove it round-trips
    # through the same path the app uses before declaring success.
    db = sqlite3.connect(args.output)
    stored = base64.b64decode(db.execute("SELECT value FROM meta WHERE key='zdict'").fetchone()[0])
    title, blob = db.execute("SELECT title, intro FROM article ORDER BY id LIMIT 1").fetchone()
    decompressor = zlib.decompressobj(-15, zdict=stored)
    text = (decompressor.decompress(blob) + decompressor.flush()).decode()
    db.close()
    assert len(text) >= MIN_LEAD_CHARS, "round-trip produced nothing"
    print(f"  round-trip ok: {title} — {text[:70]}...")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
