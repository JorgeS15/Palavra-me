package pt.jorges15.palavrame.data

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
    )

    // --- serialização pura (testável sem Android) -------------------------

    internal fun serializar(
        palavras: List<PalavraGuardada>,
        progresso: Progresso?,
        versaoDaApp: String,
    ): String {
        val raiz = JSONObject().apply {
            put("formato", FORMATO)
            put("app", "Palavra-me $versaoDaApp")
            put("exportado_em", java.time.Instant.now().toString())
            put("total", palavras.size)
            put("palavras", JSONArray().apply { palavras.forEach { put(paraJson(it)) } })
            progresso?.let { put("progresso", progressoParaJson(it)) }
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
        return Conteudo(palavras, progresso)
    }

    // --- IO ---------------------------------------------------------------

    suspend fun exportar(
        context: Context,
        destino: Uri,
        palavras: List<PalavraGuardada>,
        progresso: Progresso?,
        versaoDaApp: String,
    ): Resultado = withContext(Dispatchers.IO) {
        try {
            val json = serializar(palavras, progresso, versaoDaApp)
            context.contentResolver.openOutputStream(destino)?.use { saida ->
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
     * O progresso do jogo só é adotado da cópia se o local ainda estiver vazio:
     * restaura numa mudança de telemóvel, mas nunca esmaga um progresso já em
     * uso (por isso importar uma cópia velha por cima de uma coleção viva não
     * faz recuar a sequência).
     */
    suspend fun importar(
        context: Context,
        origem: Uri,
        palavrasDao: PalavrasDao,
        progressoDao: ProgressoDao,
    ): Resultado = withContext(Dispatchers.IO) {
        try {
            val texto = context.contentResolver.openInputStream(origem)
                ?.bufferedReader()?.use { it.readText() }
                ?: return@withContext Resultado.Erro("Não consegui ler o ficheiro.")

            val conteudo = desserializar(texto)
                ?: return@withContext Resultado.Erro(
                    "Isto não parece uma cópia do Palavra-me."
                )

            var novas = 0
            var jaExistiam = 0
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

            conteudo.progresso?.let { importado ->
                val atual = progressoDao.atual()
                if (atual == null || atual.estaVazio()) {
                    progressoDao.guardar(importado.copy(id = 1))
                }
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
            livro = o.optString("livro").takeIf { it.isNotBlank() },
            autor = o.optString("autor").takeIf { it.isNotBlank() },
            page = if (o.has("pagina")) o.optInt("pagina") else null,
            frase = o.optString("frase").takeIf { it.isNotBlank() },
            note = o.optString("nota").takeIf { it.isNotBlank() },
            significado = o.optString("significado").takeIf { it.isNotBlank() },
            mastery = o.optInt("dominio", 0),
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

    private fun Progresso.estaVazio(): Boolean =
        pontos == 0 && sequencia == 0 && acertos == 0 && erros == 0 && ultimoDia == null

    sealed interface Resultado {
        /** Na exportação, `novas` é o total escrito e `repetidas` é zero. */
        data class Feito(val novas: Int, val repetidas: Int) : Resultado
        data class Erro(val motivo: String) : Resultado
    }
}
