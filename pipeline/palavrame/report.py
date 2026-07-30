"""Relatórios do pipeline.

Dois destinatários diferentes:

* `build_report` — para quem faz a build: cobertura por fonte, buracos,
  conflitos por resolver (plano 5.4).
* `review_sheet` — para a revisão manual da F0. É o artefacto que decide se o
  projeto continua (plano 7, F0.3): 100 entradas em Markdown, legíveis de
  seguida, com espaço para marcar cada uma como útil ou não.
"""

from __future__ import annotations

import json
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

from .merge import ConflictReport
from .schema import MergedEntry
from .sources.base import SourceInfo


@dataclass
class BuildReport:
    db_version: str
    stats: dict
    coverage: dict
    conflicts: dict
    sources: list[dict]

    def to_json(self) -> str:
        return json.dumps(
            {
                "db_version": self.db_version,
                "stats": self.stats,
                "coverage": self.coverage,
                "conflicts": self.conflicts,
                "sources": self.sources,
            },
            ensure_ascii=False,
            indent=2,
        )

    def to_markdown(self) -> str:
        lines = [
            f"# Relatório de build — `dicionario-{self.db_version}.db`",
            "",
            "## Números",
            "",
            "| Métrica | Valor |",
            "|---|---:|",
        ]
        for key in (
            "lemmas", "senses", "forms", "examples", "generated_examples",
            "synonyms", "modernized_senses", "lemmas_without_sense",
            "senses_without_example", "bytes",
        ):
            if key in self.stats:
                lines.append(f"| {key} | {self.stats[key]:,} |".replace(",", " "))
        if "sha256" in self.stats:
            lines += ["", f"`sha256` — `{self.stats['sha256']}`"]

        lines += ["", "## Cobertura por fonte", "",
                  "| Fonte | Lemas tocados | Aceções | Exemplos |", "|---|---:|---:|---:|"]
        for slug, data in sorted(self.coverage.items()):
            lines.append(
                f"| {slug} | {data.get('lemmas', 0)} | {data.get('senses', 0)} "
                f"| {data.get('examples', 0)} |"
            )

        lines += ["", "## Conflitos por resolver", ""]
        total = self.conflicts.get("total", 0)
        if total == 0:
            lines.append("Nenhum.")
        else:
            lines.append(f"Total: **{total}**")
            lines.append("")
            for key in ("pos_disagreements", "rejected_lemmas",
                        "duplicate_senses", "orphan_examples"):
                items = self.conflicts.get(key) or []
                if not items:
                    continue
                lines += [f"### {key} ({len(items)})", ""]
                for item in items[:20]:
                    lines.append(f"- `{json.dumps(item, ensure_ascii=False)}`")
                if len(items) > 20:
                    lines.append(f"- … mais {len(items) - 20}")
                lines.append("")

        lines += ["", "## Fontes e licenças", "",
                  "| Fonte | Licença | Redistribuível | Verificada |",
                  "|---|---|---|---|"]
        for source in self.sources:
            lines.append(
                f"| {source['name']} | {source['license']} | "
                f"{source['redistributable']} | {source['verified']} |"
            )
        return "\n".join(lines) + "\n"


def build_report(
    db_version: str,
    entries: Sequence[MergedEntry],
    stats: dict,
    conflicts: ConflictReport,
    infos: Sequence[SourceInfo],
) -> BuildReport:
    coverage: dict[str, dict[str, int]] = {}

    lemma_hits: Counter[str] = Counter()
    for entry in entries:
        for slug in entry.contributors:
            lemma_hits[slug] += 1
    sense_hits = Counter(s.source for e in entries for s in e.senses)
    example_hits = Counter(x.source for e in entries for x in e.examples)

    for slug in set(lemma_hits) | set(sense_hits) | set(example_hits):
        coverage[slug] = {
            "lemmas": lemma_hits[slug],
            "senses": sense_hits[slug],
            "examples": example_hits[slug],
        }

    return BuildReport(
        db_version=db_version,
        stats=stats,
        coverage=coverage,
        conflicts=conflicts.as_dict(),
        sources=[
            {
                "slug": info.slug,
                "name": info.name,
                "license": info.license.name,
                "redistributable": _tri(info.license.redistributable),
                "verified": "sim" if info.license.verified else "**não**",
            }
            for info in infos
        ],
    )


def _tri(value: bool | None) -> str:
    return {True: "sim", False: "**não**"}.get(value, "**por verificar**")


# --- folha de revisão da F0 ------------------------------------------------

REVIEW_HEADER = """# F0 — folha de revisão

Este é o artefacto que decide se o projeto continua (plano, secção 7, F0).

Lê as {n} entradas abaixo como se estivesses a ler um livro e a precisar
delas. Para cada uma, a pergunta é só uma:

> **Isto ajudou-me a perceber a palavra?**

Marca cada entrada substituindo `[ ]` por `[x]` (útil) ou `[-]` (inútil).
No fim, conta. O plano não fixa um número mínimo — fixa um juízo: se a
qualidade for inaceitável, para aqui e reconsidera antes de escrever
qualquer linha de Android.

Legenda: `⚙` = fraseado modernizado por LLM · `🤖` = exemplo gerado por LLM

---

"""


def review_sheet(entries: Sequence[MergedEntry]) -> str:
    lines = [REVIEW_HEADER.format(n=len(entries))]
    for i, entry in enumerate(entries, start=1):
        lines.append(f"## {i}. {entry.lemma}")
        lines.append("")
        bits = [f"*{entry.pos}*"]
        if entry.syllables:
            bits.append(f"`{entry.syllables}`")
        if entry.frequency_rank:
            bits.append(f"freq. #{entry.frequency_rank}")
        bits.append(f"fontes: {', '.join(entry.contributors) or '—'}")
        n = len(entry.forms)
        bits.append(f"{n} forma" if n == 1 else f"{n} formas")
        lines.append(" · ".join(bits))
        lines.append("")

        if not entry.senses:
            lines.append("> **Sem definição em nenhuma fonte.**")
            lines.append("")
        for sense in entry.senses:
            mark = " ⚙" if sense.modernized else ""
            domains = f" *({', '.join(sense.domains)})*" if sense.domains else ""
            lines.append(
                f"{sense.ord}.{domains} {sense.definition}{mark}  "
                f"<sup>{sense.source}</sup>"
            )
            for example in entry.examples:
                if example.sense_ord == sense.ord:
                    lines.append(f"   - {_example_line(example)}")
            lines.append("")

        loose = [e for e in entry.examples if e.sense_ord is None]
        if loose:
            lines.append("Exemplos sem aceção atribuída:")
            lines += [f"- {_example_line(e)}" for e in loose]
            lines.append("")

        synonyms = [r.target for r in entry.relations if r.relation == "sinonimo"]
        if synonyms:
            lines.append(f"Sinónimos: {', '.join(synonyms[:12])}")
            lines.append("")

        lines.append(f"`[ ]` útil para leitura — **{entry.lemma}**")
        lines.append("")
        lines.append("---")
        lines.append("")
    return "\n".join(lines)


def _example_line(example) -> str:
    mark = "🤖 " if example.generated else ""
    ref = f" <sup>{example.source}"
    if example.source_ref:
        ref += f":{example.source_ref}"
    if example.variant and example.variant != "unknown":
        ref += f" {example.variant}"
    ref += "</sup>"
    return f"{mark}{example.sentence}{ref}"


def write_reports(out_dir: Path, report: BuildReport, entries: Sequence[MergedEntry]) -> list[Path]:
    out_dir.mkdir(parents=True, exist_ok=True)
    written = []
    for name, content in (
        (f"relatorio-{report.db_version}.md", report.to_markdown()),
        (f"relatorio-{report.db_version}.json", report.to_json()),
        (f"revisao-{report.db_version}.md", review_sheet(entries)),
    ):
        path = out_dir / name
        path.write_text(content, encoding="utf-8")
        written.append(path)
    return written
