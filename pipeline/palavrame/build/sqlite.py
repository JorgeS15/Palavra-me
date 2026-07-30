"""Escrita do `dicionario.db`.

O esquema é o da secção 6.1 do plano, literalmente. A app trata este ficheiro
como só-leitura e substitui-o por inteiro quando há atualização — nunca o
migra —, e é por isso que a `utilizador.db` guarda lemas por texto e não por
id (plano 6.2): os ids daqui podem mudar de build para build.
"""

from __future__ import annotations

import hashlib
import json
import sqlite3
import time
from pathlib import Path
from typing import Iterable, Sequence

from .. import SCHEMA_VERSION
from ..schema import MergedEntry
from ..sources.base import SourceInfo

SCHEMA_SQL = """
CREATE TABLE lemmas (
    id              INTEGER PRIMARY KEY,
    lemma           TEXT NOT NULL,
    normalized      TEXT NOT NULL,   -- sem acentos, minúsculas
    syllables       TEXT,
    pos             TEXT,            -- classe gramatical
    frequency_rank  INTEGER          -- do Leipzig; ordena resultados ambíguos
);

CREATE TABLE senses (
    id              INTEGER PRIMARY KEY,
    lemma_id        INTEGER NOT NULL REFERENCES lemmas(id),
    ord             INTEGER NOT NULL,
    definition      TEXT NOT NULL,
    domains         TEXT,            -- JSON: ["Figurado"], ["Náutica"]
    source_id       INTEGER NOT NULL REFERENCES sources(id),
    modernized      INTEGER DEFAULT 0  -- 1 = fraseado adaptado por LLM
);

CREATE TABLE forms (               -- flexionada -> lema. O coração da pesquisa
    form            TEXT NOT NULL,
    normalized      TEXT NOT NULL,
    lemma_id        INTEGER NOT NULL REFERENCES lemmas(id),
    tag             TEXT             -- morfologia, se disponível
);

CREATE TABLE examples (
    id              INTEGER PRIMARY KEY,
    sense_id        INTEGER REFERENCES senses(id),
    lemma_id        INTEGER NOT NULL REFERENCES lemmas(id),
    sentence        TEXT NOT NULL,
    source_id       INTEGER NOT NULL REFERENCES sources(id),
    source_ref      TEXT,            -- id Tatoeba, URL, etc.
    variant         TEXT,            -- 'pt-PT' | 'pt-BR' | 'unknown'
    generated       INTEGER DEFAULT 0
);

CREATE TABLE synonyms (
    lemma_id        INTEGER NOT NULL REFERENCES lemmas(id),
    synonym_id      INTEGER NOT NULL REFERENCES lemmas(id),
    relation        TEXT             -- 'sinonimo'|'antonimo'|'hiperonimo'
);

CREATE TABLE sources (             -- alimenta o ecrã de atribuição
    id              INTEGER PRIMARY KEY,
    name            TEXT NOT NULL,
    url             TEXT,
    license         TEXT NOT NULL,
    license_url     TEXT,
    attribution     TEXT NOT NULL
);

CREATE TABLE meta (
    key TEXT PRIMARY KEY, value TEXT
);  -- versão da DB, data de build, checksum
"""

INDEX_SQL = """
CREATE INDEX idx_forms_norm     ON forms(normalized);
CREATE INDEX idx_forms_lemma    ON forms(lemma_id);
CREATE INDEX idx_lemmas_norm    ON lemmas(normalized);
CREATE INDEX idx_senses_lemma   ON senses(lemma_id, ord);
CREATE INDEX idx_examples_lemma ON examples(lemma_id);
CREATE INDEX idx_examples_sense ON examples(sense_id);
CREATE INDEX idx_synonyms_lemma ON synonyms(lemma_id);
"""

# `content=lemmas` mantém o índice externo à tabela: não duplica o texto, que
# em dezenas de milhares de lemas conta para o tamanho do APK (plano 9).
FTS_SQL = """
CREATE VIRTUAL TABLE lemmas_fts USING fts5(
    lemma, normalized, content=lemmas, content_rowid=id
);
"""


def build_database(
    path: Path,
    entries: Sequence[MergedEntry],
    infos: Iterable[SourceInfo],
    *,
    db_version: str,
    extra_meta: dict | None = None,
) -> dict:
    """Escreve a base de dados e devolve estatísticas para o relatório."""
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        path.unlink()

    conn = sqlite3.connect(path)
    try:
        conn.executescript(SCHEMA_SQL)
        source_ids = _write_sources(conn, infos)
        stats = _write_entries(conn, entries, source_ids)
        conn.executescript(INDEX_SQL)
        conn.executescript(FTS_SQL)
        conn.execute(
            "INSERT INTO lemmas_fts(lemmas_fts) VALUES ('rebuild')"
        )
        _write_meta(conn, db_version, stats, extra_meta or {})
        conn.commit()
        conn.execute("VACUUM")
        conn.commit()
    finally:
        conn.close()

    # O checksum cobre o ficheiro final, por isso grava-se fora dele: um
    # ficheiro não pode conter o hash de si próprio. Fica ao lado, e é o que a
    # app verifica depois de descarregar uma atualização.
    digest = _sha256(path)
    path.with_suffix(path.suffix + ".sha256").write_text(
        f"{digest}  {path.name}\n", encoding="utf-8"
    )
    stats["sha256"] = digest
    stats["bytes"] = path.stat().st_size
    return stats


def _write_sources(conn: sqlite3.Connection, infos: Iterable[SourceInfo]) -> dict[str, int]:
    ids: dict[str, int] = {}
    for i, info in enumerate(infos, start=1):
        conn.execute(
            "INSERT INTO sources (id, name, url, license, license_url, attribution)"
            " VALUES (?,?,?,?,?,?)",
            (i, info.name, info.url, info.license.name, info.license.url,
             info.license.attribution),
        )
        ids[info.slug] = i
    return ids


def _write_entries(
    conn: sqlite3.Connection,
    entries: Sequence[MergedEntry],
    source_ids: dict[str, int],
) -> dict:
    unknown_id = _ensure_unknown_source(conn, source_ids)

    lemma_ids: dict[str, int] = {}
    counts = {
        "lemmas": 0, "senses": 0, "forms": 0, "examples": 0,
        "generated_examples": 0, "synonyms": 0, "modernized_senses": 0,
    }

    for i, entry in enumerate(entries, start=1):
        conn.execute(
            "INSERT INTO lemmas (id, lemma, normalized, syllables, pos, frequency_rank)"
            " VALUES (?,?,?,?,?,?)",
            (i, entry.lemma, entry.normalized, entry.syllables, entry.pos,
             entry.frequency_rank),
        )
        lemma_ids[entry.normalized] = i
        counts["lemmas"] += 1

    sense_id = 0
    example_id = 0
    for entry in entries:
        lemma_id = lemma_ids[entry.normalized]

        sense_by_ord: dict[int, int] = {}
        for sense in entry.senses:
            sense_id += 1
            conn.execute(
                "INSERT INTO senses (id, lemma_id, ord, definition, domains,"
                " source_id, modernized) VALUES (?,?,?,?,?,?,?)",
                (sense_id, lemma_id, sense.ord, sense.definition,
                 json.dumps(sense.domains, ensure_ascii=False) if sense.domains else None,
                 source_ids.get(sense.source, unknown_id),
                 1 if sense.modernized else 0),
            )
            sense_by_ord[sense.ord] = sense_id
            counts["senses"] += 1
            if sense.modernized:
                counts["modernized_senses"] += 1

        seen_forms: set[str] = set()
        for form in entry.forms:
            if form.form in seen_forms:
                continue
            seen_forms.add(form.form)
            conn.execute(
                "INSERT INTO forms (form, normalized, lemma_id, tag) VALUES (?,?,?,?)",
                (form.form, form.normalized, lemma_id, form.tag),
            )
            counts["forms"] += 1
        # O próprio lema tem de estar em `forms`: a app pesquisa só ali.
        if entry.lemma not in seen_forms:
            conn.execute(
                "INSERT INTO forms (form, normalized, lemma_id, tag) VALUES (?,?,?,?)",
                (entry.lemma, entry.normalized, lemma_id, "lema"),
            )
            counts["forms"] += 1

        for example in entry.examples:
            example_id += 1
            conn.execute(
                "INSERT INTO examples (id, sense_id, lemma_id, sentence, source_id,"
                " source_ref, variant, generated) VALUES (?,?,?,?,?,?,?,?)",
                (example_id, sense_by_ord.get(example.sense_ord or -1), lemma_id,
                 example.sentence, source_ids.get(example.source, unknown_id),
                 example.source_ref, example.variant,
                 1 if example.generated else 0),
            )
            counts["examples"] += 1
            if example.generated:
                counts["generated_examples"] += 1

    # As relações só se escrevem depois de todos os lemas terem id — e só
    # quando o alvo também está no dicionário.
    for entry in entries:
        lemma_id = lemma_ids[entry.normalized]
        for relation in entry.relations:
            from ..text import normalize

            target_id = lemma_ids.get(normalize(relation.target))
            if target_id is None or target_id == lemma_id:
                continue
            conn.execute(
                "INSERT INTO synonyms (lemma_id, synonym_id, relation) VALUES (?,?,?)",
                (lemma_id, target_id, relation.relation),
            )
            counts["synonyms"] += 1

    counts["senses_without_example"] = _senses_without_example(conn)
    counts["lemmas_without_sense"] = conn.execute(
        "SELECT COUNT(*) FROM lemmas l WHERE NOT EXISTS"
        " (SELECT 1 FROM senses s WHERE s.lemma_id = l.id)"
    ).fetchone()[0]
    return counts


def _ensure_unknown_source(conn: sqlite3.Connection, source_ids: dict[str, int]) -> int:
    """Rede de segurança: nenhuma linha fica sem `source_id`.

    Se isto for usado, é bug — mas é melhor uma linha atribuída a "fonte não
    declarada" do que uma violação de chave estrangeira a meio de uma build de
    horas. O `validate` deteta e falha.
    """
    next_id = max(source_ids.values(), default=0) + 1
    conn.execute(
        "INSERT INTO sources (id, name, url, license, license_url, attribution)"
        " VALUES (?,?,?,?,?,?)",
        (next_id, "Fonte não declarada", None, "DESCONHECIDA", None,
         "Erro de pipeline: conteúdo sem fonte registada."),
    )
    return next_id


def _senses_without_example(conn: sqlite3.Connection) -> int:
    return conn.execute(
        "SELECT COUNT(*) FROM senses s WHERE NOT EXISTS"
        " (SELECT 1 FROM examples e WHERE e.sense_id = s.id)"
    ).fetchone()[0]


def _write_meta(conn: sqlite3.Connection, db_version: str, stats: dict, extra: dict) -> None:
    meta = {
        "db_version": db_version,
        "schema_version": str(SCHEMA_VERSION),
        "built_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        **{f"count_{k}": str(v) for k, v in stats.items()},
        **{k: str(v) for k, v in extra.items()},
    }
    conn.executemany(
        "INSERT OR REPLACE INTO meta (key, value) VALUES (?,?)", sorted(meta.items())
    )


def readonly_uri(path: Path) -> str:
    """URI de só-leitura para um caminho do sistema de ficheiros.

    Tem de passar por `as_uri()` e não por interpolação: o SQLite trata a
    barra invertida como parte do nome do ficheiro, não como separador, por
    isso `file:C:\\Users\\...\\d.db` nunca abre no Windows. O `as_uri()`
    também trata dos espaços, que num caminho como `C:\\Users\\Jorge Silva`
    quebrariam a query string.
    """
    return f"{path.resolve().as_uri()}?mode=ro"


def open_readonly(path: Path) -> sqlite3.Connection:
    """Abre a DB como a app a abre: só leitura, sem escrita acidental."""
    conn = sqlite3.connect(readonly_uri(path), uri=True)
    conn.row_factory = sqlite3.Row
    return conn


def _sha256(path: Path, chunk: int = 1 << 20) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        while block := fh.read(chunk):
            h.update(block)
    return h.hexdigest()
