package pt.jorges15.palavrame.jogo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * O gerador de perguntas é a peça onde está o risco do modo jogo, e a única
 * que se verifica sem instalar nada. Os textos aqui são definições reais do
 * `dicionario.db`.
 */
class PerguntasTest {

    private val macilento = Candidata(
        "macilento", "adjetivo", "Magro. Pálido. Amortecido.",
        livro = "Os Maias", frase = "Tinha o rosto macilento.",
    )
    private val alfarrabio = Candidata(
        "alfarrábio", "substantivo", "livro antigo, de pouco uso ou raro",
    )
    private val enxovia = Candidata(
        "enxovia", "substantivo", "Cárcere térreo ou subterrâneo, escuro e húmido.",
    )
    private val janota = Candidata(
        "janota", "adjetivo", "diz-se do indivíduo que se veste com demasiada correção",
    )

    private val colecao = listOf(macilento, alfarrabio, enxovia, janota)

    @Test
    fun `gera tres opcoes com uma certa`() {
        val p = Perguntas.gerar(macilento, colecao, Random(1))
        assertNotNull(p)
        assertEquals(3, p!!.opcoes.size)
        assertEquals(macilento.definicao, p.definicaoCerta)
        assertEquals(3, p.opcoes.toSet().size)   // sem repetidas
    }

    @Test
    fun `leva o livro e a frase, que sao o que ancora a memoria`() {
        val p = Perguntas.gerar(macilento, colecao, Random(2))!!
        assertEquals("Os Maias", p.livro)
        assertEquals("Tinha o rosto macilento.", p.frase)
    }

    @Test
    fun `sem duas distracoes nao ha pergunta`() {
        // Uma pergunta com menos de três opções, ou com opções fracas, é
        // pior do que não perguntar nada.
        assertNull(Perguntas.gerar(macilento, listOf(macilento, alfarrabio), Random(3)))
    }

    @Test
    fun `rejeita distracao muito mais comprida, que denuncia a resposta`() {
        val comprida = Candidata(
            "verbete", "substantivo",
            "Palavra que encabeça um artigo de dicionário e que é seguida da " +
                "respetiva definição, da classe gramatical, da etimologia e, " +
                "quando os há, de exemplos de uso retirados de autores.",
        )
        // 'macilento' tem 26 caracteres; esta tem mais de 150.
        assertNull(Perguntas.gerar(macilento, listOf(alfarrabio, comprida), Random(4)))
    }

    @Test
    fun `rejeita distracao que diz o mesmo que a resposta certa`() {
        val quaseIgual = Candidata(
            "alfarrabista", "substantivo", "livro antigo, de pouco uso e raro",
        )
        assertNull(
            Perguntas.gerar(alfarrabio, listOf(quaseIgual, enxovia), Random(5))
        )
    }

    @Test
    fun `junta acecoes curtas de 1913 numa definicao com corpo`() {
        // A entrada real de 'macilento' são três palavras soltas; qualquer
        // uma sozinha seria curta demais para jogar.
        val junta = Perguntas.definicaoParaJogo(listOf("Magro.", "Pálido.", "Amortecido."))
        assertEquals("Magro; pálido; amortecido.", junta)
        assertTrue(junta!!.length >= Perguntas.MIN_CARACTERES)
    }

    @Test
    fun `usa a primeira acecao quando ela ja se basta`() {
        val d = Perguntas.definicaoParaJogo(
            listOf("Cárcere térreo ou subterrâneo, escuro e húmido.", "Curta.")
        )
        assertEquals("Cárcere térreo ou subterrâneo, escuro e húmido.", d)
    }

    @Test
    fun `nunca salta a acecao principal para ir buscar uma mais longa`() {
        // A regressão do 'deferente', encontrada a jogar com a coleção real.
        //
        // As aceções correntes são todas curtas e a quarta — anatómica — era
        // a única com 25 caracteres. O jogo perguntava os canais deferentes a
        // quem tinha encontrado a palavra num romance a significar cortesia.
        val d = Perguntas.definicaoParaJogo(
            listOf(
                "que defere",
                "gentil, cortês",
                "Que condescende.",
                "Diz-se de cada um dos vasos excretores dos testículos.",
            )
        )
        assertEquals("Que defere; gentil, cortês.", d)
        assertTrue("não pode ir parar à anatomia", !d!!.contains("testículos"))
    }

    @Test
    fun `sigla a meio nao perde as maiusculas`() {
        val d = Perguntas.definicaoParaJogo(listOf("Sigla.", "ADN nuclear."))
        assertEquals("Sigla; ADN nuclear.", d)
    }

    @Test
    fun `palavra sem definicao utilizavel fica de fora, em silencio`() {
        assertNull(Perguntas.definicaoParaJogo(emptyList()))
        assertNull(Perguntas.definicaoParaJogo(listOf("Cão.")))
    }

    @Test
    fun `a posicao da resposta certa varia`() {
        val posicoes = (1..40).map {
            Perguntas.gerar(macilento, colecao, Random(it))!!.indiceCerto
        }.toSet()
        // Se a certa saísse sempre no mesmo sítio, bastava aprender o sítio.
        assertTrue("a certa sai sempre em ${posicoes}", posicoes.size >= 2)
    }

    @Test
    fun `semelhanca ignora palavras gramaticais`() {
        // Sem ignorar 'de'/'que', duas definições quaisquer pareceriam
        // parecidas e o gerador rejeitaria distrações boas.
        val baixa = Perguntas.semelhanca(
            "livro antigo, de pouco uso ou raro",
            "cárcere que fica de baixo, escuro e húmido",
        )
        assertTrue("semelhança inesperada: $baixa", baixa < 0.34)
    }
}
