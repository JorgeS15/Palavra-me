# O pipeline

Este é o programa que **constrói o dicionário**. Junta oito fontes abertas —
dispersas, em formatos diferentes, com licenças diferentes — numa única base
de dados SQLite que a app depois carrega.

Corre no computador de quem desenvolve, não no telemóvel de ninguém. O
resultado, o `dicionario.db`, é que vai dentro da app.

> É aqui que está o trabalho difícil do projeto. A app é um bom leitor de
> SQLite; a base de dados que ela lê é que custou a fazer.

## Requisitos

Python 3.9 ou superior, e **nenhuma dependência externa** — o núcleo usa só a
biblioteca padrão. É de propósito: uma build que não precisa de resolver
dependências é uma build que ainda funciona daqui a dois anos.

```bash
pip install -e ".[dev]"     # só é preciso para correr os testes
```

## Construir o dicionário

```bash
# 1. Ver as fontes e o estado das suas licenças. Começa sempre por aqui.
python -m palavrame.cli fontes

# 2. Descarregar os dados brutos das oito fontes. O único passo que usa rede.
python -m palavrame.cli fetch --completo

# 3. Construir o dicionário inteiro a partir do que está em cache.
python -m palavrame.cli f1
```

Depois do passo 2, tudo o resto corre offline. O dicionário sai para
`out/dicionario-1.db`, com um relatório ao lado que diz quantas palavras
ficaram, quantas sem definição, e que conflitos entre fontes houve.

Para o meter na app, empacota-se primeiro:

```bash
python -m palavrame.cli empacotar --db out/dicionario-1.db
```

## Definições escritas à mão

Há cerca de quatro mil palavras que nenhuma fonte aberta define. Preenchem-se à
mão em `seeds/curadoria.csv`, uma linha por significado:

```csv
lema,classe,definicao,nota
ensonado,adjetivo,"Que tem sono; sonolento.",
```

Basta gravar e voltar a construir — não é preciso descarregar nada de novo. A
definição entra na base **em último lugar**: se a palavra já tiver significado
de uma fonte publicada, é essa que aparece primeiro, e a validação avisa que a
linha pode sair.

(É a mesma coisa que o [`CONTRIBUTING.md`](../CONTRIBUTING.md) convida qualquer
pessoa a fazer.)

## Antes de publicar uma base

```bash
python -m palavrame.cli validar --db out/dicionario-1.db --distribuicao
```

O modo `--distribuicao` **reprova** a base se alguma fonte tiver a licença por
confirmar. É deliberado: é a rede de segurança que impede publicar dados que
não se podem redistribuir.

## Como está organizado

```
palavrame/
├── cache.py       download + registo por hash (um dos poucos sítios com rede)
├── text.py        normalização — define a chave de pesquisa de todo o projeto
├── schema.py      o formato intermédio, comum a todas as fontes
├── affix.py       expansão morfológica: de um lema para as suas formas
├── sources/       uma fonte por ficheiro, isoladas umas das outras
├── merge/         resolução de conflitos quando as fontes discordam
├── build/         escrita do SQLite com índice de pesquisa
├── validate/      as verificações que decidem se a base pode sair
└── cli.py
```

## Regras que os testes garantem

Não são recomendações — há testes que falham se forem quebradas.

1. **A rede vive só onde tem de viver.** Um teste percorre os *imports* de cada
   módulo para o garantir.
2. **Cada fonte é independente.** Nenhuma conhece as outras.
3. **Nada entra na base sem fonte declarada.** Sem proveniência, a build falha.
4. **O dicionário e a coleção do utilizador nunca se tocam.** O pipeline nem
   sabe que a segunda existe.

```bash
python -m pytest
```

## Acrescentar uma fonte

1. Um ficheiro novo em `sources/`, com a licença declarada honestamente
   (`verified=False` até alguém ter mesmo lido os termos).
2. Duas funções e nada mais: `fetch()` traz os dados para o cache, `parse()`
   lê-os e devolve entradas no formato comum.
3. Registá-la em `sources/__init__.py`.
4. Uma amostra de teste em `tests/fixtures/` e os testes do *parser*.
5. O texto de atribuição e a nota de **como** a licença foi confirmada, dentro
   do próprio ficheiro. É de lá que sai o ecrã "Fontes e licenças" da app.
