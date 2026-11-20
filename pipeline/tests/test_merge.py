"""Testes das regras de fusão da secção 5.2 do plano."""

from __future__ import annotations

from palavrame.merge import merge_entries
from palavrame.schema import Example, Form, Relation, Sense, SourceEntry


def entry(lemma, source, **kwargs):
    return SourceEntry(lemma=lemma, source=source, **kwargs)


def test_simbolos_de_1913_nao_abrem_lema():
    """O Dicionário Aberto lematiza símbolos — '☿', '(...)', '&', '…'. Sem
    uma única letra, normalizam para vazio e nunca seriam pesquisáveis: não
    devem entrar na base como lemas fantasma."""
    entries = [
        entry("☿", "dicionario_aberto",
              senses=[Sense("Mercúrio.", "dicionario_aberto")]),
        entry("mercúrio", "dicionario_aberto",
              senses=[Sense("Metal líquido.", "dicionario_aberto")]),
    ]
    result = merge_entries(entries, strict_backbone=False)
    lemas = {e.lemma for e in result.entries}
    assert "☿" not in lemas
    assert "mercúrio" in lemas


def test_voc_decide_a_lista_de_lemas():
    entries = [
        entry("janela", "voc_cplp", pos="substantivo"),
        entry("janella", "dicionario_aberto",
              senses=[Sense("Grafia antiga.", "dicionario_aberto")]),
    ]
    result = merge_entries(entries)
    assert result.backbone == "voc_cplp"
    assert [e.lemma for e in result.entries] == ["janela"]
    # A grafia rejeitada não desaparece em silêncio.
    assert any(c["lemma"] == "janella" for c in result.conflicts.rejected_lemmas)


def test_sem_voc_o_modo_permissivo_aceita_tudo():
    entries = [entry("janella", "dicionario_aberto",
                     senses=[Sense("Abertura.", "dicionario_aberto")])]
    result = merge_entries(entries, strict_backbone=False)
    assert result.backbone is None
    assert [e.lemma for e in result.entries] == ["janella"]


def test_wikcionario_vem_antes_do_dicionario_aberto():
    entries = [
        entry("janela", "voc_cplp"),
        entry("janela", "dicionario_aberto",
              senses=[Sense("Abertura na parede de um edifício.", "dicionario_aberto")]),
        entry("janela", "wikcionario",
              senses=[Sense("Área retangular no ecrã.", "wikcionario")]),
    ]
    result = merge_entries(entries)
    fontes = [s.source for s in result.entries[0].senses]
    assert fontes == ["wikcionario", "dicionario_aberto"]


def test_acecoes_de_fontes_diferentes_nunca_se_fundem():
    entries = [
        entry("janela", "voc_cplp"),
        entry("janela", "wikcionario", senses=[Sense("Sentido A.", "wikcionario")]),
        entry("janela", "dicionario_aberto",
              senses=[Sense("Sentido B.", "dicionario_aberto")]),
    ]
    senses = merge_entries(entries).entries[0].senses
    assert len(senses) == 2
    assert {s.source for s in senses} == {"wikcionario", "dicionario_aberto"}
    assert [s.ord for s in senses] == [1, 2]


def test_definicao_repetida_entre_fontes_regista_conflito():
    entries = [
        entry("janela", "voc_cplp"),
        entry("janela", "wikcionario", senses=[Sense("Abertura.", "wikcionario")]),
        entry("janela", "dicionario_aberto",
              senses=[Sense("abertura", "dicionario_aberto")]),
    ]
    result = merge_entries(entries)
    assert len(result.entries[0].senses) == 1
    assert result.conflicts.duplicate_senses


def test_divergencia_de_classe_gramatical_fica_registada():
    entries = [
        entry("solta", "voc_cplp", pos="adjetivo"),
        entry("solta", "wikcionario", pos="substantivo",
              senses=[Sense("Ato de soltar.", "wikcionario")]),
    ]
    result = merge_entries(entries)
    assert result.conflicts.pos_disagreements
    # A classe do lema é a que tem mais aceções (desde a 1.21): a única
    # aceção é de substantivo ("Ato de soltar"), e mostrar "adjetivo" por
    # cima dela estava errado.
    assert result.entries[0].pos == "substantivo"


# --- classe gramatical por aceção --------------------------------------------


def test_cada_acecao_guarda_a_classe_da_entrada_de_onde_vem():
    entries = [
        entry("computador", "wikcionario", pos="adjetivo",
              senses=[Sense("Que computa.", "wikcionario")]),
        entry("computador", "wikcionario", pos="substantivo",
              senses=[Sense("Máquina capaz de fazer cálculos.", "wikcionario"),
                      Sense("Aparelho eletrónico de cálculo.", "wikcionario")]),
    ]
    e = merge_entries(entries).entries[0]
    assert [s.pos for s in e.senses] == ["adjetivo", "substantivo", "substantivo"]
    # Mais aceções de substantivo: "computador" é substantivo, como hoje se usa.
    assert e.pos == "substantivo"
    assert e.senses[0].definition == "Que computa."   # a ordem das aceções não muda


def test_empate_fica_a_classe_da_primeira_acecao():
    entries = [
        entry("jantar", "wikcionario", pos="substantivo",
              senses=[Sense("Refeição da noite.", "wikcionario")]),
        entry("jantar", "wikcionario", pos="verbo",
              senses=[Sense("Comer ao jantar.", "wikcionario")]),
    ]
    assert merge_entries(entries).entries[0].pos == "substantivo"


def test_remissao_vide_para_o_proprio_lema_sai():
    entries = [
        entry("lacónico", "wikcionario", pos="adjetivo",
              senses=[Sense("Resumido, breve.", "wikcionario")]),
        entry("lacónico", "dicionario_aberto", pos="adjetivo",
              senses=[Sense("Vide lacónico", "dicionario_aberto"),
                      Sense("Estufa seca, no tempo dos Romanos.", "dicionario_aberto")]),
    ]
    defs = [s.definition for s in merge_entries(entries).entries[0].senses]
    assert "Vide lacónico" not in defs
    assert len(defs) == 2


def test_relatorio_de_conflitos_guarda_amostra_e_conta_tudo():
    """O relatório é para ler, e ninguém lê a décima milésima discordância.

    Guardava-se tudo, e o relatório da F1 chegou a 3 MB só de classes
    gramaticais em desacordo — a crescer com cada fonte nova que traga `pos`.
    A contagem tem de continuar exata; a amostra é que fica com teto.
    """
    from palavrame.merge.merger import MAX_CONFLITOS_REGISTADOS

    excesso = MAX_CONFLITOS_REGISTADOS + 25
    entries = []
    for i in range(excesso):
        entries.append(entry(f"palavra{i}", "voc_cplp", pos="adjetivo"))
        entries.append(
            entry(f"palavra{i}", "wikcionario", pos="substantivo",
                  senses=[Sense(f"Definição {i}.", "wikcionario")])
        )
    result = merge_entries(entries)

    assert len(result.conflicts.pos_disagreements) == MAX_CONFLITOS_REGISTADOS
    assert result.conflicts.counts["pos_disagreements"] == excesso
    assert result.conflicts.total() == excesso
    assert result.conflicts.as_dict()["pos_disagreements_omitidos"] == 25


def test_exemplo_liga_se_ao_lema_pela_flexao():
    """O caminho crítico: a frase diz "couberam", o exemplo vai para "caber"."""
    entries = [
        entry("caber", "voc_cplp", pos="verbo"),
        entry("caber", "hunspell_natura", forms=[Form("couberam"), Form("cabemos")]),
        entry("couberam", "tatoeba", examples=[
            Example("Não couberam todos no carro pequeno.", "tatoeba", "1"),
        ]),
    ]
    result = merge_entries(entries)
    caber = result.entries[0]
    assert caber.lemma == "caber"
    assert len(caber.examples) == 1
    assert "couberam" in caber.examples[0].sentence


def test_grafia_brasileira_une_se_na_portuguesa():
    """bebê -> bebé: uma só entrada, na grafia portuguesa (decisão do Jorge)."""
    entries = [
        entry("bebé", "voc_cplp", pos="substantivo"),
        entry("bebê", "voc_cplp", pos="substantivo"),   # o VOC lista as duas
        entry("bebé", "hunspell_natura", forms=[Form("bebé"), Form("bebés")]),
        entry("bebê", "wikcionario",
              senses=[Sense("Forma carinhosa de tratamento.", "wikcionario")]),
    ]
    result = merge_entries(entries)
    lemas = [e.lemma for e in result.entries]
    assert "bebé" in lemas
    assert "bebê" not in lemas          # a grafia BR não é entrada à parte
    bebe = next(e for e in result.entries if e.lemma == "bebé")
    # O conteúdo pendurado no 'bebê' foi para 'bebé'.
    assert any("carinhosa" in s.definition for s in bebe.senses)
    # E a grafia BR fica como forma, para a pesquisa lá chegar.
    assert any(f.form == "bebê" for f in bebe.forms)
    assert any(c["de"] == "bebê" and c["para"] == "bebé"
               for c in result.conflicts.grafia_unida)


def test_nfc_desduplica_grafias_visualmente_iguais():
    """bebé (NFD) e bebé (NFC) parecem iguais mas são cadeias diferentes.

    O acento pode vir num só código (é, NFC) ou em dois (e + ´, NFD). A fusão
    agrupa por grafia exata, e sem canonicalizar isto criava duas entradas
    'bebé' idênticas à vista — exatamente o que apareceu depois de unir bebê.
    """
    import unicodedata as u

    bebe_nfd = "bebe\u0301"   # 'bebé' com o acento em codigo separado (NFD)
    assert not u.is_normalized("NFC", bebe_nfd)
    entries = [
        entry(bebe_nfd, "voc_cplp", pos="substantivo"),
        entry("bebê", "voc_cplp", pos="substantivo"),
        entry("bebé", "hunspell_natura", forms=[Form("bebé")]),
    ]
    result = merge_entries(entries)
    bebes = [e for e in result.entries if e.normalized == "bebe"]
    assert len(bebes) == 1
    assert u.is_normalized("NFC", bebes[0].lemma)


def test_gera_feminino_e_plurais_de_adjetivo_sem_flexao():
    """comezinho -> comezinha/comezinhos/comezinhas, para a pesquisa ligar.

    O Hunspell deixou 'comezinho' sem marcas de flexão, e sem o feminino a app
    não liga 'comezinha' ao lema onde está a definição.
    """
    entries = [
        entry("comezinho", "wikcionario", pos="adjetivo",
              senses=[Sense("Simples; caseiro.", "wikcionario")]),
    ]
    formas = {f.form for f in merge_entries(entries, strict_backbone=False).entries[0].forms}
    assert {"comezinha", "comezinhos", "comezinhas"} <= formas


def test_nao_gera_feminino_que_ja_e_outra_palavra():
    """O feminino de 'meio' seria 'meia', que é outra palavra: não se inventa."""
    entries = [
        entry("meio", "wikcionario", pos="adjetivo",
              senses=[Sense("Que é metade de um todo.", "wikcionario")]),
        entry("meia", "wikcionario", pos="substantivo",
              senses=[Sense("Peça de vestuário que cobre o pé.", "wikcionario")]),
    ]
    result = merge_entries(entries, strict_backbone=False)
    meio = next(e for e in result.entries if e.lemma == "meio")
    assert all(f.form != "meia" for f in meio.forms)


def test_trema_une_se_a_grafia_sem_trema():
    """lângüido (trema, grafia velha) funde-se em lânguido.

    O Wikcionário traz as duas: 'lânguido' com definições a sério e 'lângüido'
    só com "Ver lânguido". Sem unir, ficavam duas entradas.
    """
    entries = [
        entry("lânguido", "wikcionario",
              senses=[Sense("Sem forças; débil.", "wikcionario")]),
        entry("lângüido", "wikcionario",
              senses=[Sense("Ver lânguido.", "wikcionario")]),
    ]
    result = merge_entries(entries, strict_backbone=False)
    lemas = [e.lemma for e in result.entries]
    assert lemas == ["lânguido"]           # só uma, e sem trema
    entrada = result.entries[0]
    # A definição a sério ficou; a remissão para si próprio caiu.
    defs = [s.definition for s in entrada.senses]
    assert any("débil" in d for d in defs)
    assert all("Ver lânguido" not in d for d in defs)


def test_remissao_para_outra_palavra_mantem_se():
    """"O mesmo que X" só cai quando X é o próprio lema; para outro, fica."""
    entries = [
        entry("carro", "wikcionario",
              senses=[Sense("O mesmo que automóvel.", "wikcionario")]),
    ]
    entrada = merge_entries(entries, strict_backbone=False).entries[0]
    assert entrada.senses[0].definition == "O mesmo que automóvel."


def test_avo_e_avo_nao_se_confundem():
    """A gémea aguda de 'avô' é 'avó', mas AMBAS são portuguesas: não se unem.

    É o falso positivo que o padrão sozinho não distingue e que a condição
    'a circunflexa não é atestada' impede — as duas estão no Hunspell.
    """
    entries = [
        entry("avó", "voc_cplp", pos="substantivo"),
        entry("avô", "voc_cplp", pos="substantivo"),
        entry("avó", "hunspell_natura", forms=[Form("avó"), Form("avós")]),
        entry("avô", "hunspell_natura", forms=[Form("avô"), Form("avôs")]),
    ]
    lemas = [e.lemma for e in merge_entries(entries).entries]
    assert "avó" in lemas and "avô" in lemas


def test_grafia_br_unica_convertida_para_pt():
    """Se só a grafia BR foi atestada como lema, converte-se na portuguesa."""
    entries = [
        entry("gênero", "voc_cplp", pos="substantivo"),
        entry("género", "hunspell_natura", forms=[Form("género"), Form("géneros")]),
    ]
    result = merge_entries(entries)
    assert [e.lemma for e in result.entries] == ["género"]
    g = result.entries[0]
    assert g.pos == "substantivo"
    assert any(f.form == "gênero" for f in g.forms)


def test_portugues_nao_e_tocado():
    """'português' tem ê, mas a gémea 'portugués' não existe: não se toca."""
    entries = [
        entry("português", "voc_cplp", pos="adjetivo"),
        entry("português", "hunspell_natura", forms=[Form("português")]),
    ]
    assert [e.lemma for e in merge_entries(entries).entries] == ["português"]


def test_exemplo_em_grafia_br_chega_a_entrada_pt():
    """Uma frase escrita à brasileira ainda se liga à entrada portuguesa."""
    entries = [
        entry("bebé", "voc_cplp", pos="substantivo"),
        entry("bebê", "voc_cplp", pos="substantivo"),
        entry("bebé", "hunspell_natura", forms=[Form("bebé")]),
        entry("bebê", "tatoeba",
              examples=[Example("O bebê dormia serenamente ao colo.", "tatoeba", "9")]),
    ]
    result = merge_entries(entries)
    bebe = next(e for e in result.entries if e.lemma == "bebé")
    # A frase ligou-se à entrada portuguesa; e, já lá dentro, a grafia do
    # texto foi portugalizada: "bebê" -> "bebé".
    assert any("dormia serenamente" in ex.sentence for ex in bebe.examples)
    assert all("bebê" not in ex.sentence for ex in bebe.examples)


def test_adapta_grafia_br_no_texto_da_definicao():
    """gênero no texto de uma definição vira género; português fica intacto."""
    entries = [
        entry("género", "voc_cplp", pos="substantivo"),
        entry("género", "hunspell_natura", forms=[Form("género"), Form("géneros")]),
        entry("português", "hunspell_natura", forms=[Form("português")]),
        entry("género", "wikcionario",
              senses=[Sense("Conjunto português de seres do mesmo gênero.", "wikcionario")]),
    ]
    result = merge_entries(entries)
    g = next(e for e in result.entries if e.lemma == "género")
    texto = g.senses[0].definition
    assert "género" in texto and "gênero" not in texto
    assert "português" in texto            # a gémea 'portugués' não existe


def test_preferir_pt_deixa_cair_exemplo_br():
    """Havendo frase não-brasileira, a pt-BR não entra (decisão do Jorge)."""
    entries = [
        entry("janela", "voc_cplp"),
        entry("janela", "hunspell_natura", forms=[Form("janela")]),
        entry("janela", "tatoeba", examples=[
            Example("Uma frase brasileira sobre a janela da sala.", "tatoeba", "1",
                    variant="pt-BR"),
            Example("Uma frase portuguesa sobre a janela da sala.", "tatoeba", "2",
                    variant="pt-PT"),
        ]),
    ]
    exemplos = merge_entries(entries).entries[0].examples
    assert exemplos, "devia sobrar a frase pt-PT"
    assert all(e.variant != "pt-BR" for e in exemplos)


def test_exemplo_br_fica_quando_e_o_unico():
    """Sem alternativa, o exemplo pt-BR fica — mais vale isso do que nada."""
    entries = [
        entry("janela", "voc_cplp"),
        entry("janela", "hunspell_natura", forms=[Form("janela")]),
        entry("janela", "tatoeba", examples=[
            Example("Uma frase brasileira sobre a janela da sala.", "tatoeba", "1",
                    variant="pt-BR"),
        ]),
    ]
    exemplos = merge_entries(entries).entries[0].examples
    assert len(exemplos) == 1


def test_exemplo_liga_se_pela_forma_exata_nao_pela_normalizada():
    """"O menino bebe leite" é do verbo, não do nome 'bebé'.

    'bebe' (flexão de beber) e 'bebé' (o nome) normalizam para a mesma coisa.
    A ligação por forma normalizada colava a frase do verbo à entrada do
    nome; a ligação por forma exata (com acentos) separa-as.
    """
    entries = [
        entry("bebé", "voc_cplp", pos="substantivo"),
        entry("beber", "voc_cplp", pos="verbo"),
        entry("bebé", "hunspell_natura", forms=[Form("bebé"), Form("bebés")]),
        entry("beber", "hunspell_natura",
              forms=[Form("bebe"), Form("bebes"), Form("bebeu")]),
        entry("bebe", "tatoeba", examples=[
            Example("O menino bebe leite todos os dias.", "tatoeba", "1"),
        ]),
        entry("bebé", "tatoeba", examples=[
            Example("A mãe beijou o bebé que dormia ao colo.", "tatoeba", "2"),
        ]),
    ]
    result = merge_entries(entries)
    por_lema = {e.lemma: e for e in result.entries}
    # A frase do verbo foi para 'beber'.
    assert any("bebe leite" in ex.sentence for ex in por_lema["beber"].examples)
    # E NÃO contaminou 'bebé'.
    assert all("bebe leite" not in ex.sentence for ex in por_lema["bebé"].examples)
    # A frase do nome ficou no nome.
    assert any("beijou o bebé" in ex.sentence for ex in por_lema["bebé"].examples)


def test_exemplo_de_homografo_fica_no_lema_nao_na_flexao():
    """"Esta manga está doce" é o fruto, não o verbo mangar.

    'manga' é o lema do fruto E uma conjugação de 'mangar' (ele manga). Como a
    ligação usa a tabela de flexões, a frase do fruto colava-se ao verbo. A
    regra: se a forma é ela própria um lema, o exemplo fica nesse lema.
    """
    entries = [
        entry("manga", "voc_cplp", pos="substantivo"),
        entry("mangar", "voc_cplp", pos="verbo"),
        entry("manga", "hunspell_natura", forms=[Form("manga"), Form("mangas")]),
        entry("mangar", "hunspell_natura",
              forms=[Form("manga"), Form("mangas"), Form("mangava")]),
        entry("manga", "tatoeba", examples=[
            Example("Esta manga está muito doce hoje.", "tatoeba", "1"),
        ]),
        entry("mangar", "tatoeba", examples=[
            Example("Porque estás a mangar com a minha cara?", "tatoeba", "2"),
        ]),
    ]
    result = merge_entries(entries)
    por = {e.lemma: e for e in result.entries}
    # A frase do fruto ficou no fruto.
    assert any("doce" in ex.sentence for ex in por["manga"].examples)
    # E NÃO se colou ao verbo.
    assert all("doce" not in ex.sentence for ex in por["mangar"].examples)
    # A frase do verbo (forma não ambígua) ligou-se ao verbo.
    assert any("mangar com" in ex.sentence for ex in por["mangar"].examples)


def test_exemplo_sem_lema_conhecido_fica_orfao():
    entries = [
        entry("janela", "voc_cplp"),
        entry("xpto", "tatoeba", examples=[Example("Frase com xpto lá dentro.", "tatoeba")]),
    ]
    result = merge_entries(entries)
    assert result.conflicts.orphan_examples
    assert result.entries[0].examples == []


def test_cascata_de_exemplos_prefere_tatoeba():
    entries = [
        entry("janela", "voc_cplp"),
        entry("janela", "hunspell_natura", forms=[Form("janela")]),
        entry("janela", "leipzig", examples=[Example("Frase do Leipzig aqui.", "leipzig")]),
        entry("janela", "tatoeba", examples=[Example("Frase do Tatoeba aqui.", "tatoeba")]),
    ]
    exemplos = merge_entries(entries).entries[0].examples
    assert exemplos[0].source == "tatoeba"


def test_pt_pt_antes_de_pt_br():
    entries = [
        entry("janela", "voc_cplp"),
        entry("janela", "hunspell_natura", forms=[Form("janela")]),
        entry("janela", "tatoeba", examples=[
            Example("Frase brasileira sobre janela.", "tatoeba", "1", variant="pt-BR"),
            Example("Frase portuguesa sobre janela.", "tatoeba", "2", variant="pt-PT"),
        ]),
    ]
    exemplos = merge_entries(entries).entries[0].examples
    assert exemplos[0].variant == "pt-PT"


def test_teto_de_exemplos_por_acecao():
    from palavrame.config import MAX_EXAMPLES_PER_SENSE

    muitos = [
        Example(f"Frase número {i} sobre a janela da sala.", "tatoeba", str(i), sense_ord=1)
        for i in range(10)
    ]
    entries = [
        entry("janela", "voc_cplp"),
        entry("janela", "hunspell_natura", forms=[Form("janela")]),
        entry("janela", "wikcionario", senses=[Sense("Abertura.", "wikcionario")],
              examples=muitos),
    ]
    exemplos = merge_entries(entries).entries[0].examples
    assert len(exemplos) == MAX_EXAMPLES_PER_SENSE


def test_frequencia_mais_baixa_ganha():
    entries = [
        entry("janela", "voc_cplp"),
        entry("janela", "leipzig", frequency_rank=900),
        entry("janela", "hunspell_natura", forms=[Form("janela")], frequency_rank=42),
    ]
    assert merge_entries(entries).entries[0].frequency_rank == 42


def test_relacao_para_si_proprio_e_descartada():
    entries = [
        entry("janela", "voc_cplp"),
        entry("janela", "wordnet_pt", relations=[Relation("janela", "sinonimo")]),
    ]
    assert merge_entries(entries).entries[0].relations == []


def test_frequencia_do_leipzig_chega_aos_lemas():
    """Regressão: a fusão em streaming deixava cair o `frequency_rank`.

    O Leipzig entra pelo caminho das fontes de exemplos, que passou a ser
    separado — e nesse caminho só se aplicavam exemplos. Resultado: os
    186 mil lemas da F1 ficaram todos sem frequência, e a ordenação dos
    candidatos por frequência que o plano pede (secção 9) não fazia nada.
    """
    from palavrame.merge.merger import merge_entries
    from palavrame.schema import Sense, SourceEntry

    wiki = SourceEntry(lemma="janela", source="wikcionario")
    wiki.senses.append(Sense(definition="Abertura na parede.", source="wikcionario"))
    leipzig = SourceEntry(lemma="janela", source="leipzig", frequency_rank=42)

    resultado = merge_entries([wiki, leipzig], strict_backbone=False)
    janela = next(e for e in resultado.entries if e.lemma == "janela")
    assert janela.frequency_rank == 42


# --- limpeza do wordnet (1.21) -------------------------------------------------


def test_nomes_proprios_nao_recebem_glosas_do_wordnet():
    entries = [
        entry("SNS", "voc_cplp", pos="nome proprio"),
        entry("SNS", "wordnet_pt", senses=[Sense("Carcinoma do fígado.", "wordnet_pt")]),
    ]
    assert merge_entries(entries).entries[0].senses == []


def test_flexao_vazia_passa_para_a_palavra_com_definicao():
    tunel = entry("túnel", "wikcionario", pos="substantivo",
                  senses=[Sense("Passagem subterrânea.", "wikcionario")],
                  forms=[Form("túneis")])
    vazio = entry("túneis", "voc_cplp", pos="substantivo")
    lemas = {e.lemma: e for e in merge_entries([tunel, vazio], strict_backbone=False).entries}
    assert "túneis" not in lemas
    assert "túneis" in {f.form for f in lemas["túnel"].forms}


def test_derivada_vazia_nao_e_absorvida():
    # "oscilador" não é uma forma de "oscilar": é outra palavra, mesmo que o
    # Hunspell a gere a partir do verbo.
    oscilar = entry("oscilar", "wikcionario", pos="verbo",
                    senses=[Sense("Mover-se de um lado para o outro.", "wikcionario")],
                    forms=[Form("oscilador")])
    vazio = entry("oscilador", "voc_cplp", pos="substantivo")
    lemas = {e.lemma for e in merge_entries([oscilar, vazio], strict_backbone=False).entries}
    assert "oscilador" in lemas


def test_sigla_vazia_nao_vai_parar_a_outra_palavra():
    so = entry("só", "wikcionario", pos="adjetivo",
               senses=[Sense("Sem companhia.", "wikcionario")], forms=[Form("sós")])
    sigla = entry("SOS", "voc_cplp", pos="substantivo")
    lemas = {e.lemma for e in merge_entries([so, sigla], strict_backbone=False).entries}
    assert "SOS" in lemas


def test_flexao_vazia_com_sinonimos_fica():
    tunel = entry("túnel", "wikcionario", pos="substantivo",
                  senses=[Sense("Passagem subterrânea.", "wikcionario")], forms=[Form("túneis")])
    # A palavra vem do VOC; os sinónimos, do wordnet (que não cria palavras).
    vazio = entry("túneis", "voc_cplp", pos="substantivo",
                  relations=[Relation("galerias", "sinonimo")])
    lemas = {e.lemma for e in merge_entries([tunel, vazio], strict_backbone=False).entries}
    assert "túneis" in lemas


# --- remissões tocáveis (1.21) -------------------------------------------------


def _com_destino(*entries):
    return {e.lemma: e for e in merge_entries(list(entries), strict_backbone=False).entries}


def test_remissao_guarda_o_lema_de_destino():
    acao = entry("ação", "wikcionario", pos="substantivo",
                 senses=[Sense("Efeito de agir.", "wikcionario")])
    accao = entry("acção", "dicionario_aberto", pos="substantivo",
                  senses=[Sense("Vide ação", "dicionario_aberto")])
    lemas = _com_destino(acao, accao)
    assert lemas["acção"].senses[0].remete_para == "ação"
    assert lemas["ação"].senses[0].remete_para is None


def test_o_mesmo_que_com_texto_a_mais_usa_a_primeira_parte():
    avonde = entry("avonde", "dicionario_aberto", pos="advérbio",
                   senses=[Sense("Em abundância.", "dicionario_aberto")])
    avondo = entry("avondo", "dicionario_aberto", pos="advérbio",
                   senses=[Sense("O mesmo que avonde: «sabe sciênda avondo».", "dicionario_aberto")])
    assert _com_destino(avonde, avondo)["avondo"].senses[0].remete_para == "avonde"


def test_remissao_para_palavra_sem_definicao_nao_se_marca():
    vazio = entry("xpto", "voc_cplp", pos="substantivo")
    rem = entry("xptó", "dicionario_aberto", pos="substantivo",
                senses=[Sense("Ver quiproquó", "dicionario_aberto")])
    assert _com_destino(vazio, rem)["xptó"].senses[0].remete_para is None


def test_definicao_que_comeca_por_ver_nao_e_remissao():
    olhar = entry("olhar", "wikcionario", pos="verbo",
                  senses=[Sense("Ver com atenção.", "wikcionario")])
    com = entry("com", "wikcionario", pos="preposição",
                senses=[Sense("Em companhia de.", "wikcionario")])
    # "com atenção" não é uma palavra do dicionário: nada se marca.
    assert _com_destino(olhar, com)["olhar"].senses[0].remete_para is None


def test_marca_gramatical_do_dicionario_aberto_nao_e_remissao():
    # "V. t. (e der.)" é "verbo transitivo (e derivados)", não "vide t".
    t = entry("t", "wikcionario", pos="substantivo",
              senses=[Sense("Vigésima letra do alfabeto.", "wikcionario")])
    v = entry("refusar", "dicionario_aberto", pos="verbo",
              senses=[Sense("V. t. (e der.)", "dicionario_aberto")])
    assert _com_destino(t, v)["refusar"].senses[0].remete_para is None


def test_vide_e_v_ponto_passam_a_ver():
    acao = entry("ação", "wikcionario", pos="substantivo",
                 senses=[Sense("Efeito de agir.", "wikcionario")])
    accao = entry("acção", "dicionario_aberto", pos="substantivo",
                  senses=[Sense("Vide ação", "dicionario_aberto")])
    rizo = entry("rizoforáceas", "wikcionario", pos="substantivo",
                 senses=[Sense("Família de plantas dos mangais.", "wikcionario")])
    rizo2 = entry("rizofóreas", "dicionario_aberto", pos="substantivo",
                  senses=[Sense("V. rizoforáceas.", "dicionario_aberto")])
    marca = entry("refusar", "dicionario_aberto", pos="verbo",
                  senses=[Sense("V. t. (e der.)", "dicionario_aberto")])
    lemas = _com_destino(acao, accao, rizo, rizo2, marca)
    assert lemas["acção"].senses[0].definition == "Ver ação"
    assert lemas["acção"].senses[0].remete_para == "ação"
    assert lemas["rizofóreas"].senses[0].definition == "Ver rizoforáceas."
    # A marca gramatical do Dicionário Aberto fica como estava.
    assert lemas["refusar"].senses[0].definition == "V. t. (e der.)"


def test_vide_e_ver_iguais_ficam_so_uma():
    ioga = entry("ioga", "wikcionario", pos="substantivo",
                 senses=[Sense("Disciplina indiana de meditação.", "wikcionario")])
    yoga = entry("yoga", "wikcionario", pos="substantivo",
                 senses=[Sense("Ver ioga", "wikcionario")])
    yoga2 = entry("yoga", "dicionario_aberto", pos="substantivo",
                  senses=[Sense("Vide ioga", "dicionario_aberto")])
    s = _com_destino(ioga, yoga, yoga2)["yoga"].senses
    assert [x.definition for x in s] == ["Ver ioga"]
