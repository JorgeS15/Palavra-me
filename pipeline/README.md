# Pipeline

Constrói o `dicionario.db` que a app embarca. Corre na máquina de quem
desenvolve, nunca em produção.

> A peça central do projeto é este pipeline, não a app. A app é um leitor de
> SQLite com boa UI; o trabalho difícil é construir uma base lexical decente a
> partir de fontes abertas dispersas.

## Requisitos

Python 3.11+. **Sem dependências** — o núcleo usa só a biblioteca padrão, de
propósito: uma build que não resolve dependências é uma build que se reproduz
daqui a dois anos.

```bash
pip install -e ".[dev]"     # só para correr os testes
```

## Uso

```bash
# 1. Estado das licenças. Começa sempre por aqui.
python -m palavrame.cli fontes

# 2. Traz os ficheiros brutos para o cache. Único passo com rede.
python -m palavrame.cli fetch

# 3. Protótipo da F0 sobre os 100 lemas de seeds/lemas-f0.txt.
python -m palavrame.cli f0

# 4. Lê out/revisao-f0.md e decide se a qualidade chega.
```

Depois de um `fetch`, tudo o resto corre com `--offline`.

### AMALIA

Só depois de haver aceções na DB e de se ver quais ficaram sem exemplo:

```bash
ollama pull hf.co/amalia-llm/AMALIA-9B-0626-DPO-GGUF:Q4_K_M
python -m palavrame.cli gerar --backend ollama
python -m palavrame.cli rever         # revisão humana, obrigatória
```

Isto demora horas ou dias, e é suposto demorar. Não se está a servir nada —
está-se a gerar um dataset, uma vez. A 2 tokens/segundo continua a ser
perfeitamente viável.

### Antes de publicar

```bash
python -m palavrame.cli validar --db out/dicionario-v1.db --distribuicao
```

O modo `--distribuicao` **reprova** a base de dados se alguma fonte tiver
licença por verificar. É deliberado.

## Estrutura

```
palavrame/
├── cache.py          rede + lockfile por hash. Um dos dois sítios com sockets.
├── text.py           normalização. Define a chave de pesquisa de todo o projeto.
├── schema.py         esquema canónico intermédio (não é o SQL)
├── affix.py          expansão Hunspell: lema -> formas flexionadas
├── sources/          uma fonte por módulo, isoladas umas das outras
├── normalize/        vocabulário canónico partilhado
├── merge/            resolução de conflitos entre fontes
├── generate/         AMALIA: prompts, validação, batch
├── build/            escrita do SQLite + FTS5
├── validate/         verificações antes de publicar
├── report.py         relatório de build e folha de revisão da F0
├── review.py         revisão humana do que o LLM gerou
└── cli.py
```

## Regras que os testes impõem

Não são convenções — há testes que falham se forem violadas.

1. **A rede vive só em `sources/`** (e em `cache.py`, que descarrega, e em
   `generate/runner.py`, que fala com o modelo em localhost).
   `tests/test_no_network.py` percorre a árvore de imports de cada módulo.
2. **As fontes são independentes.** Nenhuma importa de outra; só de `base`.
3. **Nada entra na DB sem fonte declarada.** O `validate` falha se houver.
4. **Toda a saída de LLM é marcada** como gerada, na DB e na UI. Sem exceções.
5. **`dicionario.db` e `utilizador.db` nunca se tocam.** O pipeline não sabe
   sequer que a segunda existe.

```bash
python -m pytest
```

## Acrescentar uma fonte

1. Um módulo em `sources/`, com um `SourceInfo` que declara a licença
   honestamente (`verified=False` até alguém ter lido os termos).
2. `fetch()` traz para o cache; `parse()` lê do cache e devolve `SourceEntry`.
   Nada mais.
3. Registá-la em `sources/__init__.py`.
4. Uma fixture em `tests/fixtures/<slug>/` e testes do parser.
5. Uma linha em `docs/fontes.md`.
