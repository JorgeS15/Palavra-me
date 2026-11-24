package pt.jorges15.palavrame.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O ficheiro para o Anki: um tab ou uma aspa mal tratados partem as colunas,
 * e o Anki importa lixo sem avisar. Estes testes guardam isso.
 */
class AnkiTest {

    private val ensonado = PalavraGuardada(
        lemma = "ensonado",
        livro = "Memorial do Convento",
        autor = "José Saramago",
        page = 214,
        frase = "os olhos ensonados, a ver a chuva",
    )

    @Test
    fun `cabecalho diz ao Anki como ler o ficheiro`() {
        val f = Anki.ficheiro(emptyList())
        assertTrue(f.startsWith("#separator:tab\n#html:true\n#deck:Palavra-me\n"))
        assertTrue(f.contains("#guid column:1\n"))
        assertTrue(f.contains("#tags column:4\n"))
    }

    @Test
    fun `uma palavra com tudo da quatro colunas`() {
        val linha = Anki.linha(Anki.Nota(ensonado, "adjetivo", listOf("Que tem sono.", "Sonolento.")))!!
        val colunas = linha.split("\t")
        assertEquals(4, colunas.size)
        assertEquals("palavra-me:ensonado", colunas[0])
        assertEquals("ensonado", colunas[1])
        val verso = colunas[2]
        assertTrue(verso.startsWith("<i>adjetivo</i>"))
        assertTrue(verso.contains("1. Que tem sono.<br>2. Sonolento."))
        assertTrue(verso.contains("<i>“os olhos ensonados, a ver a chuva”</i>"))
        assertTrue(verso.contains("— Memorial do Convento, José Saramago, p. 214"))
        assertEquals("palavra-me livro::Memorial_do_Convento", colunas[3])
    }

    @Test
    fun `o teu significado vai primeiro, e chega sem dicionario`() {
        val p = PalavraGuardada(lemma = "papar", significado = "Comer.")
        val verso = Anki.linha(Anki.Nota(p, null, emptyList()))!!.split("\t")[2]
        assertEquals("<b>O teu significado:</b> Comer.", verso)
    }

    @Test
    fun `sem significado nenhum fica de fora`() {
        val p = PalavraGuardada(lemma = "escangalhante", frase = "Uma frase.")
        assertNull(Anki.linha(Anki.Nota(p, null, emptyList())))
        assertEquals(0, Anki.exportaveis(listOf(Anki.Nota(p, null, emptyList()))))
    }

    @Test
    fun `tabs, aspas e marcacao nao partem a linha`() {
        val p = PalavraGuardada(lemma = "x", frase = "Disse \"<não>\"\tali.\nE foi.")
        val linha = Anki.linha(Anki.Nota(p, null, listOf("Uma <b> & outra")))!!
        // O verso tem aspas: vai entre aspas, com as de dentro duplicadas.
        val verso = linha.split("\t")[2]
        assertTrue(verso.startsWith("\""))
        assertTrue(verso.contains("Uma &lt;b&gt; &amp; outra"))
        assertTrue(verso.contains("Disse \"\"&lt;não&gt;\"\" ali.<br>E foi."))
        assertEquals(4, linha.split("\t").size)
    }

    @Test
    fun `palavra inglesa tem guid proprio e etiqueta`() {
        val p = PalavraGuardada(lemma = "real", lingua = Lingua.EN)
        val colunas = Anki.linha(Anki.Nota(p, null, listOf("Actual.")))!!.split("\t")
        assertEquals("palavra-me:en:real", colunas[0])
        assertEquals("palavra-me ingles", colunas[3])
    }
}
