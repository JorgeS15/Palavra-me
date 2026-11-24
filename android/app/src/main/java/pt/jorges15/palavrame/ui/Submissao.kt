package pt.jorges15.palavrame.ui

/*
 * O texto da submissão de palavras em falta: assunto, corpo do email e o CSV
 * para o `seeds/curadoria.csv`.
 *
 * Vive à parte das Definições e sem nada de Android, de propósito: o CSV é o
 * que se cola na curadoria, e um escape mal feito (uma vírgula ou umas aspas
 * num significado) partia a linha e fazia uma definição "comer" a seguinte
 * sem ninguém dar por isso. Aqui testa-se na JVM (ver `SubmissaoTest`).
 */

/**
 * O assunto leva a contagem, para dar para triar a caixa de entrada sem abrir:
 * "Palavra-me: 3 palavras em falta". "Em falta" e não "não encontradas" porque
 * o envio inclui também as que estão no dicionário sem significado.
 */
internal fun assuntoSubmissao(n: Int): String =
    if (n == 1) "Palavra-me: 1 palavra em falta"
    else "Palavra-me: $n palavras em falta"

/** A linha de versão dos emails: "Palavra-me 1.15.2 · dicionário de 2026-10-02". */
internal fun linhaVersao(versaoApp: String, construidoEm: String?): String = buildString {
    append("Palavra-me ").append(versaoApp)
    construidoEm?.let { append(" · dicionário de ").append(it.take(10)) }
}

/**
 * O CSV pronto a colar no `seeds/curadoria.csv` do pipeline.
 *
 * Mesmas colunas: `lema,classe,definicao,nota`. A classe fica vazia (quem cura
 * decide-a); a definição leva o significado que o utilizador escreveu, se
 * escreveu, numa só linha; a nota distingue "não encontrada", "sem
 * significado" e o sentido em falta. Esta última começa por "acrescenta", a
 * marca que a curadoria usa para uma aceção que vai ao lado de outra de
 * propósito: a linha cola-se tal como vem.
 */
internal fun csvDeFalta(
    faltas: List<PalavraEmFalta>,
    incluirCabecalho: Boolean = true,
): String = buildString {
    if (incluirCabecalho) append("lema,classe,definicao,nota\n")
    faltas.forEach { f ->
        val nota = when (f.tipo) {
            TipoFalta.NAO_ENCONTRADA -> "não encontrada"
            TipoFalta.SEM_SIGNIFICADO -> "sem significado"
            TipoFalta.SENTIDO_EM_FALTA -> "acrescenta: sentido em falta"
        }
        append(csvCampo(f.lemma)).append(',')
        append(',')                                   // classe: fica para quem cura
        append(csvCampo(numaLinha(f.significadoProprio.orEmpty()))).append(',')
        append(csvCampo(nota)).append('\n')
    }
}

/** Como cada caso se diz a uma pessoa: no email e na lista de seleção. */
internal fun rotuloFalta(tipo: TipoFalta): String = when (tipo) {
    TipoFalta.NAO_ENCONTRADA -> "não está no dicionário"
    TipoFalta.SEM_SIGNIFICADO -> "sem significado"
    TipoFalta.SENTIDO_EM_FALTA -> "falta este sentido"
}

/** Escapa um campo CSV à regra normal: aspas à volta e aspas duplicadas. */
internal fun csvCampo(s: String): String =
    if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' })
        "\"" + s.replace("\"", "\"\"") + "\""
    else s

/**
 * O significado escrito em várias linhas passa a uma só: as definições da
 * curadoria são de uma linha, e o campo da app aceita várias.
 */
private fun numaLinha(texto: String): String =
    texto.replace(Regex("\\s*[\\r\\n]+\\s*"), " ").trim()

/**
 * O corpo do email: legível para uma pessoa, com o CSV no fim.
 *
 * O cabeçalho leva a versão da app e a data do dicionário: ao receber uma
 * submissão, sabe-se logo de que base ela veio. No fim vai o CSV sem
 * cabeçalho, pronto a colar por baixo das linhas do `seeds/curadoria.csv`.
 */
internal fun corpoDeFalta(
    faltas: List<PalavraEmFalta>,
    versaoApp: String,
    construidoEm: String?,
): String = buildString {
    append("Palavras da minha coleção que faltam ao dicionário do Palavra-me.\n\n")

    append(linhaVersao(versaoApp, construidoEm))
    append("\n\n")

    faltas.forEach { f ->
        append("• ").append(f.lemma).append(etiquetaLingua(f.lingua))
        append(" (").append(rotuloFalta(f.tipo)).append(")")
        f.significadoProprio?.let { append(". Significado: ").append(numaLinha(it)) }
        append('\n')
    }

    if (faltas.any { it.lingua == pt.jorges15.palavrame.data.Lingua.PT }) append("\n--- para colar no seeds/curadoria.csv ---\n")
    append(csvDeFalta(faltas.filter { it.lingua == pt.jorges15.palavrame.data.Lingua.PT }, incluirCabecalho = false))
    append("\n\nPalavra-me")
}

/**
 * O assunto de um envio de sugestões (1.30): só palavras em falta, como antes;
 * uma correção só, como o "corrigir" de antes; uma mistura, pela contagem.
 */
internal fun assuntoSugestoes(
    faltas: List<PalavraEmFalta>,
    correcoes: List<pt.jorges15.palavrame.data.CorrecaoGuardada>,
): String = when {
    correcoes.isEmpty() -> assuntoSubmissao(faltas.size)
    faltas.isEmpty() && correcoes.size == 1 -> assuntoCorrecao(correcoes.single().lemma)
    else -> {
        val n = faltas.size + correcoes.size
        "Palavra-me: $n sugestões"
    }
}

/**
 * O corpo de um envio de sugestões: as palavras em falta como sempre (com o
 * CSV no fim) e, a seguir, as correções, cada uma com a aceção como estava,
 * a fonte e o que a pessoa escreveu. Sem correções, é igual ao de antes.
 */
internal fun corpoSugestoes(
    faltas: List<PalavraEmFalta>,
    correcoes: List<pt.jorges15.palavrame.data.CorrecaoGuardada>,
    versaoApp: String,
    construidoEm: String?,
): String {
    if (correcoes.isEmpty()) return corpoDeFalta(faltas, versaoApp, construidoEm)
    return buildString {
        append("Sugestões para o dicionário do Palavra-me.\n\n")
        append(linhaVersao(versaoApp, construidoEm))
        append("\n")

        if (faltas.isNotEmpty()) {
            append("\nEm falta:\n")
            faltas.forEach { f ->
                append("• ").append(f.lemma).append(etiquetaLingua(f.lingua))
                append(" (").append(rotuloFalta(f.tipo)).append(")")
                f.significadoProprio?.let { append(". Significado: ").append(numaLinha(it)) }
                append('\n')
            }
        }

        append("\nCorreções:\n")
        correcoes.forEach { c ->
            append("• ").append(c.lemma)
            c.classe?.takeIf { it.isNotBlank() }?.let { append(" (").append(it).append(")") }
            if (c.lingua != pt.jorges15.palavrame.data.Lingua.PT) {
                append(" [").append(c.lingua.uppercase()).append("]")
            }
            append(", significado ").append(c.acecaoOrd).append(": ").append(c.acecaoTexto).append('\n')
            c.acecaoFonte?.let { append("  Fonte: ").append(it).append('\n') }
            append("  O que está mal, ou como devia ser: ").append(numaLinha(c.texto)).append('\n')
        }

        if (faltas.any { it.lingua == pt.jorges15.palavrame.data.Lingua.PT }) {
            append("\n--- para colar no seeds/curadoria.csv ---\n")
            append(csvDeFalta(faltas.filter { it.lingua == pt.jorges15.palavrame.data.Lingua.PT }, incluirCabecalho = false))
        }
        append("\n\nPalavra-me")
    }
}

/** " [EN]" a seguir a uma palavra inglesa no email; nada no português. */
private fun etiquetaLingua(lingua: String): String =
    if (lingua == pt.jorges15.palavrame.data.Lingua.PT) "" else " [" + lingua.uppercase() + "]"
