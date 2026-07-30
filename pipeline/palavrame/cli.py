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
from .sources import REGISTRY, all_infos, build as build_source
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
    p.add_argument("--markdown", action="store_true", help="saída para docs/fontes.md")
    p.set_defaults(func=cmd_fontes)

    p = sub.add_parser("fetch", help="descarrega ficheiros brutos para o cache")
    p.add_argument("--source", action="append", dest="sources",
                   help="repetível; por omissão, todas")
    p.add_argument("--seeds", default=DEFAULT_SEEDS,
                   help="lista de lemas para as fontes que buscam à peça")
    p.set_defaults(func=cmd_fetch)

    p = sub.add_parser("f0", help="protótipo da F0 sobre a lista de lemas")
    p.add_argument("--seeds", default=DEFAULT_SEEDS)
    p.add_argument("--db-version", default="f0")
    p.add_argument("--strict-backbone", action="store_true",
                   help="exige o VOC como autoridade de lemas (F1 em diante)")
    p.set_defaults(func=cmd_f0)

    p = sub.add_parser("gerar", help="gera exemplos com o AMALIA (batch)")
    p.add_argument("--entries", default=None, help="JSONL de entradas fundidas")
    p.add_argument("--backend", choices=("ollama", "echo"), default="ollama")
    p.add_argument("--model", default=None)
    p.add_argument("--limit", type=int, default=None)
    p.set_defaults(func=cmd_gerar)

    p = sub.add_parser("rever", help="revisão humana dos exemplos gerados")
    p.add_argument("--candidates", default=None)
    p.set_defaults(func=cmd_rever)

    p = sub.add_parser("validar", help="valida uma DB construída")
    p.add_argument("--db", required=True)
    p.add_argument("--distribuicao", action="store_true",
                   help="modo severo: licenças por verificar bloqueiam")
    p.set_defaults(func=cmd_validar)

    return parser


# --- comandos --------------------------------------------------------------


def cmd_fontes(args) -> int:
    infos = all_infos()
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
        print("  Preencher docs/fontes.md antes da F1 (plano, secção 8).\n")
    return 0


def cmd_fetch(args) -> int:
    paths = default_paths().ensure()
    cache = Cache(paths, offline=args.offline)
    slugs = args.sources or list(REGISTRY)
    lemmas = _read_seeds(paths, args.seeds)

    failures = 0
    for slug in slugs:
        source = build_source(slug, cache)
        print(f"  {slug} … ", end="", flush=True)
        try:
            if slug == "dicionario_aberto":
                source.fetch(lemmas)      # busca à peça, um lema por ficheiro
            else:
                source.fetch()
            print("ok")
        except (SourceUnavailable, OfflineError) as exc:
            failures += 1
            print(f"indisponível\n      {exc}")
        except Exception as exc:
            failures += 1
            print(f"falhou\n      {type(exc).__name__}: {exc}")

    print(f"\n  {len(slugs) - failures}/{len(slugs)} fontes prontas.")
    return 0 if failures == 0 else 1


def cmd_f0(args) -> int:
    paths = default_paths().ensure()
    cache = Cache(paths, offline=args.offline)
    lemmas = _read_seeds(paths, args.seeds)
    print(f"\n  {len(lemmas)} lemas na lista da F0.\n")

    entries: list[SourceEntry] = []
    available: list = []
    for slug in REGISTRY:
        source = build_source(slug, cache)
        try:
            produced = list(source.parse(lemmas))
        except SourceUnavailable as exc:
            print(f"  {slug:<20} indisponível — {exc}")
            continue
        except Exception as exc:
            print(f"  {slug:<20} erro: {type(exc).__name__}: {exc}")
            continue
        if produced:
            available.append(source.info)
        entries.extend(produced)
        print(f"  {slug:<20} {len(produced)} entradas")

    # Exemplos do AMALIA já aprovados na revisão. Não é uma fonte com
    # download: vem de `work/`, onde `palavrame rever` os deixou.
    from .generate.source import INFO as AMALIA_INFO, approved_entries

    aprovados = approved_entries(paths.work / "candidatos-amalia.jsonl")
    if aprovados:
        entries.extend(aprovados)
        available.append(AMALIA_INFO)
        total = sum(len(e.examples) for e in aprovados)
        print(f"  {'amalia':<20} {total} exemplos aprovados na revisão")

    if not entries:
        print(
            "\n  Nenhuma fonte produziu dados. Corre `palavrame fetch` primeiro "
            "(precisa de rede) ou coloca ficheiros em pipeline/cache/.\n"
        )
        return 2

    result = merge_entries(entries, strict_backbone=args.strict_backbone)
    print(f"\n  Fusão: {len(result.entries)} lemas, "
          f"autoridade de lemas: {result.backbone or 'nenhuma (modo permissivo)'}, "
          f"{result.conflicts.total()} conflitos.")

    dump_jsonl(result.entries, paths.work / "entries.jsonl")

    from .build import build_database

    db_path = paths.out / f"dicionario-{args.db_version}.db"
    stats = build_database(
        db_path, result.entries, available or all_infos(),
        db_version=args.db_version,
        extra_meta={"fase": "F0", "seeds": args.seeds},
    )
    print(f"  DB: {db_path} ({stats['bytes'] / 1024:.0f} KiB)")

    report = build_report(args.db_version, result.entries, stats,
                          result.conflicts, available or all_infos())
    written = write_reports(paths.out, report, result.entries)

    validation = validate_database(
        db_path, available or all_infos(), distribution=False, probes=_probes(lemmas)
    )
    print("\n  Validação:")
    print(validation.render())

    print("\n  Escrito:")
    for path in written:
        print(f"    {path}")
    print(
        f"\n  Passo seguinte (plano F0.3): lê {paths.out}/revisao-{args.db_version}.md"
        " e decide se a qualidade chega.\n"
    )
    return 0 if validation.ok else 1


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


def cmd_validar(args) -> int:
    report = validate_database(
        Path(args.db), all_infos(), distribution=args.distribuicao
    )
    print()
    print(report.render())
    print()
    return 0 if report.ok else 1


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
