# Contribuir

Que bom que apareceste. Há várias formas de ajudar o Palavra-me, e **a mais
valiosa de todas não precisa de saber programar** — precisa só de gostar de
palavras.

## Escrever definições que faltam

Há cerca de **quatro mil palavras portuguesas** que a app conhece mas não sabe
definir. Não é descuido: um dicionário livre de direitos de autor é, por
definição, antigo — o nosso mais antigo é de 1913 — e faltam-lhe todas as
palavras que entraram na língua desde então, além de muitas que simplesmente
nunca lá estiveram.

A palavra que deu origem a este projeto é *ensonado*. Nenhum dicionário aberto
a define. Ou alguém a escreve, ou fica vazia para sempre.

Se deres por uma palavra sem significado na app, podes escrever-lho. É uma
linha num ficheiro de texto — [`pipeline/seeds/curadoria.csv`](pipeline/seeds/curadoria.csv):

```csv
lema,classe,definicao,nota
ensonado,adjetivo,"Que tem sono; sonolento.",
```

Umas dicas para a definição ficar bem:

- **Só palavras que a app não define.** Se ela já mostra um significado, não é
  preciso — confirma primeiro.
- **Português europeu**, que é a língua da app toda.
- **Curta.** Uma linha ou duas, não uma enciclopédia.
- Começa por maiúscula e acaba em ponto.

Não tens de preencher as quatro mil. Uma palavra que te tropeçou a ler já é uma
óptima contribuição.

**Melhor ainda:** escreve a definição no [Wikcionário](https://pt.wiktionary.org).
Aí fica livre para toda a gente — para esta app, para as próximas, para quem
vier a seguir — e o teu trabalho rende juros em vez de ficar num ficheiro só.

## Dizer-nos que palavras falham

Encontraste uma palavra que a app não mostra, ou mostra mal? **Abre um
[issue](../../issues)** com a palavra e o que esperavas ver.

Parece pouco, e é o contrário: os defeitos mais sérios do projeto foram todos
apanhados assim. Uma palavra que não aparecia revelou que o pipeline andava a
comer definições inteiras; uma pergunta do jogo que trocava o significado
revelou outra falha. Cada palavra que reportas torna a app melhor para toda a
gente.

## Mexer no código

Se és programador/a e queres pôr as mãos na massa, muito bem-vindo/a. Umas
coisas que convém saber antes:

- **Testes primeiro, e contra os dados reais.** O pipeline tem quase duzentos
  testes, e por boa razão: por três vezes escrevemos um leitor a partir da
  documentação de um formato e por três vezes estava errada. Abre o ficheiro
  verdadeiro antes de escrever o *parser*.
- **A app não fala com a internet, e nunca falará.** É a garantia central do
  projeto. Qualquer proposta que a quebre — contas, anúncios, sincronização na
  nuvem — já foi ponderada e recusada.
- **A coleção de quem usa a app é sagrada.** O dicionário é substituído por
  inteiro a cada atualização; as palavras que a pessoa registou nunca são
  tocadas. Nada no código pode confundir as duas coisas.
- **Os comentários explicam o *porquê*.** Se uma decisão custou uma tarde a
  descobrir, essa tarde fica escrita ao lado do código.

Uma alteração ao código traz sempre um teste, uma subida de versão e uma linha
no [`CHANGELOG.md`](CHANGELOG.md). O resto está em
[`pipeline/README.md`](pipeline/README.md).

### Propor uma fonte de dados nova

A pergunta que decide tudo não é "os dados são bons" — é **"podem ser
redistribuídos numa app publicada?"**. *Grátis para consultar* não é o mesmo
que *livre para redistribuir*, e essa diferença já deixou de fora fontes
excelentes. Uma fonte só entra com a licença lida, confirmada, e registada no
próprio código.

## Licenças

- **O código** é [Apache 2.0](LICENSE). O que contribuíres entra sob a mesma
  licença, automaticamente — não há nada a assinar.
- **Os dados** herdam as licenças das fontes, várias delas *copyleft*, e por
  isso a base é distribuída sob **CC BY-SA**.
- **As definições que escreveres** são publicadas sob CC BY-SA 4.0, como o
  resto da base.

Ao contribuíres, aceitas estes termos. Obrigado — a sério.
