package pt.jorges15.palavrame.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A costura mais frágil do projeto: esta normalização tem de dar exatamente
 * o mesmo que `palavrame.text.normalize` no pipeline. A coluna
 * `forms.normalized` da base foi escrita pela versão em Python; se as duas
 * divergirem, a pesquisa deixa de encontrar palavras — e falha em silêncio,
 * que é a pior maneira de falhar.
 *
 * Os valores esperados abaixo não foram escritos à mão: saíram de correr a
 * função Python sobre estes casos, e os últimos vieram da coluna
 * `normalized` da `dicionario-1.db` real (186 831 lemas). Numa amostra de
 * 400 formas com acentos e hífenes, zero divergências.
 */
class TextoTest {

    @Test
    fun `normaliza como o pipeline`() {
        assertEquals("couberam", Texto.normalizar("couberam"))
        assertEquals("pusesse", Texto.normalizar("pusesse"))
        assertEquals("ensonados", Texto.normalizar("ENSONADOS"))
        assertEquals("agua", Texto.normalizar("  Água  "))
        assertEquals("por", Texto.normalizar("pôr"))
        assertEquals("acao", Texto.normalizar("ação"))
        assertEquals("coracao", Texto.normalizar("coração"))
        assertEquals("sao tome", Texto.normalizar("São Tomé"))
        assertEquals("c", Texto.normalizar("Ç"))
        assertEquals("naive", Texto.normalizar("naïve"))
    }

    @Test
    fun `preserva hifen e apostrofo, que sao parte da palavra`() {
        assertEquals("fim-de-semana", Texto.normalizar("fim-de-semana"))
        assertEquals("d'agua", Texto.normalizar("d'água"))
        // O ª é uma letra e o Python preserva-o: não o convertemos a 'a'.
        assertEquals("1ª", Texto.normalizar("1ª"))
    }

    @Test
    fun `casos tirados da base real`() {
        assertEquals("-er", Texto.normalizar("-er"))
        assertEquals("-as se mos", Texto.normalizar("-ás.se.mos"))
        assertEquals("as", Texto.normalizar("ás"))
        assertEquals("-ando", Texto.normalizar("-ando"))
    }

    @Test
    fun `limpa o que vem selecionado de um livro`() {
        // O leitor seleciona com a pontuação agarrada; a app tem de procurar
        // a palavra e não o que a rodeia.
        assertEquals("ensonados", Texto.limparSelecao("«ensonados,"))
        assertEquals("couberam", Texto.limparSelecao("  couberam.  "))
        assertEquals("fim-de-semana", Texto.limparSelecao("fim-de-semana!"))
        // Uma seleção com mais do que uma palavra: fica a primeira.
        assertEquals("O", Texto.limparSelecao("O ensonado sonhou"))
    }

    @Test
    fun `selecao e normalizacao encadeiam`() {
        assertEquals("ensonados", Texto.normalizar(Texto.limparSelecao("«Ensonados,")))
    }
}
