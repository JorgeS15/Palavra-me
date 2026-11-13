package pt.jorges15.palavrame.ui

import org.junit.Assert.assertFalse
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
