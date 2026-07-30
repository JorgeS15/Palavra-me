# Estado do projeto

Atualizado: 2026-07-30

## Onde estamos

**F0, a meio.** O pipeline existe e corre de ponta a ponta. Falta-lhe o que
não se pode fazer sem rede: os dados reais.

O plano manda parar na F0 e não escrever Android nenhum até haver 100 entradas
revistas à mão e consideradas boas (secção 10.1). Nenhuma linha de Android foi
escrita.

## Feito

| Peça | Estado |
|---|---|
| Estrutura do monorepo (`pipeline/`, `docs/`) | ✅ |
| Cache com lockfile por sha256, builds reprodutíveis | ✅ |
| Sete módulos de fonte, isolados, com licença declarada | ✅ |
| Expansão de afixos Hunspell (a tabela `forms`) | ✅ |
| Fusão com as regras de conflito da secção 5.2 | ✅ |
| Escrita do SQLite com o esquema da secção 6.1 + FTS5 | ✅ |
| Validação de integridade e de licenciamento | ✅ |
| Prompts e validação automática do AMALIA | ✅ |
| Revisão humana em terminal | ✅ |
| Relatório de build e folha de revisão da F0 | ✅ |
| Lista dos 100 lemas da F0 | ✅ |
| 125 testes, a correr offline | ✅ |
| `docs/fontes.md` — **estrutura** criada, **conteúdo por verificar** | ⚠️ |

## Por fazer, e porquê

### 1. Obter os dados (bloqueado por rede, não por código)

O pipeline foi construído num ambiente cuja política de saída bloqueia todos
os hospedeiros das fontes. Confirmado, um a um:

```
api.dicionario-aberto.net   403 (CONNECT recusado pela política)
downloads.tatoeba.org       403
voc.cplp.org                403
wordnet.pt                  403
natura.di.uminho.pt         403
wortschatz.uni-leipzig.de   403
kaikki.org                  403
huggingface.co              403
```

Só `pypi.org` e `github.com` passam. Não é um problema do pipeline: numa
máquina com rede normal, `palavrame fetch` resolve isto.

**Como se destranca:** correr numa máquina com acesso livre à Internet.

```bash
cd pipeline
python -m palavrame.cli fontes    # ver o que falta verificar
python -m palavrame.cli fetch     # descarrega tudo o que é automatizável
python -m palavrame.cli f0        # protótipo sobre os 100 lemas
```

Três fontes precisam de um passo manual antes, porque não publicam um
download estável: o **VOC** (ver `INFO.manual` em `sources/voc_cplp.py`), o
**Leipzig** e a **wordnet** (escolher os ficheiros e preencher `endpoints`).
O Hunspell do Natura também precisa que se confirme o URL.

### 2. Verificar as licenças (só depende de ler)

`docs/fontes.md` está montado como um registo por preencher, com o que
verificar em cada fonte. **Nenhuma linha está verificada** — as licenças no
código dizem-no, e `palavrame validar --distribuicao` reprova a DB enquanto
assim for.

Isto não precisa de rede especial nem de código: precisa de abrir sete sites e
ler os termos. É o pré-requisito da F1 (plano 10.5).

### 3. Rever as 100 entradas (é do Jorge, por definição)

O passo F0.3 é um juízo humano e o plano é claro: *"a decisão de continuar é
do Jorge, com esses dados à frente"*.

O `f0` produz `out/revisao-f0.md`, com as entradas formatadas para leitura
seguida e uma caixa por palavra. A pergunta é uma só: **isto ajudou-me a
perceber a palavra?**

Se a resposta for não com frequência suficiente, o plano manda parar e
reconsiderar antes de investir na app — e essa é a decisão que a F0 existe
para tornar barata.

## O que os testes já provam

Sem dados reais não se pode aferir qualidade, mas pode-se aferir correção. Os
125 testes correm sobre fixtures sintéticas (`pipeline/tests/fixtures/`, todas
marcadas como tal) e cobrem:

- **Os três casos de flexão que o plano nomeia**: `couberam` → `caber`,
  `pusesse` → `pôr`, `ensonados` → `ensonado`. Incluindo o caso difícil, que é
  o supletivo — remover o lema inteiro e substituí-lo.
- **O erro do Priberam.** A frase que o plano cita, *"O ensonado sonhou
  longamente ao almoço"*, é rejeitada pela validação automática por
  substantivar um adjetivo. O plano previu que a validação a apanhasse; apanha.
- **A autoridade do VOC** sobre a lista de lemas e a grafia, e o registo dos
  lemas rejeitados em vez do seu desaparecimento silencioso.
- **A cascata de exemplos** Tatoeba → Leipzig → AMALIA, e a preferência por
  pt-PT.
- **O bloqueio de distribuição** com licenças por verificar.
- **Que o pipeline inteiro corre offline** e que nenhum módulo fora de
  `sources/` importa rede.
- **Que corre em Windows**, que é onde é desenvolvido: caminhos com espaços e
  acentos, ficheiros com terminadores CRLF, e símbolos que a página de código
  da consola não conhece.

## Riscos que se mantêm em aberto

Do plano, secção 9, mais o que se viu ao construir:

| Risco | Nota |
|---|---|
| Qualidade das definições de 1913 | Continua a ser o risco principal. Só a F0.3 responde |
| Formato real das fontes | Os parsers assumem formatos documentados mas não confirmados. Confirmar é o passo F0.1; se divergirem, corrige-se o `parse()` da fonte |
| Licença do Leipzig | Se for NC, as frases ficam fora. Tentar manter as frequências, que são o que ordena as desambiguações |
| Licença do CETEMPúblico | Seria a melhor fonte de PT-PT. Provavelmente não é redistribuível |
| Cobertura moderna | Esperado que várias das 20 palavras modernas da lista não tenham definição. Isso é o resultado, não uma falha |
| Tamanho da DB | Só mensurável com os dados reais |
