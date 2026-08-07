"""Expansão de afixos Hunspell: gera as formas flexionadas de cada lema.

Porque é que isto existe
------------------------
Numa app de leitura o utilizador escreve o que está no livro — *"couberam"*,
*"pusesse"*, *"ensonados"* — e tem de chegar a *caber*, *pôr*, *ensonado*
(plano 4.2). Fazer isso em runtime exigiria um lematizador no telemóvel. Em
vez disso expande-se tudo aqui, em tempo de build, para uma tabela `forms`
indexada. Em runtime é um `SELECT` e mais nada.

O formato
---------
Um dicionário Hunspell são dois ficheiros. O `.dic` lista lemas com bandeiras::

    caber/XYZ
    ensonado/OS

O `.aff` diz o que cada bandeira faz::

    SFX X Y 2
    SFX X   0     mos    .
    SFX X   er    íamos  er

Cada regra é `strip`, `add`, `condition`: tira-se `strip` do fim (ou do início,
para PFX), acrescenta-se `add`, desde que o lema case com `condition`.

Implementa-se o subconjunto que os dicionários de português usam: SFX, PFX,
produto cruzado, bandeiras de continuação, e as bandeiras de controlo que
mudam se uma forma é uma palavra real (`NEEDAFFIX`, `ONLYINCOMPOUND`,
`FORBIDDENWORD`). Compostos, que o português quase não usa, ficam de fora.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterator

# Quantas formas se aceita gerar por lema. Um verbo português regular dá umas
# 60 formas; este teto só existe para apanhar recursão patológica.
MAX_FORMS_PER_LEMMA = 400


@dataclass(frozen=True)
class Rule:
    """Uma linha SFX/PFX."""

    flag: str
    kind: str                    # 'SFX' | 'PFX'
    strip: str                   # '' quando o .aff diz '0'
    add: str
    condition: list["_Atom"]
    cont_flags: frozenset[str] = frozenset()   # bandeiras que `add` traz
    morph: str | None = None     # etiqueta morfológica, quando existe

    def apply(self, word: str) -> str | None:
        """Aplica a regra, ou devolve None se não casar."""
        if self.kind == "SFX":
            if self.strip and not word.endswith(self.strip):
                return None
            if not _matches_end(word, self.condition):
                return None
            stem = word[: len(word) - len(self.strip)] if self.strip else word
            # O radical pode ficar vazio — é assim que se declaram formas
            # supletivas ("pôr" -> "pusesse"). O que não pode ficar vazio é o
            # resultado.
            return (stem + self.add) or None
        else:
            if self.strip and not word.startswith(self.strip):
                return None
            if not _matches_start(word, self.condition):
                return None
            stem = word[len(self.strip):] if self.strip else word
            return (self.add + stem) or None


@dataclass
class AffixTable:
    """Um `.aff` lido."""

    rules: dict[str, list[Rule]] = field(default_factory=dict)
    cross_product: dict[str, bool] = field(default_factory=dict)
    flag_mode: str = "short"     # 'short' | 'long' | 'num' | 'utf8'
    encoding: str = "utf-8"
    needaffix: set[str] = field(default_factory=set)
    onlyincompound: set[str] = field(default_factory=set)
    forbiddenword: set[str] = field(default_factory=set)
    circumfix: set[str] = field(default_factory=set)

    def parse_flags(self, raw: str) -> frozenset[str]:
        return frozenset(_split_flags(raw, self.flag_mode))


@dataclass(frozen=True)
class GeneratedForm:
    form: str
    tag: str | None


# --- condições -------------------------------------------------------------

_Atom = tuple[bool, frozenset[str]]   # (negado, conjunto de caracteres)
_ANY: _Atom = (True, frozenset())     # '.' — nega o conjunto vazio = qualquer


def parse_condition(text: str) -> list[_Atom]:
    """Compila a condição de uma regra numa lista de átomos.

    A gramática é minúscula: `.`, `[abc]`, `[^abc]`, ou um literal. Cada átomo
    consome exatamente um caractere, o que torna a correspondência um simples
    percurso do fim (SFX) ou do início (PFX) da palavra.
    """
    if text in {".", ""}:
        return []
    atoms: list[_Atom] = []
    i = 0
    while i < len(text):
        char = text[i]
        if char == ".":
            atoms.append(_ANY)
            i += 1
        elif char == "[":
            end = text.find("]", i)
            if end == -1:                    # '[' sem fecho: trata como literal
                atoms.append((False, frozenset(char)))
                i += 1
                continue
            body = text[i + 1:end]
            negated = body.startswith("^")
            if negated:
                body = body[1:]
            atoms.append((negated, frozenset(body)))
            i = end + 1
        else:
            atoms.append((False, frozenset(char)))
            i += 1
    return atoms


def _atom_matches(atom: _Atom, char: str) -> bool:
    negated, chars = atom
    return (char not in chars) if negated else (char in chars)


def _matches_end(word: str, condition: list[_Atom]) -> bool:
    if not condition:
        return True
    if len(word) < len(condition):
        return False
    tail = word[-len(condition):]
    return all(_atom_matches(a, c) for a, c in zip(condition, tail))


def _matches_start(word: str, condition: list[_Atom]) -> bool:
    if not condition:
        return True
    if len(word) < len(condition):
        return False
    head = word[: len(condition)]
    return all(_atom_matches(a, c) for a, c in zip(condition, head))


# --- bandeiras -------------------------------------------------------------

def _split_flags(raw: str, mode: str) -> list[str]:
    raw = raw.strip()
    if not raw:
        return []
    if mode == "num":
        return [f.strip() for f in raw.split(",") if f.strip()]
    if mode == "long":
        return [raw[i:i + 2] for i in range(0, len(raw) - len(raw) % 2, 2)]
    return list(raw)


def _split_add(raw: str, mode: str) -> tuple[str, frozenset[str]]:
    """Separa `add` das bandeiras de continuação: 'mos/AB' -> ('mos', {A,B})."""
    if "/" not in raw:
        return ("" if raw == "0" else raw), frozenset()
    add, _, flags = raw.partition("/")
    return ("" if add == "0" else add), frozenset(_split_flags(flags, mode))


# --- leitura do .aff -------------------------------------------------------

_AFF_HEADER = re.compile(r"^(SFX|PFX)\s+(\S+)\s+(Y|N)\s+(\d+)", re.IGNORECASE)
_AFF_RULE = re.compile(r"^(SFX|PFX)\s+(\S+)\s+(\S+)\s+(\S+)(?:\s+(\S+))?(?:\s+(.*))?$",
                       re.IGNORECASE)


def read_aff(path: Path) -> AffixTable:
    table = AffixTable()

    # A codificação declara-se dentro do próprio ficheiro, por isso lê-se duas
    # vezes: uma tolerante para achar o SET, outra a sério.
    head = path.read_text(encoding="latin-1", errors="replace")
    for line in head.splitlines():
        if line.upper().startswith("SET "):
            table.encoding = line.split(None, 1)[1].strip().lower()
            break
        if line.upper().startswith("FLAG "):
            table.flag_mode = line.split(None, 1)[1].strip().lower()

    try:
        text = path.read_text(encoding=table.encoding, errors="replace")
    except LookupError:
        text = head

    for line in text.splitlines():
        line = line.split("#", 1)[0].rstrip()
        if not line.strip():
            continue
        upper = line.upper()

        if upper.startswith("FLAG "):
            table.flag_mode = line.split(None, 1)[1].strip().lower()
            continue
        for keyword, bucket in (
            ("NEEDAFFIX", table.needaffix),
            ("PSEUDOROOT", table.needaffix),      # nome antigo de NEEDAFFIX
            ("ONLYINCOMPOUND", table.onlyincompound),
            ("FORBIDDENWORD", table.forbiddenword),
            ("CIRCUMFIX", table.circumfix),
        ):
            if upper.startswith(keyword + " "):
                bucket.update(_split_flags(line.split(None, 1)[1], table.flag_mode))
                break

        header = _AFF_HEADER.match(line)
        if header:
            kind, flag, cross, _count = header.groups()
            table.cross_product[flag] = cross.upper() == "Y"
            table.rules.setdefault(flag, [])
            continue

        rule = _AFF_RULE.match(line)
        if not rule:
            continue
        kind, flag, strip, add_raw, condition_raw, morph = rule.groups()
        kind = kind.upper()
        if flag not in table.rules:
            # Regra sem cabeçalho: aceita-se, com produto cruzado por omissão.
            table.rules[flag] = []
            table.cross_product.setdefault(flag, True)
        add, cont = _split_add(add_raw, table.flag_mode)
        table.rules[flag].append(
            Rule(
                flag=flag,
                kind=kind,
                strip="" if strip == "0" else strip,
                add=add,
                condition=parse_condition(condition_raw or "."),
                cont_flags=cont,
                morph=(morph or "").strip() or None,
            )
        )
    return table


# --- leitura do .dic -------------------------------------------------------

def read_dic(path: Path, table: AffixTable) -> Iterator[tuple[str, frozenset[str], str | None]]:
    """Produz (lema, bandeiras, morfologia) para cada linha do `.dic`."""
    try:
        text = path.read_text(encoding=table.encoding, errors="replace")
    except LookupError:
        text = path.read_text(encoding="utf-8", errors="replace")

    lines = text.splitlines()
    start = 1 if lines and lines[0].strip().isdigit() else 0   # contagem inicial
    for line in lines[start:]:
        line = line.strip()
        if not line or line.startswith("\t") or line.startswith("#"):
            continue
        # Campos morfológicos vêm depois de um tab ou de dois espaços.
        word_part, _, morph = line.partition("\t")
        word_part = word_part.strip()
        if not word_part:
            continue
        word, _, flag_part = word_part.partition("/")
        word = word.replace("\\/", "/").strip()
        if not word:
            continue
        yield word, table.parse_flags(flag_part), (morph.strip() or None)


# --- expansão --------------------------------------------------------------

def expand(word: str, flags: frozenset[str], table: AffixTable) -> list[GeneratedForm]:
    """Todas as formas de um lema, o próprio incluído quando é palavra real.

    O lema não entra se estiver marcado `NEEDAFFIX` (só existe flexionado, por
    exemplo uma raiz verbal) ou `ONLYINCOMPOUND`.
    """
    if flags & table.forbiddenword:
        return []

    out: dict[str, str | None] = {}
    standalone = not (flags & table.needaffix or flags & table.onlyincompound)
    if standalone:
        out[word] = None

    suffixed: list[tuple[str, str | None]] = []

    # Primeiro os sufixos, que é o que o português usa quase sempre.
    for flag in flags:
        for rule in table.rules.get(flag, ()):
            if rule.kind != "SFX":
                continue
            form = rule.apply(word)
            if form is None:
                continue
            out.setdefault(form, rule.morph)
            suffixed.append((form, rule.morph))
            # Bandeiras de continuação: o sufixo pode habilitar outros.
            for cont in rule.cont_flags:
                for cont_rule in table.rules.get(cont, ()):
                    if cont_rule.kind != "SFX":
                        continue
                    chained = cont_rule.apply(form)
                    if chained:
                        out.setdefault(chained, cont_rule.morph or rule.morph)
            if len(out) > MAX_FORMS_PER_LEMMA:
                return _pack(out)

    # Depois os prefixos, sobre o lema e — quando ambas as bandeiras permitem
    # produto cruzado — sobre as formas sufixadas.
    for flag in flags:
        cross = table.cross_product.get(flag, True)
        for rule in table.rules.get(flag, ()):
            if rule.kind != "PFX":
                continue
            base = rule.apply(word)
            if base:
                out.setdefault(base, rule.morph)
            if not cross:
                continue
            for form, morph in suffixed:
                combined = rule.apply(form)
                if combined:
                    out.setdefault(combined, morph)
            if len(out) > MAX_FORMS_PER_LEMMA:
                return _pack(out)

    return _pack(out)


def _pack(out: dict[str, str | None]) -> list[GeneratedForm]:
    return [GeneratedForm(form=f, tag=t) for f, t in out.items()]
