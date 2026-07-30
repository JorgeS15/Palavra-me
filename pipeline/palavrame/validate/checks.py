"""Verificações antes de publicar a DB.

Duas famílias:

* **integridade** — a DB é utilizável pela app? Chaves batem certo, a pesquisa
  encontra o que devia, nada ficou sem fonte.
* **licenciamento** — esta DB pode ser distribuída? Aqui a verificação é
  deliberadamente severa: uma fonte por verificar bloqueia a distribuição
  (plano 8, "a parte que não se pode fazer no fim"). Uma DB só para uso local
  passa; uma DB para publicar, não.

`distribution=True` é o modo que se usa antes de fazer upload seja do que for.
"""

from __future__ import annotations

import sqlite3
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable

from ..sources.base import SourceInfo
from ..text import normalize

SEVERITY_ORDER = {"erro": 0, "aviso": 1, "info": 2}


@dataclass
class Check:
    name: str
    severity: str          # 'erro' | 'aviso' | 'info'
    message: str
    detail: str = ""

    @property
    def blocking(self) -> bool:
        return self.severity == "erro"


@dataclass
class ValidationReport:
    checks: list[Check] = field(default_factory=list)

    def add(self, name: str, severity: str, message: str, detail: str = "") -> None:
        self.checks.append(Check(name, severity, message, detail))

    @property
    def ok(self) -> bool:
        return not any(c.blocking for c in self.checks)

    @property
    def errors(self) -> list[Check]:
        return [c for c in self.checks if c.severity == "erro"]

    @property
    def warnings(self) -> list[Check]:
        return [c for c in self.checks if c.severity == "aviso"]

    def sorted_checks(self) -> list[Check]:
        return sorted(self.checks, key=lambda c: SEVERITY_ORDER.get(c.severity, 9))

    def render(self) -> str:
        icon = {"erro": "ERRO ", "aviso": "AVISO", "info": "info "}
        lines = []
        for check in self.sorted_checks():
            lines.append(f"  [{icon.get(check.severity, '?')}] {check.name}: {check.message}")
            if check.detail:
                lines.append(f"          {check.detail}")
        verdict = "APROVADA" if self.ok else "REPROVADA"
        lines.append(f"  -> {verdict}")
        return "\n".join(lines)


def validate_database(
    path: Path,
    infos: Iterable[SourceInfo] = (),
    *,
    distribution: bool = False,
    probes: Iterable[tuple[str, str]] = (),
) -> ValidationReport:
    """Valida uma DB construída.

    `probes` são pares (forma escrita, lema esperado) que testam a pesquisa de
    ponta a ponta, exatamente como a app a faz: normaliza, procura em `forms`,
    salta para `lemmas`. É a verificação que apanha uma tabela `forms` vazia
    ou mal ligada, que de outra forma só se descobria com a app na mão.
    """
    report = ValidationReport()
    if not path.exists():
        report.add("ficheiro", "erro", f"não existe: {path}")
        return report

    conn = sqlite3.connect(f"file:{path}?mode=ro", uri=True)
    conn.row_factory = sqlite3.Row
    try:
        _check_integrity(conn, report)
        _check_contents(conn, report)
        _check_search(conn, report, probes)
        _check_provenance(conn, report)
        _check_licenses(conn, report, infos, distribution)
    finally:
        conn.close()
    return report


def _check_integrity(conn: sqlite3.Connection, report: ValidationReport) -> None:
    result = conn.execute("PRAGMA integrity_check").fetchone()[0]
    if result != "ok":
        report.add("integrity_check", "erro", "SQLite reporta corrupção", result)

    orphans = conn.execute(
        "SELECT COUNT(*) FROM senses s LEFT JOIN lemmas l ON l.id = s.lemma_id"
        " WHERE l.id IS NULL"
    ).fetchone()[0]
    if orphans:
        report.add("senses.lemma_id", "erro", f"{orphans} aceções sem lema")

    orphans = conn.execute(
        "SELECT COUNT(*) FROM forms f LEFT JOIN lemmas l ON l.id = f.lemma_id"
        " WHERE l.id IS NULL"
    ).fetchone()[0]
    if orphans:
        report.add("forms.lemma_id", "erro", f"{orphans} formas sem lema")

    orphans = conn.execute(
        "SELECT COUNT(*) FROM examples e LEFT JOIN lemmas l ON l.id = e.lemma_id"
        " WHERE l.id IS NULL"
    ).fetchone()[0]
    if orphans:
        report.add("examples.lemma_id", "erro", f"{orphans} exemplos sem lema")


def _check_contents(conn: sqlite3.Connection, report: ValidationReport) -> None:
    lemmas = conn.execute("SELECT COUNT(*) FROM lemmas").fetchone()[0]
    if lemmas == 0:
        report.add("lemmas", "erro", "dicionário vazio")
        return
    report.add("lemmas", "info", f"{lemmas} lemas")

    without_sense = conn.execute(
        "SELECT COUNT(*) FROM lemmas l WHERE NOT EXISTS"
        " (SELECT 1 FROM senses s WHERE s.lemma_id = l.id)"
    ).fetchone()[0]
    if without_sense:
        pct = 100 * without_sense / lemmas
        severity = "aviso" if pct < 50 else "erro"
        report.add(
            "lemas sem aceção", severity,
            f"{without_sense} de {lemmas} ({pct:.0f}%) não têm definição nenhuma",
        )

    senses = conn.execute("SELECT COUNT(*) FROM senses").fetchone()[0]
    no_example = conn.execute(
        "SELECT COUNT(*) FROM senses s WHERE NOT EXISTS"
        " (SELECT 1 FROM examples e WHERE e.sense_id = s.id)"
    ).fetchone()[0]
    if senses:
        report.add(
            "aceções sem exemplo", "info",
            f"{no_example} de {senses} ({100 * no_example / senses:.0f}%)",
        )

    empty = conn.execute(
        "SELECT COUNT(*) FROM senses WHERE TRIM(definition) = ''"
    ).fetchone()[0]
    if empty:
        report.add("definições vazias", "erro", f"{empty} aceções com texto vazio")

    forms = conn.execute("SELECT COUNT(*) FROM forms").fetchone()[0]
    ratio = forms / lemmas if lemmas else 0
    if ratio < 1:
        report.add(
            "forms", "erro",
            f"{forms} formas para {lemmas} lemas — a tabela `forms` não está "
            "completa e a pesquisa por palavra flexionada vai falhar",
        )
    elif ratio < 2:
        report.add(
            "forms", "aviso",
            f"apenas {ratio:.1f} formas por lema; sem Hunspell a pesquisa a "
            "partir do texto do livro fica fraca",
        )
    else:
        report.add("forms", "info", f"{forms} formas ({ratio:.1f} por lema)")


def _check_search(
    conn: sqlite3.Connection,
    report: ValidationReport,
    probes: Iterable[tuple[str, str]],
) -> None:
    failures = []
    total = 0
    for written, expected in probes:
        total += 1
        rows = conn.execute(
            "SELECT l.lemma FROM forms f JOIN lemmas l ON l.id = f.lemma_id"
            " WHERE f.normalized = ?",
            (normalize(written),),
        ).fetchall()
        found = {row["lemma"] for row in rows}
        if expected not in found:
            failures.append(f"{written} -> esperado {expected}, obtido {sorted(found) or '∅'}")
    if total:
        if failures:
            report.add(
                "pesquisa por flexão", "erro",
                f"{len(failures)} de {total} sondas falharam",
                "; ".join(failures[:5]),
            )
        else:
            report.add("pesquisa por flexão", "info", f"{total}/{total} sondas passaram")


def _check_provenance(conn: sqlite3.Connection, report: ValidationReport) -> None:
    """Nada entra na DB sem fonte declarada (plano 10.8)."""
    unknown = conn.execute(
        "SELECT id FROM sources WHERE license = 'DESCONHECIDA'"
    ).fetchall()
    if unknown:
        ids = [row["id"] for row in unknown]
        placeholders = ",".join("?" * len(ids))
        senses = conn.execute(
            f"SELECT COUNT(*) FROM senses WHERE source_id IN ({placeholders})", ids
        ).fetchone()[0]
        examples = conn.execute(
            f"SELECT COUNT(*) FROM examples WHERE source_id IN ({placeholders})", ids
        ).fetchone()[0]
        if senses or examples:
            report.add(
                "proveniência", "erro",
                f"{senses} aceções e {examples} exemplos sem fonte declarada",
            )

    generated = conn.execute(
        "SELECT COUNT(*) FROM examples WHERE generated = 1"
    ).fetchone()[0]
    if generated:
        report.add(
            "conteúdo gerado", "info",
            f"{generated} exemplos gerados por LLM, marcados como tal",
        )
    mismatch = conn.execute(
        "SELECT COUNT(*) FROM examples e JOIN sources s ON s.id = e.source_id"
        " WHERE s.name LIKE '%AMALIA%' AND e.generated = 0"
    ).fetchone()[0]
    if mismatch:
        report.add(
            "conteúdo gerado", "erro",
            f"{mismatch} exemplos do AMALIA sem a marca `generated`",
        )


def _check_licenses(
    conn: sqlite3.Connection,
    report: ValidationReport,
    infos: Iterable[SourceInfo],
    distribution: bool,
) -> None:
    used = {
        row["name"]
        for row in conn.execute(
            "SELECT DISTINCT s.name FROM sources s WHERE EXISTS"
            " (SELECT 1 FROM senses x WHERE x.source_id = s.id)"
            " OR EXISTS (SELECT 1 FROM examples x WHERE x.source_id = s.id)"
        )
    }
    by_name = {info.name: info for info in infos}

    missing_attribution = [
        row["name"]
        for row in conn.execute("SELECT name, attribution FROM sources")
        if not (row["attribution"] or "").strip()
    ]
    if missing_attribution:
        report.add(
            "atribuição", "erro",
            "fontes sem texto de atribuição: " + ", ".join(missing_attribution),
        )

    unverified: list[str] = []
    non_redistributable: list[str] = []
    copyleft: list[str] = []

    for name in sorted(used):
        info = by_name.get(name)
        if info is None:
            unverified.append(f"{name} (sem SourceInfo registado)")
            continue
        if not info.license.verified:
            unverified.append(f"{name} ({info.license.name})")
        if info.license.redistributable is False:
            non_redistributable.append(name)
        if "BY-SA" in info.license.name.upper():
            copyleft.append(name)

    severity = "erro" if distribution else "aviso"
    if unverified:
        report.add(
            "licenças por verificar", severity,
            f"{len(unverified)} fontes com licença não confirmada",
            "; ".join(unverified) + ". Preencher docs/fontes.md e pôr "
            "License(verified=True) antes de distribuir.",
        )
    if non_redistributable:
        report.add(
            "licenças", "erro",
            "fontes não redistribuíveis presentes na DB: "
            + ", ".join(non_redistributable),
        )
    if copyleft:
        report.add(
            "copyleft", "info",
            "a DB contém conteúdo CC BY-SA (" + ", ".join(copyleft)
            + ") — tem de ser publicada sob CC BY-SA",
        )
