package pt.jorges15.palavrame.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Exportar a coleção para o Anki (1.31).
 *
 * Quem já usa o Anki (ou o AnkiDroid, com o FSRS) pediu para lá levar as
 * palavras, em vez de um plugin. Sai um ficheiro de texto no formato que o
 * Anki importa sozinho (Ficheiro → Importar): uma nota por palavra, frente e
 * verso, no baralho "Palavra-me". Sem rede e sem permissões: o ficheiro vai
 * para onde a pessoa escolher, como a cópia de segurança.
 *
 * - **Frente:** a palavra.
 * - **Verso:** a classe, o teu significado (se escreveste um), até três
 *   significados do dicionário, a frase onde a encontraste (em itálico) e o
 *   livro, o autor e a página.
 * - **Etiquetas:** `palavra-me`, o livro (`livro::Os_Maias`) e `ingles` para
 *   as palavras inglesas.
 * - **GUID** fixo por palavra (`palavra-me:` + a chave): exportar e importar
 *   outra vez atualiza as notas que já lá estão, em vez de as duplicar, e o
 *   progresso do Anki nelas mantém-se.
 *
 * O texto vive em funções puras, testadas na JVM (`AnkiTest`).
 */
object Anki {

    /** O que se exporta de uma palavra: a guardada e o que o dicionário diz dela. */
    data class Nota(
        val palavra: PalavraGuardada,
        val classe: String?,
        /** As definições do dicionário, pela ordem dele. */
        val definicoes: List<String>,
    )

    private const val MAX_DEFINICOES = 3

    /** Nome sugerido no seletor: `palavra-me-anki-2026-11-25.txt`. */
    fun nomeSugerido(): String {
        val hoje = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT)
            .format(java.util.Date())
        return "palavra-me-anki-$hoje.txt"
    }

    /**
     * O ficheiro inteiro: o cabeçalho que diz ao Anki como o ler, e uma linha
     * por palavra. As palavras sem nada no verso (nem definição, nem o teu
     * significado) ficam de fora: um cartão sem resposta não serve.
     */
    internal fun ficheiro(notas: List<Nota>): String = buildString {
        append("#separator:tab\n")
        append("#html:true\n")
        append("#deck:Palavra-me\n")
        append("#guid column:1\n")
        append("#tags column:4\n")
        append("#columns:GUID\tFrente\tVerso\tEtiquetas\n")
        notas.forEach { n -> linha(n)?.let { append(it).append('\n') } }
    }

    /** Quantas notas o ficheiro leva (as que têm verso). */
    internal fun exportaveis(notas: List<Nota>): Int = notas.count { linha(it) != null }

    internal fun linha(n: Nota): String? {
        val p = n.palavra
        val significado = p.significado?.trim()?.takeIf { it.isNotEmpty() }
        val definicoes = n.definicoes.map { it.trim() }.filter { it.isNotEmpty() }.take(MAX_DEFINICOES)
        if (significado == null && definicoes.isEmpty()) return null

        val verso = buildList {
            n.classe?.takeIf { it.isNotBlank() }?.let { add("<i>${html(it)}</i>") }
            significado?.let { add("<b>O teu significado:</b> ${html(it)}") }
            when (definicoes.size) {
                0 -> Unit
                1 -> add(html(definicoes.single()))
                else -> add(definicoes.mapIndexed { i, d -> "${i + 1}. ${html(d)}" }.joinToString("<br>"))
            }
            p.frase?.trim()?.takeIf { it.isNotEmpty() }?.let { add("<i>“${html(it)}”</i>") }
            origem(p)?.let { add("— ${html(it)}") }
        }.joinToString("<br><br>")

        val etiquetas = buildList {
            add("palavra-me")
            p.livro?.trim()?.takeIf { it.isNotEmpty() }?.let { add("livro::" + etiqueta(it)) }
            if (p.lingua == Lingua.EN) add("ingles")
        }.joinToString(" ")

        val guid = "palavra-me:" + if (p.lingua == Lingua.PT) p.lemma else "${p.lingua}:${p.lemma}"
        return listOf(campo(guid), campo(html(p.lemma)), campo(verso), campo(etiquetas))
            .joinToString("\t")
    }

    /** "Os Maias, Eça de Queirós, p. 214", com o que houver. */
    private fun origem(p: PalavraGuardada): String? {
        val partes = listOfNotNull(
            p.livro?.trim()?.takeIf { it.isNotEmpty() },
            p.autor?.trim()?.takeIf { it.isNotEmpty() },
            p.page?.let { "p. $it" },
        )
        return partes.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    /** Uma etiqueta do Anki não tem espaços: "Os Maias" → "Os_Maias". */
    internal fun etiqueta(texto: String): String =
        texto.trim().replace(Regex("\\s+"), "_").replace("\"", "")

    /** O texto para HTML: escapa o que o Anki leria como marcação; quebras viram `<br>`. */
    internal fun html(texto: String): String = texto
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace(Regex("\\r?\\n"), "<br>")

    /**
     * Um campo do ficheiro: um tab partia as colunas e uma aspa no início
     * abria uma citação; entre aspas (e aspas duplicadas), o Anki lê-o inteiro.
     */
    private fun campo(texto: String): String {
        val t = texto.replace("\t", " ")
        return if (t.contains('"')) "\"" + t.replace("\"", "\"\"") + "\"" else t
    }

    /** Monta as notas da coleção com o dicionário de cada língua, e escreve o ficheiro. */
    suspend fun exportar(
        context: Context,
        destino: Uri,
        palavras: List<PalavraGuardada>,
        dicionarioDe: (String) -> Dicionario?,
    ): Backup.Resultado = withContext(Dispatchers.IO) {
        try {
            val notas = palavras.map { p ->
                val d = dicionarioDe(p.lingua)
                val e = d?.let { it.entradaPorLema(p.lemma) ?: it.procurar(p.lemma).firstOrNull()?.let { c -> it.entrada(c.lemmaId) } }
                Nota(
                    palavra = p,
                    classe = e?.pos?.takeIf { it.isNotBlank() && it != "desconhecido" }?.let(::rotuloPos),
                    definicoes = e?.acecoes?.map { it.definicao }.orEmpty(),
                )
            }
            val texto = ficheiro(notas)
            context.contentResolver.openOutputStream(destino, "wt")?.use {
                it.write(texto.toByteArray(Charsets.UTF_8))
            } ?: return@withContext Backup.Resultado.Erro("Não consegui escrever no ficheiro.")
            Backup.Resultado.Feito(exportaveis(notas), palavras.size - exportaveis(notas))
        } catch (e: Exception) {
            Backup.Resultado.Erro(e.message ?: "Falhou a exportação.")
        }
    }
}
