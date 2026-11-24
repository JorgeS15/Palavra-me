"""Interface de linha de comandos do pipeline.

O comando que interessa é `f0`: corre o protótipo do plano de ponta a ponta —
fontes, fusão, DB, validação, folha de revisão — sobre a lista de 100 lemas.
Tudo o resto são as suas peças, disponíveis à parte para depurar.

    palavrame fontes                 # estado das licenças
    palavrame fetch --source tatoeba # traz ficheiros para o cache
    palavrame f0                     # protótipo completo
    palavrame gerar --backend ollama # AMALIA em batch
    palavrame rever                  # aprovar/rejeitar o que o AMALIA gerou
    palavrame validar --db out/dicionario-f0.db --distribuicao

    palavrame fetch --lingua en      # fontes do dicionário de inglês
    palavrame en                     # build do dicionário de inglês
    palavrame empacotar --lingua en --db out/dicionario-en-1.db
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Sequence

from . import __version__
from .cache import Cache, OfflineError
from .config import default_paths
from .merge import merge_entries
from .report import build_report, write_reports
from .schema import MergedEntry, SourceEntry, dump_jsonl
from .sources import LINGUAS, REGISTRY, all_infos, build as build_source, registo
from .sources.base import SourceUnavailable
from .validate import validate_database

DEFAULT_SEEDS = "lemas-f0.txt"
# Sondas de pesquisa: escrito no livro -> lema que a app tem de encontrar.
DEFAULT_PROBES = (
    ("couberam", "caber"),
    ("pusesse", "pôr"),
    ("ensonados", "ensonado"),
    ("fizeram", "fazer"),
    ("rapariga", "rapariga"),
)


def _forcar_utf8_na_saida() -> None:
    """Garante que a saída aguenta acentos e símbolos, também no Windows.

    Em Windows a codificação por omissão de `stdout` é a do sistema (cp1252 em
    Portugal) quando a saída é redirecionada para um ficheiro ou um pipe. Um
    símbolo fora dessa página — o `∅` que aparece quando uma sonda de pesquisa
    falha, por exemplo — rebentaria com `UnicodeEncodeError`, e o relatório
    perder-se-ia por causa de um caractere.

    `errors="replace"` em vez de `"strict"`: num terminal que não saiba
    desenhar um símbolo, mais vale um `?` do que perder o relatório todo.
    """
    for fluxo in (sys.stdout, sys.stderr):
        reconfigure = getattr(fluxo, "reconfigure", None)
        if reconfigure is not None:
            try:
                reconfigure(encoding="utf-8", errors="replace")
            except (ValueError, OSError):
                pass          # fluxo já fechado ou substituído; segue na mesma


def main(argv: Sequence[str] | None = None) -> int:
    _forcar_utf8_na_saida()
    parser = _build_parser()
    args = parser.parse_args(argv)
    if not getattr(args, "func", None):
        parser.print_help()
        return 1
    try:
        return args.func(args)
    except (SourceUnavailable, OfflineError) as exc:
        print(f"\n  Fonte indisponível: {exc}\n", file=sys.stderr)
        return 2
    except KeyboardInterrupt:
        print("\n  Interrompido.", file=sys.stderr)
        return 130


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="palavrame",
        description="Pipeline de construção do dicionário da app Palavra-me.",
    )
    parser.add_argument("--version", action="version", version=f"palavrame {__version__}")
    parser.add_argument(
        "--offline", action="store_true",
        help="nunca descarrega; usa só o que já está em cache",
    )
    sub = parser.add_subparsers(dest="comando")

    p = sub.add_parser("fontes", help="estado das fontes e das licenças")
    p.add_argument("--lingua", choices=LINGUAS, default="pt")
    p.add_argument("--markdown", action="store_true", help="saída para notas/fontes.md")
    p.set_defaults(func=cmd_fontes)

    p = sub.add_parser("fetch", help="descarrega ficheiros brutos para o cache")
    p.add_argument("--lingua", choices=LINGUAS, default="pt",
                   help="de que dicionário são as fontes (por omissão, pt)")
    p.add_argument("--source", action="append", dest="sources",
                   help="repetível; por omissão, todas")
    p.add_argument("--seeds", default=DEFAULT_SEEDS,
                   help="lista de lemas para as fontes que buscam à peça")
    p.add_argument("--completo", action="store_true",
                   help="F1: descarrega o dicionário inteiro (dump do "
                        "Dicionário Aberto) em vez de buscar só os seeds")
    p.add_argument("--url",
                   help="descarrega deste URL em vez do que está no código; "
                        "exige --source. Serve para quando um URL muda")
    p.add_argument("--ficheiro",
                   help="usa um ficheiro já descarregado à mão; exige --source")
    p.set_defaults(func=cmd_fetch)

    p = sub.add_parser("f0", help="protótipo da F0 sobre a lista de lemas")
    p.add_argument("--seeds", default=DEFAULT_SEEDS)
    p.add_argument("--db-version", default="f0")
    p.add_argument("--strict-backbone", action="store_true",
                   help="exige o VOC como autoridade de lemas (F1 em diante)")
    p.set_defaults(func=cmd_f0)

    p = sub.add_parser("f1", help="build completa: o dicionário inteiro")
    p.add_argument("--db-version", default="1")
    p.add_argument("--strict-backbone", action="store_true",
                   help="exige o VOC como autoridade de lemas")
    p.set_defaults(func=cmd_f1)

    p = sub.add_parser("en", help="build do dicionário de inglês")
    p.add_argument("--db-version", default="1")
    p.add_argument("--seeds", default=None,
                   help="só estes lemas (protótipo); por omissão, todos")
    p.set_defaults(func=cmd_en)

    p = sub.add_parser("gerar", help="gera exemplos com o AMALIA (batch)")
    p.add_argument("--entries", default=None, help="JSONL de entradas fundidas")
    p.add_argument("--backend", choices=("ollama", "echo"), default="ollama")
    p.add_argument("--model", default=None)
    p.add_argument("--limit", type=int, default=None)
    p.set_defaults(func=cmd_gerar)

    p = sub.add_parser("rever", help="revisão humana dos exemplos gerados")
    p.add_argument("--candidates", default=None)
    p.set_defaults(func=cmd_rever)

    p = sub.add_parser(
        "empacotar",
        help="prepara a DB para ir dentro do APK (comprime para assets/)",
    )
    p.add_argument("--db", required=True)
    p.add_argument("--lingua", choices=LINGUAS, default="pt",
                   help="en escreve dicionario-en.db.gz em vez de dicionario.db.gz")
    p.add_argument("--destino", default="../android/app/src/main/assets")
    p.add_argument("--completa", action="store_true",
                   help="empacota a base inteira, sem tirar o que a app não usa")
    p.set_defaults(func=cmd_empacotar)

    p = sub.add_parser("validar", help="valida uma DB construída")
    p.add_argument("--db", required=True)
    p.add_argument("--distribuicao", action="store_true",
                   help="modo severo: licenças por verificar bloqueiam")
    p.set_defaults(func=cmd_validar)

    p = sub.add_parser("lacunas", help="lista os lemas sem definição, para curar")
    p.add_argument("--db", required=True)
    p.add_argument("--saida", help="ficheiro de saída (por omissão: lacunas.txt ao lado da DB)")
    p.set_defaults(func=cmd_lacunas)

    p = sub.add_parser("rever-curadoria",
                       help="revisão interativa das definições curadas (aceitar/editar/apagar)")
    p.add_argument("--ficheiro", help="por omissão: seeds/curadoria.csv")
    p.set_defaults(func=cmd_rever_curadoria)

    return parser


# --- comandos --------------------------------------------------------------


def cmd_fontes(args) -> int:
    infos = all_infos(args.lingua)
    if args.markdown:
        print(_fontes_markdown(infos))
        return 0

    print()
    for info in infos:
        lic = info.license
        state = "verificada" if lic.verified else "POR VERIFICAR"
        redis = {True: "sim", False: "NÃO", None: "?"}[lic.redistributable]
        print(f"  {info.slug:<20} {info.name}")
        print(f"  {'':<20} licença: {lic.name}  [{state}]  redistribuível: {redis}")
        print(f"  {'':<20} dá: {', '.join(info.provides) or '—'}")
        if info.manual:
            print(f"  {'':<20} manual: {info.manual}")
        print()

    pending = [i.slug for i in infos if not i.license.verified]
    if pending:
        print(f"  {len(pending)} fontes por verificar: {', '.join(pending)}")
        print("  Preencher notas/fontes.md antes da F1 (plano, secção 8).\n")
    return 0


def cmd_fetch(args) -> int:
    paths = default_paths().ensure()
    cache = Cache(paths, offline=args.offline)
    slugs = args.sources or list(registo(args.lingua))
    lemmas = _read_seeds(paths, args.seeds) if args.lingua == "pt" else []

    override = getattr(args, "url", None) or getattr(args, "ficheiro", None)
    if override:
        if len(slugs) != 1 or not args.sources:
            print("  --url e --ficheiro exigem exatamente um --source.",
                  file=sys.stderr)
            return 2
        return _fetch_override(cache, slugs[0], args)

    failures = 0
    for slug in slugs:
        source = build_source(slug, cache)
        print(f"  {slug} … ", end="", flush=True)
        try:
            if slug == "dicionario_aberto" and not args.completo:
                source.fetch(lemmas)      # busca à peça, um lema por ficheiro
            else:
                source.fetch()            # com --completo, o DA traz o dump
            print("ok")
        except (SourceUnavailable, OfflineError) as exc:
            failures += 1
            print(f"indisponível\n      {exc}")
        except Exception as exc:
            failures += 1
            print(f"falhou\n      {type(exc).__name__}: {exc}")

    print(f"\n  {len(slugs) - failures}/{len(slugs)} fontes prontas.")
    return 0 if failures == 0 else 1


def _parse_source(slug, cache, lemmas):
    """Corre o parse de uma fonte com o relato de erros padrão do pipeline."""
    source = build_source(slug, cache)
    try:
        produced = list(source.parse(lemmas))
    except SourceUnavailable as exc:
        print(f"  {slug:<20} indisponível — {exc}")
        return source, None
    except Exception as exc:
        print(f"  {slug:<20} erro: {type(exc).__name__}: {exc}")
        return source, None
    print(f"  {slug:<20} {len(produced)} entradas")
    return source, produced


def _stream_source(slug, cache, lemmas, consumir):
    """Passa as entradas de uma fonte a `consumir` sem as juntar numa lista.

    É a diferença entre a F1 caber em memória e não caber: `list(parse())`
    do Wikcionário são 400 mil objetos vivos ao mesmo tempo. Aqui cada
    entrada é entregue e libertada.
    """
    source = build_source(slug, cache)
    contador = [0]

    def contadas(it):
        for entry in it:
            contador[0] += 1
            yield entry

    try:
        consumir(contadas(source.parse(lemmas)))
    except SourceUnavailable as exc:
        print(f"  {slug:<20} indisponível — {exc}")
        return source, 0
    except Exception as exc:
        print(f"  {slug:<20} erro: {type(exc).__name__}: {exc}")
        return source, 0
    print(f"  {slug:<20} {contador[0]} entradas")
    return source, contador[0]


def _collect_amalia(paths, entries, available) -> None:
    # Exemplos do AMALIA já aprovados na revisão. Não é uma fonte com
    # download: vem de `work/`, onde `palavrame rever` os deixou.
    from .generate.source import INFO as AMALIA_INFO, approved_entries

    aprovados = approved_entries(paths.work / "candidatos-amalia.jsonl")
    if aprovados:
        entries.extend(aprovados)
        available.append(AMALIA_INFO)
        total = sum(len(e.examples) for e in aprovados)
        print(f"  {'amalia':<20} {total} exemplos aprovados na revisão")


def cmd_f0(args) -> int:
    paths = default_paths().ensure()
    cache = Cache(paths, offline=args.offline)
    lemmas = _read_seeds(paths, args.seeds)
    print(f"\n  {len(lemmas)} lemas na lista da F0.\n")

    entries: list[SourceEntry] = []
    available: list = []
    for slug in REGISTRY:
        source, produced = _parse_source(slug, cache, lemmas)
        if produced:
            available.append(source.info)
            entries.extend(produced)

    _collect_amalia(paths, entries, available)

    if not entries:
        print(
            "\n  Nenhuma fonte produziu dados. Corre `palavrame fetch` primeiro "
            "(precisa de rede) ou coloca ficheiros em pipeline/cache/.\n"
        )
        return 2

    result = merge_entries(entries, strict_backbone=args.strict_backbone)
    return _finish_build(
        result, paths, available,
        db_version=args.db_version,
        extra_meta={"fase": "F0", "seeds": args.seeds},
        probes=_probes(lemmas),
        next_step=(
            f"Passo seguinte (plano F0.3): lê "
            f"{paths.out}/revisao-{args.db_version}.md e decide se a "
            f"qualidade chega."
        ),
    )


def _finish_build(result, paths, available, *, db_version, extra_meta,
                  probes, next_step) -> int:
    print(f"\n  Fusão: {len(result.entries)} lemas, "
          f"autoridade de lemas: {result.backbone or 'nenhuma (modo permissivo)'}, "
          f"{result.conflicts.total()} conflitos.")

    # Curadoria de origem: rótulos escritos à mão (Brasil, Estrangeirismo…)
    # para as palavras que a fonte não etiquetou. Ver `palavrame.origem`.
    from .origem import aplicar as aplicar_origem, carregar as carregar_origem

    marcadas = aplicar_origem(result.entries, carregar_origem(paths.seeds))
    if marcadas:
        print(f"  Curadoria de origem: {marcadas} aceções marcadas.")

    dump_jsonl(result.entries, paths.work / "entries.jsonl")

    from .build import build_database

    db_path = paths.out / f"dicionario-{db_version}.db"
    stats = build_database(
        db_path, result.entries, available or all_infos(),
        db_version=db_version,
        extra_meta=extra_meta,
    )
    print(f"  DB: {db_path} ({stats['bytes'] / 1024:.0f} KiB)")

    report = build_report(db_version, result.entries, stats,
                          result.conflicts, available or all_infos())
    written = write_reports(paths.out, report, result.entries)

    from .sources.curadoria import FICHEIRO as CURADORIA_CSV, lemas_acrescentados

    validation = validate_database(
        db_path, available or all_infos(), distribution=False, probes=probes,
        acrescentos=lemas_acrescentados(paths.seeds / CURADORIA_CSV),
    )
    print("\n  Validação:")
    print(validation.render())

    print("\n  Escrito:")
    for path in written:
        print(f"    {path}")
    print(f"\n  {next_step}\n")
    return 0 if validation.ok else 1


def cmd_f1(args) -> int:
    """F1: o pipeline sobre o dicionário INTEIRO, sem lista de seeds.

    Três rondas, para não segurar as fontes todas em memória (ver `Merger`):

    1. atestação — cada fonte de lemas é lida e reduzida a strings;
    2. aplicação — as mesmas fontes são relidas e o conteúdo entra;
    3. exemplos — Tatoeba e Leipzig, resolvidos pelo índice de formas.

    Precisa do dump completo no cache: `palavrame fetch --completo`.
    """
    from .config import EXAMPLE_SOURCE_PRIORITY
    from .merge.merger import Merger, NON_LEMMA_SOURCES, ordem_de_aplicacao

    paths = default_paths().ensure()
    cache = Cache(paths, offline=args.offline)
    print("\n  F1 — dicionário completo.\n")

    # Quem ATESTA lemas (1ª volta) e quem só os PREENCHE (2ª) são perguntas
    # diferentes, e confundi-las custou caro. O wordnet preenche — dá glosas —
    # mas não atesta: as suas entradas vêm alinhadas com a WordNet inglesa.
    fontes_de_lemas = ordem_de_aplicacao(
        [s for s in REGISTRY if s not in NON_LEMMA_SOURCES]
    )
    # A 3ª volta é só para quem se resolve pela FORMA e precisa do índice de
    # formas cheio — Tatoeba e Leipzig. Dividir aqui por NON_LEMMA_SOURCES,
    # como se fazia, atirava o wordnet para depois da curadoria e invertia a
    # prioridade das aceções que o `config` define.
    fontes_de_conteudo = ordem_de_aplicacao(
        [s for s in REGISTRY if s not in EXAMPLE_SOURCE_PRIORITY]
    )
    fontes_de_exemplos = [s for s in REGISTRY if s in EXAMPLE_SOURCE_PRIORITY]

    merger = Merger(strict_backbone=args.strict_backbone)
    available: list = []
    disponiveis: list[str] = []

    print("  1/3 — que palavras existem\n")
    for slug in fontes_de_lemas:
        source, n = _stream_source(
            slug, cache, None, lambda it, s=slug: merger.atestar(s, it)
        )
        if n:
            available.append(source.info)
            disponiveis.append(slug)

    merger.fechar_lemas()
    if not merger.merged:
        print(
            "\n  Nenhuma fonte de lemas produziu dados. Corre "
            "`palavrame fetch --completo` primeiro (precisa de rede).\n"
        )
        return 2
    universo = {e.normalized for e in merger.merged.values()}
    print(f"\n  universo: {len(merger.merged)} lemas "
          f"({len(universo)} formas normalizadas)\n")

    print("  2/3 — definições, flexões, relações\n")
    for slug in fontes_de_conteudo:
        source = build_source(slug, cache)
        try:
            merger.aplicar(slug, source.parse(None))
        except SourceUnavailable:
            continue
        if slug not in disponiveis:
            # Fontes que não atestam lemas (o wordnet) não passaram pela 1ª
            # volta e ainda não estão na lista de atribuição.
            disponiveis.append(slug)
            available.append(source.info)
        print(f"  {slug:<20} aplicada")

    print("\n  3/3 — exemplos\n")
    entries: list[SourceEntry] = []
    for slug in fontes_de_exemplos:
        source, n = _stream_source(
            slug, cache, universo, lambda it, s=slug: merger.aplicar(s, it)
        )
        if n:
            available.append(source.info)

    _collect_amalia(paths, entries, available)
    if entries:
        merger.aplicar("amalia", entries)
    del entries

    result = merger.terminar()
    return _finish_build(
        result, paths, available,
        db_version=args.db_version,
        extra_meta={"fase": "F1"},
        probes=_probes(["caber", "pôr", "ensonado", "janela"]),
        next_step=(
            "F1 construída. Valida para distribuição com: "
            "python -m palavrame.cli validar --db "
            f"{paths.out / f'dicionario-{args.db_version}.db'} --distribuicao"
        ),
    )


def cmd_en(args) -> int:
    """O dicionário de inglês: Wiktionary inglês + frequências do Leipzig.

    Mais simples do que a F1 portuguesa (ver `palavrame.ingles`): uma fonte de
    conteúdo e uma de frequência. Precisa de `palavrame fetch --lingua en`.
    """
    from .build import build_database
    from .ingles import fundir, ranks_de
    from .merge.merger import ConflictReport

    paths = default_paths().ensure()
    cache = Cache(paths, offline=args.offline)
    lemmas = _read_seeds(paths, args.seeds) if args.seeds else None
    alcance = f"{len(lemmas)} lemas da lista" if lemmas else "completo"
    print(f"\n  Dicionário de inglês — {alcance}.\n")

    # As formas flexionadas de um lema da lista também têm de passar o filtro
    # da fonte, senão `ran` ficava de fora quando se pede `run`. Por isso o
    # Wiktionary lê-se inteiro e o recorte faz-se depois da fusão.
    wikt = build_source("wiktionary_en", cache)
    leipzig = build_source("leipzig_en", cache)

    ranks = ranks_de(leipzig.parse(None))
    print(f"  {'leipzig_en':<20} {len(ranks)} frequências")

    entradas = fundir(wikt.parse(None), ranks)
    if lemmas is not None:
        pedidos = set(lemmas)
        entradas = [e for e in entradas if e.lemma in pedidos]
    print(f"  {'wiktionary_en':<20} {len(entradas)} lemas")
    if not entradas:
        print("\n  Nada para construir. Corre `palavrame fetch --lingua en` "
              "primeiro (precisa de rede).\n")
        return 2

    infos = [wikt.info] + ([leipzig.info] if ranks else [])
    versao = f"en-{args.db_version}"
    db_path = paths.out / f"dicionario-{versao}.db"
    stats = build_database(
        db_path, entradas, infos, db_version=versao,
        extra_meta={"lingua": "en", "fase": "EN" if lemmas is None else "EN-F0"},
    )
    print(f"  DB: {db_path} ({stats['bytes'] / 1048576:.1f} MB)")

    report = build_report(versao, entradas, stats, ConflictReport(), infos)
    written = write_reports(paths.out, report, entradas)

    sondas = [(w, l) for w, l in PROBES_EN if lemmas is None or l in set(lemmas)]
    validation = validate_database(db_path, infos, distribution=False, probes=sondas)
    print("\n  Validação:")
    print(validation.render())
    print("\n  Escrito:")
    for path in written:
        print(f"    {path}")
    print(f"\n  Para a app: python -m palavrame.cli empacotar --lingua en --db {db_path}\n")
    return 0 if validation.ok else 1


# Sondas da pesquisa em inglês: flexões irregulares, o apóstrofo tipográfico dos
# ebooks, e uma grafia britânica.
PROBES_EN = (
    ("ran", "run"),
    ("dogs", "dog"),
    ("went", "go"),
    ("bleakest", "bleak"),
    ("wouldn\u2019t", "wouldn't"),
    ("colour", "colour"),
)


def cmd_gerar(args) -> int:
    from .generate import EchoBackend, OllamaBackend
    from .generate.runner import DEFAULT_MODEL, generate_examples, save_candidates
    from .schema import load_jsonl

    paths = default_paths().ensure()
    entries_path = Path(args.entries) if args.entries else paths.work / "entries.jsonl"
    if not entries_path.exists():
        print(f"  Falta {entries_path}. Corre `palavrame f0` primeiro.", file=sys.stderr)
        return 2

    entries = load_jsonl(entries_path, MergedEntry)
    if args.limit:
        entries = entries[: args.limit]

    if args.backend == "echo":
        backend = EchoBackend(lambda prompt: "")
        print("  Backend `echo`: não gera nada. Serve para testar o circuito.")
    else:
        backend = OllamaBackend(model=args.model or DEFAULT_MODEL)
        print(f"  AMALIA via Ollama: {backend.model}")
        print("  Isto demora. É suposto demorar — corre em background (plano 5.3).\n")

    candidates = generate_examples(entries, backend, progress=lambda m: print(f"  {m}"))
    out = paths.work / "candidatos-amalia.jsonl"
    save_candidates(candidates, out)

    ok = sum(1 for c in candidates if c.status == "pendente" and c.sentence)
    print(f"\n  {ok}/{len(candidates)} passaram a validação automática.")
    print(f"  {out}\n  Revê com: palavrame rever\n")
    return 0


def cmd_rever(args) -> int:
    from .review import review_loop
    from .generate.runner import load_candidates, save_candidates

    paths = default_paths().ensure()
    path = Path(args.candidates) if args.candidates else paths.work / "candidatos-amalia.jsonl"
    if not path.exists():
        print(f"  Falta {path}. Corre `palavrame gerar` primeiro.", file=sys.stderr)
        return 2

    candidates = load_candidates(path)
    changed = review_loop(candidates)
    save_candidates(candidates, path)
    approved = sum(1 for c in candidates if c.status == "aprovado")
    print(f"\n  {changed} decisões nesta sessão. {approved} aprovados no total.\n")
    return 0


def cmd_empacotar(args) -> int:
    """Comprime a DB para dentro dos assets da app.

    A base tem 200 MB; a cópia para a app, 100 MB, e comprime para perto
    de 36. Vai gzipada no APK e a
    app descomprime-a no primeiro arranque — é o preço de ter o dicionário
    a funcionar mal se instala a app, sem passo manual nenhum.

    O ficheiro NÃO é versionado no git (ver android/.gitignore): quem
    reconstrói o projeto corre `f1` e depois isto.
    """
    import gzip
    import shutil

    origem = Path(args.db)
    if not origem.exists():
        print(f"  Não existe: {origem}", file=sys.stderr)
        return 2

    destino_dir = Path(args.destino)
    destino_dir.mkdir(parents=True, exist_ok=True)
    # O dicionário de inglês vai ao lado, com o sufixo da língua; o português
    # mantém o nome de sempre, para as apps já instaladas.
    sufixo = "" if args.lingua == "pt" else f"-{args.lingua}"
    destino = destino_dir / f"dicionario{sufixo}.db.gz"

    # A cópia que vai no APK leva só o que a app usa (ver `_emagrecer`). A base
    # completa em `out/` fica intacta: é essa que se valida e publica.
    if args.completa:
        para_app, temporaria = origem, None
    else:
        print(f"\n  A preparar a cópia para a app ({origem.stat().st_size / 1048576:.0f} MB)…")
        para_app = temporaria = _emagrecer(origem)
        print(f"  Sem o que a app não usa: {para_app.stat().st_size / 1048576:.0f} MB")

    try:
        print(f"  A comprimir…")
        with open(para_app, "rb") as entrada, gzip.open(destino, "wb", compresslevel=6) as saida:
            shutil.copyfileobj(entrada, saida, 1 << 20)
        assinatura = _sha256(para_app)
    finally:
        if temporaria is not None:
            temporaria.unlink(missing_ok=True)

    # Uma marca de versão ao lado do ficheiro. É o que permite à app saber
    # que o dicionário dentro do APK é mais recente do que o que já tem
    # instalado — sem isto, uma base nova nunca substituía a antiga.
    import sqlite3
    from .build.sqlite import readonly_uri

    conn = sqlite3.connect(readonly_uri(origem), uri=True)
    try:
        linha = conn.execute(
            "SELECT value FROM meta WHERE key = 'db_version'"
        ).fetchone()
    finally:
        conn.close()
    versao = linha[0] if linha else "?"
    marca = destino_dir / f"dicionario{sufixo}.versao"
    marca.write_text(f"{versao}\n{assinatura}\n", encoding="utf-8")

    tamanho = destino.stat().st_size / 1048576
    print(f"  Escrito: {destino} ({tamanho:.0f} MB), versao {versao}")
    print("\n  A app descomprime-o no primeiro arranque. Compila com:")
    print("    cd android && .\\gradlew installDebug\n")
    return 0


def _emagrecer(origem: Path) -> Path:
    """Uma cópia da base sem o que a app nunca lê, num temporário local.

    Mais de um quarto dos 200 MB eram coisas que só o pipeline usa:

    * `forms.tag` (≈34 MB): a morfologia do Hunspell. A app só procura por
      `normalized` e mostra `form`.
    * `idx_forms_lemma` (≈17 MB): nenhuma consulta da app vai de lema para
      formas.
    * `lemmas_fts` (≈4,5 MB): a pesquisa da app usa `forms` e `lemmas`, não o
      FTS.

    Na 1.21 (medido na base de 2026-11-04: 146 MB -> 100 MB, 53 MB -> 36 MB
    comprimida), a tabela `forms` fica com a forma que a app consulta:

    * `WITHOUT ROWID` com chave `(normalized, lemma_id)`. A chave É o índice
      da pesquisa, e deixa de ser preciso guardar as linhas e um índice à
      parte com o mesmo conteúdo (o `idx_forms_norm` eram 30 MB).
    * Uma linha por forma e lema: a app agrupa por lema e fica com uma forma
      só (`MIN`), por isso as repetidas (40 mil, de grafias que normalizam
      para o mesmo, como "Abril" e "abril") não faziam nada.
    * `form` vazio quando é igual a `normalized` (62% das linhas). A app lê
      `COALESCE(f.form, f.normalized)`.

    Constrói-se fora da pasta de destino pela mesma razão do `build_database`:
    numa pasta sincronizada (ownCloud) o SQLite corrompe-se a meio da escrita.
    """
    import os
    import shutil
    import sqlite3
    import tempfile

    fd, nome = tempfile.mkstemp(prefix="palavrame-app-", suffix=".db")
    os.close(fd)
    copia = Path(nome)
    shutil.copyfile(origem, copia)
    conn = sqlite3.connect(copia)
    try:
        try:
            conn.execute("DROP TABLE IF EXISTS lemmas_fts")
        except sqlite3.OperationalError as e:
            # Sem o módulo FTS5 neste Python não se consegue largar a tabela;
            # fica, e a base continua válida.
            print(f"  (aviso: FTS mantido — {e})")
        conn.executescript(
            """
            DROP INDEX IF EXISTS idx_forms_lemma;
            DROP INDEX IF EXISTS idx_forms_norm;
            CREATE TABLE forms_app (
                normalized TEXT NOT NULL,
                lemma_id   INTEGER NOT NULL REFERENCES lemmas(id),
                form       TEXT,             -- NULL = igual a `normalized`
                PRIMARY KEY (normalized, lemma_id)
            ) WITHOUT ROWID;
            INSERT INTO forms_app (normalized, lemma_id, form)
                SELECT normalized, lemma_id, NULLIF(MIN(form), normalized)
                FROM forms GROUP BY normalized, lemma_id;
            DROP TABLE forms;
            ALTER TABLE forms_app RENAME TO forms;
            INSERT OR REPLACE INTO meta (key, value)
                SELECT 'count_forms', COUNT(*) FROM forms;
            INSERT OR REPLACE INTO meta (key, value) VALUES ('para_app', '1');
            """
        )
        conn.commit()
        conn.execute("VACUUM")
    except Exception:
        conn.close()
        copia.unlink(missing_ok=True)
        raise
    conn.close()
    return copia


def _sha256(path: Path) -> str:
    import hashlib

    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for bloco in iter(lambda: fh.read(1 << 20), b""):
            h.update(bloco)
    return h.hexdigest()


def cmd_validar(args) -> int:
    from .sources.curadoria import FICHEIRO as CURADORIA_CSV, lemas_acrescentados

    report = validate_database(
        Path(args.db), all_infos(), distribution=args.distribuicao,
        acrescentos=lemas_acrescentados(default_paths().seeds / CURADORIA_CSV),
    )
    print()
    print(report.render())
    print()
    return 0 if report.ok else 1


def cmd_lacunas(args) -> int:
    from .lacunas import escrever_lista, lemas_sem_definicao
    import sqlite3

    caminho = Path(args.db)
    if not caminho.exists():
        print(f"  Não existe: {caminho}", file=sys.stderr)
        return 2

    saida = Path(args.saida) if args.saida else caminho.parent / "lacunas.txt"
    sem_def, total, com_freq = escrever_lista(caminho, saida)

    pct = 100 * sem_def / total if total else 0
    print(f"\n  {sem_def} lemas sem definição, de {total} ({pct:.1f}%).")
    print(f"  {com_freq} têm frequência conhecida — são os que interessa curar primeiro.")
    print(f"\n  Lista escrita em: {saida}")

    conn = sqlite3.connect(caminho)
    try:
        topo = lemas_sem_definicao(conn)[:15]
    finally:
        conn.close()
    if topo:
        print("\n  As mais frequentes por curar:")
        for lemma, pos, _ in topo:
            print(f"    {lemma:<22} {pos or ''}")
    print()
    return 0


def cmd_rever_curadoria(args) -> int:
    from .revisao_curadoria import rever

    caminho = (Path(args.ficheiro) if args.ficheiro
               else default_paths().seeds / "curadoria.csv")
    if not caminho.exists():
        print(f"  Não existe: {caminho}", file=sys.stderr)
        return 2
    rever(caminho)
    return 0


def _fetch_override(cache, slug: str, args) -> int:
    """Alimenta uma fonte a partir de um URL ou ficheiro dado à mão.

    Existe porque os URLs das fontes mudam — o kaikki reorganiza caminhos, o
    Leipzig renomeia corpora — e obrigar quem corre o pipeline a editar código
    de cada vez seria uma forma tola de o bloquear. O ficheiro entra no cache
    e no lockfile exatamente como se tivesse sido descarregado pelo caminho
    normal, portanto a build continua verificável.
    """
    info = build_source(slug, cache).info

    if args.ficheiro:
        origem = Path(args.ficheiro)
        if not origem.exists():
            print(f"  Não existe: {origem}", file=sys.stderr)
            return 2
        # Mantém o nome original: para o Hunspell, o Leipzig e a wordnet é a
        # extensão que escolhe o parser (.aff/.dic, .tar.gz, .nt/.tsv).
        nome = info.primary if info.primary and origem.suffix == Path(info.primary).suffix \
            else origem.name
        guardado = cache.local(slug, nome, origem)
        print(f"  {slug}: {origem.name} -> cache/{slug}/{nome}")
    else:
        nome = info.primary or args.url.rstrip("/").split("/")[-1] or "download"
        guardado = cache.fetch(args.url, slug, nome)
        print(f"  {slug}: descarregado para cache/{slug}/{nome}")

    print(f"  {guardado.bytes / 1024:.0f} KiB, sha256 {guardado.sha256[:16]}…")
    print(f"\n  Se este URL for o certo, fixa-o em sources/{slug}.py "
          "para a próxima build não depender de o teres à mão.\n")
    return 0


# --- auxiliares ------------------------------------------------------------


def _read_seeds(paths, name: str) -> list[str]:
    path = Path(name)
    if not path.exists():
        path = paths.seeds / name
    if not path.exists():
        raise SystemExit(f"Lista de lemas não encontrada: {name}")
    lemmas = []
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.split("#", 1)[0].strip()
        if line:
            lemmas.append(line)
    return lemmas


def _probes(lemmas: Sequence[str]) -> list[tuple[str, str]]:
    """Só as sondas cujo lema está na lista — as outras falhariam por ausência."""
    present = set(lemmas)
    return [(written, lemma) for written, lemma in DEFAULT_PROBES if lemma in present]


def _fontes_markdown(infos) -> str:
    lines = [
        "| Fonte | Licença | Redistribuível | Verificada | Dá |",
        "|---|---|---|---|---|",
    ]
    for info in infos:
        lic = info.license
        redis = {True: "sim", False: "**não**", None: "**por verificar**"}[
            lic.redistributable
        ]
        lines.append(
            f"| [{info.name}]({info.url}) | {lic.name} | {redis} | "
            f"{'sim' if lic.verified else '**não**'} | {', '.join(info.provides)} |"
        )
    return "\n".join(lines)


if __name__ == "__main__":
    raise SystemExit(main())
