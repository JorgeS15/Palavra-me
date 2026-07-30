# Fontes e licenças

> **Estado: POR PREENCHER.** Nenhuma linha deste ficheiro está verificada.
>
> O plano é explícito (secção 8, secção 10.5): sem isto preenchido, **não
> avançar para a F1**. Não é documentação para fazer no fim — é o que decide
> se cada fonte pode sequer entrar na base de dados.

## Como se preenche

Para cada fonte, abre o site, lê os termos, e preenche a linha. Depois marca
a fonte como verificada no código:

```python
# pipeline/palavrame/sources/<fonte>.py
license=License(
    name="CC BY 2.0 FR",          # o nome exato que a fonte usa
    url="...",                    # o link para os termos que leste
    attribution="...",            # o texto de atribuição que a fonte exige
    redistributable=True,         # podes distribuí-lo numa app publicada?
    verified=True,                # <- só depois de teres lido
)
```

O `verified=True` não é decorativo: enquanto for falso,
`palavrame validar --distribuicao` **recusa** aprovar a base de dados. É essa
a rede de segurança que impede publicar conteúdo cuja licença nunca ninguém
leu.

Verifica o estado atual a qualquer momento:

```bash
palavrame fontes
```

## A pergunta que interessa

Para cada fonte há três perguntas, e só a terceira decide:

1. Que licença tem?
2. Que atribuição exige?
3. **Permite redistribuir o conteúdo dentro de uma app publicada?**

Uma fonte pode ser aberta para consulta e mesmo assim não ser redistribuível.
"Uso académico" e "não comercial" são os dois casos que mais provavelmente
excluem uma fonte — e são os que é preciso confirmar por escrito, não por
suposição.

---

## Registo

Legenda do estado: ❌ por verificar · ✅ verificada e aprovada · 🚫 verificada e excluída

| Fonte | O que dá | Licença declarada no código | Redistribuível | Estado |
|---|---|---|---|---|
| [Dicionário Aberto](https://dicionario-aberto.net/) | lemas, aceções | Domínio público | sim (presumido) | ❌ |
| [Wikcionário PT](https://pt.wiktionary.org/) | aceções modernas, flexões, exemplos | CC BY-SA 4.0 | sim (copyleft) | ❌ |
| [VOC — CPLP](https://voc.cplp.org/) | lista oficial de lemas, grafia AO90 | por verificar | ? | ❌ |
| [PULO / OpenWordNet-PT](http://wordnet.pt/) | sinónimos, antónimos, hiperónimos | por verificar | ? | ❌ |
| [Hunspell pt-PT (Natura)](https://natura.di.uminho.pt/wiki/doku.php?id=dicionarios:main) | flexões | por verificar (GPL/LGPL/MPL?) | ? | ❌ |
| [Tatoeba](https://tatoeba.org/) | frases de exemplo | CC BY 2.0 FR | sim, com atribuição | ❌ |
| [Leipzig Corpora](https://wortschatz.uni-leipzig.de/en/download/Portuguese) | frequências, frases | por verificar (CC BY-NC?) | ? | ❌ |
| [CETEMPúblico / Linguateca](https://www.linguateca.pt/cetempublico/) | frases PT-PT | uso académico | provavelmente não | ❌ |

---

## Notas por fonte

Cada nota diz **o que verificar**, não o que é verdade. Substitui a nota pela
conclusão depois de leres os termos.

### Dicionário Aberto

A obra base — Cândido de Figueiredo, *Novo Diccionário da Língua Portuguesa*,
1913 — está em domínio público sem margem para dúvida. O que falta verificar é
outra coisa: **se a edição digital do projeto acrescenta termos próprios** à
transcrição, e que atribuição pede.

Verificar também qual é o URL do dump TEI, para poder preencher
`endpoints["dump"]` em `sources/dicionario_aberto.py`. Sem ele, a F1 tem de
buscar palavra a palavra, o que não é razoável para o dicionário inteiro.

### Wikcionário PT

**A fonte com a consequência mais pesada.** É CC BY-SA, portanto copyleft: a
base de dados derivada que inclua estas aceções tem de ser publicada sob
CC BY-SA. Isso não obriga a abrir o código da app — obriga a publicar a DB,
que é coisa que o plano já quer fazer (secção 8).

Verificar: a versão exata da licença em vigor no ptwiktionary (3.0 ou 4.0), e
a forma de atribuição que a Wikimedia pede para conteúdo reutilizado.

Verificar também o URL dos dumps do wiktextract em kaikki.org — o caminho
mudou no passado, e o que está em `sources/wikcionario.py` é uma hipótese.

### VOC — CPLP

**A fonte que decide o que é uma palavra.** Sem ela, o pipeline corre em modo
permissivo e aceita como lema tudo o que qualquer fonte propuser, incluindo
grafias pré-AO90 do dicionário de 1913 e brasileirismos do Wikcionário.

Duas coisas a verificar:

1. Existe um dump descarregável, ou só consulta pelo site? Se só houver
   consulta, a fonte fica manual — está preparada para isso, ver
   `INFO.manual` em `sources/voc_cplp.py`.
2. Os termos de uso permitem redistribuir a lista de lemas?

Nota honesta sobre o ponto 2: uma lista de palavras de uma língua tem pouca
originalidade e em vários ordenamentos jurídicos não é protegível por direito
de autor. Isso é uma opinião defensável, não uma verificação. Vale a pena ler
o que o IILP diz.

### PULO / OpenWordNet-PT

São dois projetos diferentes com termos diferentes. Verificar cada um por si e
decidir: usar um, o outro, ou fundir só a parte que for redistribuível.

O OpenWordNet-PT costuma ser o mais permissivo dos dois, mas confirma.

Verificar também o formato do ficheiro escolhido: o parser em
`sources/wordnet_pt.py` aceita N-Triples e TSV, e é provável que sirva, mas o
formato real manda.

### Hunspell pt-PT (Natura)

Verificar a licença exata do pacote pt-PT — o projeto usa licenças livres, mas
há mais do que uma consoante o pacote, e algumas são copyleft.

Distinção que importa: o que a app embarca **não são** os ficheiros `.aff` e
`.dic`, é a tabela `forms` derivada deles. Uma licença copyleft pode ou não
alcançar esse derivado. Se a resposta não for óbvia ao ler os termos, vale
mais um email ao projeto do que uma suposição.

### Tatoeba

CC BY 2.0 FR: a atribuição é obrigatória e é **por frase**, não só por corpus.
É por isso que cada exemplo guarda o `source_ref` com o id da frase — sem
isso, o ecrã de fontes da app mente.

Verificar: a licença atualmente indicada na página de downloads (já mudou de
versão no passado) e o texto de atribuição pedido.

### Leipzig Corpora

**A fonte de maior risco prático**, porque é fácil de obter e pode ser NC.

Verificar em separado duas coisas, porque a resposta pode não ser a mesma:

1. As **frases** — se forem NC, ficam fora de uma app publicada.
2. As **frequências** (`frequency_rank`) — uma tabela de contagens é um
   conjunto de factos sobre a língua, e o risco é substancialmente menor.
   Se as frases ficarem de fora, vale a pena tentar manter as frequências:
   são elas que ordenam as desambiguações (*"cantada"* pode ser várias
   coisas) e a app fica pior sem elas.

Verificar também quais os corpora a usar. Preferir os `por_pt_*`, que são de
Portugal — os `por_*` sem marca misturam variantes e o filtro heurístico não
compensa isso bem.

### CETEMPúblico / Linguateca

180 milhões de palavras de PT-PT jornalístico. Seria a melhor fonte de frases
autênticas do conjunto todo.

O obstáculo é o de sempre: é distribuído para uso académico, e isso quase de
certeza não cobre redistribuição numa app publicada. Verificar por escrito.

Se não permitir, há duas saídas honestas, ambas previstas no plano (secção 8):
fica fora, ou entra apenas numa build local que não é distribuída.

---

## Ação paralela: Priberam

O plano recomenda-a e não é bloqueante (secção 2): enviar email à Priberam a
descrever o projeto e a perguntar por licença para uso pessoal ou educativo.

Se responderem que sim, o Priberam entra como fonte opcional numa **build
separada, não distribuída** — nunca na app publicada. Se não responderem, não
muda nada.

Registar aqui a data do envio e a resposta:

- Enviado em: —
- Resposta: —
