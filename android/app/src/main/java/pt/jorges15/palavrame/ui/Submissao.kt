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
 * escreveu, numa só linha; a nota distingue "não encontrada" de "sem
 * significado".
 */
internal fun csvDeFalta(
    faltas: List<PalavraEmFalta>,
    incluirCabecalho: Boolean = true,
): String = buildString {
    if (incluirCabecalho) append("lema,classe,definicao,nota\n")
    faltas.forEach { f ->
        val nota = if (f.tipo == TipoFalta.NAO_ENCONTRADA) "não encontrada"
                   else "sem significado"
        append(csvCampo(f.lemma)).append(',')
        append(',')                                   // classe: fica para quem cura
        append(csvCampo(numaLinha(f.significadoProprio.orEmpty()))).append(',')
        append(csvCampo(nota)).append('\n')
    }
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
        append("• ").append(f.lemma)
        append(
            if (f.tipo == TipoFalta.NAO_ENCONTRADA) " (não está no dicionário)"
            else " (sem significado)"
        )
        f.significadoProprio?.let { append(". Significado: ").append(numaLinha(it)) }
        append('\n')
    }

    append("\n--- para colar no seeds/curadoria.csv ---\n")
    append(csvDeFalta(faltas, incluirCabecalho = false))
    append("\n\nPalavra-me")
}
