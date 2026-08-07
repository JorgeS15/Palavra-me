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
    FILL_ONLY_SOURCES,
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
#
# O `papel` e o `ontopt` entram aqui pela mesma razão que o `wordnet_pt`: o
# seu vocabulário inclui expressões compostas e itens lematizados por máquina
# (`abrir_o_apetite`, `reino_monera`), e deixá-los abrir lemas encheria a base
# de entradas que nenhum dicionário português reconhece como palavras.
NON_LEMMA_SOURCES = ("tatoeba", "leipzig", "wordnet_pt", "papel", "ontopt")


# Quantos exemplos de cada tipo de conflito guardar no relatório. O relatório
# serve para inspeção humana, e ninguém lê a décima milésima discordância de
# classe gramatical; a contagem total continua exata.
MAX_CONFLITOS_REGISTADOS = 500


@dataclass
class ConflictReport:
    """Tudo o que exigiu uma decisão, para inspeção humana.

    Guarda uma amostra de cada família e conta o resto. Antes guardava tudo, e
    o relatório da F1 chegava a 3 MB só de discordâncias de classe gramatical —
    ilegível, e a crescer com cada fonte nova que traz `pos`.
    """

    pos_disagreements: list[dict] = field(default_factory=list)
    rejected_lemmas: list[dict] = field(default_factory=list)
    orphan_examples: list[dict] = field(default_factory=list)
    duplicate_senses: list[dict] = field(default_factory=list)
    counts: dict = field(default_factory=dict)

    _FAMILIAS = (
        "pos_disagreements", "rejected_lemmas", "orphan_examples",
        "duplicate_senses",
    )

    def record(self, familia: str, item: dict) -> None:
        """Conta sempre; guarda só até ao teto."""
        self.counts[familia] = self.counts.get(familia, 0) + 1
        amostra = getattr(self, familia)
        if len(amostra) < MAX_CONFLITOS_REGISTADOS:
            amostra.append(item)

    def total(self) -> int:
        return sum(self.counts.get(f, 0) for f in self._FAMILIAS)

    def as_dict(self) -> dict:
        out: dict = {"total": self.total(), "contagens": dict(self.counts)}
        for familia in self._FAMILIAS:
            amostra = getattr(self, familia)
            out[familia] = amostra
            omitidos = self.counts.get(familia, 0) - len(amostra)
            if omitidos > 0:
                out[f"{familia}_omitidos"] = omitidos
        return out


@dataclass
class MergeResult:
    entries: list[MergedEntry]
    conflicts: ConflictReport
    backbone: str | None      # que fonte definiu a lista de lemas, se alguma


def ordem_de_aplicacao(slugs: Iterable[str]) -> list[str]:
    """Fontes de aceções pela prioridade do plano, o resto a seguir."""
    slugs = list(slugs)
    ordered = [s for s in SENSE_SOURCE_PRIORITY if s in slugs]
    ordered += [s for s in slugs if s not in ordered]
    return ordered


class Merger:
    """Fusão incremental: processa uma fonte de cada vez.

    Porque é que isto existe
    ------------------------
    A primeira F1 com o dicionário completo esgotou a memória. As entradas
    de todas as fontes juntas — 403 mil do Wikcionário, 128 mil do
    Dicionário Aberto, mais Hunspell, Tatoeba e Leipzig — passam de 1 GB só
    em objetos Python, e antes segurava-se tudo isso ao mesmo tempo que se
    construía o resultado. Aqui cada fonte é consumida e libertada.

    Uso::

        m = Merger(strict_backbone=False)
        for slug in ordem_de_aplicacao(fontes_de_lemas):
            m.atestar(slug, fonte.parse(None))     # 1ª volta: só strings
        m.fechar_lemas()
        for slug in ordem_de_aplicacao(fontes_de_lemas):
            m.aplicar(slug, fonte.parse(None))     # 2ª volta: o conteúdo
        for slug in fontes_de_exemplos:
            m.aplicar(slug, fonte.parse(universo))
        resultado = m.terminar()

    A 1ª volta guarda apenas `(normalizado, grafia, tem_aceções)` — strings,
    não entradas — o que custa uma ordem de grandeza menos do que manter as
    entradas todas. O preço é reler as fontes de lemas uma segunda vez, o
    que numa build que corre raramente é troca barata.
    """

    def __init__(self, strict_backbone: bool = True):
        self.conflicts = ConflictReport()
        self.strict_backbone = strict_backbone
        self.backbone: str | None = None
        # A chave da fusão é a GRAFIA EXATA, não a forma normalizada. Lição
        # da primeira F0 real: com chaves normalizadas, `por` (preposição) e
        # `pôr` (verbo) colapsavam numa entrada só, e a sonda pusesse -> pôr
        # falhava. Lemas que só diferem no acento são palavras diferentes, e
        # o plano (secção 9) manda mostrar os candidatos todos.
        #
        # `spellings` agrupa, por forma normalizada, as grafias atestadas
        # pelas fontes com autoridade para criar lemas. Uma grafia que
        # nenhuma delas conhece (ex.: acentuação de 1913) é remapeada para a
        # primeira grafia atestada do grupo, para não duplicar palavras.
        self.spellings: dict[str, list[str]] = defaultdict(list)
        self.backbone_pos: dict[str, str] = {}
        self.merged: dict[str, MergedEntry] = {}
        self.form_index: dict[str, set[str]] = defaultdict(set)
        self._buffer: dict[str, list[tuple[str, str, str, bool]]] = defaultdict(list)

    # --- 1ª volta ---------------------------------------------------------

    def atestar(self, source: str, entries: Iterable[SourceEntry]) -> None:
        """Regista que lemas esta fonte propõe. Só guarda strings."""
        buf = self._buffer[source]
        for entry in entries:
            buf.append((entry.normalized, entry.lemma, entry.pos, bool(entry.senses)))

    def fechar_lemas(self) -> None:
        """Decide o universo de lemas e cria as entradas vazias."""
        if self.strict_backbone:
            self.backbone = next(
                (s for s in BACKBONE_SOURCES if self._buffer.get(s)), None
            )

        if self.backbone:
            for norm, lemma, pos, _ in self._buffer[self.backbone]:
                self._attest(norm, lemma)
                # A classe gramatical da autoridade entra já aqui, antes das
                # aceções. Sem isto, o Wikcionário — primeiro na ordem das
                # aceções, por ser o moderno — passaria à frente do VOC
                # também na classe gramatical (plano 4.1).
                if pos != "desconhecido":
                    self.backbone_pos.setdefault(lemma, pos)
        else:
            # Duas voltas: primeiro quem traz definições, depois o resto — a
            # ordem no dump não pode decidir se 'Mao' (sem glosa) abre lema
            # antes de 'mão' (com glosas) chegar.
            # `NON_LEMMA_SOURCES` manda mesmo estando em SENSE_SOURCE_PRIORITY:
            # o wordnet dá definições mas não decide o que é uma palavra. As
            # suas entradas são traduções alinhadas com a WordNet inglesa, e
            # deixá-las abrir lemas encheria a base de palavras que nenhum
            # dicionário português reconhece.
            primarias = [
                registo
                for source in SENSE_SOURCE_PRIORITY
                if source not in NON_LEMMA_SOURCES
                for registo in self._buffer.get(source, ())
            ]
            for norm, lemma, _, tem_senses in primarias:
                if tem_senses:
                    self._attest(norm, lemma)
            for norm, lemma, _, tem_senses in primarias:
                if not tem_senses:
                    self._attest(norm, lemma, so_grupo_vazio=True)
            for source, registos in self._buffer.items():
                if source in NON_LEMMA_SOURCES or source in SENSE_SOURCE_PRIORITY:
                    continue
                for norm, lemma, _, _ in registos:
                    self._attest(norm, lemma, so_grupo_vazio=True)

        self._buffer.clear()   # já não é preciso: liberta a memória

        self.merged = {
            lemma: MergedEntry(
                lemma=lemma, pos=self.backbone_pos.get(lemma, "desconhecido")
            )
            for group in self.spellings.values()
            for lemma in group
        }
        # O índice vai de forma normalizada para grafias exatas de lemas: é
        # o mesmo salto que a app faz (normaliza o que o utilizador escreve,
        # procura em `forms`, chega aos lemas candidatos).
        for lemma, entry in self.merged.items():
            self.form_index[entry.normalized].add(lemma)

    def _attest(self, norm: str, lemma: str, *, so_grupo_vazio: bool = False) -> None:
        group = self.spellings[norm]
        if lemma in group:
            return
        # Uma entrada sem definições (o Hunspell inteiro; entradas do
        # Wikcionário como 'agua', "grafia obsoleta de água") só abre lema
        # novo se mais ninguém reclamou aquela palavra. Caso contrário seria
        # uma entrada fantasma ao lado da verdadeira. O grupo vazio continua
        # a abrir ('ensonado' só existe no Hunspell, e a entrada sem
        # definição É o retrato honesto dessa lacuna).
        if so_grupo_vazio and group:
            return
        group.append(lemma)

    def _resolve(self, entry: SourceEntry) -> str | None:
        group = self.spellings.get(entry.normalized)
        if not group:
            return None
        if entry.lemma in group:
            return entry.lemma
        return group[0]

    # --- 2ª volta ---------------------------------------------------------

    def aplicar(self, source: str, entries: Iterable[SourceEntry]) -> None:
        """Aplica o conteúdo de uma fonte às entradas já criadas.

        As fontes de exemplos (Tatoeba, Leipzig) resolvem-se pela FORMA —
        vêm indexadas pela palavra tal como aparece na frase — e por isso
        têm de ser aplicadas depois de todas as formas estarem no índice.
        """
        por_forma = source in EXAMPLE_SOURCE_PRIORITY
        for entry in entries:
            if por_forma:
                self._aplicar_exemplos_por_forma(source, entry)
                continue

            key = self._resolve(entry)
            if key is None:
                if source not in NON_LEMMA_SOURCES:
                    self.conflicts.record(
                        "rejected_lemmas",
                        {"lemma": entry.lemma, "source": source,
                         "reason": f"ausente da fonte de autoridade ({self.backbone})"},
                    )
                continue
            target = self.merged[key]

            for form in entry.forms:
                _add_form(target, form)
                self.form_index[normalize(form.form)].add(key)

            _apply_pos(target, entry, self.conflicts)
            _apply_senses(target, entry, self.conflicts)
            _apply_relations(target, entry)
            if entry.syllables and not target.syllables:
                target.syllables = entry.syllables
            if entry.frequency_rank is not None:
                if (target.frequency_rank is None
                        or entry.frequency_rank < target.frequency_rank):
                    target.frequency_rank = entry.frequency_rank
            # Exemplos colados à própria entrada (Wikcionário, AMALIA).
            for example in entry.examples:
                _add_example(target, example)
            if source not in target.contributors:
                target.contributors.append(source)

    def _aplicar_exemplos_por_forma(self, source: str, entry: SourceEntry) -> None:
        candidates = self.form_index.get(entry.normalized)
        if not candidates:
            for example in entry.examples:
                self.conflicts.record(
                    "orphan_examples",
                    {"form": entry.lemma, "source": source,
                     "sentence": example.sentence},
                )
            return
        for key in candidates:
            target = self.merged[key]
            for example in entry.examples:
                _add_example(target, example)
            # A frequência vem pelo mesmo caminho dos exemplos (é o Leipzig
            # que dá as duas coisas) e tem de ser aplicada aqui. Quando este
            # caminho passou a ser separado, na fusão em streaming, a
            # frequência ficou pelo caminho e os 186 mil lemas ficaram todos
            # sem `frequency_rank` — o que torna inútil a ordenação dos
            # candidatos por frequência que o plano pede (secção 9).
            if entry.frequency_rank is not None:
                if (target.frequency_rank is None
                        or entry.frequency_rank < target.frequency_rank):
                    target.frequency_rank = entry.frequency_rank
            if (entry.examples or entry.frequency_rank is not None) \
                    and source not in target.contributors:
                target.contributors.append(source)

    # --- fim --------------------------------------------------------------

    def terminar(self) -> MergeResult:
        for entry in self.merged.values():
            _trim_examples(entry)
            _trim_relations(entry)
        ordered = sorted(self.merged.values(), key=lambda e: (e.normalized, e.lemma))
        self.merged = {}
        return MergeResult(
            entries=ordered, conflicts=self.conflicts, backbone=self.backbone
        )


def merge_entries(entries: Iterable[SourceEntry], strict_backbone: bool = True) -> MergeResult:
    """Funde entradas já em memória. Conveniência para a F0 e para os testes.

    A F1 usa o `Merger` diretamente, para não segurar as fontes todas de uma
    vez. Aqui as entradas já vieram numa lista, portanto agrupa-se e
    delega-se — o resultado é idêntico.

    `strict_backbone=False` desliga a autoridade do VOC — para o protótipo da
    F0, quando ainda não há lista oficial de lemas.
    """
    by_source: dict[str, list[SourceEntry]] = defaultdict(list)
    for entry in entries:
        by_source[entry.source].append(entry)

    merger = Merger(strict_backbone=strict_backbone)
    ordem = ordem_de_aplicacao(by_source)
    for source in ordem:
        merger.atestar(source, by_source[source])
    merger.fechar_lemas()
    # As fontes de exemplos por último: precisam do índice de formas cheio.
    lemas = [s for s in ordem if s not in EXAMPLE_SOURCE_PRIORITY]
    exemplos = [s for s in ordem if s in EXAMPLE_SOURCE_PRIORITY]
    for source in lemas + exemplos:
        merger.aplicar(source, by_source[source])
    return merger.terminar()


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
        conflicts.record(
            "pos_disagreements",
            {"lemma": target.lemma, "kept": target.pos,
             "other": entry.pos, "source": entry.source},
        )


def _apply_senses(target: MergedEntry, entry: SourceEntry, conflicts: ConflictReport) -> None:
    # Fontes de preenchimento só falam quando mais ninguém falou. Ver
    # FILL_ONLY_SOURCES no `config`: as glosas do wordnet são tradução
    # automática e não podem encostar-se a uma definição de lexicógrafo.
    if entry.source in FILL_ONLY_SOURCES and target.senses:
        return

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
            conflicts.record(
                "duplicate_senses",
                {"lemma": target.lemma, "source": sense.source,
                 "definition": sense.definition},
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


def _seen(target: MergedEntry, attr: str, initial) -> set:
    """Conjunto de deduplicação anexado à entrada, criado na primeira vez.

    A alternativa — percorrer e renormalizar a lista inteira a cada
    inserção — é O(n²) e foi o que congelou a primeira F0 real: um lema
    comum apanha dezenas de milhares de frases do Tatoeba, e n² sobre isso
    são centenas de milhões de normalizações. Com o conjunto, é O(n).
    O atributo é dinâmico e privado: não entra no dataclass, não é
    serializado, e morre com o objeto.
    """
    seen = getattr(target, attr, None)
    if seen is None:
        seen = set(initial)
        setattr(target, attr, seen)
    return seen


def _add_form(target: MergedEntry, form: Form) -> None:
    seen = _seen(target, "_seen_forms", (f.form for f in target.forms))
    if form.form in seen:
        return
    seen.add(form.form)
    target.forms.append(Form(form=form.form, tag=form.tag))


def _add_example(target: MergedEntry, example: Example) -> None:
    seen = _seen(
        target, "_seen_examples", (normalize(e.sentence) for e in target.examples)
    )
    key = normalize(example.sentence)
    if key in seen:
        return
    seen.add(key)
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


def _trim_relations(entry: MergedEntry) -> None:
    """Tira as relações que apontam para uma flexão da própria palavra.

    O Onto.PT dá `pistola-metralhadora` como sinónimo de
    `pistolas-metralhadoras`, e `amarguíssimo` de `amaríssimo`. Não são
    sinónimos: são a mesma palavra noutra forma. Mostrar isso a quem procura
    o significado é ruído, e num jogo de escolha múltipla seria pior.

    Corre-se no fim, e não quando a relação é aplicada, porque as flexões
    chegam do Hunspell — que na ordem de aplicação vem depois das fontes de
    aceções. No momento em que a relação entra, a tabela de formas da entrada
    ainda está vazia.
    """
    if not entry.relations:
        return
    proprias = {normalize(f.form) for f in entry.forms}
    proprias.add(entry.normalized)
    entry.relations = [
        r for r in entry.relations if normalize(r.target) not in proprias
    ]


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
