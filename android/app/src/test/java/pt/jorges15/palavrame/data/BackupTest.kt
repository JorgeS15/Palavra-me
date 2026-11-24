package pt.jorges15.palavrame.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip da cópia de segurança: exportar e voltar a ler não pode perder
 * nada. É o teste que teria apanhado o bug 1.1 — a `proxima_revisao` e o
 * progresso a ficarem por escrever.
 */
class BackupTest {

    @Test
    fun `o round-trip preserva todos os campos da palavra`() {
        val p = PalavraGuardada(
            id = 7,
            lemma = "ensonado",
            guardadaEm = 1000L,
            livro = "Os Maias",
            autor = "Eça de Queirós",
            page = 42,
            frase = "Tinha o rosto ensonado.",
            note = "coloquial",
            significado = "Que tem sono; sonolento.",
            mastery = 5,
            revistaEm = 2000L,
            proximaRevisao = 3000L,
        )
        val json = Backup.serializar(listOf(p), null, "1.0")
        val volta = Backup.desserializar(json)!!.palavras.single()

        // O id não se preserva de propósito (o Room atribui um novo); o resto sim.
        assertEquals(p.lemma, volta.lemma)
        assertEquals(p.guardadaEm, volta.guardadaEm)
        assertEquals(p.livro, volta.livro)
        assertEquals(p.autor, volta.autor)
        assertEquals(p.page, volta.page)
        assertEquals(p.frase, volta.frase)
        assertEquals(p.note, volta.note)
        assertEquals(p.significado, volta.significado)
        assertEquals(p.mastery, volta.mastery)
        assertEquals(p.revistaEm, volta.revistaEm)
        // O campo do bug 1.1: sem ele, a coleção importada voltava toda à fila.
        assertEquals(p.proximaRevisao, volta.proximaRevisao)
    }

    @Test
    fun `o round-trip preserva o progresso do jogo`() {
        val prog = Progresso(
            pontos = 523, sequencia = 11, ultimoDia = "2026-08-31",
            acertos = 46, erros = 6,
        )
        val json = Backup.serializar(emptyList(), prog, "1.0")
        val volta = Backup.desserializar(json)!!.progresso!!

        assertEquals(523, volta.pontos)
        assertEquals(11, volta.sequencia)
        assertEquals("2026-08-31", volta.ultimoDia)
        assertEquals(46, volta.acertos)
        assertEquals(6, volta.erros)
    }

    @Test
    fun `uma palavra por jogar (proxima_revisao nula) mantem-se nula`() {
        val p = PalavraGuardada(lemma = "acervo", proximaRevisao = null, mastery = 0)
        val volta = Backup.desserializar(
            Backup.serializar(listOf(p), null, "1.0")
        )!!.palavras.single()
        assertNull(volta.proximaRevisao)
        assertEquals(0, volta.mastery)
    }

    @Test
    fun `sem progresso na copia, o campo fica nulo`() {
        val json = Backup.serializar(emptyList(), null, "1.0")
        assertNull(Backup.desserializar(json)!!.progresso)
    }

    @Test
    fun `um ficheiro que nao e uma copia devolve nulo`() {
        assertNull(Backup.desserializar("isto não é json"))
        assertNull(Backup.desserializar("{}"))            // sem 'palavras'
    }

    @Test
    fun `o round-trip preserva as fichas dos livros e as leituras`() {
        val livros = listOf(
            LivroCompleto(
                FichaLivro(titulo = "Os Maias", autor = "Eça de Queirós", paginas = 714,
                    nota = "Releitura.", criadoEm = 1000L),
                listOf(
                    Leitura(livroId = 0, iniciadaEm = 1000L, terminadaEm = 5000L),
                    Leitura(livroId = 0, iniciadaEm = 9000L, parada = true),
                ),
            ),
            LivroCompleto(
                FichaLivro(titulo = "Ensaio sobre a Cegueira", criadoEm = 2000L, emCurso = true),
                emptyList(),
            ),
        )
        val volta = Backup.desserializar(
            Backup.serializar(emptyList(), null, "1.0", livros)
        )!!.livros
        assertEquals(livros, volta)
    }

    @Test
    fun `o formato das versoes de teste le-se como leituras`() {
        val json = """
            {"palavras": [], "livros": [
              {"titulo": "A", "iniciado_em": 100, "terminado_em": 200},
              {"titulo": "B", "iniciado_em": 300, "em_curso": true},
              {"titulo": "C", "iniciado_em": 400}
            ]}
        """.trimIndent()
        val livros = Backup.desserializar(json)!!.livros.associateBy { it.titulo }
        assertEquals(listOf(Leitura(livroId = 0, iniciadaEm = 100, terminadaEm = 200)), livros["A"]!!.leituras)
        assertEquals(EstadoLeitura.A_LER, livros["B"]!!.estado)
        assertEquals(EstadoLeitura.SEM_LEITURA, livros["C"]!!.estado)
        assertEquals(400L, livros["C"]!!.ficha.criadoEm)
    }

    @Test
    fun `o titulo do livro entra sem espacos a volta`() {
        val json = Backup.serializar(
            listOf(
                PalavraGuardada(lemma = "langor", livro = "  Os Maias "),
                PalavraGuardada(lemma = "pundonor", livro = "   "),
            ),
            null, "1.0",
        )
        val p = Backup.desserializar(json)!!.palavras
        assertEquals("Os Maias", p[0].livro)
        assertNull(p[1].livro)
    }

    @Test
    fun `a pagina onde se vai viaja com a leitura`() {
        val livro = LivroCompleto(
            FichaLivro(titulo = "Revolução", criadoEm = 1),
            listOf(Leitura(livroId = 1, iniciadaEm = 5, pagina = 214, paginaEm = 9)),
        )
        val json = Backup.serializar(emptyList(), null, "1.0", listOf(livro))
        val volta = Backup.desserializar(json)!!.livros.single().leituras.single()
        assertEquals(214, volta.pagina)
        assertEquals(9L, volta.paginaEm)
    }

    @Test
    fun `uma copia anterior sem livros le-se com a lista vazia`() {
        val json = Backup.serializar(emptyList(), null, "1.0")
        assertEquals(emptyList<LivroCompleto>(), Backup.desserializar(json)!!.livros)
    }

    @Test
    fun `o estado sai da leitura mais recente`() {
        val f = FichaLivro(id = 1, titulo = "A", criadoEm = 0)
        assertEquals(EstadoLeitura.SEM_LEITURA, LivroCompleto(f).estado)
        val lida = Leitura(id = 1, livroId = 1, iniciadaEm = 100, terminadaEm = 200)
        assertEquals(EstadoLeitura.LIDO, LivroCompleto(f, listOf(lida)).estado)
        val releitura = Leitura(id = 2, livroId = 1, iniciadaEm = 300)
        assertEquals(EstadoLeitura.A_LER, LivroCompleto(f, listOf(lida, releitura)).estado)
        assertEquals(
            EstadoLeitura.PARADO,
            LivroCompleto(f, listOf(lida, releitura.copy(parada = true))).estado,
        )
        assertEquals(listOf(lida), LivroCompleto(f, listOf(lida, releitura)).lidas)
    }

    @Test
    fun `a lingua da palavra e do livro viaja na copia`() {
        val p = PalavraGuardada(lemma = "wistful", livro = "Persuasion", lingua = Lingua.EN)
        val livro = LivroCompleto(FichaLivro(titulo = "Persuasion", criadoEm = 1L, lingua = Lingua.EN))
        val volta = Backup.desserializar(Backup.serializar(listOf(p), null, "1.0", listOf(livro)))!!
        assertEquals(Lingua.EN, volta.palavras.single().lingua)
        assertEquals(Lingua.EN, volta.livros.single().ficha.lingua)
    }

    @Test
    fun `uma copia sem lingua e portugues`() {
        val json = """{"formato":1,"palavras":[{"lema":"ensonado"}],"livros":[{"titulo":"Os Maias"}]}"""
        val volta = Backup.desserializar(json)!!
        assertEquals(Lingua.PT, volta.palavras.single().lingua)
        assertEquals(Lingua.PT, volta.livros.single().ficha.lingua)
    }

    @Test
    fun `o round-trip preserva as correcoes e o que ja se enviou`() {
        val c = CorrecaoGuardada(
            lemma = "crèdor", acecaoOrd = 2, acecaoTexto = "Que tem crédito.",
            acecaoFonte = "Wikcionário", classe = "substantivo",
            texto = "Grafia antiga de credor.", criadaEm = 1000L, enviadaEm = 2000L,
        )
        val p = PalavraGuardada(lemma = "rebarbativo", significado = "Antipático.", enviado = "Antipático.")
        val volta = Backup.desserializar(
            Backup.serializar(listOf(p), null, "1.30.0", correcoes = listOf(c))
        )!!
        assertEquals(c.copy(id = 0), volta.correcoes.single().copy(id = 0))
        assertEquals("Antipático.", volta.palavras.single().enviado)
    }

    @Test
    fun `uma copia sem correcoes le-se como antes`() {
        val volta = Backup.desserializar(
            Backup.serializar(listOf(PalavraGuardada(lemma = "acervo")), null, "1.0")
        )!!
        assertTrue(volta.correcoes.isEmpty())
        assertNull(volta.palavras.single().enviado)
    }
}
