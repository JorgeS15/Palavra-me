package pt.jorges15.palavrame.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NovidadesTest {

    @Test
    fun `instalacao nova nao mostra novidades`() {
        assertFalse(mostrarNovidades(versaoAtual = 29, versaoVista = 0, foiAtualizacao = false))
    }

    @Test
    fun `depois de atualizar mostra uma vez`() {
        assertTrue(mostrarNovidades(versaoAtual = 29, versaoVista = 0, foiAtualizacao = true))
        assertTrue(mostrarNovidades(versaoAtual = 29, versaoVista = 28, foiAtualizacao = true))
        assertFalse(mostrarNovidades(versaoAtual = 29, versaoVista = 29, foiAtualizacao = true))
    }

    @Test
    fun `quem salta versoes ve as notas mais recentes por ver`() {
        val notas = mapOf(29 to Novidades(listOf("a")), 31 to Novidades(listOf("b")))
        // Da 1.19.0 (29) para a 1.20.1 (32): as da 1.20.0 (31).
        assertEquals(31, notasPorVer(versaoAtual = 32, versaoVista = 29, notas = notas))
        // Já viu as da 31: nada.
        assertNull(notasPorVer(versaoAtual = 32, versaoVista = 31, notas = notas))
        // Uma versão sem notas por cima de outra sem notas: nada.
        assertNull(notasPorVer(versaoAtual = 30, versaoVista = 29, notas = notas))
    }

    @Test
    fun `as notas sao curtas e seguem as regras de texto`() {
        NOVIDADES.forEach { (versao, notas) ->
            assertTrue("$versao: entre 1 e 4 pontos", notas.itens.size in 1..4)
            notas.itens.forEach { item ->
                assertFalse("$versao: travessão em \"$item\"", '—' in item)
                assertFalse("$versao: aspas curvas em \"$item\"", item.any { it in "“”«»" })
            }
        }
    }
}
