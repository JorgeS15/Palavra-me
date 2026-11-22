package pt.jorges15.palavrame.data

import androidx.room.withTransaction
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cópia de segurança da coleção, em JSON.
 *
 * Existe porque a coleção é a única coisa nesta app que não se recupera: o
 * dicionário reconstrói-se do pipeline, mas as palavras que alguém registou
 * a ler — com o livro, a página e a frase — só existem no telemóvel dele.
 *
 * Guarda-se **tudo o que se perderia** ao mudar de telemóvel: cada palavra com
 * a sua caixa de revisão e a data da próxima revisão, e o progresso do jogo
 * (pontos, sequência de dias, acertos, erros). Sem a `proxima_revisao`, a
 * coleção importada voltava toda à fila no primeiro dia; sem o progresso,
 * perdia-se a sequência — que é o que puxa a pessoa a voltar.
 *
 * O formato é JSON legível de propósito. Se um dia a app desaparecer, o
 * trabalho de anos de quem a usou deve continuar a poder ser aberto num
 * editor de texto.
 *
 * A serialização (`serializar`/`desserializar`) está separada do IO para se
 * poder testar o round-trip sem Android — é lá que vive o risco de um campo
 * ficar por escrever.
 */
object Backup {

    private const val FORMATO = 1

    /** Nome sugerido no seletor: `palavra-me-2026-08-05.json`. */
    fun nomeSugerido(): String {
        val hoje = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT)
            .format(java.util.Date())
        return "palavra-me-$hoje.json"
    }

    /** O que uma cópia contém: as palavras e o progresso (se houver). */
    internal data class Conteudo(
        val palavras: List<PalavraGuardada>,
        val progresso: Progresso?,
        /** As fichas dos livros, com as leituras (1.22). Vazio numa cópia anterior. */
        val livros: List<LivroCompleto> = emptyList(),
    )

    // --- serialização pura (testável sem Android) -------------------------

    internal fun serializar(
        palavras: List<PalavraGuardada>,
        progresso: Progresso?,
        versaoDaApp: String,
        livros: List<LivroCompleto> = emptyList(),
    ): String {
        val raiz = JSONObject().apply {
            put("formato", FORMATO)
            put("app", "Palavra-me $versaoDaApp")
            put("exportado_em", java.time.Instant.now().toString())
            put("total", palavras.size)
            put("palavras", JSONArray().apply { palavras.forEach { put(paraJson(it)) } })
            progresso?.let { put("progresso", progressoParaJson(it)) }
            if (livros.isNotEmpty()) {
                put("livros", JSONArray().apply { livros.forEach { put(livroParaJson(it)) } })
            }
        }
        return raiz.toString(2)
    }

    /** Lê uma cópia. Devolve `null` se não parecer uma cópia do Palavra-me. */
    internal fun desserializar(texto: String): Conteudo? {
        val raiz = try {
            JSONObject(texto)
        } catch (e: Exception) {
            return null
        }
        val lista = raiz.optJSONArray("palavras") ?: return null
        val palavras = (0 until lista.length()).mapNotNull { deJson(lista.getJSONObject(it)) }
        val progresso = raiz.optJSONObject("progresso")?.let { progressoDeJson(it) }
        val livros = raiz.optJSONArray("livros")?.let { l ->
            (0 until l.length()).mapNotNull { livroDeJson(l.getJSONObject(it)) }
        }.orEmpty()
        return Conteudo(palavras, progresso, livros)
    }

    // --- IO ---------------------------------------------------------------

    suspend fun exportar(
        context: Context,
        destino: Uri,
        palavras: List<PalavraGuardada>,
        progresso: Progresso?,
        versaoDaApp: String,
        livros: List<LivroCompleto> = emptyList(),
    ): Resultado = withContext(Dispatchers.IO) {
        try {
            val json = serializar(palavras, progresso, versaoDaApp, livros)
            // "wt": truncar. Sem o modo, escrever por cima de uma cópia mais
            // comprida deixava o resto da antiga no fim, e o JSON ficava
            // inválido sem ninguém dar por isso até precisar dele.
            context.contentResolver.openOutputStream(destino, "wt")?.use { saida ->
                saida.write(json.toByteArray(Charsets.UTF_8))
            } ?: return@withContext Resultado.Erro("Não consegui escrever no ficheiro.")
            Resultado.Feito(palavras.size, 0)
        } catch (e: Exception) {
            Resultado.Erro(e.message ?: "Falhou a exportação.")
        }
    }

    /**
     * Importa uma cópia.
     *
     * **Nunca substitui nem apaga.** As palavras que já estão na coleção ficam
     * como estão, e só entram as que faltam — importar é juntar, não trocar.
     *
     * O progresso do jogo (pontos, sequência, respostas) é um ou outro: com o
     * deste telemóvel vazio, entra o da cópia; com os dois em uso e diferentes,
     * pergunta-se (`escolherProgresso`, verdadeiro = o da cópia). Antes ficava
     * sempre o daqui, e quem restaurava uma cópia perdia a sequência sem saber.
     *
     * Numa transação só: ou entra a cópia inteira, ou nada. Antes, uma falha a
     * meio deixava uma importação parcial, e repetir dava "N já existiam" sem
     * se saber o que faltava.
     */
    suspend fun importar(
        context: Context,
        origem: Uri,
        db: UtilizadorDb,
        escolherProgresso: suspend (local: Progresso, daCopia: Progresso) -> Boolean = { _, _ -> false },
    ): Resultado = withContext(Dispatchers.IO) {
        try {
            val texto = context.contentResolver.openInputStream(origem)
                ?.bufferedReader()?.use { it.readText() }
                ?: return@withContext Resultado.Erro("Não consegui ler o ficheiro.")

            val conteudo = desserializar(texto)
                ?: return@withContext Resultado.Erro(
                    "Isto não parece uma cópia do Palavra-me."
                )

            val palavrasDao = db.palavras()
            val progressoDao = db.progresso()
            // A pergunta, se for preciso, antes da transação: espera pela pessoa.
            val local = progressoDao.atual()
            val progressoDaCopia = conteudo.progresso?.takeIf { copia ->
                when {
                    copia.estaVazio() -> false
                    local == null || local.estaVazio() -> true
                    local.copy(id = 1) == copia.copy(id = 1) -> false
                    else -> escolherProgresso(local, copia)
                }
            }
            var novas = 0
            var jaExistiam = 0
            db.withTransaction {
            for (palavra in conteudo.palavras) {
                if (palavrasDao.porLema(palavra.lemma) != null) {
                    jaExistiam++
                } else {
                    // id = 0 para o Room atribuir um novo: os ids da cópia
                    // podem colidir com os desta instalação.
                    palavrasDao.guardar(palavra.copy(id = 0))
                    novas++
                }
            }

            progressoDaCopia?.let { progressoDao.guardar(it.copy(id = 1)) }

            // As fichas da cópia só entram para os livros que ainda não existem
            // aqui (com as suas leituras); o livro em curso fica o desta
            // instalação. Os livros das palavras sem ficha na cópia (uma cópia
            // anterior à 1.22) ganham-na a partir delas.
            val livrosDao = db.livros()
            for (livro in conteudo.livros) {
                if (livrosDao.porTitulo(livro.titulo) != null) continue
                val id = livrosDao.inserir(livro.ficha.copy(id = 0, emCurso = false))
                if (id <= 0) continue
                livro.leituras.forEach { livrosDao.inserirLeitura(it.copy(id = 0, livroId = id)) }
            }
            livrosDao.preencherEmFalta()
            livrosDao.preencherAutores()
            }

            Resultado.Feito(novas, jaExistiam)
        } catch (e: Exception) {
            Resultado.Erro(e.message ?: "Falhou a importação.")
        }
    }

    // --- JSON de cada peça ------------------------------------------------

    private fun paraJson(p: PalavraGuardada) = JSONObject().apply {
        put("lema", p.lemma)
        put("guardada_em", p.guardadaEm)
        p.livro?.let { put("livro", it) }
        p.autor?.let { put("autor", it) }
        p.page?.let { put("pagina", it) }
        p.frase?.let { put("frase", it) }
        p.note?.let { put("nota", it) }
        p.significado?.let { put("significado", it) }
        if (p.mastery != 0) put("dominio", p.mastery)
        p.revistaEm?.let { put("revista_em", it) }
        // A próxima revisão TEM de ir: sem ela, a palavra importada fica
        // vencida desde sempre e a coleção inteira volta à fila (bug 1.1).
        p.proximaRevisao?.let { put("proxima_revisao", it) }
    }

    private fun deJson(o: JSONObject): PalavraGuardada? {
        val lema = o.optString("lema").takeIf { it.isNotBlank() } ?: return null
        return PalavraGuardada(
            lemma = lema,
            guardadaEm = o.optLong("guardada_em", System.currentTimeMillis()),
            // Sem espaços à volta: os livros ligam-se às palavras pelo título exato
            // (ver `data/Livros.kt`), e uma cópia de antes da 1.17.1 pode tê-los.
            livro = o.optString("livro").trim().takeIf { it.isNotEmpty() },
            autor = o.optString("autor").takeIf { it.isNotBlank() },
            page = if (o.has("pagina")) o.optInt("pagina") else null,
            frase = o.optString("frase").takeIf { it.isNotBlank() },
            note = o.optString("nota").takeIf { it.isNotBlank() },
            significado = o.optString("significado").takeIf { it.isNotBlank() },
            // A caixa de Leitner vai de 0 a 5; um ficheiro editado à mão (ou
            // estragado) não pode pôr a palavra numa caixa que não existe.
            mastery = o.optInt("dominio", 0).coerceIn(0, 5),
            revistaEm = if (o.has("revista_em")) o.optLong("revista_em") else null,
            proximaRevisao = if (o.has("proxima_revisao")) o.optLong("proxima_revisao") else null,
        )
    }

    private fun progressoParaJson(pr: Progresso) = JSONObject().apply {
        put("pontos", pr.pontos)
        put("sequencia", pr.sequencia)
        pr.ultimoDia?.let { put("ultimo_dia", it) }
        put("acertos", pr.acertos)
        put("erros", pr.erros)
    }

    private fun progressoDeJson(o: JSONObject) = Progresso(
        id = 1,
        pontos = o.optInt("pontos", 0),
        sequencia = o.optInt("sequencia", 0),
        ultimoDia = o.optString("ultimo_dia").takeIf { it.isNotBlank() },
        acertos = o.optInt("acertos", 0),
        erros = o.optInt("erros", 0),
    )

    private fun livroParaJson(l: LivroCompleto) = JSONObject().apply {
        val f = l.ficha
        put("titulo", f.titulo)
        f.autor?.let { put("autor", it) }
        f.paginas?.let { put("paginas", it) }
        f.nota?.let { put("nota", it) }
        put("criado_em", f.criadoEm)
        if (f.emCurso) put("em_curso", true)
        put("leituras", JSONArray().apply {
            l.leituras.forEach { r ->
                put(JSONObject().apply {
                    r.iniciadaEm?.let { put("iniciada_em", it) }
                    r.terminadaEm?.let { put("terminada_em", it) }
                    if (r.parada) put("parada", true)
                    r.pagina?.let { put("pagina", it) }
                    r.paginaEm?.let { put("pagina_em", it) }
                })
            }
        })
    }

    private fun JSONObject.longOuNulo(chave: String): Long? = if (has(chave)) optLong(chave) else null

    private fun livroDeJson(o: JSONObject): LivroCompleto? {
        val titulo = o.optString("titulo").trim().takeIf { it.isNotEmpty() } ?: return null
        val emCurso = o.optBoolean("em_curso", false)
        val ficha = FichaLivro(
            titulo = titulo,
            autor = o.optString("autor").trim().takeIf { it.isNotEmpty() },
            paginas = if (o.has("paginas")) o.optInt("paginas").takeIf { it > 0 } else null,
            nota = o.optString("nota").takeIf { it.isNotBlank() },
            criadoEm = o.longOuNulo("criado_em") ?: o.longOuNulo("iniciado_em") ?: 0L,
            emCurso = emCurso,
        )
        val leituras = o.optJSONArray("leituras")?.let { a ->
            (0 until a.length()).map { i ->
                val r = a.getJSONObject(i)
                Leitura(
                    livroId = 0,
                    iniciadaEm = r.longOuNulo("iniciada_em"),
                    terminadaEm = r.longOuNulo("terminada_em"),
                    parada = r.optBoolean("parada", false),
                    pagina = if (r.has("pagina")) r.optInt("pagina").takeIf { it > 0 } else null,
                    paginaEm = r.longOuNulo("pagina_em"),
                )
            }
        } ?: run {
            // O formato das versões de teste da 1.22 (uma data de início e uma
            // de fim por livro): só vira leitura se estava a ser lido ou lido.
            val fim = o.longOuNulo("terminado_em")
            if (emCurso || fim != null) {
                listOf(Leitura(livroId = 0, iniciadaEm = o.longOuNulo("iniciado_em"), terminadaEm = fim))
            } else {
                emptyList()
            }
        }
        return LivroCompleto(ficha, leituras)
    }

    private fun Progresso.estaVazio(): Boolean =
        pontos == 0 && sequencia == 0 && acertos == 0 && erros == 0 && ultimoDia == null

    sealed interface Resultado {
        /** Na exportação, `novas` é o total escrito e `repetidas` é zero. */
        data class Feito(val novas: Int, val repetidas: Int) : Resultado
        data class Erro(val motivo: String) : Resultado
    }
}
