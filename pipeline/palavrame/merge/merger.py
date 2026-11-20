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

import re
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
from ..portugalizar import para_grafia_aguda
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

# Uma "palavra" para efeitos de adaptação de grafia: só letras (inclui
# acentuadas), sem dígitos nem pontuação. Serve para trocar grafias BR->PT no
# texto sem tocar em números nem símbolos.
_PALAVRA_TEXTO = re.compile(r"[^\W\d_]+", re.UNICODE)

# Remissão de uma aceção para outra palavra: "Ver lânguido.", "O mesmo que X".
# Quando o alvo é o PRÓPRIO lema (depois de unir `lângüido` -> `lânguido`),
# a aceção fica a apontar para si mesma e não diz nada — deita-se fora.
# "Vide X" e "V. X" são a forma do Dicionário Aberto ("lacónico: Vide
# lacónico" escapava, por só se conhecer o "Ver").
_REMISSAO = re.compile(
    r"^\s*(?:ver|vide|v\.|o mesmo que|idem,?)\s+(.+?)\.?\s*$", re.IGNORECASE
)


# Uma aceção que é (ou começa por) uma remissão: "Vide X", "V. X", "Ver X",
# "O mesmo que X". Ver `_resolver_remissoes`.
_REMISSAO_INICIO = re.compile(r"^\s*(vide|v\.|ver|o mesmo que)\s+(.+)$", re.IGNORECASE)
_PARENTESES = re.compile(r"\s*\([^)]*\)")
_CORTE = re.compile(r"\s*(?:[:;,«]|\s+ou\s+|\s+e\s+|\s+etc\b)")


_VIDE = re.compile(r"^\s*vide\s+", re.IGNORECASE)
_V_PONTO = re.compile(r"^\s*v\.\s+", re.IGNORECASE)


def _uniformizar_remissao(definicao: str, resolvida: bool) -> str:
    """"Vide X" e "V. X" passam a "Ver X", como no Wikcionário.

    Querem dizer o mesmo; "vide" (latim, "vê") era a forma dos dicionários
    antigos, e o Dicionário Aberto é de 1913. Na app misturavam-se as duas,
    conforme a fonte. "Ver" percebe-o qualquer leitor.

    O "V." só muda quando o destino foi confirmado: "V. t. (e der.)" é a marca
    "verbo transitivo", não uma remissão, e fica como está.
    """
    if _VIDE.match(definicao):
        return _VIDE.sub("Ver ", definicao, count=1)
    if resolvida and _V_PONTO.match(definicao):
        return _V_PONTO.sub("Ver ", definicao, count=1)
    return definicao


def _tirar_aceções_repetidas(entry: MergedEntry) -> None:
    """Depois de uniformizar, "Vide ioga" e "Ver ioga" podem ficar iguais."""
    vistas: set[str] = set()
    unicas = []
    for sense in entry.senses:
        chave = normalize(sense.definition)
        if chave in vistas:
            continue
        vistas.add(chave)
        unicas.append(sense)
    if len(unicas) != len(entry.senses):
        for i, sense in enumerate(unicas, start=1):
            sense.ord = i
        entry.senses[:] = unicas


def _alvos_de_remissao(definicao: str) -> list[str]:
    """Os destinos possíveis de uma remissão, do mais completo ao mais curto."""
    m = _REMISSAO_INICIO.match(definicao)
    if not m:
        return []
    resto = _PARENTESES.sub("", m.group(2)).strip()
    alvos = [resto]
    # "Ver" também é o verbo ("Ver televisão, ouvir rádio"): aí só a frase
    # inteira conta como destino, nunca a primeira parte.
    if m.group(1).lower() != "ver":
        primeira = _CORTE.split(resto, maxsplit=1)[0]
        if primeira and primeira != resto:
            alvos.append(primeira)
    return alvos


# Sufixos de derivação: uma palavra assim é outra palavra, não uma flexão,
# mesmo que o Hunspell a gere a partir do verbo ("oscilador", "bafejante",
# "enchumaço"). Ver `_absorver_flexoes_vazias`.
_SUFIXO_DERIVACAO = re.compile(
    r"(dor|dora|dores|doras|ção|ções|mento|mentos|ante|antes|ente|entes|inte|intes"
    r"|vel|veis|ismo|ismos|ista|istas|idade|idades|ice|eiro|eira|eiros|eiras"
    r"|aço|aça|aços|ura|uras|ivo|iva|ivos|ivas|oso|osa|osos|osas|ia|ias|ês|esa|ense)$"
)


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
    # Não é um conflito: é o registo das grafias brasileiras unidas na
    # portuguesa (bebê -> bebé), para inspeção — confirmar que nenhuma fusão
    # juntou palavras diferentes. Fica fora de `total()` de propósito.
    grafia_unida: list[dict] = field(default_factory=list)
    # Também informativo: lemas cuja classe principal mudou para a que tem
    # mais aceções ("computador": adjetivo -> substantivo).
    pos_principal: list[dict] = field(default_factory=list)
    # Informativo: lemas vazios que eram só flexões e passaram para a palavra
    # com definição ("túneis" -> "túnel").
    flexao_absorvida: list[dict] = field(default_factory=list)
    counts: dict = field(default_factory=dict)

    _FAMILIAS = (
        "pos_disagreements", "rejected_lemmas", "orphan_examples",
        "duplicate_senses",
    )
    # Famílias informativas: entram no relatório mas não contam como conflito.
    _FAMILIAS_INFO = ("grafia_unida", "pos_principal", "flexao_absorvida")

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
        for familia in self._FAMILIAS + self._FAMILIAS_INFO:
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
        # Como `form_index`, mas indexado pela forma EXATA (minúsculas, com
        # acentos). Os exemplos ligam-se por aqui: "bebe" e "bebé" normalizam
        # igual mas são palavras diferentes, e a frase do verbo não pode cair
        # no nome. A pesquisa da app continua a usar a forma normalizada.
        self.form_index_exact: dict[str, set[str]] = defaultdict(set)
        # Decisão "unir na grafia PT" (ver `portugalizar`): uma grafia atestada
        # pode ser encaminhada para outra (bebê -> bebé). `rota` diz, para cada
        # grafia atestada, qual é a entrada que a recebe; `br_para_pt` é o mapa
        # de grafias unidas, usado depois para adaptar o texto das definições e
        # dos exemplos; `formas_extra` guarda as grafias BR a registar como
        # forma pesquisável da entrada portuguesa.
        self.rota: dict[str, str] = {}
        self.br_para_pt: dict[str, str] = {}
        self.formas_extra: dict[str, list[str]] = defaultdict(list)
        # Grafias que o Hunspell pt-PT conhece (lemas + flexões), em
        # minúsculas. É o crivo de "isto é português" para adaptar a grafia do
        # texto: só se troca ê/ô por é/ó quando a gémea aguda está aqui e a
        # circunflexa não. Vazio se o Hunspell faltar — e então não se adapta.
        self.pt_surface: set[str] = set()
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
            # A curadoria pode abrir lema novo — mas só quando ninguém, nem a
            # autoridade, reclamou aquela grafia (`so_grupo_vazio`). É a sua
            # regra 2, e é o que permite acrescentar uma palavra que falta a
            # todas as fontes abertas (`adelgaçante`) sem nunca duplicar uma
            # que o VOC já tem. Corre depois do backbone, de propósito.
            for norm, lemma, _, _ in self._buffer.get("curadoria", ()):
                self._attest(norm, lemma, so_grupo_vazio=True)
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

        # As grafias que o Hunspell pt-PT atesta são o sinal de "isto é
        # português". Guarda-se ANTES de limpar o buffer, para a unificação de
        # grafia decidir o que é brasileiro. (O Hunspell é fonte de lemas,
        # portanto passou pela 1ª volta — ver `fontes_de_lemas` na CLI.)
        pt_atestadas = {
            lemma.lower() for _, lemma, _, _ in self._buffer.get("hunspell_natura", ())
        }
        # Semente da superfície portuguesa: os lemas do Hunspell. As flexões
        # juntam-se depois, quando o Hunspell é aplicado (2ª volta).
        self.pt_surface = set(pt_atestadas)
        self._buffer.clear()   # já não é preciso: liberta a memória

        self.merged = {}
        for group in self.spellings.values():
            self._resolver_grupo(group, pt_atestadas)

        # O índice vai de forma normalizada para grafias exatas de lemas: é
        # o mesmo salto que a app faz (normaliza o que o utilizador escreve,
        # procura em `forms`, chega aos lemas candidatos). As grafias BR unidas
        # entram como forma da entrada portuguesa, para a pesquisa e os
        # exemplos escritos à brasileira ainda lá chegarem.
        for lemma, entry in self.merged.items():
            self.form_index[entry.normalized].add(lemma)
            self.form_index_exact[lemma.lower()].add(lemma)
            for br in self.formas_extra.get(lemma, ()):
                entry.forms.append(Form(form=br, tag="grafia-br"))
                self.form_index[normalize(br)].add(lemma)
                self.form_index_exact[br.lower()].add(lemma)

    def _resolver_grupo(self, group: list[str], pt_atestadas: set[str]) -> None:
        """Decide, dentro de um grupo de grafias, quais são entrada e quais se
        unem na grafia portuguesa (decisão "unir na grafia PT")."""
        for lemma in group:
            agudo = para_grafia_aguda(lemma)
            e_brasileira = (
                agudo is not None
                and agudo.lower() in pt_atestadas
                and lemma.lower() not in pt_atestadas
            )
            if e_brasileira:
                # `lemma` é grafia brasileira de `agudo`, que é palavra
                # portuguesa atestada. A entrada passa a ser a portuguesa; a
                # grafia BR fica como forma pesquisável e some da lista.
                destino = agudo
                self.rota[lemma] = destino
                self.br_para_pt[lemma.lower()] = destino
                if lemma not in self.formas_extra[destino]:
                    self.formas_extra[destino].append(lemma)
                self.conflicts.record("grafia_unida", {"de": lemma, "para": destino})
                pos = self.backbone_pos.get(destino) or self.backbone_pos.get(lemma)
                self.merged.setdefault(
                    destino, MergedEntry(lemma=destino, pos=pos or "desconhecido")
                )
            else:
                self.rota[lemma] = lemma
                self.merged.setdefault(
                    lemma,
                    MergedEntry(lemma=lemma, pos=self.backbone_pos.get(lemma, "desconhecido")),
                )

    def _attest(self, norm: str, lemma: str, *, so_grupo_vazio: bool = False) -> None:
        # Grafias sem uma única letra — o Dicionário Aberto de 1913 lematiza
        # símbolos astronómicos e de pontuação como se fossem palavras ('☿',
        # '(...)', '&', '…'). Normalizam para vazio e não são pesquisáveis:
        # ficariam na base como lemas fantasma. Não abrem lema.
        if not norm:
            return
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
        # A grafia atestada pode ter sido unida noutra (bebê -> bebé): o
        # conteúdo segue a rota até à entrada que existe.
        destino = self.rota.get(entry.lemma)
        if destino is not None:
            return destino
        # Grafia não atestada (modo permissivo, ou fonte que traz uma grafia
        # que o VOC não tem): cai na primeira grafia canónica do grupo.
        primeiro = self.rota.get(group[0], group[0])
        return primeiro if primeiro in self.merged else None

    # --- 2ª volta ---------------------------------------------------------

    def aplicar(self, source: str, entries: Iterable[SourceEntry]) -> None:
        """Aplica o conteúdo de uma fonte às entradas já criadas.

        As fontes de exemplos (Tatoeba, Leipzig) resolvem-se pela FORMA —
        vêm indexadas pela palavra tal como aparece na frase — e por isso
        têm de ser aplicadas depois de todas as formas estarem no índice.
        """
        por_forma = source in EXAMPLE_SOURCE_PRIORITY
        for entry in entries:
            # A superfície portuguesa (para adaptar a grafia do texto no fim)
            # é tudo o que o Hunspell reconhece: o lema e cada flexão.
            if source == "hunspell_natura":
                self.pt_surface.add(entry.lemma.lower())
                for f in entry.forms:
                    self.pt_surface.add(f.form.lower())

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
                self.form_index_exact[form.form.lower()].add(key)

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
        # Os exemplos ligam-se pela forma EXATA, com acentos. "O menino bebe
        # leite" indexa por "bebe" (do verbo) e não pode ir parar a "bebé" (o
        # nome), embora normalizem para a mesma coisa. Antes ligava-se pela
        # normalizada e a frase caía nos dois.
        if entry.examples:
            alvos = self.form_index_exact.get(entry.lemma.lower())
            # Se a forma é ela própria um lema (homógrafo), o exemplo fica NESSE
            # lema e não nas flexões de outros. "manga" é o fruto e também "ele
            # manga" (de mangar); "esta manga está doce" pertence ao fruto. Sem
            # isto, as frases da manga-fruto colavam-se ao verbo mangar.
            if alvos:
                proprios = {k for k in alvos if k.lower() == entry.lemma.lower()}
                if proprios:
                    alvos = proprios
            if not alvos:
                for example in entry.examples:
                    self.conflicts.record(
                        "orphan_examples",
                        {"form": entry.lemma, "source": source,
                         "sentence": example.sentence},
                    )
            else:
                for key in alvos:
                    target = self.merged[key]
                    for example in entry.examples:
                        _add_example(target, example)
                    if source not in target.contributors:
                        target.contributors.append(source)

        # A frequência é do lema e não é sensível ao acento: "agua" no corpus
        # conta para "água". Liga-se pela forma normalizada — todos os lemas
        # desse grupo — como sempre fez. (É o Leipzig que traz as duas coisas;
        # sem isto, os 186 mil lemas ficavam sem `frequency_rank` e a
        # ordenação por frequência que o plano pede — secção 9 — não fazia
        # nada.)
        if entry.frequency_rank is not None:
            for key in self.form_index.get(entry.normalized, ()):
                target = self.merged[key]
                if (target.frequency_rank is None
                        or entry.frequency_rank < target.frequency_rank):
                    target.frequency_rank = entry.frequency_rank
                if source not in target.contributors:
                    target.contributors.append(source)

    def _adaptar_grafia(self, entry: MergedEntry) -> None:
        """Portugaliza a grafia no texto das definições e dos exemplos.

        Troca palavra a palavra a grafia circunflexa do Brasil pela aguda
        portuguesa (`gênero` -> `género`, `nenê` -> `nené`), mas só quando a
        gémea aguda é palavra portuguesa (está em `pt_surface`) e a circunflexa
        não. Assim `português` e `três` ficam intactos. Vocabulário próprio do
        Brasil que não seja grafia (`shopping`, `ônibus`) não é tocado — não há
        gémea aguda que o substitua.
        """
        if not self.pt_surface:
            return

        def troca(m: "re.Match[str]") -> str:
            palavra = m.group(0)
            agudo = para_grafia_aguda(palavra)
            if (agudo is not None
                    and agudo.lower() in self.pt_surface
                    and palavra.lower() not in self.pt_surface):
                return agudo
            return palavra

        for sense in entry.senses:
            sense.definition = _PALAVRA_TEXTO.sub(troca, sense.definition)
        for example in entry.examples:
            example.sentence = _PALAVRA_TEXTO.sub(troca, example.sentence)

    def _gerar_femininos(self, entry: MergedEntry) -> None:
        """Gera o feminino e os plurais regulares de um adjetivo em `-o`.

        O Hunspell deixou muitos adjetivos sem marcas de flexão (`comezinho`
        sem o `/fp` que o `lânguido/fp` tem), e sem elas a app não liga o
        feminino ao lema: quem procura "comezinha" não chega a "comezinho",
        onde está a definição. Aqui preenche-se essa lacuna com a flexão
        regular da classe `-o/-a/-os/-as`.

        Só adjetivos, e com rede: se a forma gerada já é OUTRA palavra (o
        feminino de `meio` seria `meia`, que é outra coisa), não se inventa.
        """
        if "adjetivo" not in _classes(entry):
            return
        base = entry.lemma
        if len(base) < 3 or not base.endswith("o"):
            return
        ja = {f.form for f in entry.forms}
        ja.add(base)
        for candidata in (base[:-1] + "a", base + "s", base[:-1] + "as"):
            if candidata in ja:
                continue
            if candidata in self.merged and candidata != base:
                continue    # é outra palavra: não a atropelamos
            entry.forms.append(Form(form=candidata, tag="flexao-regular"))
            ja.add(candidata)

    # --- fim --------------------------------------------------------------

    def _absorver_flexoes_vazias(self) -> None:
        """Tira os lemas vazios que são só flexões de outra palavra.

        "túneis", "abandonada", "pudera", "amenorréia": entram como lemas
        próprios (vêm do Hunspell ou do VOC) sem definição nenhuma, e a
        pesquisa oferecia-os ao lado da palavra certa ("túnel", "abandonado",
        "poder", "amenorreia"), uma entrada vazia a fazer-se passar por
        resposta. Saem; as suas formas e exemplos passam para a palavra com
        definição, e a pesquisa continua a chegar lá.

        Regra apertada, revista à mão sobre a base de 2026-11-04 (338 saem):
        - sem aceções e sem relações (os que têm sinónimos ficam: a app
          mostra "o mesmo que...", que é útil);
        - só minúsculas (nomes e siglas ficam: "SOS" não pode ir dar a "só");
        - há outra palavra **com definição** a que esta forma pertence;
        - não acaba num sufixo de derivação ("oscilador" não é uma forma de
          "oscilar", é outra palavra; o Hunspell gera-as como se fossem),
          a não ser que seja só outra grafia da mesma palavra.
        """
        sair: list[str] = []
        for key, entry in self.merged.items():
            if entry.senses or entry.relations:
                continue
            if entry.lemma != entry.lemma.lower():
                continue
            alvos = [
                k for k in self.form_index.get(entry.normalized, ())
                if k != key and k in self.merged and self.merged[k].senses
            ]
            if not alvos:
                continue
            variante = any(self.merged[k].normalized == entry.normalized for k in alvos)
            if not variante and _SUFIXO_DERIVACAO.search(entry.lemma):
                continue
            for k in alvos:
                destino = self.merged[k]
                _add_form(destino, Form(form=entry.lemma, tag="absorvida"))
                for form in entry.forms:
                    _add_form(destino, form)
                for example in entry.examples:
                    _add_example(destino, example)
            self.conflicts.record(
                "flexao_absorvida",
                {"lemma": entry.lemma, "destino": sorted(self.merged[k].lemma for k in alvos)},
            )
            sair.append(key)
        for key in sair:
            del self.merged[key]

    def _resolver_remissoes(self) -> None:
        """Marca as aceções que remetem para outra palavra, com o destino.

        "acção: Vide ação", "email: O mesmo que e-mail", "andante: O mesmo
        que andador.": na app eram texto, e a pessoa tinha de procurar o
        destino à mão. Com o lema de destino guardado (`remete_para`), a app
        torna-o tocável e mostra logo a definição dele.

        Só se marca quando o destino **existe e tem definição**: exato, só
        com a maiúscula a mais, ou sem acentos quando há um único candidato.
        Se o resto da aceção traz mais texto ("O mesmo que avonde: «...»",
        "Ver parqueamento, estacionamento"), tenta-se também a primeira parte.
        """
        com_def = {k: e for k, e in self.merged.items() if e.senses}
        por_lema = {e.lemma: e.lemma for e in com_def.values()}
        por_minuscula: dict[str, str] = {}
        por_norma: dict[str, list[str]] = defaultdict(list)
        for e in com_def.values():
            por_minuscula.setdefault(e.lemma.lower(), e.lemma)
            por_norma[e.normalized].append(e.lemma)

        def resolver(alvo: str) -> str | None:
            alvo = alvo.strip().rstrip(".").strip()
            # Menos de 3 letras não é um destino: "V. t. (e der.)", no
            # Dicionário Aberto, é a marca "verbo transitivo", não "vide t".
            if len(normalize(alvo)) < 3:
                return None
            if alvo in por_lema:
                return alvo
            if alvo.lower() in por_minuscula:
                return por_minuscula[alvo.lower()]
            candidatos = por_norma.get(normalize(alvo), [])
            return candidatos[0] if len(candidatos) == 1 else None

        for entry in self.merged.values():
            for sense in entry.senses:
                for alvo in _alvos_de_remissao(sense.definition):
                    destino = resolver(alvo)
                    if destino and normalize(destino) != entry.normalized:
                        sense.remete_para = destino
                        break
                sense.definition = _uniformizar_remissao(
                    sense.definition, resolvida=sense.remete_para is not None
                )
            _tirar_aceções_repetidas(entry)

    def terminar(self) -> MergeResult:
        # Antes de tudo o resto: as formas absorvidas têm de estar no destino
        # quando se geram femininos e se escreve a base.
        self._absorver_flexoes_vazias()
        self._resolver_remissoes()
        for entry in self.merged.values():
            _trim_examples(entry)
            _trim_relations(entry)
            self._adaptar_grafia(entry)
            self._gerar_femininos(entry)
            _escolher_classe_principal(entry, self.conflicts)
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


def _classes(entry: MergedEntry) -> set[str]:
    """Todas as classes da palavra: a do lema e as das aceções."""
    out = {s.pos for s in entry.senses if s.pos}
    if entry.pos and entry.pos != "desconhecido":
        out.add(entry.pos)
    return out


def _escolher_classe_principal(entry: MergedEntry, conflicts: ConflictReport) -> None:
    """A classe do lema passa a ser a que tem mais aceções.

    Até aqui era a da primeira fonte que a dava: "computador" ficava
    adjetivo (a primeira entrada do Wikcionário é "que computa"), embora
    três das suas aceções sejam de substantivo; "ser" e "jantar" ficavam
    substantivos, com dezenas de aceções de verbo.

    Desempate: a classe da primeira aceção (as fontes publicadas vêm
    primeiro, e cada uma põe o sentido principal à frente). Sem aceções com
    classe, fica a que estava.
    """
    contagem: dict[str, int] = {}
    for s in entry.senses:
        if s.pos:
            contagem[s.pos] = contagem.get(s.pos, 0) + 1
    if not contagem:
        return
    primeira = next(s.pos for s in entry.senses if s.pos)
    maximo = max(contagem.values())
    empatadas = {p for p, n in contagem.items() if n == maximo}
    principal = primeira if primeira in empatadas else sorted(empatadas)[0]
    if principal != entry.pos:
        conflicts.record(
            "pos_principal",
            {"lemma": entry.lemma, "antes": entry.pos, "depois": principal,
             "classes": contagem},
        )
        entry.pos = principal


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
    # Nem a nomes próprios e siglas: o alinhamento do wordnet liga-os a outras
    # coisas ("SNS: carcinoma do fígado", "Intel: uma capa de chuva
    # impermeável"). Um nome sem definição é melhor do que um disparate.
    if entry.source in FILL_ONLY_SOURCES and target.lemma[:1].isupper():
        return

    existing = {(s.source, normalize(s.definition)) for s in target.senses}
    seen_text = {normalize(s.definition) for s in target.senses}
    for sense in entry.senses:
        text = normalize(sense.definition)
        if not text:
            continue
        # Remissão para o próprio lema ("Ver lânguido." na entrada 'lânguido'):
        # não acrescenta nada e destoa ao lado das definições a sério.
        rem = _REMISSAO.match(sense.definition)
        if rem and normalize(rem.group(1)) == target.normalized:
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
                # A classe da aceção é a da entrada de onde vem: o Wikcionário
                # (wiktextract) dá uma entrada por classe, e é aqui que ela
                # deixa de se perder ao juntar tudo num lema.
                pos=sense.pos or (entry.pos if entry.pos != "desconhecido" else None),
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

    # "Preferir PT": num mesmo sentido, se houver uma frase que não seja pt-BR,
    # as pt-BR não entram. Só ficam quando são a única frase que há.
    tem_alternativa: dict[int | None, bool] = defaultdict(bool)
    for example in entry.examples:
        if example.variant != "pt-BR":
            tem_alternativa[example.sense_ord] = True

    kept: list[Example] = []
    per_sense: dict[int | None, int] = defaultdict(int)
    for example in entry.examples:
        bucket = example.sense_ord
        if example.variant == "pt-BR" and tem_alternativa[bucket]:
            continue
        if per_sense[bucket] >= MAX_EXAMPLES_PER_SENSE:
            continue
        per_sense[bucket] += 1
        kept.append(example)
    entry.examples = kept
