package pt.jorges15.palavrame.data

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import java.io.File

/**
 * Acesso ao `dicionario.db` — **só leitura, sempre**.
 *
 * Regra inviolável do projeto: esta base é substituída inteira a cada
 * atualização do dicionário e nunca contém dados do utilizador. Por isso
 * abre-se com `OPEN_READONLY` e não há aqui uma única instrução de escrita.
 * Não usa Room de propósito: Room quer gerir migrações, e uma migração
 * sobre esta base seria um erro conceptual — o que se faz é trocar o
 * ficheiro.
 */
class Dicionario private constructor(private val db: SQLiteDatabase) {

    companion object {
        const val NOME_FICHEIRO = "dicionario.db"

        fun abrir(ficheiro: File): Dicionario? = try {
            if (!ficheiro.exists()) null
            else Dicionario(
                SQLiteDatabase.openDatabase(
                    ficheiro.path, null, SQLiteDatabase.OPEN_READONLY
                )
            )
        } catch (e: SQLiteException) {
            null
        }
    }

    fun fechar() = db.close()

    private fun meta(chave: String): String? =
        db.rawQuery("SELECT value FROM meta WHERE key = ?", arrayOf(chave))
            .use { if (it.moveToFirst()) it.getString(0) else null }

    val versao: String? get() = meta("db_version")

    /**
     * Quando a base foi construída, em ISO-8601.
     *
     * É isto que o rodapé mostra, e não o `db_version`: duas builds
     * seguidas do pipeline têm ambas versão "1", e olhar para o ecrã não
     * dizia qual delas estava instalada — foi exatamente a dúvida que
     * custou uma tarde.
     */
    val construidoEm: String? get() = meta("built_at")

    val totalLemas: Int? get() = meta("count_lemmas")?.toIntOrNull()
    val totalAcecoes: Int? get() = meta("count_senses")?.toIntOrNull()
    val totalFormas: Int? get() = meta("count_forms")?.toIntOrNull()
    val totalExemplos: Int? get() = meta("count_examples")?.toIntOrNull()

    /**
     * O coração da app: o que está escrito no livro -> lemas candidatos.
     *
     * Procura na tabela `forms`, que o pipeline construiu com as flexões
     * todas. Sem isto, "couberam" não chegaria a "caber" e a app falhava na
     * primeira utilização a sério. Um lema pode ter vários candidatos
     * ("cantada" é substantivo e particípio) — devolvem-se todos, ordenados
     * pela frequência, como o plano manda (secção 9).
     */
    fun procurar(escrito: String, limite: Int = 25): List<Candidato> {
        val chave = Texto.normalizar(Texto.limparSelecao(escrito))
        if (chave.isEmpty()) return emptyList()

        val sql = """
            SELECT DISTINCT l.id, l.lemma, l.pos, l.frequency_rank, f.form
            FROM forms f
            JOIN lemmas l ON l.id = f.lemma_id
            WHERE f.normalized = ?
            ORDER BY (l.frequency_rank IS NULL), l.frequency_rank, l.lemma
            LIMIT ?
        """
        return db.rawQuery(sql, arrayOf(chave, limite.toString())).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val lemma = c.getString(1)
                    val forma = c.getString(4)
                    add(
                        Candidato(
                            lemmaId = c.getLong(0),
                            lemma = lemma,
                            pos = c.getString(2) ?: "",
                            frequencyRank = if (c.isNull(3)) null else c.getInt(3),
                            viaForma = forma.takeIf {
                                it != null && Texto.normalizar(it) != Texto.normalizar(lemma)
                            },
                        )
                    )
                }
            }
        }
    }

    /** Sugestões enquanto se escreve. Prefixo sobre a forma normalizada. */
    fun sugerir(prefixo: String, limite: Int = 12): List<String> {
        val chave = Texto.normalizar(prefixo)
        if (chave.length < 2) return emptyList()
        val sql = """
            SELECT l.lemma
            FROM lemmas l
            WHERE l.normalized GLOB ?
            ORDER BY (l.frequency_rank IS NULL), l.frequency_rank, l.lemma
            LIMIT ?
        """
        return db.rawQuery(sql, arrayOf("$chave*", limite.toString())).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }
    }

    /**
     * A entrada de um lema pela grafia exata.
     *
     * O jogo parte da coleção, onde o lema está guardado como texto, e não
     * pode passar pelo `procurar`: esse resolve flexões e devolveria vários
     * candidatos. Aqui quer-se a palavra que foi registada, aquela e mais
     * nenhuma. Devolve nulo se o dicionário foi substituído por um que já
     * não a tem — caso em que a palavra fica de fora do jogo, em silêncio.
     */
    fun entradaPorLema(lemma: String): Entrada? {
        val id = db.rawQuery(
            "SELECT id FROM lemmas WHERE lemma = ? LIMIT 1", arrayOf(lemma)
        ).use { if (it.moveToFirst()) it.getLong(0) else null } ?: return null
        return entrada(id)
    }

    fun entrada(lemmaId: Long): Entrada? {
        val cabecalho = db.rawQuery(
            "SELECT lemma, pos, syllables FROM lemmas WHERE id = ?",
            arrayOf(lemmaId.toString())
        ).use { if (it.moveToFirst()) Triple(it.getString(0), it.getString(1), it.getString(2)) else null }
            ?: return null

        return Entrada(
            lemmaId = lemmaId,
            lemma = cabecalho.first,
            pos = cabecalho.second ?: "",
            silabas = cabecalho.third,
            acecoes = acecoes(lemmaId),
            exemplos = exemplos(lemmaId),
            relacionadas = relacionadas(lemmaId),
        )
    }

    private fun acecoes(lemmaId: Long): List<Acecao> {
        val sql = """
            SELECT s.id, s.ord, s.definition, s.domains, src.name, s.modernized
            FROM senses s JOIN sources src ON src.id = s.source_id
            WHERE s.lemma_id = ? ORDER BY s.ord
        """
        return db.rawQuery(sql, arrayOf(lemmaId.toString())).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Acecao(
                            id = c.getLong(0),
                            ord = c.getInt(1),
                            definicao = c.getString(2),
                            dominios = dominiosDeJson(c.getString(3)),
                            fonte = c.getString(4),
                            modernizada = c.getInt(5) == 1,
                        )
                    )
                }
            }
        }
    }

    private fun exemplos(lemmaId: Long): List<Exemplo> {
        // pt-PT primeiro, real antes de gerado: é a cascata que o plano
        // define (4.3), aplicada também na apresentação.
        val sql = """
            SELECT e.sentence, src.name, e.source_ref, e.variant, e.generated, e.sense_id
            FROM examples e JOIN sources src ON src.id = e.source_id
            WHERE e.lemma_id = ?
            ORDER BY e.generated, (e.variant <> 'pt-PT'), LENGTH(e.sentence)
            LIMIT 40
        """
        return db.rawQuery(sql, arrayOf(lemmaId.toString())).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Exemplo(
                            frase = c.getString(0),
                            fonte = c.getString(1),
                            referencia = c.getString(2),
                            variante = c.getString(3),
                            gerado = c.getInt(4) == 1,
                            acecaoId = if (c.isNull(5)) null else c.getLong(5),
                        )
                    )
                }
            }
        }
    }

    private fun relacionadas(lemmaId: Long): List<Relacionada> {
        val sql = """
            SELECT l.lemma, y.relation
            FROM synonyms y JOIN lemmas l ON l.id = y.synonym_id
            WHERE y.lemma_id = ?
            ORDER BY y.relation, l.lemma LIMIT 40
        """
        return db.rawQuery(sql, arrayOf(lemmaId.toString())).use { c ->
            buildList { while (c.moveToNext()) add(Relacionada(c.getString(0), c.getString(1))) }
        }
    }

    /**
     * Definições do dicionário para servirem de distração no jogo.
     *
     * Só o texto interessa — a palavra a que pertencem nunca aparece no
     * ecrã. Filtra-se pela classe gramatical e pelo comprimento, para as
     * opções serem comparáveis, e excluem-se os gentílicos, que são
     * milhares e não servem de distração a nada.
     */
    fun definicoesParaDistracao(
        pos: String,
        minimo: Int,
        maximo: Int,
        limite: Int = 30,
    ): List<Triple<String, String, String>> {
        val sql = """
            SELECT l.lemma, l.pos, s.definition
            FROM senses s JOIN lemmas l ON l.id = s.lemma_id
            WHERE l.pos = ?
              AND LENGTH(s.definition) BETWEEN ? AND ?
              -- Milhares de adjetivos do Wikcionário são gentílicos
              -- ("relativo ou pertencente a Melo"). Como distração são
              -- todos iguais entre si e nenhum ensina nada.
              AND s.definition NOT LIKE 'relativo ou pertencente%'
              AND s.definition NOT LIKE 'natural ou habitante%'
            ORDER BY RANDOM() LIMIT ?
        """
        return db.rawQuery(
            sql,
            arrayOf(pos, minimo.toString(), maximo.toString(), limite.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Triple(c.getString(0), c.getString(1), c.getString(2)))
                }
            }
        }
    }

    /**
     * Alimenta o ecrã "Fontes e licenças" (plano F5).
     *
     * Exclui a linha "Fonte não declarada", que o pipeline cria como rede de
     * segurança para conteúdo sem fonte registada. Quando não é precisa — o
     * caso normal — o pipeline apaga-a, mas as bases construídas antes disso
     * ainda a trazem, e não faz sentido mostrar ao leitor uma fonte com
     * licença "DESCONHECIDA" que não contribuiu com nada.
     *
     * Filtra-se pela licença e não por "não tem aceções": o Hunspell e o
     * PAPEL também não têm — dão formas e relações — e as suas licenças
     * exigem atribuição.
     */
    fun fontes(): List<Fonte> =
        db.rawQuery(
            "SELECT name, url, license, license_url, attribution FROM sources"
                + " WHERE license <> 'DESCONHECIDA' ORDER BY name",
            null
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Fonte(c.getString(0), c.getString(1), c.getString(2),
                        c.getString(3), c.getString(4)))
                }
            }
        }

    private fun dominiosDeJson(bruto: String?): List<String> {
        if (bruto.isNullOrBlank()) return emptyList()
        // O pipeline escreve JSON simples: ["Fig."] ou ["Náut.","Bot."].
        return bruto.trim().removeSurrounding("[", "]")
            .split(',')
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotEmpty() }
    }
}
