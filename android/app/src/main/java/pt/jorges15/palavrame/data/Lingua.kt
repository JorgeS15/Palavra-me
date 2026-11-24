package pt.jorges15.palavrame.data

/**
 * As línguas dos dicionários. Um código curto, que é o que se guarda nas
 * palavras e nos livros (`saved_words.lingua`, `livros.lingua`) e o que vem na
 * `meta.lingua` de cada base.
 *
 * O português é a língua de sempre: uma base sem `meta.lingua`, uma palavra de
 * uma cópia de segurança antiga, um livro de antes da 1.23 — tudo isso é
 * português.
 */
object Lingua {
    const val PT = "pt"
    const val EN = "en"

    val todas = listOf(PT, EN)

    /** "PT", "EN": o que vai nos botões e na etiqueta das palavras. */
    fun sigla(lingua: String): String = lingua.uppercase()

    fun nome(lingua: String): String = when (lingua) {
        EN -> "Inglês"
        else -> "Português"
    }

    /** Qualquer coisa desconhecida (ou nula) é português. */
    fun deCodigo(codigo: String?): String = codigo?.lowercase()?.takeIf { it in todas } ?: PT

    fun outra(lingua: String): String = if (lingua == EN) PT else EN
}
