package pt.jorges15.palavrame.jogo

import kotlin.random.Random

/**
 * Uma palavra da coleção pronta a entrar no jogo.
 *
 * A definição já vem escolhida e possivelmente juntada — ver
 * `definicaoParaJogo`. Aqui o gerador só decide quem joga contra quem.
 */
data class Candidata(
    val lemma: String,
    val pos: String,
    val definicao: String,
    val livro: String? = null,
    val frase: String? = null,
)

data class Pergunta(
    val lemma: String,
    val pos: String,
    /** Três definições, já baralhadas. */
    val opcoes: List<String>,
    val indiceCerto: Int,
    val livro: String?,
    val frase: String?,
) {
    val definicaoCerta: String get() = opcoes[indiceCerto]
}

/**
 * Constrói perguntas de escolha múltipla a partir da coleção do utilizador.
 *
 * Sem Android nenhum, de propósito: é a peça onde está o risco todo do modo
 * jogo, e é a única que se pode verificar sem instalar nada.
 *
 * A regra que atravessa tudo: **mais vale não fazer a pergunta do que fazer
 * uma pergunta má.** Uma pergunta que se acerta por eliminação — porque a
 * resposta certa é a mais comprida, ou a única do assunto — ensina a
 * eliminar, não ensina a palavra.
 */
object Perguntas {

    /** Uma definição curta demais não dá pergunta: a certa salta à vista. */
    const val MIN_CARACTERES = 25

    /** Quanto pode uma distração ser mais curta ou mais longa do que a certa. */
    private const val RACIO_COMPRIMENTO = 2.5

    /** Acima disto, duas definições dizem o mesmo e a pergunta é injusta. */
    private const val MAX_SEMELHANCA = 0.34

    /**
     * A partir de quantas palavras a coleção se basta a si própria.
     *
     * Abaixo disto as distrações repetir-se-iam de tal maneira que se
     * acertaria por eliminação, e completam-se com o dicionário.
     */
    const val COLECAO_AUTOSSUFICIENTE = 15

    /**
     * Junta as aceções de uma palavra numa definição utilizável.
     *
     * O Dicionário Aberto escreve muitas entradas como palavras soltas
     * (*"Magro."*, *"Pálido."*), e cada uma delas sozinha é curta demais
     * para jogar. Juntá-las é fiel à fonte — é assim que 1913 as apresenta —
     * e produz uma definição com corpo.
     *
     * **Junta-se sempre a partir da primeira**, nunca se salta à procura de
     * uma aceção comprida. A versão anterior escolhia a primeira aceção com
     * 25 caracteres ou mais, e isso escolhia o sentido errado sempre que os
     * sentidos correntes eram curtos: para `deferente`, cujas aceções são
     * *"que defere"*, *"gentil, cortês"* e *"Que condescende."*, saltava para
     * a quarta — *"Diz-se de cada um dos vasos excretores dos testículos"* —
     * e perguntava anatomia a quem tinha encontrado a palavra a significar
     * cortesia. As primeiras aceções são as principais; é por aí que se
     * começa.
     */
    fun definicaoParaJogo(acecoes: List<String>): String? {
        val limpas = acecoes.map { it.trim() }.filter { it.isNotEmpty() }
        if (limpas.isEmpty()) return null

        // A principal, quando já se basta a si própria.
        if (limpas.first().length >= MIN_CARACTERES) return limpas.first()

        val juntas = StringBuilder()
        for (a in limpas) {
            val pedaco = a.trimEnd('.', ';', ',', ' ')
            if (pedaco.isEmpty()) continue
            if (juntas.isEmpty()) {
                juntas.append(pedaco)
            } else {
                // "Magro; pálido; amortecido." e não "Magro. Pálido.
                // Amortecido." nem "Magro Pálido Amortecido": são sentidos
                // da mesma palavra, e o ponto e vírgula é o que os separa
                // num dicionário.
                juntas.append("; ").append(minusculaInicial(pedaco))
            }
            if (juntas.length >= MIN_CARACTERES) break
        }
        if (juntas.length < MIN_CARACTERES) return null
        return juntas.append('.').toString()
    }

    /**
     * Desmaiuscula a inicial de uma aceção que vai entrar a meio de outra.
     *
     * Só quando é maiúscula de início de frase — `Pálido` -> `pálido`. Uma
     * sigla ou um nome próprio em maiúsculas fica como está.
     */
    private fun minusculaInicial(texto: String): String {
        if (texto.length < 2) return texto
        if (!texto[0].isUpperCase() || !texto[1].isLowerCase()) return texto
        return texto[0].lowercaseChar() + texto.substring(1)
    }

    /**
     * Gera a pergunta para `alvo`, ou `null` se não houver distrações à
     * altura.
     *
     * As candidatas a distração são ordenadas por qualidade — primeiro a
     * classe gramatical igual, depois o comprimento parecido — e só entram
     * as que passam os filtros. Sem duas, não há pergunta.
     */
    fun gerar(
        alvo: Candidata,
        outras: List<Candidata>,
        aleatorio: Random,
        /**
         * Definições do dicionário, para completar quando a coleção ainda é
         * pequena.
         *
         * Com quatro palavras registadas, as distrações seriam sempre as
         * mesmas três e ao fim de dias acertava-se por eliminação — o jogo
         * mediria memória de posição, não vocabulário. Estas entram só
         * quando faltam, e vão desaparecendo à medida que a coleção cresce.
         *
         * Note-se que a palavra da distração **nunca aparece no ecrã**, só
         * a definição. Não é preciso escolher palavras que o leitor
         * reconheça: basta que a definição seja credível.
         */
        reserva: List<Candidata> = emptyList(),
    ): Pergunta? {
        if (alvo.definicao.length < MIN_CARACTERES) return null

        fun filtrar(lista: List<Candidata>) = lista
            .filter { it.lemma != alvo.lemma }
            .filter { comprimentoCompativel(alvo.definicao, it.definicao) }
            .filter { semelhanca(alvo.definicao, it.definicao) <= MAX_SEMELHANCA }

        // Baralha primeiro para não escolher sempre as mesmas duas, e só
        // depois ordena pela classe gramatical — assim a preferência
        // mantém-se sem tornar a pergunta previsível.
        fun ordenar(lista: List<Candidata>) = lista
            .shuffled(aleatorio)
            .sortedByDescending { it.pos == alvo.pos && alvo.pos.isNotBlank() }

        val daColecao = ordenar(filtrar(outras))
        // O gatilho não é "não chegam para duas" — é "são sempre as mesmas".
        // Com quatro palavras registadas há sempre duas distrações
        // disponíveis, mas são as mesmas todos os dias, e ao terceiro dia
        // acerta-se por eliminação. Abaixo deste tamanho, mistura-se sempre.
        val colecaoDaMalGasta = outras.size < COLECAO_AUTOSSUFICIENTE
        val escolhidas = if (daColecao.size >= 2 && !colecaoDaMalGasta) {
            daColecao.take(2)
        } else {
            // Completa com o dicionário, evitando repetir definições. Uma
            // da coleção e uma do dicionário é a mistura preferida: revisita
            // uma palavra registada e mantém a variedade.
            val jaUsadas = daColecao.map { it.definicao }.toSet() + alvo.definicao
            val doDicionario = ordenar(filtrar(reserva))
                .filter { it.definicao !in jaUsadas }
                .distinctBy { it.definicao }
            (daColecao.take(1) + doDicionario + daColecao.drop(1)).take(2)
        }
        if (escolhidas.size < 2) return null

        val opcoes = (escolhidas.map { it.definicao } + alvo.definicao).shuffled(aleatorio)
        return Pergunta(
            lemma = alvo.lemma,
            pos = alvo.pos,
            opcoes = opcoes,
            indiceCerto = opcoes.indexOf(alvo.definicao),
            livro = alvo.livro,
            frase = alvo.frase,
        )
    }

    private fun comprimentoCompativel(certa: String, distracao: String): Boolean {
        if (distracao.length < MIN_CARACTERES) return false
        val maior = maxOf(certa.length, distracao.length).toDouble()
        val menor = minOf(certa.length, distracao.length).toDouble()
        return maior / menor <= RACIO_COMPRIMENTO
    }

    /**
     * Semelhança entre duas definições, por palavras significativas.
     *
     * Serve para rejeitar distrações que dizem o mesmo que a resposta
     * certa: *"livro antigo e raro"* contra *"livro velho e de pouco
     * valor"* seria uma pergunta sem resposta correta defensável. As
     * palavras gramaticais são ignoradas, senão duas definições quaisquer
     * pareceriam parecidas por partilharem "de" e "que".
     */
    internal fun semelhanca(a: String, b: String): Double {
        val pa = significativas(a)
        val pb = significativas(b)
        if (pa.isEmpty() || pb.isEmpty()) return 0.0
        val comuns = pa.intersect(pb).size.toDouble()
        return comuns / minOf(pa.size, pb.size)
    }

    private val VAZIAS = setOf(
        "a", "o", "as", "os", "um", "uma", "uns", "umas", "de", "do", "da",
        "dos", "das", "em", "no", "na", "nos", "nas", "por", "para", "com",
        "sem", "que", "se", "e", "ou", "ao", "aos", "à", "às", "é", "ser",
        "seu", "sua", "ter", "mais", "muito", "como", "qual", "cujo", "seja",
    )

    private fun significativas(texto: String): Set<String> =
        texto.lowercase()
            .split(Regex("[^\\p{L}\\p{N}-]+"))
            .filter { it.length > 2 && it !in VAZIAS }
            .toSet()
}
