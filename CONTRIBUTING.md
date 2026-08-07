# Contribuir

Obrigado pelo interesse. Este documento diz o que é útil, por ordem de
utilidade — e a primeira coisa da lista não precisa de saber programar.

## 1. Escrever definições que faltam

**É a contribuição mais valiosa que existe neste projeto.**

Há cerca de quatro mil palavras portuguesas correntes que **nenhuma fonte
aberta define**. Não é por procurarmos mal: os direitos de autor duram a vida
do autor mais setenta anos, portanto qualquer dicionário em domínio público é
antigo. O nosso esqueleto é o de Cândido de Figueiredo, de 1913.

A palavra que deu origem a isto é `ensonado`. Não está no Dicionário Aberto,
não está nas 624 mil entradas do Wikcionário, e o Leipzig tem frases reais com
ela. Ou alguém a escreve, ou fica vazia para sempre.

Escrevem-se em [`pipeline/seeds/curadoria.csv`](pipeline/seeds/curadoria.csv),
uma linha por aceção:

```csv
lema,classe,definicao,nota
ensonado,adjetivo,"Que tem sono; sonolento.",
```

Regras:

- **Só o que não existe em fonte aberta.** Se a palavra já tem definição, a
  curada entra a seguir e o `validar` avisa que a linha pode sair. Confirma na
  app antes de escrever.
- **Português europeu.** É a variante da app inteira.
- **Uma definição, não uma enciclopédia.** Uma ou duas linhas.
- Começa por maiúscula e acaba em ponto — há um teste que o verifica.

Não é preciso preencher as quatro mil. Uma palavra que te tropeçou a ler é uma
contribuição perfeitamente boa.

### Ainda melhor: contribui para o Wikcionário

Se escreveres a definição no [Wikcionário](https://pt.wiktionary.org), ela fica
CC BY-SA para toda a gente — para esta app, para as próximas, para quem vier a
seguir. É o único caminho em que o trabalho rende juros em vez de ficar preso
a um ficheiro.

## 2. Reportar palavras que falham

Abre um *issue* com a palavra e o que esperavas ver. Parece pouco e não é: os
defeitos mais graves deste projeto foram todos encontrados assim.

O `macilento` que não aparecia levou à descoberta de que a heurística de
domínios comia definições inteiras. O `deferente`, que no jogo perguntava
anatomia em vez de cortesia, levou a rever a escolha da definição. E jogar uma
ronda com 24 palavras reais destapou seis defeitos numa hora.

## 3. Código

- **Testes primeiro contra os dados reais.** O pipeline tem quase duzentos
  testes e nenhum deles é decorativo. Três vezes escrevemos um leitor a partir
  da documentação de um formato e três vezes estava errado — o dump do
  Dicionário Aberto usa `INSERT  IGNORE INTO`, o `.dic` do Natura não é uma
  lista de lemas, e o Onto.PT é Turtle e não o esquema que anuncia. Abre o
  ficheiro antes de escrever o parser.
- **Versão e CHANGELOG a cada alteração.** Convenção do projeto: alterações
  pequenas sobem o número menor.
- **Comentários explicam o *porquê*, não o *quê*.** Se uma decisão custou uma
  tarde a descobrir, essa tarde tem de ficar escrita ao lado do código.
- **A separação `dicionario.db` / `utilizador.db` é inviolável.** Nenhuma
  migração pode tocar em dados de quem usa a app.
- **A app não tem permissão de rede.** É a garantia central do projeto e não se
  negoceia. Qualquer proposta que a exija — contas, anúncios, sincronização —
  já foi discutida e recusada.

## 4. Fontes de dados

A pergunta que importa não é "os dados são bons" — é **"podem ser
redistribuídos dentro de uma app publicada"**. *Grátis para usar* não é
*redistribuível*, e essa distinção já excluiu o CETEMPúblico, que seria a
melhor fonte de frases em português europeu que existe.

Uma fonte só entra com a licença lida, confirmada e registada. Cada módulo em
`pipeline/palavrame/sources/` declara a sua no `SourceInfo`, incluindo o texto
de atribuição exigido e **como** a licença foi verificada — é aí que essa
informação vive, e é de lá que sai o ecrã "Fontes e licenças" da app.

O `palavrame fontes` mostra o estado de todas.

## Licenças

- **O código é Apache 2.0.** Ver [`LICENSE`](LICENSE) e [`NOTICE`](NOTICE).
  As contribuições de código entram sob a mesma licença, como a secção 5 da
  Apache 2.0 estabelece — não é preciso assinar nada.
- **Os dados não.** A base construída herda as licenças das fontes, incluindo
  copyleft: leva conteúdo CC BY-SA do Dicionário Aberto e do Wikcionário, e
  por isso é distribuída sob **CC BY-SA**.
- **O que escreveres no `curadoria.csv`** é publicado sob CC BY-SA 4.0, como o
  resto da base.

Ao contribuires, aceitas estes termos.
