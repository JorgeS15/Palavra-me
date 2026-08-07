# Changelog

Todas as alterações relevantes ao projeto. Formato baseado em
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versões seguem
[SemVer](https://semver.org/lang/pt-BR/).

Duas versões, porque são dois artefactos: a do **pipeline** (`pipeline/
pyproject.toml`) titula as entradas; a da **app** (`android/app/build.gradle.kts`)
vai indicada quando muda. Correções e afinações sobem o PATCH; funcionalidade
nova sobe o MINOR.

Nota: no esquema do `dicionario.db`, uma subida de MINOR pode trazer
alterações incompatíveis enquanto o pipeline estiver antes da 1.0.0.

## [0.25.3] - 2026-08-07

### Changed

- **Licença do código: MIT → Apache 2.0.** Permissiva na mesma, mas com
  concessão explícita de patentes e obrigação de assinalar ficheiros
  alterados. A escolha assenta numa observação: o que este projeto tem de
  valioso é o pipeline, e mesmo com o código permissivo quem construísse um
  produto fechado continuaria obrigado a publicar a base, porque o copyleft do
  CC BY-SA viaja com ela. Ficar permissivo maximiza a hipótese de outro
  projeto português reaproveitar o pipeline — e recursos abertos para
  português são escassos.
- **`NOTICE` novo**, como a Apache 2.0 prevê. Diz o que a `LICENSE` não pode
  dizer: **o código é Apache 2.0, os dados não são**. Lista as oito fontes com
  as respetivas licenças, incluindo a exigência de atribuição por frase do
  Tatoeba.

### Preparação do repositório público

- `CONTRIBUTING.md`, com a curadoria de definições em primeiro lugar — é a
  contribuição mais valiosa e a única que não exige saber programar.
- `.github/FUNDING.yml`. **Confirmar o nome de utilizador antes do push.**
- `docs/` e `plano-palavra-me.md` ficam fora do repositório, por decisão do
  Jorge. O `docs/emails.md` é correspondência privada e continha o registo de
  quem confirmou a licença do PAPEL. A tabela de atribuições passou para o
  README e para o `NOTICE`, que era o que o `docs/fontes.md` dava ao público.

## [0.25.2] - 2026-08-06 — app v1.8.2

### Fixed

**A palavra da notificação não era a palavra do jogo.** Encontrado pelo Jorge,
e são três causas a somar — a app parecia avariada logo no primeiro gesto que
lhe pedimos.

1. **O lembrete não verificava se a palavra era jogável.** Pegava na mais
   atrasada e anunciava-a. O jogo descarta as que não têm definição com corpo
   suficiente — e há palavras registadas nessas condições: o `lugubremente`
   não chega aos 25 caracteres em aceção nenhuma. Esta era a causa
   sistemática. O `LembreteWorker` passa a abrir o dicionário e a escolher
   como o jogo escolhe.
2. **O jogo salta palavras sem distrações à altura.** O lembrete não sabia
   disso.
3. **A app reescolhia ao abrir.** Bastava registar uma palavra nova entre o
   aviso e o toque para o jogo perguntar outra coisa — as acabadas de
   registar têm `proxima_revisao` a nulo e são as primeiras da fila.

A escolha passa a viver num sítio só (`jogo/Escolha.kt`) e **a palavra viaja
no *intent***, para não ser reescolhida quando a app abre. Se entretanto
deixar de estar vencida, segue-se a ordem normal em vez de insistir.

Fica um resto conhecido: se a geração da pergunta falhar precisamente para a
palavra anunciada, o jogo passa à seguinte e volta a haver discrepância. É
raro — a escolha das distrações é determinística — e a alternativa seria o
lembrete correr a geração inteira só para decidir o que escrever.

## [0.25.1] - 2026-08-06 — app v1.8.1

### Fixed

- **"Fonte não declarada" aparecia no ecrã "Fontes e licenças".** O builder
  cria essa linha como rede de segurança, para servir de destino a conteúdo
  que chegue sem fonte registada — é o que evita uma violação de chave
  estrangeira a meio de uma build de horas. Mas era criada
  **incondicionalmente**, e ficava na tabela mesmo sem nada apontar para ela.
  O leitor via uma fonte com licença "DESCONHECIDA" que não contribuiu com
  uma única palavra.

  Passa a ser apagada no fim da escrita quando não foi usada. Se **for** usada
  fica, e o validador continua a reprovar a base — que é o comportamento
  certo, porque nesse caso há mesmo conteúdo sem proveniência.

  A app também a esconde, para as bases já construídas não obrigarem a
  reconstruir só por isto. Filtra pela licença e não por "não tem aceções": o
  Hunspell e o PAPEL também não têm nenhuma — dão formas e relações — e as
  suas licenças exigem atribuição.

## [0.25.0] - 2026-08-06 — app v1.8.0

Duas fontes novas, e uma mudança de ângulo: deixámos de procurar só definições.

### Cada fonte no que é boa

Medido contra a base de 181 751 lemas, e depois inspecionado à mão:

| | PAPEL (avaliado à mão, 99-100%) | Onto.PT (synsets automáticos) |
|---|---|---|
| `urso-formigueiro` | formigueiro, **papa-formigas** ✅ | comichão, dormência ❌ |
| `ensonado` | ensonolento, **sonolento** ✅ | dorminhoco, **imóvel** ❌ |
| `esfuziante` | **deslumbrante** ✅ | flamífero, **inefável** ❌ |

O Onto.PT fundiu o `urso-formigueiro` com o *formigueiro* de "sentir
formigueiro nas pernas". Cobre quase o dobro dos lemas e erra à vista.

Daí a divisão: **Onto.PT dá as definições** (125 507 aceções; o PAPEL não tem
nenhuma) e **o PAPEL dá as relações** (266 692, com avaliação manual
publicada). As relações do Onto.PT ficam atrás de uma constante,
`RELACOES_DO_ONTOPT`, reversível numa linha.

Perde-se sinónimo em cerca de 43 mil lemas. Mais vale isso do que dizer a
alguém que `urso-formigueiro` quer dizer *comichão*.

### Added

- **PAPEL** (Porto Editora / Linguateca) — 83 mil relações de sinonímia e 49
  mil de hiperonímia, extraídas **das definições do Dicionário da Língua
  Portuguesa da Porto Editora**. Avaliação manual publicada: 99-100% de
  precisão nas duas relações que a app mostra.
- **Onto.PT** (Universidade de Coimbra) — **CC BY 3.0**, a licença mais
  permissiva de toda a base. 156 mil formas em 117 mil synsets, 173 mil
  relações, e definições atribuídas a synsets. Construído a partir do PAPEL,
  do Dicionário Aberto, do Wikcionário, do TeP, do OpenWordNet-PT e do
  OpenThesaurus.PT.

  Ambas as fontes foram confirmadas pelo Jorge a 2026-08-06 como utilizáveis.

- **A entrada sem definição passa a mostrar sinónimos.** Era esta a mudança de
  ângulo: andávamos à procura de definições porque é isso que falta, mas para
  quem encontra `ensonado` a meio de um romance, *"o mesmo que sonolento"*
  resolve o problema igualmente bem — e é frequentemente tudo o que as fontes
  abertas têm sobre a palavra. Em vez de *"Sem definição em nenhuma fonte"*,
  aparecem os sinónimos, tocáveis como os outros.

### Notas

- As duas entram em `NON_LEMMA_SOURCES`: o seu vocabulário inclui expressões
  compostas e itens lematizados por máquina (`abrir_o_apetite`,
  `reino_monera`), e deixá-las abrir lemas encheria a base de entradas que
  nenhum dicionário português reconhece.
- O Onto.PT é fonte de preenchimento, à frente do wordnet: as suas glosas vêm
  de dicionários portugueses e não de tradução automática do inglês.
- **O primeiro leitor de N3 do Onto.PT estava errado de ponta a ponta.**
  Escrevi-o a partir da documentação, que anuncia o esquema WordNet RDF/OWL —
  e o ficheiro é Turtle, sem nível `wordsense`, com o synset a ter as formas
  diretamente. Reescrito com o ficheiro aberto ao lado. É a terceira vez que
  isto acontece (o `INSERT  IGNORE INTO` do Dicionário Aberto, o `.dic` do
  Natura que não é uma lista de lemas) e a lição é sempre a mesma.
- **Relações que eram flexões da própria palavra.** O Onto.PT dava
  `pistola-metralhadora` como sinónimo de `pistolas-metralhadoras`. Novo
  `_trim_relations` no fim da fusão — corre no fim, e não quando a relação
  entra, porque as flexões chegam do Hunspell, que é aplicado depois.
- **Grafia brasileira nas formas**, não só nas glosas: `abdômen`,
  `acadêmico`, `abstêmio`. São 895, e apareceriam como sinónimos numa app
  portuguesa. O filtro que já existia passou a correr também sobre as
  palavras.
- **Produto cartesiano nas relações entre synsets.** A primeira versão
  produzia 700 mil hiperonímias a partir de 173 mil reais, ligando todos os
  membros de um synset a todos os do outro. Passa a ligar só à forma
  representativa do destino.
- O pacote do PAPEL traz `relacoes_final.txt` **e** um ficheiro por grupo;
  ler ambos duplicava cada triplo.
- Licença do PAPEL confirmada por email (Jorge, 2026-08-06). É a **única
  fonte da base sem licença escrita** — o email é o que sustenta a decisão e
  fica registado em `docs/fontes.md`.

## [0.24.3] - 2026-08-06 — app v1.7.1

### Fixed

Duas coisas, e ambas a mesma leitura errada minha: separei "botão de rever" de
"treino livre" quando para o Jorge são a mesma coisa.

- **O botão "Rever" aparecia sem o Modo Desenvolvedor.** Passa a ser exclusivo
  dele. Para quem lê, o jogo vem pelo lembrete: chega, responde-se, acabou.
  Perder um lembrete não perde nada — a palavra continua vencida e volta no
  seguinte.
- **Com o Modo Desenvolvedor não havia "Palavra seguinte".** O encadeamento
  estava preso ao *treino livre* — que só se ativa quando não há nada vencido —
  em vez de ao Modo Desenvolvedor. Quem tinha palavras vencidas, que é o caso
  normal, respondia a uma e ficava sem saída. Verificar perguntas uma a uma,
  fechando e reabrindo a app entre cada, é trabalho a mais para quem está a
  caçar defeitos.
- **Ligar o Modo Desenvolvedor não fazia efeito no jogo até fechar a app.** O
  `ViewModel` era reaproveitado pela chave; a chave passa a levar o modo
  dentro.

## [0.24.2] - 2026-08-06 — app v1.7.0

Primeira ronda de correções do Jorge depois de usar a app a sério num
Galaxy S22 Ultra e de jogar o jogo.

### Fixed

- **A barra de navegação ficava clara com a app em escuro.** Só se via no
  telemóvel, nunca no emulador — que costuma ter o sistema e a app no mesmo
  tema. Quando divergem, é a app que tem de dizer ao sistema como desenhar
  as barras. Acrescentado também `enableEdgeToEdge()`, que a partir do
  Android 15 é o que faz os `Scaffold` receberem os *insets* certos.
- **O contador de palavras cortava contra a margem** do ecrã. Removido: o
  número já aparece na linha de estatísticas e no topo da coleção.

### Changed

- **Uma pergunta por sessão.** Era assim que o jogo tinha sido pensado e não
  foi assim que ficou construído. O "Palavra seguinte" transformava um gesto
  de dez segundos numa sessão de estudo, e a app deixava de caber no
  intervalo em que se pousa o livro. Quem quiser mais responde ao lembrete
  seguinte.
- **Vários lembretes por dia, numa janela de horas.** Até seis, distribuídos
  uniformemente com os extremos incluídos: três entre as 8h e as 22h dão 8h,
  15h e 22h. As horas calculadas aparecem escritas nas Definições, para a
  regra ser visível em vez de mágica. Um trabalho periódico por hora, em vez
  de um que se reagenda a si próprio — se o sistema matar um, os outros
  continuam.
- **A proveniência das definições sai do ecrã.** Saber que uma aceção veio do
  Dicionário Aberto é ferramenta de quem constrói a base, não informação de
  quem lê um livro.

  **A fonte das frases fica.** O Tatoeba distribui em CC BY com atribuição
  *por frase* (ver `docs/fontes.md`) — é obrigação, não estilo. O que sai é a
  referência interna, do género `por-pt_web_2015_1M:282349`.

- **Estatísticas no ecrã inicial**: palavras, pontos, certas, erradas e
  sequência de dias, numa linha em letra miúda por cima das palavras
  recentes. Só aparece depois de haver jogo — antes disso seriam quatro zeros
  a insinuar que se está atrasado em alguma coisa.

### Added

- **Modo desenvolvedor**, no fim das Definições. Mostra a proveniência de
  cada aceção e as referências das frases, e desbloqueia o **treino livre** —
  jogar palavras que ainda não estão vencidas, para experimentar perguntas
  sem esperar dias. Sem ele, "não há nada a rever" é uma resposta legítima e
  o jogo diz isso.

### Verificação

Sete testes novos para a distribuição das horas, incluindo os casos que
partem: mais lembretes do que horas na janela, janela invertida, e janela de
uma hora só.

## [0.24.1] - 2026-08-06 — app v1.6.0

O modo jogo, completo. Passos 3 e 4 do `docs/jogo.md`; o desenho estava
aprovado desde 5 de agosto e não mudou ao ser construído — o que é bom sinal
para o desenho e para o hábito de o escrever antes.

### Added

- **Ecrã do jogo.** Uma palavra, três definições, e depois de responder a
  entrada inteira mais **o livro e a frase onde a encontraste**. Não se lembra
  a definição; lembra-se o momento.

  Botão no ecrã principal, que **só aparece quando há com que jogar** — um
  botão que leva sempre a um ecrã de desculpas é pior do que não existir.

- **Repetição espaçada e pontos** (`jogo/Sessao.kt`, sem Android nenhum, como
  o gerador de perguntas). Caixas de Leitner 0–5 com intervalos a duplicar de
  1 até 32 dias; +10 por acerto, −5 por erro com piso em zero, e +2 por dia
  consecutivo até +10. O bónus é atribuído **uma vez por dia**, na primeira
  resposta: o que se quer premiar é voltar amanhã, não jogar mais hoje.

- **Treino livre.** Quando não há nada vencido e se quer jogar à mesma, os
  pontos contam mas o calendário não mexe. Sem isto, bastava jogar muito num
  dia para nunca mais rever nada — o contrário do que a repetição espaçada faz.

- **Lembrete diário**, desligado por omissão e com a hora escolhida nas
  Definições (21:00 por omissão). `WorkManager` periódico, não alarme exato:
  a hora não precisa de ser ao segundo e um alarme exato exigiria uma
  permissão especial que esta app não tem como justificar.

  A permissão de notificações é pedida **no momento em que se liga o modo**, e
  não ao arrancar. Quem nunca ligar o jogo nunca vê o pedido. Se a recusar, o
  interruptor volta atrás — um interruptor ligado que não notifica seria uma
  mentira na interface.

  **Não se notifica se não houver nada vencido.** Uma app que avisa todos os
  dias mesmo sem ter nada a dizer ensina a ignorar o aviso.

- `Dicionario.entradaPorLema` — o jogo parte da coleção, onde o lema está
  guardado como texto, e precisa da palavra exata que foi registada, não dos
  candidatos que o `procurar` devolveria.

### Notas

- Dependência nova: `androidx.work:work-runtime:2.11.2`. É o artefacto
  principal e não o `-ktx`: este último está vazio desde que o
  `CoroutineWorker` e o resto das APIs de corrotinas passaram para o
  principal, e declará-lo só acrescentaria um POM sem código.
- Continua a **não haver permissão de rede**. O lembrete decide-se todo dentro
  do telemóvel, a partir da `utilizador.db`.
- Nenhuma migração da base do utilizador: a v3 já previa `mastery`,
  `proxima_revisao` e a tabela `progresso`.
- Sem sons nem animações, de propósito. O valor desta app é a calma.

## [0.24.0] - 2026-08-05 — app v1.5.1

Tudo o que está aqui foi encontrado da mesma maneira: **a jogar**. O Jorge
registou 24 palavras a ler *Em Parte Incerta*, gerámos as perguntas com o
dicionário real, e jogámos antes de existir ecrã nenhum. Uma pergunta de
escolha múltipla põe três definições lado a lado, e o que numa entrada
isolada passa despercebido salta à vista quando está ao lado de outras duas.

### Fixed — o jogo

- **O jogo perguntava o sentido errado.** `definicaoParaJogo` escolhia a
  primeira aceção com 25 caracteres ou mais, e isso escolhia mal sempre que
  os sentidos correntes eram curtos. Para `deferente` — aceções *"que
  defere"*, *"gentil, cortês"*, *"Que condescende."* — saltava para a quarta,
  *"Diz-se de cada um dos vasos excretores dos testículos"*, e perguntava
  anatomia a quem tinha encontrado a palavra num romance a significar
  cortesia.

  Passa a **juntar sempre a partir da primeira**: as primeiras aceções são as
  principais. E junta com ponto e vírgula, não com espaço — *"que preme ou
  comprime urgente"* e *"mancha desdoiro infâmia labéu"* liam-se como
  disparate.

### Fixed — o dicionário

- **Parênteses partidos ao meio: 5 105 aceções → 53.** As etimologias e
  remissões de 1913 vêm entre parênteses e atravessam linhas; o parser
  cortava-as ao meio e a cauda virava aceção. O `bruxulear` tinha como
  segundo significado `cast. grujulear)`, com o parêntese órfão à vista. Só
  se corta onde os parênteses estão fechados.
- **Remissões e abonos: 8 821 aceções → 200.** *"Irritação, agastamento. Cf.
  Filinto, XIII, 86."* — a referência à obra fazia parte do texto da
  definição. Tira-se `Cf.`, `Cp.` e `(Colhido em X)`. Uma aceção que era
  apenas uma citação desaparece.
- **Trema: 54 → 0.** `reünir`, `Freqüente`, `Retribuïção`. Abolido em
  Portugal em 1945.
- **Maiúscula inicial em todas as definições.** O Wikcionário escreve em
  minúscula e o Dicionário Aberto em maiúscula; 158 mil aceções começavam em
  minúscula e a mistura, sobretudo nas três opções do jogo, parecia descuido.

### Conhecido e não corrigido

- Cerca de **vinte** aceções em 209 mil ficam com um resto de anotação
  (`Ind`, `Lat`), quando a anotação inteira é um parêntese numa linha só.
  Corrigi-lo exigiria decidir que uma aceção completa é anotação, o que dá
  mais falsos positivos do que os vinte que resolve. Registado em teste.
- **Ortografia pré-AO90 do Dicionário Aberto** (`colecção`, `acto`) mantém-se.
  Converter em condições exige verificar cada palavra contra um léxico AO90 —
  em PT-PT há consoantes que se mantêm (`facto`, `pacto`) — e é trabalho para
  sua própria versão.

### Verificação

185 testes. Reparse completo do Dicionário Aberto: 128 517 entradas,
209 588 aceções, com as contagens acima medidas sobre os dados reais.

## [0.23.0] - 2026-08-05

O PULO trazia definições e nós estávamos a deitá-las fora.

### Added

- **Glosas do wordnet como aceções.** O dump do PULO tem uma tabela
  `wei_por-30_synset` com 117 717 glosas em português; o módulo só lia
  sinónimos e relações. Passa a ler as glosas: **13 125 palavras** ganham
  definição.

  A qualidade obriga a cuidados, e é por isso que isto não é uma linha de
  código. As glosas são tradução automática das glosas inglesas da WordNet de
  Princeton. Umas são boas — *"uma sala onde um prisioneiro é mantido"*.
  Outras são literais ao ponto de estarem erradas: para `espoliar` a glosa diz
  *"tosquiar a lã de"*, que é o inglês *fleece* vertido à letra e não o que a
  palavra significa em português. Daí duas travas:

  - **Filtro de ortografia brasileira.** 4,1% das glosas escrevem *oxigênio*,
    *sinônimos*, *idéias*, *você*. Uma app de leitura de literatura
    portuguesa não pode dizer isso. `text.parece_do_brasil` deteta ô/ê antes
    de nasal seguida de vogal, o `-éia` pré-reforma e o `você`; rejeita 2,6%
    das glosas que teriam entrado.
  - **Fonte de preenchimento** (`FILL_ONLY_SOURCES`). Uma glosa só entra se a
    palavra não tiver definição nenhuma. Encostada a uma entrada do Dicionário
    Aberto, estragava uma entrada boa; sozinha numa entrada vazia, é melhor do
    que *"sem definição em nenhuma fonte"*.

  As glosas passam por `limpar_glosa`, que tira os exemplos entre aspas do
  formato de Princeton e as põe com maiúscula inicial e ponto final, para não
  se distinguirem das outras aceções pela pontuação.

- Verificação nova: **quantas palavras dependem só de uma glosa traduzida**.
  É a fila de espera natural da curadoria manual.

### Fixed

- **A curadoria manual aparecia depois da tradução automática.** Uma definição
  escrita à mão surgia por baixo de uma glosa do wordnet. Invertido: a
  curadoria vem antes, e como o wordnet é fonte de preenchimento, escrever a
  definição no `curadoria.csv` faz a glosa **desaparecer** da entrada.
- **O `f1` dividia as fases pelo critério errado.** Separava por
  `NON_LEMMA_SOURCES`, o que atirava o wordnet para a terceira volta, depois
  da curadoria, invertendo a prioridade que o `config` declara. Passa a
  separar por fontes de exemplos — que são as que precisam do índice de formas
  cheio — ficando igual ao que o `merge_entries` já fazia na F0.
- `fechar_lemas` respeita `NON_LEMMA_SOURCES` mesmo para fontes que estão em
  `SENSE_SOURCE_PRIORITY`. Sem isto, o wordnet passaria a criar lemas e a base
  encher-se-ia de palavras alinhadas com a WordNet inglesa que nenhum
  dicionário português reconhece.

### Verificação

172 testes. F0 sobre o cache real continua aprovada, 4/4 sondas.

## [0.22.1] - 2026-08-05

### Changed

- A biblioteca de curadoria passa a apresentar-se como **"Palavra-me"** e não
  "Curadoria Palavra-me" — decisão do Jorge. É uma biblioteca do projeto, a
  par do Dicionário Aberto e do Wikcionário, e o ecrã fica mais limpo. A
  proveniência não se perde: o texto de atribuição continua a dizer que são
  definições escritas à mão onde nenhuma fonte aberta define a palavra.

  Cosmético — não obriga a reconstruir a base só por isto; entra na próxima.

## [0.22.0] - 2026-08-05

Começou por uma pergunta do Jorge — *porque é que o `ensonado` não tem
definição?* — e acabou no defeito mais caro que o pipeline teve.

A resposta à pergunta é honesta e curta: nenhuma fonte aberta define
`ensonado`. Não está no Dicionário Aberto (é de 1913, a palavra é posterior)
nem nas 624 mil entradas do Wikcionário. Só o vocabulário do Natura o conhece.

A resposta interessante apareceu ao verificar isso.

### Fixed

- **O `.dic` do Natura não é uma lista de lemas — é uma lista de formas.** O
  campo morfológico de cada linha diz o que a forma é, e nós líamo-lo e
  deitávamo-lo fora:

  ```
  ensonado    [CAT=adj,N=s,G=m]                      -> lema, adjetivo
  tinham      [$ter$CAT=v,T=inf,TR=_$P=3,N=p,T=pi]   -> flexão de "ter"
  ```

  Consequência, medida na base de 186 mil lemas: **4 448 lemas fantasma** —
  `tinham`, `ativeras`, `púnheis`, `corróis`, `pusesse`, `couberam` — cada um
  com entrada própria ao lado do verbo a que pertence, todos sem definição
  nenhuma, todos devolvidos pela pesquisa como candidatos. Passam a ser o que
  sempre foram: formas do lema verdadeiro. Os 324 casos em que a palavra
  também é lema por direito próprio (tem definição de outra fonte) ficam
  intactos.

- **A classe gramatical estava na mesma etiqueta.** `CAT=` resolve **5 109
  lemas** que diziam "desconhecido" — o `ensonado` entre eles, que agora diz
  adjetivo. Acrescentada a classe `nome proprio` para os 2 946 casos que o
  Natura marca `CAT=np` (Serralves, Aachen, HTTP): ficam na base, por decisão
  do Jorge, mas deixam de se fazer passar por palavras de dicionário.

### Added

- **Fonte de curadoria manual** (`pipeline/seeds/curadoria.csv`). As
  definições que nenhuma fonte aberta tem, escritas à mão — o ponto 3 da
  secção 4.4 do plano. Regras, postas pelo Jorge ao aprová-la:
  - **última na prioridade das aceções**: só preenche lacunas, nunca passa à
    frente de uma fonte publicada;
  - **não inventa palavras**: como o Hunspell, só abre lema se mais ninguém
    reclamou aquela grafia;
  - **fica marcada** na base e no ecrã como "Curadoria Palavra-me", com
    licença CC BY-SA 4.0 e atribuição próprias;
  - **não é conteúdo de LLM** — se um dia se gerarem definições, isso é outra
    fonte, com a marca `generated`.

  O validador avisa quando uma palavra curada ganhou entretanto definição
  aberta, para que a linha possa sair do ficheiro. Aviso, nunca erro.

  Começa com cinco entradas: `ensonado`, `esfuziante`, `jusnaturalismo`,
  `contraplacado`, `obsessionante`.

### Changed

- **O relatório de conflitos guarda uma amostra e conta o resto.** Guardava
  tudo, e o `relatorio-1.json` da F1 chegava a 3 MB só de discordâncias de
  classe gramatical — ilegível, e a crescer agora que o Hunspell também traz
  classes. Teto de 500 por família; as contagens continuam exatas.
- Fixture do Hunspell atualizada para o formato verdadeiro do Natura, com
  etiquetas morfológicas. Sem elas, os testes não exercitavam o caminho que
  mais lemas produz.

### Verificação

161 testes. F0 sobre o cache real: 120 lemas, **zero sem aceção** (antes 9 817
em 186 190 na F1), zero lemas fantasma, 4/4 sondas de pesquisa por flexão,
`ensonado` = adjetivo com definição curada e `frequency_rank` preenchido.

Exige reconstruir a base (`palavrame f1`) — como as correções da 0.21.0, que
ainda não foram aplicadas.

## [0.21.0] - 2026-08-05 — app v1.5.0

Da coleção real do Jorge — quatro palavras que destaparam dois defeitos que
não têm nada a ver com o jogo.

### Fixed

- **As frequências do Leipzig nunca chegavam à base.** Os 186 mil lemas
  tinham `frequency_rank` a NULL, o que torna inútil a ordenação dos
  candidatos por frequência que o plano pede (secção 9) — procurar
  *"cantada"* mostrava os candidatos por ordem alfabética, não pela mais
  provável. Regressão minha, introduzida na fusão em streaming (0.5.0): as
  fontes de exemplos passaram a ter um caminho próprio, e esse caminho só
  aplicava exemplos. Corrigido com teste de regressão.
- **A marcação de itálico de 1913 chegava ao ecrã.** O Dicionário Aberto
  escreve `De _autor_.` e os sublinhados apareciam tal e qual em **48 mil
  aceções — 13% do total**. A limpeza passa a tirá-los.

Ambos exigem reconstruir a base (`palavrame f1`) para se verem na app.

### Changed

- **Distrações completadas com o dicionário quando a coleção é pequena.**
  Com quatro palavras registadas o gerador funcionava, mas as três
  definições eram sempre as mesmas: ao terceiro dia acertava-se por
  eliminação, e o jogo mediria memória de posição em vez de vocabulário.
  Abaixo de 15 palavras mistura-se — uma da coleção, uma do dicionário —
  e a mistura desaparece sozinha à medida que a coleção cresce.

  As definições de reserva excluem gentílicos (*"relativo ou pertencente a
  Melo"*), que são milhares e todos iguais entre si.

### Verificado

Gerador corrido sobre a coleção real (4 palavras): **4/4 utilizáveis, 0
perguntas impossíveis**, e três dias seguidos da mesma palavra dão
distrações diferentes de cada vez.

## [0.20.0] - 2026-08-05 — app v1.4.0

Modo jogo: as duas peças de baixo. Sem interface ainda, de propósito.

### Added

- **`jogo/Perguntas.kt` — o gerador**, escrito sem uma linha de Android para
  poder ser verificado sem instalar nada. É aqui que estava o risco todo.
  Junta as aceções curtas de 1913 numa definição com corpo (*"Magro. Pálido.
  Amortecido."*), rejeita distrações demasiado compridas, demasiado curtas
  ou que digam o mesmo que a resposta certa, e prefere a mesma classe
  gramatical. Se não houver duas distrações à altura, **não faz a pergunta**.
- `PerguntasTest.kt` — dez testes sobre definições reais da base.
- Migração **v2 → v3**: `proxima_revisao` em cada palavra e a tabela
  `progresso`. Nada é apagado; as palavras já registadas ficam com
  `proxima_revisao` a nulo, que o jogo lê como "vencida desde sempre" — ou
  seja, entram desde o primeiro dia.

### Verificado

Perguntas geradas com o dicionário real, sobre uma coleção de 18 palavras
literárias: **18/18 utilizáveis, 0 perguntas impossíveis, e em nenhuma o
comprimento entrega a resposta**. Exemplo:

```
macilento · adjetivo
  -> Magro. Pálido. Amortecido.
     Indivíduo pouco atilado; ingénuo; pacóvio.
     diz-se do indivíduo que se veste com demasiada correção
```

### Changed

- Pontuação: **−5 por erro**, com piso em zero (decisão do Jorge). O
  raciocínio dos dois lados está em `docs/jogo.md`.

## [0.19.0] - 2026-08-05 — app v1.3.0

### Changed

- **Definições mais limpas.** O estado do dicionário e a lista de fontes
  saíram da página principal para um ecrã próprio, "Dicionário e licenças",
  acessível a partir de Sobre. São informação de consulta — ninguém vai lá
  todos os dias — e a ocupar espaço faziam as Definições parecerem uma
  ficha técnica. As escolhas que se fazem (tema, cópias) ficam à vista.

### Added

- `docs/jogo.md` — o desenho do modo jogo, escrito antes de programar.
  Decidido: pergunta **palavra → 3 definições**, distrações tiradas de
  **outras palavras da própria coleção**, **repetição espaçada por caixas**
  mais pontos, e notificação que **abre a app** em vez de responder na
  própria notificação. Desligado por omissão.

  O documento identifica o risco principal, que não é técnico: as
  definições de 1913 são muitas vezes uma palavra só e as do Wikcionário
  são frases longas — num teste de três opções, **o comprimento entregaria
  a resposta sem se ler nada**. As mitigações estão escritas, e a geração
  da pergunta vai ser construída e verificada antes de haver interface.

## [0.18.1] - 2026-08-05

Preparação para o GitHub.

### Added

- `.github/workflows/testes.yml` — corre os testes do pipeline a cada
  alteração, em Python 3.9 e 3.12. Só o pipeline: a app precisaria do
  dicionário de 200 MB, que não é versionado. Os testes do pipeline correm
  sobre fixtures sintéticas e são inteiramente offline, o que os torna
  perfeitos para integração contínua.
- `scripts/publicar.py` — publica uma versão no GitHub com um comando:
  etiqueta, notas tiradas da secção certa do CHANGELOG, e o APK anexado.
  Antes disso confirma o que se esquece quando é manual — que há notas para
  esta versão, que o APK existe e **traz mesmo o dicionário lá dentro**, que
  não há alterações por commitar, e que a etiqueta ainda não existe. Tem
  ensaio por omissão; só publica com `--a-serio`.

  Corre localmente e não no GitHub Actions de propósito: compilar exige o
  dicionário de 60 MB (não versionado) e a chave de assinatura (que nunca
  deve estar num repositório). Automatiza-se o que se pode automatizar sem
  guardar segredos onde não devem estar.
- `.github/workflows/release.yml` — ao empurrar uma etiqueta `vX.Y.Z`, cria
  a release com as notas do CHANGELOG. Não anexa o APK, e não pode: um
  workflow corre nos servidores do GitHub e não vê o computador de quem
  compila. Fica como alternativa a quem prefira arrastar o ficheiro no
  browser em vez de usar o `publicar.py`.
- `docs/publicar.md` — o passo a passo de uma publicação, incluindo o aviso
  sobre o `versionCode` (que tem de subir sempre, mesmo num patch, ou o
  Android recusa instalar por cima).

### Fixed

- O `dicionario.versao` nos assets passou a ser ignorado pelo git. Sozinho,
  faria uma cópia recém-clonada julgar que traz um dicionário que não está
  lá — auditoria feita antes do primeiro commit, que é quando ainda é
  barato corrigir.

## [0.16.1] - 2026-08-05 — app v1.2.1

### Fixed

- Tirado o "(opcional)" dos campos Livro e Autor no diálogo de registo. No
  formulário **tudo é opcional menos a palavra**, e dizê-lo só em dois
  campos insinuava que os outros três eram obrigatórios — o contrário do
  que se pretende, que é registar uma palavra sem atrito nenhum.

## [0.16.0] - 2026-08-05 — app v1.2.0

### Added

- **Exportar e importar a coleção**, em Definições → As minhas palavras. O
  ficheiro é JSON legível de propósito: se um dia a app desaparecer, o
  trabalho de anos de quem a usou deve continuar a poder abrir-se num
  editor de texto. Leva tudo — livro, autor, página, frase, nota, data de
  registo e progresso de revisão.

  **Importar junta, nunca substitui.** As palavras já na coleção ficam como
  estão e só entram as que faltam; o resultado diz quantas de cada. Uma
  importação que apagasse o que estava seria a única forma de perder dados
  nesta app, e por isso não existe.

  A cópia automática do Android já protegia o `utilizador.db`, mas depende
  de a pessoa a ter ativa e de a conta ser a mesma. Um ficheiro que se
  guarda onde se quiser não depende de nada.

## [0.15.0] - 2026-08-05 — app v1.1.1

Assinatura para instalação no telemóvel, e a limpeza que ela obrigou.

### Added

- **Configuração de assinatura de lançamento.** A chave e as palavras-passe
  vivem em `keystore.properties`, que não é versionado; sem ele a build cai
  para a chave de depuração e avisa, para que quem clone o projeto o consiga
  compilar. A chave decide se uma instalação pode ser **atualizada** —
  trocar de chave obriga a desinstalar, e desinstalar leva a coleção de
  palavras. Por isso existe desde já.

### Fixed

- **As regras de cópia de segurança eram inválidas** e faziam o lint falhar
  a build de lançamento: quando se usa `<include>`, tudo o resto já fica de
  fora, e excluir explicitamente o dicionário era contraditório. As linhas
  a mais saíram. (A sugestão do lint — criar um *baseline* — seria esconder
  o erro; a regra estava mesmo errada.)
- `java.util.Properties` não resolvia no `build.gradle.kts`: no Kotlin DSL o
  nome `java` é a extensão do plugin Java e esconde o pacote. Mesmo erro que
  já tinha acontecido com o `ZipFile` — ambos agora importados no topo.
- `kotlinOptions { jvmTarget }`, depreciado desde o Kotlin 2.0, passou para
  `kotlin { compilerOptions }`, que é tipado em vez de string. Fecha os três
  avisos de deprecação que se acumulavam.

## [0.14.0] - 2026-08-04

### Fixed

- **"Instalar do APK" continuava disponível depois de instalar.** A opção
  aparecia sempre que houvesse um dicionário empacotado, em vez de só
  quando havia um **por instalar**. Agora recalcula-se depois de instalar e
  desaparece — não faz sentido convidar a repetir um trabalho de 200 MB.

### Added

- **Ecrã de Definições**, com três secções: aspeto, dicionário e fontes. O
  rodapé do ecrã principal ficou só com a versão da app; a data da base, as
  contagens e a lista de fontes com as licenças passaram para aqui, que é
  onde se consultam uma vez em vez de ocuparem espaço permanente.
- **Tema forçável.** Segue o telemóvel por omissão, mas quem lê à noite
  pode querer o escuro sempre. Guardado nas preferências.
- **Editar uma palavra registada** — livro, autor, página, frase e nota.
  A edição usa `copy` sobre o registo existente, preservando o id, a data
  de registo e o progresso de revisão: corrigir uma nota não é registar a
  palavra de novo.

## [0.13.0] - 2026-08-04

**O dicionário chega à app.** E as correções de 1913 confirmam-se no ecrã:
*macilento* aparece como adjetivo, com as três aceções.

### Fixed

- **O APK trazia o dicionário; a app é que o procurava pelo nome errado.**
  O AGP trata os assets terminados em `.gz` de forma especial: guarda-os
  descomprimidos e **tira-lhes a extensão**, pelo que o `dicionario.db.gz`
  ficava no APK como `dicionario.db`. A app pedia o nome original, recebia
  um "ficheiro não encontrado", e concluía que não havia dicionário nenhum.
  Passa a aceitar os dois nomes e a detetar o formato pelos bytes mágicos —
  a única coisa que não depende de convenções de terceiros.

  Pelo caminho excluíram-se, um a um: o ownCloud, a marca de versão, a
  compilação incremental e as versões das ferramentas. O que resolveu foi o
  diálogo de diagnóstico listar os assets reais: `dicionario.db,
  dicionario.versao, geoid_map, images, webkit`. O nome estava à vista.

- `assets.list()` deixou de ser usado para decidir se há dicionário; abre-se
  o ficheiro e vê-se. A listagem servia para diagnóstico e foi promovida a
  isso.
- A verificação do APK no Gradle aceita os dois nomes e, quando falha, diz
  que assets encontrou.

## [0.12.0] - 2026-08-04

Ferramentas de compilação atualizadas, e a razão pela qual não estavam.

### Changed

- **AGP 8.5.2 → 9.2.0, Kotlin 2.0.20 → 2.2.21, compileSdk/targetSdk 35 → 36,
  Compose BOM 2024.09 → 2026.06, Room 2.6.1 → 2.8.2.** As versões iniciais
  eram de meados de 2024 e o Android Studio instala o Gradle 9.3 — uma
  combinação que nunca foi testada, e candidata a explicar o asset do
  dicionário não entrar no APK. O `compileSdk 35` exige AGP 8.6+; estávamos
  abaixo disso.
- **Removido o `android.suppressUnsupportedCompileSdk`** que eu tinha
  acrescentado na versão anterior. Era um penso rápido sobre um aviso que
  estava a dizer a verdade — a crítica do Jorge foi acertada e a correção é
  atualizar, não calar.

### Added

- Tarefa de Gradle que **recusa compilar sem o dicionário nos assets**, com
  a instrução de como o gerar. Uma compilação que falha com uma mensagem
  clara vale mais do que uma app que instala silenciosamente a base antiga.
- Diálogo de diagnóstico (tocar no rodapé): diz se há dicionário dentro do
  APK, qual está instalado e quantos lemas tem, e permite forçar a
  instalação. Foi o que identificou o problema em dois toques.
- O rodapé passa a mostrar a **data de construção e o número de lemas** em
  vez do `db_version` — que é sempre "1" e não distinguia nada.

## [0.11.0] - 2026-08-04

Interface, terceira ronda. E a razão de o dicionário novo não chegar à app.

### Fixed

- **Um dicionário reconstruído nunca substituía o instalado.** A app só
  instalava a base do APK quando não havia nenhuma — quem já a tinha a
  funcionar ficava preso à antiga para sempre. O `empacotar` passa a
  escrever uma marca de versão (`dicionario.versao`, com o db_version e o
  sha256) ao lado do ficheiro comprimido; a app compara-a com a que
  instalou e atualiza quando diferem, dizendo "A atualizar o dicionário".

### Added

- **Tipografia com serifa** para a palavra e as definições; os controlos
  ficam na tipografia do sistema. Um dicionário lê-se com serifas, e usa-se
  a do próprio Android — não acrescenta um byte ao APK. Entrelinha mais
  larga no corpo, que são textos densos e muitas vezes de 1913. As cores
  dinâmicas do sistema saíram: o papel e a tinta são a identidade da app.
- **Ecrã inicial com propósito** — em vez de vazio, mostra as últimas
  palavras registadas, tocáveis. Transforma "app que consulto" em "app que
  abro". Sem coleção ainda, explica o gesto do leitor de e-books.
- **Sinónimos e antónimos tocáveis.** Eram texto morto; um sinónimo é uma
  palavra que também se pode não conhecer.
- **Rodapé com as versões** da app e do dicionário. Parece detalhe, mas
  resolve uma dúvida real: depois de reconstruir a base, saber se a que
  está no telemóvel é a nova.
- Coleção: **contagem de palavras por livro** e alternar entre ordem
  cronológica (rever o recente) e alfabética (procurar).

## [0.10.0] - 2026-08-04

Interface, da segunda ronda de uso pelo Jorge.

### Fixed

- **Dava para registar a mesma palavra duas vezes** ('sumptuoso' apareceu
  duplicado na coleção). O lema passa a ser único: a coleção é o registo do
  que já se aprendeu, não um diário de encontros. Migração v1→v2 escrita à
  mão, que **guarda o registo mais antigo** — o que traz o livro e a frase
  da primeira vez — em vez de apagar e recomeçar. O `@Insert` usa IGNORE e
  não REPLACE pela mesma razão.
- A classe gramatical 'desconhecido' já não aparece por baixo da palavra;
  não dizer nada é melhor do que dizer que não se sabe.

### Added

- Título centrado e com peso de cabeçalho, com um contador das palavras
  registadas ao lado do marcador.
- Botão que mostra **«Registada»** quando a palavra já está na coleção, em
  vez de convidar a duplicá-la.
- Botão de limpar na caixa de pesquisa.
- Esquecer uma palavra a partir da coleção, com confirmação que explica que
  o dicionário não muda — só a coleção.

## [0.9.0] - 2026-08-04

Da primeira utilização da app a sério pelo Jorge.

### Fixed

- **Três defeitos que deixavam o dicionário de 1913 mutilado.** Vieram todos
  ao de cima por *macilento* não ter definição na app:

  1. **A deteção de domínios comia as definições.** A entrada de 1913 é
     `Magro. Pálido. Amortecido.`, e a heurística aceitava qualquer palavra
     capitalizada terminada em ponto como marca de domínio — engolia as três
     e deixava a definição vazia. Agora só reconhece marcas entre
     parênteses ou da lista de abreviaturas conhecidas, e nunca consome o
     texto todo. **+60 mil aceções** (152 336 → 212 534).
  2. **As aceções vinham coladas.** O dicionário escreve-as em linhas
     seguidas dentro do mesmo `<def>`; eram tratadas como um bloco só.
     Passam a ser aceções separadas, como num dicionário a sério.
  3. **A classe gramatical perdia-se toda.** Procurava-se `<pos>` quando o
     ficheiro usa `<gramGrp>`; e, corrigido isso, um `Element` sem filhos é
     falso em ElementTree, pelo que o `or` o descartava na mesma. Por fim,
     1913 etiqueta os substantivos só pelo género (`m.`, `f.` — 150 mil
     entradas), que não estava mapeado. De **0% para 100%** dos lemas do
     Dicionário Aberto com classe gramatical.

### Added

- **`palavrame empacotar`** — comprime a DB para `android/app/src/main/assets`.
  Decisão do Jorge: os 200 MB vão dentro do APK (~60 MB comprimidos) e a app
  descomprime-os no primeiro arranque. Deixa de ser preciso instalar a base
  à mão; o seletor de ficheiros fica como caminho para a trocar depois.
- **Caixa de seleção do livro** no diálogo de registo, com o último livro já
  preenchido. Quem lê um livro regista dele muitas palavras seguidas, e
  reescrever o título de cada vez era a fricção mais fácil de eliminar.

## [0.8.0] - 2026-08-04

### Changed

- **Pacote da app: `pt.jorges15.palavrame`** (era `pt.stonehub237.palavrame`,
  o do plano). Escolha do Jorge; o plano fica desatualizado nesse ponto.

### Added

- **Instalação do dicionário pelo seletor de ficheiros do Android**, em vez
  de `adb push`. Copia-se o `.db` para o telemóvel por onde for mais
  cómodo — cabo, Drive, ownCloud — e escolhe-se na app. Dispensa o `adb`,
  e é também o caminho para trocar o dicionário por uma versão nova, coisa
  que o plano quer que seja trivial.
  A cópia escreve para um ficheiro temporário e só substitui no fim: se
  falhar a meio (bateria, espaço, cabo), a base que lá estava continua boa.
  Antes de substituir, confirma que o ficheiro escolhido é mesmo um
  dicionário do Palavra-me, lendo-lhe a versão.

## [0.7.0] - 2026-08-04

**A app existe.** F1 aceite pelo Jorge; começa a F2.

### Added

- `android/` — app Kotlin + Compose, `pt.stonehub237.palavrame`, minSdk 26.
  Pesquisa com resolução de flexões, ecrã de entrada (aceções por fonte,
  exemplos, sinónimos), botão registar com livro/página/frase, coleção
  agrupada por livro. **Sem permissão de rede**, como o plano manda para a
  F2: assim é impossível haver uma dependência escondida de um servidor.
- **`PROCESS_TEXT` já na F2** (o plano punha-o na F4 e sugeria antecipar):
  selecionar uma palavra no leitor de e-books e enviá-la direto para a app.
- `Dicionario.kt` abre a base com `OPEN_READONLY` e não usa Room — Room
  quer gerir migrações, e migrar esta base seria um erro conceptual: o que
  se faz é trocar o ficheiro. O Room fica só para `utilizador.db`, onde
  `lemma` é guardado como TEXTO, não como chave estrangeira.
- `TextoTest.kt` trava a costura mais frágil do projeto: a normalização em
  Kotlin tem de dar exatamente o mesmo que `palavrame.text.normalize`. Os
  valores esperados saíram de correr a versão Python; numa amostra de 400
  formas acentuadas da base real, zero divergências.
- Regras de cópia de segurança que incluem `utilizador.db` e excluem o
  dicionário (200 MB, público e reconstruível).

### Fixed

- **`couberam` e `pusesse` apareciam como palavras** a par de `caber` e
  `pôr`. As páginas de flexão do Wikcionário que trazem tabela de
  conjugação escapavam ao filtro da 0.4.0, porque tinham `forms` mesmo sem
  aceções. Uma página que só remete para outra palavra não é um lema.
  Descoberto ao validar as consultas da app contra a base real.

### Verificado

Consultas da app contra `dicionario-1.db` (186 831 lemas): `couberam` →
caber, `pusesse` → pôr, `ensonados` → ensonado, todas abaixo de 10 ms.

## [0.6.0] - 2026-08-01

O PULO passa a dar sinónimos, antónimos e hierarquia — a tabela `synonyms`
deixa de estar vazia.

### Added

- **Parser do dump SQL do PULO.** Esquema confirmado no ficheiro real:
  `wei_por-30_variant` (palavra → synset), `wei_por-30_relation`
  (synset → synset) e `wei_por-30_synset` (glosas). Os códigos de relação
  são numéricos e o dump não traz tabela de nomes; identificaram-se por
  dupla via — pares legíveis ('capaz' → 'incapaz') e contagens que batem
  com as estatísticas publicadas do OpenWordNet-PT (similarTo 21386,
  memberHolonym 12293, partHolonym 9097). Mapeiam-se só os códigos de que
  há certeza: 12 (hierarquia), 33 (antónimo), 34 (similar). O resto
  ignora-se, que é melhor do que inventar. As relações hierárquicas são
  geradas nos dois sentidos.
- `palavrame/mysqldump.py` — leitura de mysqldump partilhada. Existe porque
  duas fontes precisam dela e o contrato do projeto proíbe uma fonte
  importar de outra (o teste `test_sources_nao_importam_umas_das_outras`
  apanhou a violação).

### Changed

- **Sinónimos só de synsets pequenos** (`MAX_SINONIMOS_POR_SYNSET = 5`). O
  PULO alinha português com a WordNet de Princeton, e o synset inglês
  'hole' recolhe *janela, buraco, covil, défice, dívida* — traduções do
  mesmo conceito inglês que em português não são sinónimas. Mostrar isso a
  quem lê seria pior do que não mostrar nada. Preserva 96% dos synsets;
  'cão' continua a dar 'cachorro', 'janela' deixa de dar 'deficit'.

### Notas

- As glosas do PULO estão **em português** e cobrem ~18 mil synsets. São
  tradução automática do inglês e a qualidade varia. Não entram como
  aceções — é uma decisão de conteúdo, e essas são do Jorge.

## [0.5.0] - 2026-08-01

### Fixed

- **A F1 esgotava a memória** e parecia bloquear na fusão (no Windows entra
  em swap; num ambiente com 3 GB é morta pelo sistema). A causa: o pipeline
  segurava as entradas de todas as fontes ao mesmo tempo que construía o
  resultado — só o Wikcionário são 615 MB de objetos Python, e o dump do
  Dicionário Aberto, que passou a ser lido na 0.4.1, foi a gota.

  A fusão passou a ser incremental (`merge.merger.Merger`): três rondas —
  atestação (cada fonte reduzida a strings), aplicação (as mesmas fontes
  relidas, conteúdo aplicado), exemplos. As fontes são consumidas por
  iterador e libertadas; `_stream_source` no CLI garante que nunca se faz
  `list()` de uma fonte inteira. Medido: a fase que rebentava passou de
  615 MB para 111 MB. O preço é reler as fontes de lemas uma segunda vez —
  troca barata numa build que corre raramente.

  `merge_entries()` mantém-se como conveniência para a F0 e para os testes,
  agora a delegar no `Merger`.

### Changed

- **PULO entra.** Alberto Simões (U. Minho) respondeu ao email: *"Pode usar
  os dados do PULO, com a mesma licença do dicionário aberto"* — CC BY-SA
  2.5 PT, o mesmo copyleft que a DB já herda. A fonte `wordnet_pt` passa a
  apontar primeiro ao SQL do PULO (URL estável) e só depois ao
  OpenWordNet-PT, cujo dump no GitHub deu 404.

## [0.4.1] - 2026-08-01

### Fixed

- **O dump do Dicionário Aberto era descarregado mas nunca lido**: a F1 dava
  92 entradas (só o que a API trouxera na F0) em vez das 128 520 do dump. O
  ficheiro real escreve `INSERT  IGNORE INTO` — com `IGNORE` e dois espaços —
  e o parser só reconhecia `INSERT INTO`. Passa por uma expressão regular
  que aceita as duas formas; verificado contra o dump real: 128 520 entradas,
  152 336 aceções, ~40 s de leitura. A fixture de teste passou a usar a
  variante com `IGNORE`, para que a regressão não volte.

## [0.4.0] - 2026-07-31

Da primeira F1 real (396 948 lemas, 255 MB — números que revelaram o
problema).

### Changed

- **As páginas de flexão do Wikcionário deixaram de ser lemas.** 54% das
  entradas portuguesas do wiktextract são remissões ('cantada — feminino do
  particípio de cantar'), marcadas com `form_of`; entravam como lemas
  próprios e incharam a F1. Agora convertem-se em linhas da tabela `forms`
  penduradas no lema verdadeiro — pesquisar "cantada" chega a "cantar"
  mesmo onde o Hunspell não conhece a forma, e o universo de lemas encolhe
  para perto do léxico real. Reconstruir com `python -m palavrame.cli f1`.
- A mensagem final do `f1` sugere o comando completo
  (`python -m palavrame.cli validar …`) — o atalho `palavrame` só existe
  se o pacote for instalado no venv.

## [0.3.0] - 2026-07-31

A F0 fechou com decisão de continuar; isto é a infraestrutura da F1.

### Added

- **`palavrame f1`** — a build do dicionário inteiro, sem lista de seeds.
  Duas rondas: as fontes que criam lemas correm primeiro e definem o
  universo; as fontes de exemplos/relações indexam sobre ele. Termina com
  fusão, DB, relatórios e validação, como a F0.
- **`fetch --completo`** — o Dicionário Aberto passa a poder trazer o
  dicionário todo: o único dump publicado é o mysqldump de 2015-12-13 no
  repositório oficial (SQL/data-20151213.sql.xz), e o parser novo lê-o em
  duas passagens (a tabela `word` diz a revisão em vigor; a `revision` traz
  o TEI), com um tokenizador próprio para os escapes do mysqldump. A API
  por palavra continua a prevalecer sobre o dump quando ambos estão no
  cache, por ser mais recente.
- OpenWordNet-PT com caminhos candidatos (dump/own-pt.nt.gz no GitHub),
  leitura de .nt.gz por bytes mágicos, e `fetch --url` como recurso.
- Leipzig com corpora candidatos e instrução manual. Descoberta do
  caminho: o padrão real de nomes usa hífen na variante (`por-pt_*`),
  não underscore como o plano supunha.

### Changed

- Decisão do Jorge (2026-07-31): a wordnet da F1 é **só o OpenWordNet-PT**
  (CC BY 4.0, verificada); o PULO fica fora até resposta ao email pedido de
  termos. O VOC fica fora da F1 (modo permissivo), com email ao IILP em
  paralelo. Emails redigidos: Priberam, PULO/ambs, IILP.
- `cmd_f0` refatorado: o troço fontes→fusão→DB→validação passou a ser
  partilhado com o `f1` (`_parse_source`, `_collect_amalia`,
  `_finish_build`).

## [0.2.3] - 2026-07-30

Da primeira leitura da folha de revisão pelo Jorge.

### Fixed

- **'agua' aparecia como entrada vazia ao lado de 'água'.** Entradas sem
  definições (o Hunspell inteiro; entradas do Wikcionário como "grafia
  obsoleta de água" ou nomes próprios sem glosa) só abrem lema novo quando
  mais ninguém reclamou a palavra — caso contrário remetem para a grafia
  definida do grupo. Pesquisar "agua" leva agora a "água"; 'Mao' deixou de
  ser entrada fantasma ao lado de 'mão'. O grupo vazio continua a abrir
  entrada ('ensonado' só existe no Hunspell, e a entrada sem definição é o
  retrato honesto dessa lacuna). A atestação primária passou a duas voltas
  (primeiro quem tem aceções), para que a ordem no dump não decida.

### Known issues (registados, não corrigidos)

- Exemplos apanhados por forma podem calhar no homógrafo errado: as frases
  com "cantada" (particípio de cantar) aparecem na entrada 'cantada'
  (substantivo). Em parte é artefacto da amostra F0 — 'cantar' não está nos
  100 seeds, na F1 a flexão levará também ao verbo — mas a atribuição por
  forma não distingue homógrafos. Mitigação prevista para a F3: verificação
  de classe gramatical (lematizador) e exemplos por aceção via AMALIA.

## [0.2.2] - 2026-07-30

A primeira F0 sobre dados reais encravou e depois mentiu. Três bugs, todos da
mesma família: tratar a forma normalizada como se fosse a identidade do lema.

### Fixed

- **A fusão "congelava"** (na verdade demorava horas): a deduplicação de
  exemplos renormalizava a lista inteira a cada inserção — O(n²) sobre os
  ~30 000 exemplos que uma palavra comum apanha no Tatoeba real. Passou a
  conjunto de chaves anexado à entrada: a fusão completa caiu para ~2 s.
- **`por` e `pôr` colapsavam numa entrada só** e a sonda `pusesse -> pôr`
  falhava: a fusão era indexada pela forma normalizada. A chave passou a ser
  a grafia exata; grafias que nenhuma fonte de lemas atesta (acentuação de
  1913) são remapeadas para a grafia atestada do grupo, e fontes secundárias
  (Hunspell, que traz nomes próprios) não abrem lema novo por mera diferença
  de capitalização.
- **O writer do SQLite tinha o mesmo bug**: `lemma_ids` indexado pelo
  normalizado fazia `Internet`/`internet` partilharem id — as aceções de
  ambos caíam num só lema e o outro ficava vazio na DB. Indexado pela grafia
  exata; o normalizado fica só como recurso para alvos de relações.
- **O Dicionário Aberto capitaliza todos os verbetes** ('Casa', 'Caber') — é
  tipografia de 1913, não grafia, e criava um duplicado vazio ao lado de cada
  entrada do Wikcionário. O parser rebaixa o padrão de verbete; siglas ficam.

Resultado na F0 real: de 191 lemas (48% vazios) para 118 lemas com 3 vazios
legítimos ('Mao', 'agua' grafia obsoleta, 'ensonado' ausente das fontes —
a lacuna de cobertura moderna que o plano previa), 4/4 sondas de flexão, DB
APROVADA pela validação.

### Added

- O Hunspell do Natura ficou **verificado**: o Jorge confirmou no cabeçalho
  do `pt_PT.aff` do pacote 20251001 a tri-licença "GPL/LGPL/MPL licenses, by
  this order". Ao abrigo da MPL, a tabela `forms` derivada é redistribuível.
  Fica só o VOC e o PULO por decidir — nenhum bloqueia a F0.

## [0.2.1] - 2026-07-30

### Fixed

- `test_ficheiros_com_terminadores_windows` usava `write_text(newline="")`,
  que também só existe desde o 3.10. Passou a `write_bytes`, que preserva o
  CRLF de forma ainda mais literal.
- O pipeline não arrancava no Python 3.9 (o de sistema na máquina onde a F0
  vai correr): `PosChecker = Callable[..., bool | None]` é um alias avaliado
  em runtime, e a sintaxe `|` só existe desde o 3.10 — o `from __future__
  import annotations` não cobre aliases. Trocado por `Optional[bool]`, com o
  porquê em comentário. Era a única união em runtime no código todo
  (confirmado por varrimento da AST); o resto são anotações diferidas, que o
  3.9 aceita.

## [0.2.0] - 2026-07-30

A verificação de licenças da F0, feita por leitura dos termos de cada fonte.
`docs/fontes.md` deixou de ser um registo por preencher: cinco fontes ficaram
conclusivas, três registadas com argumentos e decisão em aberto (do Jorge).

### Changed

- **O Dicionário Aberto não é domínio público.** O site declara a edição
  digital como CC BY-SA 2.5 PT; a obra de 1913 é que está em domínio público.
  Corrigido no código e na documentação. Consequência prática: nenhuma — a DB
  derivada já ia ser CC BY-SA por causa do Wikcionário.
- Leipzig confirmado **CC BY nos downloads** (o CC BY-NC é só no portal web):
  frases e frequências entram ambas, ao contrário do que o plano temia.
- Tatoeba confirmado CC BY 2.0 FR com atribuição **por frase** (ToU §6.5) —
  o `source_ref` por exemplo já cumpria.
- Wikcionário confirmado CC BY-SA 4.0 (ToU Wikimedia; a página local de
  direitos de autor está desatualizada). O primeiro candidato do kaikki.org
  passou a ser o extrato da edição portuguesa (`pt-extract.jsonl.gz`,
  confirmado na página de raw data), com os antigos como recurso.
- O `fetch` do Hunspell passou a dar precedência a ficheiros postos à mão e,
  na falta deles, a descarregar o pacote do Natura (endpoint fixado no último
  publicado, 20251001) e a extrair os `.aff`/`.dic` do tarball.
- Os dois testes que codificavam «nenhuma fonte verificada» passaram a testar
  a nova realidade: a DB de fixtures passa o modo distribuição, e a recusa
  continua a ser testada com uma licença artificialmente regredida.

### Added

- O parser do Wikcionário aceita o dump comprimido (.gz) tal como vem do
  kaikki, por deteção dos bytes mágicos — sem passo manual de descompressão.
- `_extract_tarballs` no Hunspell: tira os `.aff`/`.dic` de qualquer tarball
  no cache da fonte, com extração plana e sem confiar em caminhos internos.
- Notas de decisão nas três fontes em aberto (PULO sem licença publicada, VOC
  sem termos nem dump, tri-licença do Natura por confirmar no pacote), com os
  argumentos de cada lado e os contactos para desbloquear por email.

### Fixed

- `sources/tatoeba.py` tinha ficado truncado numa gravação anterior (faltavam
  o fim de `_open_text` e a função `_guess_variant` inteira). Reconstruído; a
  heurística de variante volta a devolver pt-PT/pt-BR/unknown e o teste
  `test_tatoeba_deteta_variante_pt_pt` volta a passar.

## [0.1.3] - 2026-07-30

Primeira execução real do pipeline, em Windows. O que se aprendeu.

### Added

- `fetch --url` e `fetch --ficheiro` — alimentam uma fonte a partir de um URL
  ou de um ficheiro descarregado à mão, sem editar código. Os URLs das fontes
  mudam, e obrigar a mexer no código de cada vez seria uma forma tola de
  bloquear quem está a correr o pipeline. O ficheiro entra no cache e no
  lockfile como qualquer outro, portanto a build continua verificável.
- `SourceInfo.primary` — nome canónico do ficheiro principal de cada fonte, o
  que permite ao `--url` saber onde guardar o que descarrega.

### Changed

- O Wikcionário tenta três caminhos conhecidos do kaikki.org antes de desistir,
  e quando desiste explica como obter o URL certo. O caminho que estava fixado
  no código dava 404 — o kaikki reorganiza os dumps, e assumir um caminho
  estável era erro meu.

## [0.1.2] - 2026-07-30

Portabilidade para Windows, que é onde o projeto é desenvolvido.

### Fixed

- **A base de dados não abria em Windows.** O URI de só-leitura era construído
  por interpolação do caminho (`file:{path}?mode=ro`), e o SQLite trata a barra
  invertida como parte do nome do ficheiro, não como separador — portanto
  `file:C:\Users\...\dicionario.db` nunca abria. Passa por `Path.as_uri()`,
  que também trata dos espaços num caminho como `C:\Users\Jorge Silva`.
- Saída da consola forçada a UTF-8. Em Windows, `stdout` redirecionado usa a
  página de código do sistema (cp1252), e um símbolo fora dela — o `∅` que
  aparece quando uma sonda de pesquisa falha — rebentava com
  `UnicodeEncodeError` e levava o relatório inteiro com ele.

### Added

- `tests/test_portabilidade.py` — caminhos com espaços e acentos, ficheiros com
  terminadores CRLF, símbolos fora do cp1252, e uma verificação que percorre o
  código a garantir que nenhuma leitura de ficheiro fica sem `encoding`
  explícito (em Windows, o padrão seria cp1252 e estragaria todos os acentos).
- `.gitattributes` — impede a conversão para CRLF ao clonar em Windows, que
  alteraria os hashes do lockfile e quebraria a reprodutibilidade entre
  máquinas.
- Guia reescrito para PowerShell, com ambiente virtual, e uma secção de
  diagnóstico específica do Windows.

## [0.1.1] - 2026-07-30

### Added

- `docs/como-comecar.md` — guia passo a passo para completar a F0: obter cada
  fonte, verificar as licenças, correr o protótipo e conduzir a revisão final.

### Fixed

- `fetch --source hunspell_natura` deixa de falhar quando os ficheiros
  `.aff`/`.dic` foram colocados à mão. Passa a registá-los no lockfile, como o
  VOC já fazia — um `fetch` não deve reclamar do que já está em cache.

## [0.1.0] - 2026-07-30

Primeira versão do pipeline de construção do dicionário (plano, fase F0).
Sem código de Android, por decisão do plano: nada de app antes de haver 100
entradas revistas à mão.

### Added

- **Pipeline** (`pipeline/`), em Python 3.11 e **sem dependências externas**,
  para que uma build se reproduza sem resolver dependências.
- **Cache endereçado por conteúdo** com lockfile `sources.lock.json`
  (url → sha256), downloads com repetição e recuo exponencial, e modo
  `--offline`.
- **Sete módulos de fonte**, isolados uns dos outros, cada um com a sua
  licença declarada e o estado de verificação: Dicionário Aberto (API por
  palavra e dump TEI), Wikcionário (JSONL do wiktextract), VOC da CPLP,
  wordnet PT (N-Triples e TSV), Hunspell do Natura, Tatoeba, Leipzig.
- **Expansão de afixos Hunspell** (`affix.py`): SFX, PFX, produto cruzado,
  bandeiras de continuação, condições com classes de caracteres, e strip do
  lema inteiro para formas supletivas. É o que produz a tabela `forms`, e é o
  que faz `couberam` chegar a `caber`.
- **Fusão** com as regras da secção 5.2 do plano: o VOC decide a lista de
  lemas, a grafia e a classe gramatical; aceções de fontes diferentes nunca se
  fundem; conflitos ficam num relatório em vez de se resolverem em silêncio.
- **Escrita do `dicionario.db`** com o esquema da secção 6.1, índices, FTS5
  com `content=lemmas`, tabela `meta` com versão e data, e checksum sha256 ao
  lado do ficheiro.
- **Validação** em duas famílias: integridade (chaves, proveniência