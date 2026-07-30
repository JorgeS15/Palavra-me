"""Fusão das fontes num único conjunto de entradas.

As regras são as da secção 5.2 do plano, e a primeira é a que mais importa:

* **A lista de lemas e a grafia vêm do VOC.** Se o VOC estiver disponível, é
  ele que decide o que é uma palavra. As outras fontes só podem preencher
  lemas que ele já reconheceu — o que resolve de uma vez a grafia pré-AO90 do
  Dicionário Aberto e o vocabulário de outras variantes no Wikcionário.
* **As aceções nunca se fundem entre fontes.** Cada uma mantém a proveniência
  e é apresentada em separado. Wikcionário primeiro (moderno), Dicionário
  Aberto a seguir (cobertura).
* **Todos os conflitos ficam registados** para inspeção manual.

Ligação de exemplos
-------------------
As fontes de exemplos (Tatoeba, Leipzig) indexam por *forma como aparece na
frase*, não por lema. A ligação forma -> lema faz-se aqui, com a tabela
`forms` já construída — é o mesmo lookup que a app faz em runtime, o que
significa que um exemplo só se cola a um lema se a app for capaz de lá chegar.
"""

from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass, field
from typing import Iterable

from ..config import (
    EXAMPLE_SOURCE_PRIORITY,
    MAX_EXAMPLES_PER_SENSE,
    SENSE_SOURCE_PRIORITY,
)
from ..text import normalize
from ..schema import Example, Form, MergedEntry, Relation, Sense, SourceEntry

# Fontes que definem o conjunto de lemas, por ordem de autoridade. Se nenhuma
# estiver presente, todos os lemas encontrados entram (modo permissivo, útil
# no protótipo da F0 quando o VOC ainda não foi obtido).
BACKBONE_SOURCES = ("voc_cplp",)

# Fontes que só contribuem para lemas já existentes: nunca criam palavras.
NON_LEMMA_SOURCES = ("tatoeba", "leipzig", "wordnet_pt")


@dataclass
class ConflictReport:
    """Tudo o que exigiu uma decisão, para inspeção humana."""

    pos_disagreements: list[dict] = field(default_factory=list)
    rejected_lemmas: list[dict] = field(default_factory=list)
    orphan_examples: list[dict] = field(default_factory=list)
    duplicate_senses: list[dict] = field(default_factory=list)

    def total(self) -> int:
        return (
            len(self.pos_disagreements)
            + len(self.rejected_lemmas)
            + len(self.orphan_examples)
            + len(self.duplicate_senses)
        )

    def as_dict(self) -> dict:
        return {
            "pos_disagreements": self.pos_disagreements,
            "rejected_lemmas": self.rejected_lemmas,
            "orphan_examples": self.orphan_examples,
            "duplicate_senses": self.duplicate_senses,
            "total": self.total(),
        }


@dataclass
class MergeResult:
    entries: list[MergedEntry]
    conflicts: ConflictReport
    backbone: str | None      # que fonte definiu a lista de lemas, se alguma


def merge_entries(entries: Iterable[SourceEntry], strict_backbone: bool = True) -> MergeResult:
    """Funde entradas de todas as fontes num conjunto de `MergedEntry`.

    `strict_backbone=False` desliga a autoridade do VOC — só para o protótipo
    da F0, quando ainda não há lista oficial de lemas.
    """
    by_source: dict[str, list[SourceEntry]] = defaultdict(list)
    for entry in entries:
        by_source[entry.source].append(entry)

    conflicts = ConflictReport()
    backbone = _pick_backbone(by_source) if strict_backbone else None

    # 1. Chaves canónicas e grafia oficial.
    spelling: dict[str, str] = {}      # normalizado -> grafia a usar
    backbone_pos: dict[str, str] = {}  # a classe gramatical da autoridade
    if backbone:
        for entry in by_source[backbone]:
            spelling.setdefault(entry.normalized, entry.lemma)
            if entry.pos != "desconhecido":
                backbone_pos.setdefault(entry.normalized, entry.pos)
    else:
        for source in SENSE_SOURCE_PRIORITY:
            for entry in by_source.get(source, ()):
                spelling.setdefault(entry.normalized, entry.lemma)
        for source, items in by_source.items():
            if source in NON_LEMMA_SOURCES:
                continue
            for entry in items:
                spelling.setdefault(entry.normalized, entry.lemma)

    # A classe gramatical da fonte de autoridade entra já aqui, antes das
    # aceções. Sem isto, o Wikcionário — que vem primeiro na ordem das
    # aceções, por ser o moderno — passaria à frente do VOC também na classe
    # gramatical, e o VOC é quem manda nisso (plano 4.1).
    merged: dict[str, MergedEntry] = {
        key: MergedEntry(lemma=lemma, pos=backbone_pos.get(key, "desconhecido"))
        for key, lemma in spelling.items()
    }

    # 2. Formas, primeiro — os exemplos precisam delas para se ligarem.
    form_index: dict[str, set[str]] = defaultdict(set)
    for key, entry in merged.items():
        form_index[key].add(key)       # o próprio lema é uma forma
    for source, items in by_source.items():
        for entry in items:
            key = entry.normalized
            if key not in merged:
                continue
            for form in entry.forms:
                _add_form(merged[key], form)
                form_index[normalize(form.form)].add(key)

    # 3. Classe gramatical, aceções, sinónimos, frequência.
    for source in _source_order(by_source):
        for entry in by_source[source]:
            key = entry.normalized
            if key not in merged:
                if source not in NON_LEMMA_SOURCES:
                    conflicts.rejected_lemmas.append(
                        {"lemma": entry.lemma, "source": source,
                         "reason": f"ausente da fonte de autoridade ({backbone})"}
                    )
                continue
            target = merged[key]
            _apply_pos(target, entry, conflicts)
            _apply_senses(target, entry, conflicts)
            _apply_relations(target, entry)
            if entry.syllables and not target.syllables:
                target.syllables = entry.syllables
            if entry.frequency_rank is not None:
                if target.frequency_rank is None or entry.frequency_rank < target.frequency_rank:
                    target.frequency_rank = entry.frequency_rank
            if source not in target.contributors:
                target.contributors.append(source)

    # 4. Exemplos, resolvidos pela forma.
    for source in EXAMPLE_SOURCE_PRIORITY:
        for entry in by_source.get(source, ()):
            candidates = form_index.get(entry.normalized) or set()
            if not candidates:
                for example in entry.examples:
                    conflicts.orphan_examples.append(
                        {"form": entry.lemma, "source": source,
                         "sentence": example.sentence}
                    )
                continue
            for key in candidates:
                target = merged[key]
                for example in entry.examples:
                    _add_example(target, example)
                if entry.examples and source not in target.contributors:
                    target.contributors.append(source)

    # Exemplos que vêm colados à própria entrada (Wikcionário, AMALIA).
    for source, items in by_source.items():
        if source in EXAMPLE_SOURCE_PRIORITY:
            continue
        for entry in items:
            target = merged.get(entry.normalized)
            if target is None:
                continue
            for example in entry.examples:
                _add_example(target, example)

    for entry in merged.values():
        _trim_examples(entry)

    ordered = sorted(merged.values(), key=lambda e: (e.normalized, e.lemma))
    return MergeResult(entries=ordered, conflicts=conflicts, backbone=backbone)


# --- passos individuais ----------------------------------------------------


def _pick_backbone(by_source: dict[str, list[SourceEntry]]) -> str | None:
    for slug in BACKBONE_SOURCES:
        if by_source.get(slug):
            return slug
    return None


def _source_order(by_source: dict[str, list[SourceEntry]]) -> list[str]:
    """Fontes de aceções pela prioridade do plano, o resto a seguir."""
    ordered = [s for s in SENSE_SOURCE_PRIORITY if s in by_source]
    ordered += [s for s in by_source if s not in ordered]
    return ordered


def _apply_pos(target: MergedEntry, entry: SourceEntry, conflicts: ConflictReport) -> None:
    if entry.pos == "desconhecido":
        return
    if target.pos == "desconhecido":
        target.pos = entry.pos
    elif target.pos != entry.pos:
        # Não se escolhe automaticamente: uma palavra pode legitimamente ser
        # substantivo e adjetivo. Fica registado, mantém-se o primeiro.
        conflicts.pos_disagreements.append(
            {"lemma": target.lemma, "kept": target.pos,
             "other": entry.pos, "source": entry.source}
        )


def _apply_senses(target: MergedEntry, entry: SourceEntry, conflicts: ConflictReport) -> None:
    existing = {(s.source, normalize(s.definition)) for s in target.senses}
    seen_text = {normalize(s.definition) for s in target.senses}
    for sense in entry.senses:
        text = normalize(sense.definition)
        if not text:
            continue
        if (sense.source, text) in existing:
            continue
        if text in seen_text:
            # Mesma definição vinda de outra fonte: guarda-se uma só vez, mas
            # regista-se, porque é sinal de que uma fonte copiou a outra.
            conflicts.duplicate_senses.append(
                {"lemma": target.lemma, "source": sense.source,
                 "definition": sense.definition}
            )
            continue
        target.senses.append(
            Sense(
                definition=sense.definition,
                source=sense.source,
                ord=len(target.senses) + 1,
                domains=list(sense.domains),
                modernized=sense.modernized,
                original_definition=sense.original_definition,
            )
        )
        existing.add((sense.source, text))
        seen_text.add(text)


def _apply_relations(target: MergedEntry, entry: SourceEntry) -> None:
    existing = {(r.target, r.relation) for r in target.relations}
    for relation in entry.relations:
        key = (relation.target, relation.relation)
        if key not in existing and normalize(relation.target) != target.normalized:
            target.relations.append(
                Relation(target=relation.target, relation=relation.relation,
                         source=relation.source)
            )
            existing.add(key)


def _add_form(target: MergedEntry, form: Form) -> None:
    if not any(f.form == form.form for f in target.forms):
        target.forms.append(Form(form=form.form, tag=form.tag))


def _add_example(target: MergedEntry, example: Example) -> None:
    key = normalize(example.sentence)
    if any(normalize(e.sentence) == key for e in target.examples):
        return
    target.examples.append(
        Example(
            sentence=example.sentence,
            source=example.source,
            source_ref=example.source_ref,
            variant=example.variant,
            generated=example.generated,
            sense_ord=example.sense_ord,
        )
    )


def _trim_examples(entry: MergedEntry) -> None:
    """Corta os exemplos ao teto por aceção, respeitando a cascata.

    Ordem: fonte por prioridade (Tatoeba > Leipzig > AMALIA), depois pt-PT
    antes de variante desconhecida, depois frase mais curta — que numa app de
    leitura é quase sempre a mais útil.
    """
    priority = {name: i for i, name in enumerate(EXAMPLE_SOURCE_PRIORITY)}
    variant_rank = {"pt-PT": 0, "unknown": 1, "pt-BR": 2}

    entry.examples.sort(
        key=lambda e: (
            priority.get(e.source, len(priority)),
            variant_rank.get(e.variant, 3),
            len(e.sentence),
        )
    )

    kept: list[Example] = []
    per_sense: dict[int | None, int] = defaultdict(int)
    for example in entry.examples:
        bucket = example.sense_ord
        if per_sense[bucket] >= MAX_EXAMPLES_PER_SENSE:
            continue
        per_sense[bucket] += 1
        kept.append(example)
    entry.examples = kept
