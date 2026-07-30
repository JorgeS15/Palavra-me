# Palavra-me

Dicionário de Português pessoal, onde podes registar as tuas palavras recém
descobertas.

Lês um livro, encontras uma palavra que não conheces, pesquisas, vês o
significado e frases de exemplo, e registas a palavra na tua coleção — com o
livro e a frase onde a encontraste, se quiseres.

O nome vem da forma enclítica portuguesa (*lembra-me*, *ensina-me*): nomeia o
gesto repetido, não o objeto.

## Decisões estruturantes

1. **Offline-first.** O dicionário inteiro vive dentro do dispositivo. Sem
   backend, sem rede no caminho crítico.
2. **Só fontes abertas.** Nada de conteúdo proprietário. A app é publicável e
   o código é aberto.
3. **Geração por LLM em tempo de build, não em runtime.** É o que torna o
   ponto 1 possível.

## Estado

**F0 — a validar as fontes.** O pipeline está construído e testado; falta
correr sobre os dados reais e rever o resultado à mão.

Não há código de Android, e é de propósito: o plano manda não escrever nenhum
antes de existirem 100 entradas revistas e consideradas úteis. Ver
[`docs/estado.md`](docs/estado.md).

## Estrutura

```
pipeline/    constrói o dicionario.db a partir de fontes abertas (Python)
docs/        fontes e licenças, estado do projeto
android/     app (F2 em diante, ainda não existe)
```

A peça central do projeto é o pipeline, não a app. A app é um leitor de SQLite
com boa UI; o trabalho difícil é construir uma base lexical decente a partir
de fontes abertas dispersas.

## Começar

```bash
cd pipeline
python -m palavrame.cli fontes    # que fontes há e o que falta verificar
python -m palavrame.cli fetch     # descarrega (precisa de rede)
python -m palavrame.cli f0        # protótipo sobre 100 lemas
python -m pytest                  # 114 testes, correm offline
```

Ver [`pipeline/README.md`](pipeline/README.md) para o resto.

## Duas bases de dados, sempre separadas

Regra inviolável do projeto:

| | `dicionario.db` | `utilizador.db` |
|---|---|---|
| Conteúdo | lemas, aceções, exemplos, flexões | palavras registadas, notas, progresso |
| Acesso | só leitura | leitura/escrita |
| Origem | gerada pelo pipeline | criada no dispositivo |
| Em atualização | **substituída inteira** | **nunca tocada** |

As palavras do utilizador guardam o lema como **texto**, não como chave
estrangeira. Uma atualização do dicionário pode reordenar ids; a coleção de
quem usa a app não pode depender disso.

## Licenças

O código é MIT. **Os dados não são.**

A base de dados construída herda as licenças das fontes que a compõem —
incluindo copyleft, se levar conteúdo do Wikcionário. Ver
[`docs/fontes.md`](docs/fontes.md), que é um pré-requisito da F1 e não
documentação para fazer no fim.
