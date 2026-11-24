package pt.jorges15.palavrame.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O CSV da submissão é o que o Jorge cola no `seeds/curadoria.csv`. Um escape
 * mal feito não dá erro nenhum na app: parte a linha, e uma definição "come"
 * a seguinte quando o pipeline lê o ficheiro. Estes testes guardam isso.
 */
class SubmissaoTest {

    private val semSentido = PalavraEmFalta("escangalhante", null, TipoFalta.NAO_ENCONTRADA)
    private val comVirgulaEAspas =
        PalavraEmFalta("lânguido", "Sem forças, \"mole\".", TipoFalta.SEM_SIGNIFICADO)
    private val emDuasLinhas =
        PalavraEmFalta("ensonado", "Que tem sono.\nSonolento.", TipoFalta.NAO_ENCONTRADA)

    @Test
    fun `virgulas e aspas vao escapadas`() {
        assertEquals(
            "lânguido,,\"Sem forças, \"\"mole\"\".\",sem significado\n",
            csvDeFalta(listOf(comVirgulaEAspas), incluirCabecalho = false),
        )
    }

    @Test
    fun `significado em varias linhas passa a uma`() {
        assertEquals(
            "ensonado,,Que tem sono. Sonolento.,não encontrada\n",
            csvDeFalta(listOf(emDuasLinhas), incluirCabecalho = false),
        )
    }

    @Test
    fun `sem significado a definicao fica vazia`() {
        assertEquals(
            "escangalhante,,,não encontrada\n",
            csvDeFalta(listOf(semSentido), incluirCabecalho = false),
        )
    }

    @Test
    fun `cada linha volta a dar os mesmos quatro campos`() {
        val faltas = listOf(semSentido, comVirgulaEAspas, emDuasLinhas)
        val linhas = lerCsv(csvDeFalta(faltas, incluirCabecalho = false))
        assertEquals(3, linhas.size)
        linhas.forEach { assertEquals("campos: $it", 4, it.size) }
        assertEquals("Sem forças, \"mole\".", linhas[1][2])
        assertEquals(listOf("não encontrada", "sem significado", "não encontrada"), linhas.map { it[3] })
    }

    @Test
    fun `sentido em falta vai marcado como acrescento para a curadoria`() {
        val rebarbativo = PalavraEmFalta(
            "rebarbativo", "Antipático, desagradável.", TipoFalta.SENTIDO_EM_FALTA,
        )
        assertEquals(
            "rebarbativo,,\"Antipático, desagradável.\",acrescenta: sentido em falta\n",
            csvDeFalta(listOf(rebarbativo), incluirCabecalho = false),
        )
        val corpo = corpoDeFalta(listOf(rebarbativo), "1.18.0", null)
        assertTrue(corpo.contains("• rebarbativo (falta este sentido). Significado: Antipático, desagradável."))
    }

    @Test
    fun `cabecalho so quando pedido`() {
        assertTrue(csvDeFalta(listOf(semSentido)).startsWith("lema,classe,definicao,nota\n"))
        assertTrue(csvDeFalta(listOf(semSentido), incluirCabecalho = false).startsWith("escangalhante"))
    }

    @Test
    fun `assunto conta as palavras`() {
        assertEquals("Palavra-me: 1 palavra em falta", assuntoSubmissao(1))
        assertEquals("Palavra-me: 3 palavras em falta", assuntoSubmissao(3))
    }

    @Test
    fun `corpo leva versao, lista e o csv sem cabecalho`() {
        val corpo = corpoDeFalta(listOf(semSentido), "1.15.2", "2026-10-02T10:41:00")
        assertTrue(corpo.contains("Palavra-me 1.15.2 · dicionário de 2026-10-02"))
        assertTrue(corpo.contains("• escangalhante (não está no dicionário)"))
        assertTrue(corpo.contains("\nescangalhante,,,não encontrada\n"))
        assertTrue("o CSV do corpo não leva cabeçalho", !corpo.contains("lema,classe"))
    }

    /** Leitor de CSV mínimo (RFC 4180), só para os testes. */
    private fun lerCsv(texto: String): List<List<String>> {
        val linhas = mutableListOf<List<String>>()
        var campos = mutableListOf<String>()
        val atual = StringBuilder()
        var entreAspas = false
        var i = 0
        while (i < texto.length) {
            val c = texto[i]
            when {
                entreAspas && c == '"' && i + 1 < texto.length && texto[i + 1] == '"' -> {
                    atual.append('"'); i++
                }
                c == '"' -> entreAspas = !entreAspas
                !entreAspas && c == ',' -> { campos.add(atual.toString()); atual.clear() }
                !entreAspas && c == '\n' -> {
                    campos.add(atual.toString()); atual.clear()
                    linhas.add(campos); campos = mutableListOf()
                }
                else -> atual.append(c)
            }
            i++
        }
        return linhas
    }

    private val correcao = pt.jorges15.palavrame.data.CorrecaoGuardada(
        id = 5, lemma = "crèdor", acecaoOrd = 2, acecaoTexto = "Que tem crédito.",
        acecaoFonte = "Wikcionário", classe = "substantivo",
        texto = "É a grafia antiga de credor.",
    )

    @Test
    fun `so palavras em falta da o email de sempre`() {
        assertEquals(assuntoSubmissao(1), assuntoSugestoes(listOf(semSentido), emptyList()))
        assertEquals(
            corpoDeFalta(listOf(semSentido), "1.30.0", null),
            corpoSugestoes(listOf(semSentido), emptyList(), "1.30.0", null),
        )
    }

    @Test
    fun `uma correcao so tem o assunto de corrigir`() {
        assertEquals("Palavra-me: corrigir \"crèdor\"", assuntoSugestoes(emptyList(), listOf(correcao)))
        val corpo = corpoSugestoes(emptyList(), listOf(correcao), "1.30.0", null)
        assertTrue(corpo.contains("• crèdor (substantivo), significado 2: Que tem crédito."))
        assertTrue(corpo.contains("Fonte: Wikcionário"))
        assertTrue(corpo.contains("como devia ser: É a grafia antiga de credor."))
        // Sem palavras em falta, não há CSV para colar.
        assertTrue(!corpo.contains("curadoria.csv"))
    }

    @Test
    fun `uma mistura conta tudo e leva o csv das que faltam`() {
        assertEquals(
            "Palavra-me: 2 sugestões",
            assuntoSugestoes(listOf(semSentido), listOf(correcao)),
        )
        val corpo = corpoSugestoes(listOf(semSentido), listOf(correcao), "1.30.0", null)
        assertTrue(corpo.contains("• escangalhante (não está no dicionário)"))
        assertTrue(corpo.contains("Correções:"))
        assertTrue(corpo.contains("escangalhante,,,não encontrada\n"))
    }

    @Test
    fun `uma palavra inglesa vai marcada e fica fora do csv da curadoria`() {
        val ingles = PalavraEmFalta("papar", "To eat.", TipoFalta.NAO_ENCONTRADA, lingua = "en")
        val corpo = corpoDeFalta(listOf(ingles, semSentido), "1.30.0", null)
        assertTrue(corpo.contains("• papar [EN] (não está no dicionário). Significado: To eat."))
        assertTrue(corpo.contains("escangalhante,,,não encontrada"))
        assertTrue(!corpo.contains("papar,,"))
        // Só inglesas: não há CSV para colar.
        assertTrue(!corpoDeFalta(listOf(ingles), "1.30.0", null).contains("curadoria.csv"))
    }
}
