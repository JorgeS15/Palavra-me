# Changelog

Todas as alterações relevantes ao projeto. Formato baseado em
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versões seguem
[SemVer](https://semver.org/lang/pt-BR/).

Nota: antes da 1.0.0, uma subida de MINOR pode trazer alterações
incompatíveis — nomeadamente no esquema do `dicionario.db`.

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
- **Validação** em duas famílias: integridade (chaves, proveniência, sondas de
  pesquisa por flexão de ponta a ponta) e licenciamento. Em modo
  `--distribuicao`, uma fonte com licença por verificar **reprova** a base de
  dados.
- **Geração com o AMALIA**: prompts que recebem a aceção específica e proíbem
  explicitamente o erro de classe gramatical; as quatro validações automáticas
  do plano; retentativas; backend Ollama e backend de teste.
- **Revisão humana** em terminal (aprovar/rejeitar/editar), obrigatória antes
  de qualquer conteúdo gerado entrar na base de dados. Os aprovados voltam ao
  pipeline como fonte de exemplos; os pendentes e os rejeitados não entram.
- **Relatório de build** (cobertura por fonte, buracos, conflitos) e **folha de
  revisão da F0** em Markdown, que é o artefacto de decisão da fase.
- **Lista dos 100 lemas da F0** (`seeds/lemas-f0.txt`), escolhida a dedo em
  cinco eixos: comuns, arcaicas, verbos irregulares, modernas e polissémicas.
- **114 testes**, todos offline, incluindo um que percorre a árvore de imports
  para garantir que a rede só vive em `sources/`, e outro que rejeita a frase
  que o plano cita como exemplo de erro do Priberam.
- **`docs/fontes.md`** — registo de licenças montado como lista de verificação,
  com o que confirmar em cada fonte. Pré-requisito da F1.
- **`docs/estado.md`** — o que está feito, o que falta, e o que está bloqueado.
