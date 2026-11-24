package pt.jorges15.palavrame.jogo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import pt.jorges15.palavrame.data.Lingua
import pt.jorges15.palavrame.data.PalavraGuardada
import pt.jorges15.palavrame.data.Texto

/**
 * A chave das palavras no jogo e nas notificações (1.23): o português fica com
 * o lema sozinho, como antes, e as outras línguas levam-na à frente.
 */
class ChaveTest {

    @Test
    fun `o portugues fica so com o lema, como antes`() {
        // Uma notificação de antes da 1.23 trazia só o lema: tem de continuar a abrir a palavra.
        assertEquals("ensonado", PalavraGuardada(lemma = "ensonado").chave)
    }

    @Test
    fun `a mesma grafia em linguas diferentes sao palavras diferentes`() {
        val pt = PalavraGuardada(lemma = "real").chave
        val en = PalavraGuardada(lemma = "real", lingua = Lingua.EN).chave
        assertNotEquals(pt, en)
        assertEquals("en:real", en)
    }

    @Test
    fun `a chave desfaz-se em lema e lingua`() {
        assertEquals("wistful", lemaDaChave("en:wistful"))
        assertEquals(Lingua.EN, linguaDaChave("en:wistful"))
        assertEquals("ensonado", lemaDaChave("ensonado"))
        assertEquals(Lingua.PT, linguaDaChave("ensonado"))
        // Um lema com dois pontos que não é língua nenhuma fica inteiro.
        assertEquals("ab:cd", lemaDaChave("ab:cd"))
        assertEquals(Lingua.PT, linguaDaChave("ab:cd"))
    }

    @Test
    fun `o possessivo ingles sai na pesquisa`() {
        assertEquals("darcy", Texto.semPossessivo(Texto.normalizar("Darcy’s")))
        assertEquals("dogs", Texto.semPossessivo(Texto.normalizar("dogs'")))
        assertEquals("is", Texto.semPossessivo("is"))
    }
}
