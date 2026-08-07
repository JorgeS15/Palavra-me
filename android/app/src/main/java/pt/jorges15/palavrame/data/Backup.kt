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
 * A cópia automática do Android já as protege, mas depende de a pessoa ter
 * a cópia ativa e de a conta ser a mesma; um ficheiro que se guarda onde se
 * quiser não depende de nada.
 *
 * O formato é JSON legível de propósito. Se um dia a app desaparecer, o
 * trabalho de anos de quem a usou deve continuar a poder ser aberto num
 * editor de texto.
 */
object Backup {

    private const val FORMATO = 1

    /** Nome sugerido no seletor: `palavra-me-2026-08-05.json`. */
    fun nomeSugerido(): String {
        val hoje = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT)
            .format(java.util.Date())
        return "palavra-me-$hoje.json"
    }

    suspend fun exportar(
        context: Context,
        destino: Uri,
        palavras: List<PalavraGuardada>,
        versaoDaApp: String,
    ): Resultado = withContext(Dispatchers.IO) {
        try {
            val raiz = JSONObject().apply {
                put("formato", FORMATO)
                put("app", "Palavra-me $versaoDaApp")
                put("exportado_em", java.time.Instant.now().toString())
                put("total", palavras.size)
                put("palavras", JSONArray().apply { palavras.forEach { put(paraJson(it)) } })
            }
            context.contentResolver.openOutputStream(destino)?.use { saida ->
                saida.write(raiz.toString(2).toByteArray(Charsets.UTF_8))
            } ?: return@withContext Resultado.Erro("Não consegui escrever no ficheiro.")
            Resultado.Feito(palavras.size, 0)
        } catch (e: Exception) {
            Resultado.Erro(e.message ?: "Falhou a exportação.")
        }
    }

    /**
     * Importa uma cópia.
     *
     * **Nunca substitui nem apaga**: as palavras que já estão na coleção
     * ficam como estão, e só entram as que faltam. Importar é juntar, não
     * trocar — e uma importação que apagasse o que estava seria a única
     * forma de perder dados nesta app.
     */
    suspend fun importar(
        context: Context,
        origem: Uri,
        dao: PalavrasDao,
    ): Resultado = withContext(Dispatchers.IO) {
        try {
            val texto = context.contentResolver.openInputStream(origem)
                ?.bufferedReader()?.use { it.readText() }
                ?: return@withContext Resultado.Erro("Não consegui ler o ficheiro.")

            val raiz = JSONObject(texto)
            val lista = raiz.optJSONArray("palavras")
                ?: return@withContext Resultado.Erro(
                    "Isto não parece uma cópia do Palavra-me."
                )

            var novas = 0
            var jaExistiam = 0
            for (i in 0 until lista.length()) {
                val palavra = deJson(lista.getJSONObject(i)) ?: continue
                if (dao.porLema(palavra.lemma) != null) {
                    jaExistiam++
                } else {
                    // id = 0 para o Room atribuir um novo: os ids da cópia
                    // podem colidir com os desta instalação.
                    dao.guardar(palavra.copy(id = 0))
                    novas++
                }
            }
            Resultado.Feito(novas, jaExistiam)
        } catch (e: Exception) {
            Resultado.Erro(e.message ?: "Falhou a importação.")
        }
    }

    private fun paraJson(p: PalavraGuardada) = JSONObject().apply {
        put("lema", p.lemma)
        put("guardada_em", p.guardadaEm)
        p.livro?.let { put("livro", it) }
        p.autor?.let { put("autor", it) }
        p.page?.let { put("pagina", it) }
        p.frase?.let { put("frase", it) }
        p.note?.let { put("nota", it) }
        if (p.mastery != 0) put("dominio", p.mastery)
        p.revistaEm?.let { put("revista_em", it) }
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
            mastery = o.optInt("dominio", 0),
            revistaEm = if (o.has("revista_em")) o.optLong("revista_em") else null,
        )
    }

    sealed interface Resultado {
        /** Na exportação, `novas` é o total escrito e `repetidas` é zero. */
        data class Feito(val novas: Int, val repetidas: Int) : Resultado
        data class Erro(val motivo: String) : Resultado
    }
}
