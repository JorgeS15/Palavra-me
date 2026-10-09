package pt.jorges15.palavrame.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
