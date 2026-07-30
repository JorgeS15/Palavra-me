# Como completar a F0

Guia prático para o Jorge, do zero até à decisão de continuar ou parar.

Tempo realista: **uma tarde para os passos 1 a 4**, mais o tempo de leitura do
passo 5, que é o que interessa e não se apressa.

Ordem obrigatória: o passo 3 depende do 2, e o 5 depende do 4. Os passos 1 e 2
podem fazer-se em paralelo — o 2 é ler sites, não é correr código.

---

## Passo 0 — Pôr o pipeline a andar (5 minutos)

Precisas de Python 3.11 ou mais recente. Mais nada: o pipeline não tem
dependências.

```bash
git clone https://github.com/JorgeS15/Palavra-me.git
cd Palavra-me/pipeline
python3 --version          # tem de dizer 3.11 ou superior
```

Confirma que está tudo de pé antes de tocar em dados reais:

```bash
python3 -m pytest
```

Devem passar 116 testes, em poucos segundos e sem rede. Se passarem, o
pipeline está sã; o que falhar a partir daqui é dado, não código.

E vê o mapa das fontes:

```bash
python3 -m palavrame.cli fontes
```

Isto lista as sete fontes, o que cada uma dá, e — em maiúsculas — quais têm
licença por verificar. Neste momento são todas.

---

## Passo 1 — Obter os dados

Três fontes descarregam-se sozinhas. Quatro precisam que decidas alguma coisa
primeiro, porque não publicam um download estável e documentado.

### 1a. As automáticas

```bash
python3 -m palavrame.cli fetch --source dicionario_aberto
python3 -m palavrame.cli fetch --source tatoeba
python3 -m palavrame.cli fetch --source wikcionario
```

O que esperar:

- **Dicionário Aberto** — busca à peça, uma palavra por ficheiro, só os 100
  lemas da lista. São 100 pedidos pequenos, demora menos de um minuto. É de
  propósito: não faz sentido puxar o dicionário inteiro para validar 100
  palavras.
- **Tatoeba** — descarrega o export do português inteiro (dezenas de MB). É o
  download mais demorado dos três.
- **Wikcionário** — descarrega o dump do wiktextract em kaikki.org. É grande.

**Se algum falhar com HTTP 404**, o URL mudou. Não é drama: abre o módulo da
fonte em `palavrame/sources/`, corrige o `endpoints`, volta a correr. Os URLs
estão marcados no código como hipóteses precisamente porque não os pude
confirmar.

### 1b. VOC — a lista oficial de lemas

Esta é a mais importante das quatro manuais, porque é ela que decide o que é
uma palavra e como se escreve. Sem ela, o pipeline corre em modo permissivo e
aceita como lema tudo o que qualquer fonte propuser — incluindo grafias
pré-AO90 de 1913 e brasileirismos do Wikcionário.

1. Vai a https://voc.cplp.org/
2. Procura uma forma de exportar a lista de lemas do português europeu.
3. Guarda como `pipeline/cache/voc_cplp/voc.csv`, neste formato:

```csv
lema,classe
abacate,substantivo
caber,verbo
```

A coluna `classe` é opcional. Se só tiveres a lista de palavras, uma coluna
chega — as outras fontes preenchem a classe gramatical depois.

4. Regista no lockfile:

```bash
python3 -m palavrame.cli fetch --source voc_cplp
```

**Se não conseguires exportar nada**, salta esta fonte. A F0 corre sem ela, em
modo permissivo, e o relatório diz-te que a autoridade de lemas é «nenhuma».
Para validar qualidade de definições isso chega. Para a F1 já não chega.

### 1c. Hunspell — as flexões

É o que faz a app encontrar `caber` quando escreves `couberam`. Numa app de
leitura isto não é acessório, é metade do produto.

1. Vai a https://natura.di.uminho.pt/wiki/doku.php?id=dicionarios:main
2. Descarrega o pacote pt-PT. **Lê a licença enquanto lá estás** — precisas
   dela no passo 2.
3. Extrai o `.aff` e o `.dic` para `pipeline/cache/hunspell_natura/`.
4. `python3 -m palavrame.cli fetch --source hunspell_natura`

Ficheiros postos à mão são aceites e registados no lockfile, tal como o VOC.

**Como saber se correu bem:** o passo 4 vai correr sondas de pesquisa e
dizer-te `pesquisa por flexão: 4/4 sondas passaram`. Se disser menos, a
expansão de afixos não está a produzir as formas certas — e aí vale a pena
abrir uma issue, porque é bug meu, não teu.

### 1d. Leipzig — frequências e frases

Escolhe os corpora e preenche os URLs em `palavrame/sources/leipzig.py`:

```python
endpoints={
    "por_pt_2019_1M": "https://downloads.wortschatz-leipzig.de/corpora/por_pt_2019_1M.tar.gz",
},
```

Duas coisas a saber:

- **Prefere os `por_pt_*`.** São de Portugal. Os `por_*` sem marca misturam
  variantes, e o filtro heurístico de PT-PT/PT-BR não compensa isso bem.
- **O nome da chave importa.** O pipeline lê a variante do nome do ficheiro:
  `por_pt` → pt-PT, `por_br` → pt-BR. Um nome sem marca dá `unknown`.

Depois: `python3 -m palavrame.cli fetch --source leipzig`

**Esta é a fonte com maior probabilidade de más notícias na licença.** Se for
não-comercial, as frases ficam fora de uma app publicada. Ver o passo 2.

### 1e. Wordnet — sinónimos

Decide entre PULO (wordnet.pt) e OpenWordNet-PT, ou usa os dois. Preenche
`endpoints` em `palavrame/sources/wordnet_pt.py`.

**A extensão do nome do ficheiro importa**, porque é ela que escolhe o parser:
`.nt` para N-Triples, `.tsv` para a tabela simples.

```python
endpoints={
    "own-pt.nt": "https://.../own-pt.nt",
},
```

É a fonte menos crítica das cinco. Se te atrasar, salta-a: sinónimos são um
bónus, não são o que decide se o dicionário serve para ler.

---

## Passo 2 — Verificar as licenças

**Não precisa de código. Precisa de abrir sete sites e ler.** É o
pré-requisito da F1, e o plano é explícito quanto a não o deixar para o fim.

Abre `docs/fontes.md`. Está montado como lista de verificação, com uma secção
por fonte a dizer o que confirmar e porquê.

Para cada fonte, três perguntas — e só a terceira decide:

1. Que licença tem?
2. Que atribuição exige, e em que forma?
3. **Permite redistribuir o conteúdo dentro de uma app publicada?**

Quando tiveres a resposta, marca no código. Exemplo, em
`palavrame/sources/tatoeba.py`:

```python
license=License(
    name="CC BY 2.0 FR",
    url="https://creativecommons.org/licenses/by/2.0/fr/",
    attribution="Frases de tatoeba.org, CC BY 2.0 FR.",
    redistributable=True,
    verified=True,          # <- só depois de teres lido
),
```

O `verified=True` não é decorativo: enquanto for falso,
`palavrame validar --distribuicao` recusa aprovar a base de dados. É a rede
que impede publicar conteúdo cuja licença ninguém leu.

### Por onde começar, se tiveres pouco tempo

Por ordem de risco:

1. **Leipzig** — a mais provável de ser não-comercial. Se for, vê se consegues
   separar: as frases ficam de fora, mas as *frequências* são contagens sobre
   a língua e o risco é muito menor. Sem elas, a app não sabe ordenar
   candidatos quando escreves «cantada».
2. **Hunspell/Natura** — pode ser copyleft. Nota que o que a app embarca não
   são os ficheiros `.aff`/`.dic`, é a tabela `forms` derivada deles. Se os
   termos não forem claros sobre derivados, vale mais um email ao projeto do
   que uma suposição tua.
3. **Wikcionário** — CC BY-SA, é certo. A consequência é que a base de dados
   derivada tem de ser publicada sob CC BY-SA. Não obriga a abrir o código da
   app; obriga a publicar a DB, que já querias fazer.
4. **VOC** — provavelmente sem obstáculo, mas confirma.
5. **Dicionário Aberto** — a obra de 1913 é domínio público sem dúvida. O que
   falta é ver se a edição digital acrescenta termos próprios.

---

## Passo 3 — Correr o protótipo

```bash
python3 -m palavrame.cli f0
```

Faz tudo: lê as fontes, funde, escreve o SQLite, valida, gera os relatórios.
Sobre 100 lemas demora segundos.

O que aparece no ecrã, e como o ler:

```
  100 lemas na lista da F0.

  voc_cplp             98 entradas        <- quantos lemas cada fonte reconheceu
  dicionario_aberto    71 entradas
  wikcionario          44 entradas
  ...

  Fusão: 100 lemas, autoridade de lemas: voc_cplp, 23 conflitos.

  Validação:
  [AVISO] lemas sem aceção: 18 de 100 (18%)     <- o buraco de 1913. Esperado.
  [info ] pesquisa por flexão: 4/4 sondas passaram   <- as flexões funcionam
  -> APROVADA
```

**Números que importam:**

- `lemas sem aceção` — as palavras modernas que nenhuma fonte define. Esperado
  rondar as 20, que é quantas palavras modernas pus na lista de propósito.
  Muito acima disso significa que o Wikcionário não está a entrar.
- `pesquisa por flexão` — tem de ser 4/4. Menos que isso, o Hunspell falhou.
- `autoridade de lemas` — se disser «nenhuma», o VOC não entrou.

Escreve três ficheiros em `out/`:

| Ficheiro | Para quê |
|---|---|
| `dicionario-f0.db` | a base de dados. Podes abri-la com qualquer cliente SQLite |
| `relatorio-f0.md` | cobertura por fonte, buracos, conflitos por resolver |
| `revisao-f0.md` | **o que interessa.** Ver passo 5 |

---

## Passo 4 — Exemplos com o AMALIA (opcional nesta fase)

Podes saltar isto e decidir a F0 só com definições. Faz sentido saltar se o
que queres saber é se as *definições* servem — que é a pergunta central.

Se quiseres exemplos:

```bash
ollama pull hf.co/amalia-llm/AMALIA-9B-0626-DPO-GGUF:Q4_K_M
python3 -m palavrame.cli gerar --backend ollama --limit 20
python3 -m palavrame.cli rever
python3 -m palavrame.cli f0        # volta a correr: agora inclui os aprovados
```

Sobre a lentidão: é suposto ser lento e não é problema. Não estás a servir
nada, estás a gerar um dataset uma vez. A 2 tokens/segundo continua viável —
deixa correr em background.

O `--limit 20` é para experimentares o circuito antes de o largares sobre o
dicionário todo.

Na revisão, cada frase aparece com a definição que era suposto ilustrar, e
respondes `a` (aprovar), `r` (rejeitar), `e` (editar), `s` (saltar), `q`
(guardar e sair). **Só o que aprovares entra na base de dados** — o que passou
na validação automática mas não foi visto por ti fica de fora.

---

## Passo 5 — A decisão

Abre `out/revisao-f0.md`. São as 100 entradas formatadas para leitura seguida:
definições com a fonte de cada uma, exemplos, sinónimos, e uma caixa por
palavra.

**Lê-as como se estivesses a ler um livro e a precisar delas.** Não avalies o
software. A pergunta é uma só, e é por palavra:

> Isto ajudou-me a perceber a palavra?

Marca `[x]` para útil, `[-]` para inútil. No fim, conta.

O plano não fixa um número mínimo de propósito — fixa um juízo teu. Mas para
teres uma referência ao ler:

- **Acima de 80 úteis** — o dicionário serve. Avança para a F1 com confiança.
- **Entre 60 e 80** — serve com trabalho. Vale a pena olhar para *quais*
  falharam antes de decidir: se forem as modernas, resolve-se com melhor
  cobertura do Wikcionário. Se forem as arcaicas, é mau sinal, porque são
  exactamente o caso de uso da app.
- **Abaixo de 60** — para e reconsidera, que é o que a F0 existe para tornar
  barato. Melhor descobrir agora do que depois de escrever a app.

Repara em duas coisas enquanto lês, que o número sozinho não capta:

1. **O fraseado de 1913 incomoda-te?** Se as definições estiverem certas mas
   soarem a outro século, o problema tem solução — é o passo de modernização
   por LLM da secção 4.4, que está implementado mas ainda não ligado ao `f0`.
2. **As palavras arcaicas são as que mais importam.** São elas que te mandam
   ao dicionário quando lês Eça ou Camilo. Se o dicionário for bom nas comuns
   e mau nas arcaicas, é mau para o que queres fazer com ele.

Quando decidires, regista a decisão em `docs/estado.md`. Daqui a três meses
vais querer saber porque decidiste o que decidiste.

---

## Se alguma coisa correr mal

| Sintoma | Provável causa |
|---|---|
| `Nenhuma fonte produziu dados` | Nada em `pipeline/cache/`. Volta ao passo 1 |
| `HTTP 404` num fetch | URL mudou. Corrige `endpoints` no módulo da fonte |
| `pesquisa por flexão: 0/4` | Hunspell não entrou, ou o `.aff`/`.dic` está noutro sítio |
| `autoridade de lemas: nenhuma` | O VOC não entrou. Corre-se na mesma, em modo permissivo |
| Muitos `lemas sem aceção` | Wikcionário ou Dicionário Aberto não entraram |
| `validar --distribuicao` reprova | Normal enquanto o passo 2 não estiver feito |

Para ver o que cada fonte produziu isoladamente:

```bash
python3 -c "
from palavrame.cache import Cache
from palavrame.config import default_paths
from palavrame.sources import build
fonte = build('dicionario_aberto', Cache(default_paths(), offline=True))
for e in list(fonte.parse(['janela']))[:3]:
    print(e.lemma, e.pos, [s.definition for s in e.senses])
"
```
