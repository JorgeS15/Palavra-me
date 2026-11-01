package pt.jorges15.palavrame.data

/**
 * Rótulo em português, acentuado, para uma classe gramatical que a base guarda
 * por slug sem acento ("nome proprio", "adverbio", "preposicao"...). É uma app
 * de português: o que aparece no ecrã leva os acentos, ainda que o slug interno
 * não os tenha. As que já estão certas (substantivo, adjetivo, verbo...) passam
 * sem mudança.
 */
fun rotuloPos(pos: String): String = when (pos) {
    "nome proprio" -> "nome próprio"
    "adverbio" -> "advérbio"
    "preposicao" -> "preposição"
    "conjuncao" -> "conjunção"
    "interjeicao" -> "interjeição"
    "locucao" -> "locução"
    else -> pos
}

/** Um candidato devolvido pela pesquisa. "cantada" pode ser mais do que um. */
data class Candidato(
    val lemmaId: Long,
    val lemma: String,
    val pos: String,
    val frequencyRank: Int?,
    /** Como a palavra foi escrita, quando difere do lema: "couberam" -> "caber". */
    val viaForma: String?,
)

data class Acecao(
    val id: Long,
    val ord: Int,
    val definicao: String,
    val dominios: List<String>,
    val fonte: String,
    val modernizada: Boolean,
)

data class Exemplo(
    val frase: String,
    val fonte: String,
    val referencia: String?,
    val variante: String?,
    val gerado: Boolean,
    val acecaoId: Long?,
)

/**
 * Uma palavra relacionada. `temDefinicao` diz se abri-la leva a algum lado:
 * as que não têm aceção nenhuma mostram-se mais apagadas.
 */
data class Relacionada(val lemma: String, val relacao: String, val temDefinicao: Boolean = true)

/** Uma sugestão enquanto se escreve: o lema e a classe, para a lista. */
data class Sugestao(val lemma: String, val pos: String)

/** Um "Querias dizer": o lema e a primeira definição, se houver. */
data class Parecida(val lemma: String, val definicao: String?)

/** Uma entrada completa, pronta a mostrar. */
data class Entrada(
    val lemmaId: Long,
    val lemma: String,
    val pos: String,
    val silabas: String?,
    val acecoes: List<Acecao>,
    val exemplos: List<Exemplo>,
    val relacionadas: List<Relacionada>,
) {
    /** Exemplos que não estão presos a nenhuma aceção (ver docs: cascata). */
    val exemplosSoltos: List<Exemplo> get() = exemplos.filter { it.acecaoId == null }

    fun exemplosDe(acecao: Acecao): List<Exemplo> =
        exemplos.filter { it.acecaoId == acecao.id }
}

data class Fonte(
    val nome: String,
    val url: String?,
    val licenca: String,
    val licencaUrl: String?,
    val atribuicao: String,
)
