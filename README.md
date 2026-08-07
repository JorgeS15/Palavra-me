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

**F2 — a app, a funcionar.** A F0 fechou (100 entradas revistas e aprovadas)
e a F1 produziu o dicionário completo: **186 190 lemas, 358 285 aceções,
1,4 M formas flexionadas, 143 mil exemplos e 136 mil relações**, a partir do
Wikcionário, Dicionário Aberto, Hunspell do Natura, PULO, Tatoeba e Leipzig.
(Esses números são da build anterior; a próxima traz menos lemas e melhores —
ver CHANGELOG 0.22.0.)

Onde nenhuma fonte aberta define uma palavra — acontece com cerca de 4 mil —
a definição pode ser escrita à mão em
[`pipeline/seeds/curadoria.csv`](pipeline/seeds/curadoria.csv), e aparece na
app identificada como tal. Contribuições bem-vindas.

A app está em [`android/`](android/), com o dicionário empacotado dentro do
APK. O que já faz:

- pesquisa que resolve flexões — escreve-se *couberam* e chega-se a *caber*;
- entrada com aceções separadas por fonte, exemplos e sinónimos tocáveis;
- **`PROCESS_TEXT`**: seleciona-se a palavra no leitor de e-books e vai
  direta para a app, sem copiar nada;
- registo com livro, autor, página e a frase onde a palavra apareceu;
- coleção agrupada por livro, com contagens, ordenação e edição;
- exportar e importar a coleção em JSON;
- tudo offline, e **sem permissão de rede** — a app não consegue ligar-se a
  lado nenhum, o que é a forma mais simples de o garantir.

## Estrutura

```
pipeline/    constrói o dicionario.db a partir de fontes abertas (Python)
android/     a app (Kotlin + Compose)
```

A peça central do projeto é o pipeline, não a app. A app é um leitor de SQLite
com boa UI; o trabalho difícil é construir uma base lexical decente a partir
de fontes abertas dispersas.

## Começar

```powershell
cd pipeline
py -3 -m venv .venv               # uma vez
.venv\Scripts\Activate.ps1        # em cada consola nova

python -m palavrame.cli fontes           # que fontes há e em que licença
python -m palavrame.cli fetch --completo # descarrega tudo (precisa de rede)
python -m palavrame.cli f1               # constrói o dicionário inteiro
python -m pytest                         # 138 testes, correm offline
```

A app. O dicionário vai dentro do APK, portanto empacota-se primeiro:

```powershell
python -m palavrame.cli empacotar --db out\dicionario-1.db
cd ..\android
.\gradlew installDebug        # ou o botão Run do Android Studio
```

A compilação recusa-se a produzir um APK sem o dicionário, e verifica-o
dentro do ficheiro final:

```powershell
.\gradlew verificarApk
```

Para instalar no telemóvel, cria a chave de assinatura uma vez (Build →
Generate Signed App Bundle or APK) e preenche o `keystore.properties` a
partir do exemplo. **Guarda a chave**: trocá-la obriga a desinstalar a app,
e desinstalar leva a coleção de palavras de quem a tiver.

Em Linux ou macOS é igual, com `source .venv/bin/activate`.

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

## Contribuir

A contribuição mais valiosa não precisa de saber programar: **escrever as
definições que faltam**. São cerca de quatro mil palavras portuguesas
correntes que nenhuma fonte aberta define — e não é por procurarmos mal, é
porque qualquer dicionário em domínio público é antigo por definição.

Ver [`CONTRIBUTING.md`](CONTRIBUTING.md).

## Gratuita, e assim fica

Sem anúncios, sem versão paga, sem contas, sem rastreio. A app **não tem
permissão de rede** — não é uma promessa, é verificável no manifesto.

Essas decisões tornam-na difícil de rentabilizar, e é esse o ponto. Quem
quiser apoiar o projeto pode fazê-lo pelo botão de patrocínio aqui no
repositório; nunca haverá pedidos de dinheiro dentro da aplicação.

## Licenças

O código é **Apache 2.0** — ver [`LICENSE`](LICENSE) e [`NOTICE`](NOTICE).
**Os dados não são.**

A base construída herda as licenças das fontes que a compõem, e algumas são
copyleft — o Dicionário Aberto e o Wikcionário são CC BY-SA. Por isso a
`dicionario.db` é distribuída sob **CC BY-SA**, com atribuição a:

| Fonte | Licença |
|---|---|
| [Dicionário Aberto](https://dicionario-aberto.net/) | CC BY-SA 2.5 PT |
| [Wikcionário PT](https://pt.wiktionary.org/) | CC BY-SA 4.0 |
| [Onto.PT](https://ontopt.dei.uc.pt/) — Universidade de Coimbra | CC BY 3.0 |
| [PULO](http://wordnet.pt/) — Universidade do Minho | CC BY-SA 2.5 PT |
| [PAPEL](https://www.linguateca.pt/PAPEL/) — Linguateca / Porto Editora | público e gratuito |
| [Hunspell pt-PT](https://natura.di.uminho.pt/wiki/doku.php?id=dicionarios:main) — projeto Natura | GPL/LGPL/MPL |
| [Tatoeba](https://tatoeba.org/) | CC BY 2.0 FR |
| [Leipzig Corpora](https://wortschatz.uni-leipzig.de/) | CC BY |

Cada aceção e cada frase guarda a sua proveniência na base, e a app mostra-a
no ecrã "Fontes e licenças". O `python -m palavrame.cli fontes` mostra o
estado de todas, com a licença e se foi verificada.

As definições escritas à mão em
[`pipeline/seeds/curadoria.csv`](pipeline/seeds/curadoria.csv) são conteúdo
próprio, publicado sob **CC BY-SA 4.0**.
