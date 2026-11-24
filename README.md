# Palavra-me

<img width="1024" height="500" alt="recurso-grafico-1024x500" src="https://github.com/user-attachments/assets/e4e9276c-ebc3-4ec6-a410-2de1d5db6ef6" />

**Um dicionário de português (e de inglês) que se lembra das palavras que aprendeste.**

Disponível na [Google Play Store!](https://play.google.com/store/apps/details?id=pt.jorges15.palavrame&pcampaignid=web_share)

Estás a ler um livro, encontras uma palavra que não conheces, e procura-la.
Vês o significado, os sinónimos, frases onde ela aparece — e ela fica guardada
na tua coleção, com o livro e a frase onde a encontraste. Da próxima vez que
abrires a app, está lá, à espera de ser revista.

<img width="2560" height="1440" alt="computador-1" src="https://github.com/user-attachments/assets/4d1299e9-e3e3-4f57-9392-8e91ecb21793" />

O nome vem da forma enclítica portuguesa — *lembra-me*, *ensina-me*. Nomeia o
gesto, não o objeto.

> App Android, gratuita e de código aberto. Funciona **sem ligação à
> internet** e não recolhe nada sobre ti.

---

## O que a torna diferente

**Funciona offline, a sério.** O dicionário inteiro — 180 mil palavras — vive
dentro do telemóvel. Não há servidor, não há conta, não há espera. A app nem
sequer pede permissão de rede: é impossível ligar-se a lado nenhum, e isso
qualquer pessoa pode confirmar.

**Não é só procurar — é fixar.** Cada palavra que registas volta, mais tarde,
num pequeno jogo de revisão. Acertas, e ela demora mais tempo a reaparecer;
falhas, e volta amanhã. É o teu vocabulário a crescer, sem o teres de gerir.

**Lembra-se de onde a encontraste.** Uma palavra sozinha esquece-se. Uma
palavra ligada ao romance onde tropeçaste nela, à frase, à página — essa fica.

**Seleciona e envia.** Encontraste a palavra no teu leitor de e-books?
Seleciona-a, escolhe *Palavra-me*, e ela é procurada de imediato. Não precisas
sequer de a copiar.

## O que a app faz

- **Procura que percebe flexões** — escreves *couberam* e chegas a *caber*.
- **Português e inglês** — um dicionário de inglês, com as definições em
  inglês, para quem também lê em inglês. PT ou EN na caixa de pesquisa.
- **Significados de várias fontes**, com sinónimos, antónimos e as palavras
  da mesma família, todos tocáveis.
- **Coleção pessoal** agrupada por livro, com contagens, ordenação e notas.
- **Os teus livros**, numa estante: o que estás a ler (com a página onde
  vais), os lidos por ano, os que estão em pausa, as datas de cada leitura e
  uma imagem para partilhar quando acabas um livro.
- **Revisão espaçada** — o jogo diário que transforma a coleção em memória.
  Também só das palavras de um livro, e, se quiseres, a escrever a palavra a
  partir da definição.
- **Corrigir ou acrescentar um significado**, por baixo das definições:
  fica para ti, e podes submetê-lo ao dicionário num email preparado pela
  app, logo ou mais tarde.
- **A tua coleção é tua** — exporta-a e importa-a num ficheiro, quando
  quiseres.
- **Tudo offline**, sempre.

## Onde vem o dicionário

Não existe nenhum dicionário aberto de português a sério. Existem, sim, muitas
peças espalhadas — um dicionário de 1913 em domínio público, o Wikcionário,
redes de sinónimos de universidades, corpora de frases. O trabalho difícil
deste projeto foi juntá-las todas numa base coerente que cabe num telemóvel.

O resultado: cerca de **180 mil palavras**, **360 mil significados**, 1,4
milhões de formas flexionadas e centenas de milhares de sinónimos e relações,
construídos a partir de oito fontes abertas.

Faltam palavras? Faltam. Há cerca de quatro mil palavras portuguesas correntes
que **nenhuma fonte aberta define** — porque um dicionário em domínio público
é, por definição, antigo. Essas escrevem-se à mão, aos poucos, e é aí que
qualquer pessoa pode ajudar (ver [Contribuir](#contribuir)).

## Instalar

Está na [**Google Play**](https://play.google.com/store/apps/details?id=pt.jorges15.palavrame).
Instala e está pronta: o dicionário vem dentro, e a app funciona sem
internet.

## Contribuir

**A ajuda mais valiosa não exige saber programar: escrever as definições que
faltam.** Se dás por uma palavra sem significado na app, ou por uma definição
errada, toca em *Corrigir ou acrescentar* e depois em *Submeter ao
dicionário*. Também se faz à mão: uma linha num ficheiro de texto, e ela passa
a estar lá para toda a gente.

Ver [`CONTRIBUTING.md`](CONTRIBUTING.md).

## Gratuita, e assim fica

Sem anúncios, sem versão paga, sem contas, sem rastreio. Essas decisões tornam
a app difícil de rentabilizar, e é esse o ponto — é o que a mantém calma e
privada. Quem quiser apoiar o projeto pode fazê-lo pelo botão **Sponsor** no
topo desta página; nunca haverá pedidos de dinheiro dentro da aplicação.

## Para quem quiser mexer no código

O projeto tem duas partes: o **pipeline** (`pipeline/`, Python) que constrói o
dicionário a partir das fontes abertas, e a **app** (`android/`, Kotlin +
Compose) que o lê. As instruções de compilação estão em
[`pipeline/README.md`](pipeline/README.md).

## Licenças

O **código** é [Apache 2.0](LICENSE). Os **dados** não — a base herda as
licenças das fontes que a compõem, várias delas copyleft, e por isso é
distribuída sob **CC BY-SA**. As oito fontes e as suas licenças estão listadas
no [`NOTICE`](NOTICE); a app mostra a mesma lista no ecrã "Fontes e licenças".

As definições escritas à mão pela comunidade são publicadas sob CC BY-SA 4.0.
