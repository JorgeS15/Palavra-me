"""Onto.PT — ontologia lexical para o português (Universidade de Coimbra).

**CC BY 3.0 Unported**, declarada na página de descarga. É a licença mais
permissiva de toda a base: redistribuível, uso comercial permitido, só exige
atribuição, e não é copyleft.

Importa porque é construído, entre outros, a partir do **PAPEL** — ou seja, do
Dicionário PRO da Porto Editora — e aqui esse conteúdo vem com licença escrita.

O que traz, medido no ficheiro (v0.6)
-------------------------------------
* **117 450 synsets**, 156 623 formas lexicais, 166 574 relações
* **46 374 synsets com definição** (39%)
* confrontado com a nossa base de 181 751 lemas:
  - dos 5 285 sem definição, **651 ganham definição** e mais 633 ganham
    pelo menos um sinónimo
  - **68 332 lemas** — 38% da base inteira — ganham sinónimos novos

O ganho no buraco é modesto, e era previsível: dois dos dicionários de origem
do Onto.PT são o Dicionário Aberto e o Wikcionário, que já temos inteiros. O
valor está sobretudo no segundo número — sinónimos de dicionários portugueses
por toda a base, onde hoje só há os do PULO, alinhados com a WordNet inglesa.

Formato
-------
Turtle: prefixos no cabeçalho, sujeito seguido de pares
`predicado objecto` separados por `;`, objetos múltiplos separados por `,`, e
a instrução a fechar num `.`::

    OntoPT:77 a OntoPT:AdjectivoSynset ;
        OntoPT:definicao "diz-se do que é o primeiro ou o mais antigo..." ;
        OntoPT:formaLexical "primeiro" , "primitivo" , "original" ;
        OntoPT:antonimoAdjDe OntoPT:2084 , OntoPT:3622 .

**Não é o esquema WordNet RDF/OWL** que a página anuncia: não há nível
`wordsense`, o synset tem as formas diretamente. A primeira versão deste
módulo foi escrita a partir da documentação e estava errada de ponta a ponta;
esta foi escrita com o ficheiro aberto ao lado.
"""

from __future__ import annotations

import re
from typing import Iterable, Iterator, Optional

from ..schema import Relation, Sense, SourceEntry, canonical_pos
from ..text import limpar_glosa, parece_do_brasil
from .base import License, Source, SourceInfo, SourceUnavailable

INFO = SourceInfo(
    slug="ontopt",
    name="Onto.PT (Universidade de Coimbra)",
    url="https://ontopt.dei.uc.pt/",
    license=License(
        name="CC BY 3.0 Unported",
        url="https://creativecommons.org/licenses/by/3.0/",
        attribution=(
            "Onto.PT, por Universidade de Coimbra, licenciado sob "
            "Creative Commons Attribution 3.0 Unported. "
            "https://ontopt.dei.uc.pt/"
        ),
        redistributable=True,
        verified=True,   # 2026-08-06: licença declarada na página de descarga
        notes=(
            "A licença está escrita no rodapé de "
            "https://ontopt.dei.uc.pt/index.php?sec=download_ontopt — ao "
            "contrário do PAPEL, que não publica nenhuma. Como o Onto.PT "
            "incorpora o PAPEL, é por aqui que esse conteúdo entra com "
            "licença escrita. Atenção: a página 'Outros recursos' do mesmo "
            "sítio (CARTÃO, tesauros, triplos de 10 recursos) NÃO declara "
            "licença e não está coberta por esta nota."
        ),
    ),
    provides=("senses", "pos"),
    primary="OntoPTv0.6_n3.zip",
    endpoints={
        "OntoPTv0.6_n3.zip":
            "https://ontopt.dei.uc.pt/recursos/OntoPTv0.6_n3.zip",
    },
    manual=(
        "https://ontopt.dei.uc.pt/index.php?sec=download_ontopt tem as "
        "ligações para N3 e RDF/XML. Depois: "
        "palavrame fetch --source ontopt --url <URL>"
    ),
)

# Acima disto o synset deixa de ser um conjunto de sinónimos.
#
# 99% dos synsets do Onto.PT têm 15 membros ou menos e 98% têm 10 ou menos —
# a distribuição é saudável. Mas a cauda traz o mesmo defeito que obrigou a
# limitar o PULO: um synset de 18 membros que junta *grande, bom, largo,
# belo, nobre, liberal, generoso, franco, bizarro*. O maior tem 145.
#
# O corte vale só para a sinonímia. Um synset grande pode ter uma definição
# perfeitamente boa, e essa aproveita-se na mesma.
MAX_SINONIMOS_POR_SYNSET = 10

# Quantos sinónimos guardar por palavra. Sem teto, palavras muito polissémicas
# arrastavam dezenas de sinónimos de synsets diferentes e a entrada deixava de
# se ler.
MAX_SINONIMOS_POR_PALAVRA = 12

# O Onto.PT entra como fonte de DEFINIÇÕES, não de relações.
#
# Os synsets são descobertos automaticamente e erram de forma visível. O
# `urso-formigueiro` ficou no mesmo synset que *comichão*, *dormência* e
# *formigamento* — o Onto.PT fundiu o animal com o formigueiro de "sentir
# formigueiro nas pernas". O `esfuziante` saiu com *inefável* e *flamífero*.
#
# O PAPEL cobre a mesma necessidade com relações que foram avaliadas à mão a
# 99-100% de precisão, e nas mesmas palavras acerta: `urso-formigueiro` ->
# *papa-formigas*, `ensonado` -> *sonolento*, `esfuziante` -> *deslumbrante*.
# Cobre menos lemas (53 mil contra 96 mil), e é uma troca que vale a pena:
# mais vale não sugerir sinónimo nenhum do que dizer que `urso-formigueiro`
# quer dizer *comichão*.
#
# As definições são outra história — dessas o PAPEL não tem nenhuma, e as do
# Onto.PT são boas: `ensonado` -> "Que tem sono", `macilento` -> "Da cor do
# barro, amarelo pálido".
#
# Pôr a verdadeiro devolve as relações, se um dia houver razão.
RELACOES_DO_ONTOPT = False

_CLASSES = {
    "NomeSynset": "substantivo",
    "VerboSynset": "verbo",
    "AdjectivoSynset": "adjetivo",
    "AdverbioSynset": "adverbio",
}

_RELACOES = {
    "hiperonimoDe": "hiponimo",     # A hiperónimoDe B -> B é hipónimo de A
    "hiponimoDe": "hiperonimo",
    "antonimoAdjDe": "antonimo",
    "antonimoNDe": "antonimo",
    "antonimoVDe": "antonimo",
    "antonimoAdvDe": "antonimo",
}

_LITERAL = re.compile(r'"((?:[^"\\]|\\.)*)"(?:@\w+|\^\^\S+)?')
_REFERENCIA = re.compile(r"OntoPT:(\d+)")


def instrucoes(texto: str) -> Iterator[str]:
    """Parte o Turtle em instruções, respeitando o ponto dentro de aspas.

    Um `.` só fecha a instrução quando está fora de um literal — senão
    `"diz-se do que é o primeiro..."` partia a meio, e as definições são
    precisamente o que tem pontos lá dentro.
    """
    buffer: list = []
    dentro = escapa = False
    for char in texto:
        if escapa:
            buffer.append(char)
            escapa = False
            continue
        if char == "\\":
            buffer.append(char)
            escapa = True
            continue
        if char == '"':
            dentro = not dentro
        if char == "." and not dentro:
            yield "".join(buffer)
            buffer = []
            continue
        buffer.append(char)
    if buffer:
        yield "".join(buffer)


class Synset:
    __slots__ = ("pos", "formas", "definicao", "ligacoes")

    def __init__(self) -> None:
        self.pos = "desconhecido"
        self.formas: list = []
        self.definicao: Optional[str] = None
        self.ligacoes: list = []      # (relacao_canonica, synset_alvo)


class OntoPt(Source):
    info = INFO

    def fetch(self) -> None:
        for name, url in self.info.endpoints.items():
            self.cache.fetch(url, self.slug, name)

    def parse(self, lemmas: Optional[Iterable[str]] = None) -> Iterator[SourceEntry]:
        wanted = self._wanted(lemmas)
        synsets = self._ler_synsets()
        yield from self._montar(synsets, wanted)

    # --- leitura ----------------------------------------------------------

    def _ler_synsets(self) -> "dict[str, Synset]":
        synsets: "dict[str, Synset]" = {}
        for instrucao in instrucoes(self._texto()):
            instrucao = instrucao.strip()
            if not instrucao.startswith("OntoPT:"):
                continue                      # prefixos, classes, propriedades
            cabeca, _, resto = instrucao.partition(" ")
            identificador = cabeca.split(":", 1)[1]
            if not identificador.isdigit():
                continue                      # declarações da ontologia

            synset = synsets.setdefault(identificador, Synset())
            for pedaco in resto.split(";"):
                pedaco = pedaco.strip()
                if not pedaco:
                    continue
                predicado, _, objetos = pedaco.partition(" ")
                self._aplicar(synset, predicado, objetos.strip())
        return synsets

    def _aplicar(self, synset: Synset, predicado: str, objetos: str) -> None:
        if predicado == "a":
            synset.pos = canonical_pos(
                _CLASSES.get(objetos.replace("OntoPT:", "").strip())
            )
            return

        nome = predicado.split(":", 1)[-1]
        if nome == "formaLexical":
            for encontrado in _LITERAL.finditer(objetos):
                forma = encontrado.group(1).replace("_", " ").strip()
                # A grafia brasileira entra nas formas tal como entrava nas
                # glosas do PULO: `abdômen`, `acadêmico`, `abstêmio`. São 895,
                # e apareceriam como sinónimos numa app portuguesa.
                if forma and not parece_do_brasil(forma):
                    synset.formas.append(forma)
        elif nome == "definicao":
            encontrado = _LITERAL.search(objetos)
            if encontrado:
                synset.definicao = encontrado.group(1)
        elif nome in _RELACOES:
            for alvo in _REFERENCIA.findall(objetos):
                synset.ligacoes.append((_RELACOES[nome], alvo))

    # --- montagem ---------------------------------------------------------

    def _montar(self, synsets, wanted) -> Iterator[SourceEntry]:
        entradas: "dict[str, SourceEntry]" = {}
        rejeitadas: set = set()

        def entrada(palavra: str) -> Optional[SourceEntry]:
            if palavra in rejeitadas:
                return None
            e = entradas.get(palavra)
            if e is None:
                e = SourceEntry(lemma=palavra, source=self.slug)
                if wanted is not None and e.normalized not in wanted:
                    rejeitadas.add(palavra)
                    return None
                entradas[palavra] = e
            return e

        for synset in synsets.values():
            formas = _unicas(synset.formas)
            if not formas:
                continue

            definicao = None
            if synset.definicao and not parece_do_brasil(synset.definicao):
                definicao = limpar_glosa(synset.definicao)

            for palavra in formas:
                e = entrada(palavra)
                if e is None:
                    continue
                if e.pos == "desconhecido":
                    e.pos = synset.pos
                if definicao and definicao not in [s.definition for s in e.senses]:
                    e.senses.append(
                        Sense(definition=definicao, source=self.slug,
                              ord=len(e.senses) + 1)
                    )
                # Sinonímia: só dentro de synsets de tamanho defensável.
                if RELACOES_DO_ONTOPT and len(formas) <= MAX_SINONIMOS_POR_SYNSET:
                    for outra in formas:
                        if outra != palavra:
                            e.relations.append(
                                Relation(target=outra, relation="sinonimo",
                                         source=self.slug)
                            )

        if RELACOES_DO_ONTOPT:
            self._ligar(synsets, entrada)

        for e in entradas.values():
            e.relations = _limitar(_sem_repetidas(e.relations))
            if e.senses or e.relations:
                yield e

    def _ligar(self, synsets, entrada) -> None:
        """Relações entre synsets, expandidas para relações entre palavras.

        **Só para a primeira forma do synset de destino**, não para todas.
        Um synset de dez membros ligado a outro de dez dava cem pares, e a
        primeira medição produziu 700 mil relações de hiperonímia a partir de
        173 mil relações reais. Quem lê quer saber que `urso-formigueiro` é um
        `mamífero` — não quer os outros nove nomes do mesmo synset, que a
        sinonímia já dá se for a esse.

        A primeira forma é a representativa: é a ordem em que o Onto.PT as
        escreve.
        """
        inverso = {"hiperonimo": "hiponimo", "hiponimo": "hiperonimo"}
        for synset in synsets.values():
            origens = _unicas(synset.formas)[:MAX_SINONIMOS_POR_SYNSET]
            for relacao, alvo_id in synset.ligacoes:
                alvo = synsets.get(alvo_id)
                if alvo is None:
                    continue
                destinos = _unicas(alvo.formas)[:1]
                for a in origens:
                    for b in destinos:
                        if a == b:
                            continue
                        if (e := entrada(a)) is not None:
                            e.relations.append(
                                Relation(target=b, relation=relacao,
                                         source=self.slug)
                            )
                        if (e := entrada(b)) is not None:
                            e.relations.append(
                                Relation(target=a,
                                         relation=inverso.get(relacao, relacao),
                                         source=self.slug)
                            )

    def _texto(self) -> str:
        import zipfile

        base = self.cache.paths.cache / self.slug
        if not base.is_dir():
            raise SourceUnavailable(
                f"Falta o pacote do Onto.PT em {base}. {self.info.manual}"
            )

        for arquivo in sorted(base.glob("*.zip")):
            with zipfile.ZipFile(arquivo) as zf:
                for membro in zf.namelist():
                    if membro.lower().endswith((".n3", ".ttl", ".nt")):
                        return zf.read(membro).decode("utf-8", "replace")

        for solto in sorted(base.glob("*.n3")) + sorted(base.glob("*.ttl")):
            return solto.read_text(encoding="utf-8", errors="replace")

        raise SourceUnavailable(
            f"O pacote do Onto.PT em {base} não tem ficheiros N3. "
            f"{self.info.manual}"
        )


def _unicas(formas: "list[str]") -> "list[str]":
    """Sem repetidas, mantendo a ordem: a primeira costuma ser a principal."""
    vistas, saida = set(), []
    for f in formas:
        if f and f not in vistas:
            vistas.add(f)
            saida.append(f)
    return saida


def _sem_repetidas(relacoes: "list[Relation]") -> "list[Relation]":
    vistas, saida = set(), []
    for r in relacoes:
        chave = (r.target, r.relation)
        if chave not in vistas:
            vistas.add(chave)
            saida.append(r)
    return saida


def _limitar(relacoes: "list[Relation]") -> "list[Relation]":
    """Teto por tipo de relação, para a entrada continuar legível."""
    contagem: "dict[str, int]" = {}
    saida = []
    for r in relacoes:
        n = contagem.get(r.relation, 0)
        if n >= MAX_SINONIMOS_POR_PALAVRA:
            continue
        contagem[r.relation] = n + 1
        saida.append(r)
    return saida
