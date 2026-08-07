"""A etiqueta morfológica do Natura — flexões e classe gramatical.

Contexto, porque estes testes só se percebem com ele: o `.dic` do Natura não é
uma lista de lemas, é uma lista de FORMAS. Durante meses tratou-se cada linha
como um lema, e o resultado na F1 real foi:

* 4 448 lemas fantasma — `tinham`, `ativeras`, `púnheis`, `corróis` — ao lado
  do verbo a que pertencem, todos sem definição nenhuma, todos devolvidos pela
  pesquisa como candidatos;
* 5 109 lemas com classe gramatical "desconhecido" quando a fonte a sabia.

Ambas as coisas estavam escritas no campo morfológico, que se lia e se deitava
fora. Estes testes existem para que nunca mais se deite.
"""

from __future__ import annotations

from palavrame.sources import build as build_source
from palavrame.sources.hunspell_natura import lema_de, pos_de


# --- leitura da etiqueta ---------------------------------------------------

def test_entrada_que_e_lema_nao_tem_redirecao():
    assert lema_de("[CAT=adj,N=s,G=m]") is None
    assert lema_de(None) is None
    assert lema_de("") is None


def test_entrada_que_e_flexao_diz_de_quem():
    assert lema_de("[$ter$CAT=v,T=inf,TR=_$P=3,N=p,T=pi]") == "ter"
    assert lema_de("[$pôr$CAT=v,T=inf,TR=_$P=1,N=s,T=pic]") == "pôr"
    assert lema_de("[$caber$CAT=v,T=inf,TR=_$P=3,N=p,T=pmp]") == "caber"
    # Lemas com espaços — o Natura trata multipalavras assim.
    assert lema_de("[$Burquina Faso$CAT=np,SEM=p]") == "Burquina Faso"


def test_classe_gramatical_sai_do_cat():
    assert pos_de("[CAT=adj,N=s,G=m]") == "adjetivo"
    assert pos_de("[CAT=nc,G=f,N=s]") == "substantivo"
    assert pos_de("[CAT=v,T=inf,TR=_]") == "verbo"
    assert pos_de("[CAT=np,SEM=p]") == "nome proprio"
    assert pos_de("[CAT=adv,SUBCAT=modo]") == "adverbio"
    # A flexão também traz a classe, e é a do lema a que pertence.
    assert pos_de("[$ter$CAT=v,T=inf,TR=_$P=3,N=p,T=pi]") == "verbo"


def test_etiquetas_que_nao_sao_classes_ficam_desconhecidas():
    """`cp` são contrações ("ao", "comigo") e `punct` é pontuação.

    Nenhuma das duas é uma classe gramatical, e o Wikcionário trata-as melhor.
    Deixar passar seria pior do que não dizer nada.
    """
    assert pos_de("[CAT=cp,Prep=de,Art=o]") == "desconhecido"
    assert pos_de("[CAT=punct1a]") == "desconhecido"
    assert pos_de("sem etiqueta nenhuma") == "desconhecido"


# --- efeito no parse -------------------------------------------------------

def test_flexao_nao_abre_lema_e_penduranse_no_verbo(cache):
    """`couberam` é forma de `caber`, não é uma palavra do dicionário.

    Esta é a regressão que mais lemas limpa: 4 448 na F1 real.
    """
    entries = list(build_source("hunspell_natura", cache).parse())
    lemas = {e.lemma for e in entries}

    assert "couberam" not in lemas
    assert "pusesse" not in lemas
    assert "caber" in lemas and "pôr" in lemas

    formas_de_caber = {f.form for e in entries if e.lemma == "caber"
                       for f in e.forms}
    assert "couberam" in formas_de_caber


def test_hunspell_da_a_classe_gramatical(cache):
    entries = {e.lemma: e for e in build_source("hunspell_natura", cache).parse()}
    assert entries["ensonado"].pos == "adjetivo"
    assert entries["janela"].pos == "substantivo"
    assert entries["caber"].pos == "verbo"
    assert entries["Serralves"].pos == "nome proprio"


def test_pesquisa_por_flexao_continua_a_chegar_ao_lema(cache):
    """O que não pode partir com esta mudança.

    A app procura em `forms`. Redirecionar a entrada para o lema verdadeiro
    tem de manter — ou melhorar — a ligação forma -> lema, nunca perdê-la.
    """
    entries = list(build_source("hunspell_natura", cache).parse())
    forma_para_lema: dict = {}
    for e in entries:
        for f in e.forms:
            forma_para_lema.setdefault(f.form, set()).add(e.lemma)

    assert "caber" in forma_para_lema["couberam"]
    assert "pôr" in forma_para_lema["pusesse"]


def test_restricao_por_lemas_segue_o_lema_verdadeiro(cache):
    """Pedir `caber` tem de trazer também as linhas que são flexões dele.

    Antes o filtro comparava com a palavra escrita na linha, portanto pedir
    `caber` não trazia a linha do `couberam` — e a F0 ficava com menos formas
    do que a F1 para o mesmo lema, o que torna a F0 inútil como ensaio.
    """
    entries = list(build_source("hunspell_natura", cache).parse(["caber"]))
    assert {e.lemma for e in entries} == {"caber"}
    assert "couberam" in {f.form for e in entries for f in e.forms}
