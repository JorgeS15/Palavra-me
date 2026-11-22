package pt.jorges15.palavrame.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.jorges15.palavrame.data.FichaLivro
import pt.jorges15.palavrame.data.Leitura
import pt.jorges15.palavrame.data.LivroCompleto
import pt.jorges15.palavrame.data.PalavraGuardada
import java.time.LocalDate
import java.time.ZoneId

class LivrosTest {

    private val zona = ZoneId.of("Europe/Lisbon")
    private val hoje = LocalDate.of(2026, 11, 18)
    private fun dia(a: Int, m: Int, d: Int) = millisDoDia(LocalDate.of(a, m, d), zona)

    private val ficha = FichaLivro(id = 1, titulo = "Os Maias", criadoEm = 0)
    private fun livro(vararg l: Leitura) = LivroCompleto(ficha, l.toList())
    private fun leitura(id: Long, inicio: Long?, fim: Long? = null, parada: Boolean = false) =
        Leitura(id = id, livroId = 1, iniciadaEm = inicio, terminadaEm = fim, parada = parada)

    private fun periodo(l: LivroCompleto?) = periodoDeLeitura(l, hoje, zona)

    @Test
    fun `um livro sem leituras nao diz nada`() {
        assertNull(periodo(null))
        assertNull(periodo(livro()))
    }

    @Test
    fun `a ler mostra o dia de inicio, com o ano so se nao for este`() {
        assertEquals("A ler desde 3 de março", periodo(livro(leitura(1, dia(2026, 3, 3)))))
        assertEquals("A ler desde 20 de dezembro de 2025", periodo(livro(leitura(1, dia(2025, 12, 20)))))
        assertEquals("A ler", periodo(livro(leitura(1, null))))
    }

    @Test
    fun `parado diz so parado`() {
        assertEquals("Em pausa", periodo(livro(leitura(1, dia(2026, 3, 3), parada = true))))
    }

    @Test
    fun `lido diz os meses`() {
        assertEquals("Lido em maio de 2026", periodo(livro(leitura(1, dia(2026, 5, 2), dia(2026, 5, 30)))))
        assertEquals("Lido de março a maio de 2026", periodo(livro(leitura(1, dia(2026, 3, 3), dia(2026, 5, 30)))))
        assertEquals(
            "Lido de dezembro de 2025 a fevereiro de 2026",
            periodo(livro(leitura(1, dia(2025, 12, 20), dia(2026, 2, 1)))),
        )
        assertEquals("Lido em maio de 2026", periodo(livro(leitura(1, null, dia(2026, 5, 30)))))
    }

    @Test
    fun `lido mais do que uma vez conta as vezes`() {
        val l = livro(
            leitura(1, dia(2019, 1, 2), dia(2019, 3, 1)),
            leitura(2, dia(2026, 4, 1), dia(2026, 5, 30)),
        )
        assertEquals("Lido 2 vezes, a última em maio de 2026", periodo(l))
    }

    @Test
    fun `releitura em curso conta como a ler`() {
        val l = livro(leitura(1, dia(2019, 1, 2), dia(2019, 3, 1)), leitura(2, dia(2026, 11, 1)))
        assertEquals("A ler desde 1 de novembro", periodo(l))
    }

    @Test
    fun `cada leitura em texto`() {
        assertEquals("De 3 de março a 20 de maio de 2026", textoDaLeitura(leitura(1, dia(2026, 3, 3), dia(2026, 5, 20)), zona))
        assertEquals(
            "De 20 de dezembro de 2025 a 2 de fevereiro de 2026",
            textoDaLeitura(leitura(1, dia(2025, 12, 20), dia(2026, 2, 2)), zona),
        )
        assertEquals("Desde 3 de março de 2026", textoDaLeitura(leitura(1, dia(2026, 3, 3)), zona))
        assertEquals("Desde 3 de março de 2026, em pausa", textoDaLeitura(leitura(1, dia(2026, 3, 3), parada = true), zona))
        assertEquals("Acabada a 20 de maio de 2026", textoDaLeitura(leitura(1, null, dia(2026, 5, 20)), zona))
    }

    @Test
    fun `os dias contam o primeiro e o ultimo`() {
        assertEquals(1, diasDeLeitura(dia(2026, 3, 3), dia(2026, 3, 3), zona))
        assertEquals(47, diasDeLeitura(dia(2026, 3, 1), dia(2026, 4, 16), zona))
        assertNull(diasDeLeitura(null, dia(2026, 3, 3), zona))
        assertNull(diasDeLeitura(dia(2026, 3, 3), dia(2026, 3, 1), zona))
    }

    @Test
    fun `o resumo conta as sabidas e mostra as que custam, as mais fracas primeiro`() {
        val p = listOf(
            PalavraGuardada(lemma = "lânguido", mastery = 4, guardadaEm = 1),
            PalavraGuardada(lemma = "rebarbativo", mastery = 0, guardadaEm = 2),
            PalavraGuardada(lemma = "ensonado", mastery = 2, guardadaEm = 3),
            PalavraGuardada(lemma = "periclitante", mastery = 0, guardadaEm = 4),
            PalavraGuardada(lemma = "lacónico", mastery = 3, guardadaEm = 5),
        )
        val r = resumoDoLivro(p, null)
        assertEquals(5, r.palavras)
        assertEquals(2, r.sabidas)
        assertEquals(listOf("rebarbativo", "periclitante", "ensonado"), r.aCustar)
        assertNull(r.dias)
        assertEquals("Guardaste 5 palavras. Já sabes 2.", frasesDoResumo(r))
    }

    @Test
    fun `o resumo de um livro lido diz em quantos dias`() {
        val r = resumoDoLivro(emptyList(), livro(leitura(1, dia(2026, 3, 1), dia(2026, 4, 16))))
        assertEquals("Lido em 47 dias. Ainda não guardaste palavras dele.", frasesDoResumo(r))
    }

    @Test
    fun `o progresso vem da pagina da palavra mais recente`() {
        val p = listOf(
            PalavraGuardada(lemma = "a", page = 300, guardadaEm = 1),
            PalavraGuardada(lemma = "b", page = null, guardadaEm = 3),
            PalavraGuardada(lemma = "c", page = 214, guardadaEm = 2),
        )
        assertEquals(214 to 480, progressoDoLivro(p, 480))
        assertNull(progressoDoLivro(p, null))
        assertNull(progressoDoLivro(listOf(PalavraGuardada(lemma = "x")), 480))
        // Uma página a mais (gralha) não passa do fim.
        assertEquals(100 to 100, progressoDoLivro(p, 100))
    }

    @Test
    fun `a ler agora poe primeiro o livro em curso, depois o mais recente`() {
        fun l(id: Long, titulo: String, inicio: Long, emCurso: Boolean = false, fim: Long? = null) =
            LivroCompleto(
                FichaLivro(id = id, titulo = titulo, criadoEm = 0, emCurso = emCurso),
                listOf(Leitura(id = id, livroId = id, iniciadaEm = inicio, terminadaEm = fim)),
            )
        val livros = listOf(
            l(1, "A", 100), l(2, "B", 300), l(3, "C", 200, emCurso = true), l(4, "D", 400, fim = 500),
        ).associateBy { it.titulo }
        assertEquals(listOf("C", "B", "A"), livrosALer(livros).map { it.titulo })
    }

    @Test
    fun `a pagina onde vou e a mais recente entre a escrita a mao e a da ultima palavra`() {
        val p = listOf(
            PalavraGuardada(lemma = "a", page = 180, guardadaEm = 10),
            PalavraGuardada(lemma = "b", page = 200, guardadaEm = 20),
        )
        assertEquals(200, paginaOndeVou(p))
        // Pousou o livro na 214, depois da última palavra.
        val depois = leitura(1, 1).copy(pagina = 214, paginaEm = 30)
        assertEquals(214, paginaOndeVou(p, depois))
        assertEquals(214 to 560, progressoDoLivro(p, 560, depois))
        // Uma palavra guardada depois volta a mandar.
        val antes = leitura(1, 1).copy(pagina = 214, paginaEm = 15)
        assertEquals(200, paginaOndeVou(p, antes))
        // Ao voltar a ler, as palavras da primeira leitura não contam.
        assertEquals(null, paginaOndeVou(p, leitura(2, 25)))
        // Sem palavras com página, só a escrita.
        assertEquals(50, paginaOndeVou(emptyList(), leitura(1, 1).copy(pagina = 50, paginaEm = 1)))
    }

    // --- a imagem do livro lido ---

    @Test
    fun `a frase da imagem diz as palavras e os dias`() {
        assertEquals(
            "Guardei 32 palavras neste livro, em 47 dias de leitura." to "32 palavras",
            fraseDoLivroLido(32, 47),
        )
        assertEquals("Guardei 1 palavra neste livro, num dia de leitura." to "1 palavra", fraseDoLivroLido(1, 1))
        assertEquals("Guardei 5 palavras neste livro." to "5 palavras", fraseDoLivroLido(5, null))
        assertEquals("Li este livro em 12 dias." to null, fraseDoLivroLido(0, 12))
        assertEquals("Li este livro." to null, fraseDoLivroLido(0, null))
    }

    @Test
    fun `a imagem leva as primeiras palavras guardadas, ate quatro`() {
        val p = (1..6).map { PalavraGuardada(lemma = "p$it", guardadaEm = (10 - it).toLong()) }
        assertEquals(listOf("p6", "p5", "p4", "p3"), palavrasParaImagem(p).map { it.lemma })
        assertEquals(listOf("p6", "p5", "p4"), palavrasParaImagem(p, 3).map { it.lemma })
    }

    @Test
    fun `os dados da imagem vem da ultima leitura acabada`() {
        val l = LivroCompleto(
            ficha.copy(autor = " Eça de Queirós "),
            listOf(
                leitura(1, dia(2019, 1, 2), dia(2019, 3, 1)),
                leitura(2, dia(2026, 3, 1), dia(2026, 4, 16)),
            ),
        )
        val p = listOf(
            PalavraGuardada(lemma = "langor", guardadaEm = 2),
            PalavraGuardada(lemma = "estroina", guardadaEm = 1),
        )
        val d = dadosLivroLido(l, p, mapOf("estroina" to "Boémio."), zona)
        assertEquals("Os Maias", d.titulo)
        assertEquals("Eça de Queirós", d.autor)
        assertEquals("Lido de março a abril de 2026", d.periodo)
        assertEquals("Guardei 2 palavras neste livro, em 47 dias de leitura.", d.frase)
        assertEquals(listOf("estroina" to "Boémio.", "langor" to null), d.palavras)
    }

    @Test
    fun `uma leitura com o inicio depois do fim nao diz de`() {
        assertEquals("Lido em maio de 2026", periodoDaLeituraAcabada(leitura(1, dia(2026, 6, 2), dia(2026, 5, 30)), zona))
        assertNull(periodoDaLeituraAcabada(leitura(1, dia(2026, 6, 2)), zona))
        assertNull(periodoDaLeituraAcabada(null, zona))
    }
}
