package pt.jorges15.palavrame.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/*
 * Os livros (1.22): cada livro é uma ficha própria, com as suas leituras.
 *
 * O título continua a ser o elo com as palavras (`saved_words.book_title`):
 * é único na tabela, e renomear ou juntar livros muda-o nas palavras na mesma
 * transação. Assim o resto da app (a Coleção, o jogo, a partilha, as cópias
 * de segurança antigas) continua a ler o livro de cada palavra como sempre,
 * e não há duas verdades a desencontrar-se.
 *
 * Estilo diário de leitura, calmo: datas, notas, páginas. Sem estrelas,
 * metas nem desafios.
 */

/** A ficha de um livro. O título é único; o autor daqui é o que vale. */
@Entity(tableName = "livros", indices = [Index(value = ["titulo"], unique = true)])
data class FichaLivro(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val titulo: String,
    val autor: String? = null,
    /** Para o progresso: "p. 214 de 480" a partir das páginas das palavras. */
    val paginas: Int? = null,
    /** Uma nota sobre o livro, do próprio. */
    val nota: String? = null,
    @ColumnInfo(name = "criado_em") val criadoEm: Long = System.currentTimeMillis(),
    /** O livro que vem já escolhido ao registar uma palavra. No máximo um. */
    @ColumnInfo(name = "em_curso", defaultValue = "0") val emCurso: Boolean = false,
    /**
     * Em que língua se lê o livro (`Lingua`). Escolhe-se ao acrescentá-lo, e é
     * a língua em que a pesquisa abre enquanto é o livro em curso.
     */
    @ColumnInfo(defaultValue = "'pt'") val lingua: String = Lingua.PT,
)

/**
 * Uma leitura de um livro: começou, acabou (ou parou). Um livro pode ter
 * várias: voltar a ler abre uma nova e guarda as anteriores.
 */
@Entity(tableName = "leituras", indices = [Index(value = ["livro_id"])])
data class Leitura(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "livro_id") val livroId: Long,
    @ColumnInfo(name = "iniciada_em") val iniciadaEm: Long? = null,
    @ColumnInfo(name = "terminada_em") val terminadaEm: Long? = null,
    /** Posta de lado sem acabar. */
    @ColumnInfo(defaultValue = "0") val parada: Boolean = false,
    /**
     * Onde se vai, escrito à mão ("Em que página vais?", 1.22): a página de
     * quando se pousou o livro, que pode ser depois da última palavra.
     */
    val pagina: Int? = null,
    /** Quando se escreveu a página: compara-se com a da última palavra guardada. */
    @ColumnInfo(name = "pagina_em") val paginaEm: Long? = null,
) {
    val lida: Boolean get() = terminadaEm != null
}

enum class EstadoLeitura { SEM_LEITURA, A_LER, PARADO, LIDO }

/** Um livro com as suas leituras, pronto a mostrar. */
data class LivroCompleto(val ficha: FichaLivro, val leituras: List<Leitura> = emptyList()) {
    val titulo: String get() = ficha.titulo

    /** A leitura mais recente: a que começou (ou acabou) mais tarde. */
    val atual: Leitura?
        get() = leituras.maxWithOrNull(
            compareBy<Leitura>({ it.iniciadaEm ?: it.terminadaEm ?: 0L }, { it.id })
        )

    /** O estado sai da leitura mais recente. */
    val estado: EstadoLeitura
        get() {
            val l = atual ?: return EstadoLeitura.SEM_LEITURA
            return when {
                l.terminadaEm != null -> EstadoLeitura.LIDO
                l.parada -> EstadoLeitura.PARADO
                else -> EstadoLeitura.A_LER
            }
        }

    /** As leituras acabadas, a mais antiga primeiro. */
    val lidas: List<Leitura> get() = leituras.filter { it.lida }.sortedBy { it.terminadaEm }

    /** Quando se acabou de ler pela última vez (para "Lidos" por ano). */
    val ultimaVezLido: Long? get() = lidas.lastOrNull()?.terminadaEm
}

/** As fichas e as leituras juntas, por título. */
fun livrosCompletos(fichas: List<FichaLivro>, leituras: List<Leitura>): Map<String, LivroCompleto> {
    val porLivro = leituras.groupBy { it.livroId }
    return fichas.associate { it.titulo to LivroCompleto(it, porLivro[it.id].orEmpty()) }
}

@Dao
interface LivrosDao {
    @Query("SELECT * FROM livros ORDER BY titulo")
    fun fichas(): Flow<List<FichaLivro>>

    @Query("SELECT * FROM livros ORDER BY titulo")
    suspend fun fichasAgora(): List<FichaLivro>

    @Query("SELECT * FROM leituras")
    fun leituras(): Flow<List<Leitura>>

    @Query("SELECT * FROM leituras")
    suspend fun leiturasAgora(): List<Leitura>

    @Query("SELECT * FROM livros WHERE titulo = :titulo")
    suspend fun porTitulo(titulo: String): FichaLivro?

    @Query("SELECT * FROM livros WHERE id = :id")
    suspend fun porId(id: Long): FichaLivro?

    @Query("SELECT * FROM leituras WHERE livro_id = :livroId")
    suspend fun leiturasDe(livroId: Long): List<Leitura>

    /** IGNORE: um título que já existe fica como está. Devolve -1 nesse caso. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun inserir(ficha: FichaLivro): Long

    @Update
    suspend fun atualizar(ficha: FichaLivro)

    @Query("DELETE FROM livros WHERE id = :id")
    suspend fun apagar(id: Long)

    @Insert
    suspend fun inserirLeitura(leitura: Leitura): Long

    @Update
    suspend fun atualizarLeitura(leitura: Leitura)

    @Query("DELETE FROM leituras WHERE id = :id")
    suspend fun apagarLeitura(id: Long)

    @Query("DELETE FROM leituras WHERE livro_id = :livroId")
    suspend fun apagarLeiturasDe(livroId: Long)

    @Query("UPDATE leituras SET livro_id = :para WHERE livro_id = :de")
    suspend fun moverLeituras(de: Long, para: Long)

    @Query("UPDATE livros SET em_curso = 0 WHERE em_curso = 1")
    suspend fun nenhumEmCurso()

    /**
     * Uma ficha para cada livro das palavras que ainda não a tem, criada no
     * dia da primeira palavra dele. Corre depois de guardar ou importar.
     */
    @Query(
        "INSERT OR IGNORE INTO livros (titulo, autor, paginas, nota, criado_em, em_curso)"
        + " SELECT TRIM(book_title), MAX(NULLIF(TRIM(book_author), '')), NULL, NULL,"
        + " MIN(saved_at), 0 FROM saved_words"
        + " WHERE book_title IS NOT NULL AND TRIM(book_title) <> ''"
        + " GROUP BY TRIM(book_title)"
    )
    suspend fun preencherEmFalta()

    /** Um livro sem autor fica com o que as palavras dele tiverem. */
    @Query(
        "UPDATE livros SET autor = (SELECT MAX(NULLIF(TRIM(w.book_author), ''))"
        + " FROM saved_words w WHERE TRIM(w.book_title) = livros.titulo)"
        + " WHERE autor IS NULL"
    )
    suspend fun preencherAutores()

    @Query("SELECT MIN(saved_at) FROM saved_words WHERE book_title = :titulo")
    suspend fun primeiraPalavra(titulo: String): Long?

    @Query("SELECT COUNT(*) FROM saved_words WHERE book_title = :titulo")
    suspend fun quantasPalavras(titulo: String): Int

    @Query("UPDATE saved_words SET book_title = :para WHERE book_title = :de")
    suspend fun renomearNasPalavras(de: String, para: String)

    @Query("UPDATE saved_words SET book_author = :autor WHERE book_title = :titulo")
    suspend fun autorNasPalavras(titulo: String, autor: String?)
}

// --- operações (cada uma numa transação) -------------------------------------------

/** Depois de guardar ou importar palavras: os livros novos ganham ficha. */
suspend fun UtilizadorDb.acertarLivros() = withTransaction {
    livros().preencherEmFalta()
    livros().preencherAutores()
}

private suspend fun UtilizadorDb.garantirFicha(titulo: String, agora: Long): FichaLivro {
    val dao = livros()
    dao.porTitulo(titulo)?.let { return it }
    dao.inserir(FichaLivro(titulo = titulo, criadoEm = dao.primeiraPalavra(titulo) ?: agora))
    return dao.porTitulo(titulo)!!
}

/**
 * "Estou a ler este livro" (ou "Retomar", ou "Voltar a ler"). Passa a ser o
 * livro em curso. Se a leitura mais recente estava parada, retoma-a; se
 * estava acabada (ou não havia), abre uma nova. A primeira leitura de um livro
 * que já tem palavras começa no dia da primeira palavra dele.
 */
suspend fun UtilizadorDb.comecarLivro(titulo: String, agora: Long) = withTransaction {
    val dao = livros()
    val ficha = garantirFicha(titulo, agora)
    dao.nenhumEmCurso()
    dao.atualizar(ficha.copy(emCurso = true))
    val leituras = dao.leiturasDe(ficha.id)
    val atual = LivroCompleto(ficha, leituras).atual
    when {
        atual != null && atual.terminadaEm == null ->
            if (atual.parada) dao.atualizarLeitura(atual.copy(parada = false))
        leituras.isEmpty() ->
            dao.inserirLeitura(Leitura(livroId = ficha.id, iniciadaEm = dao.primeiraPalavra(titulo) ?: agora))
        else -> dao.inserirLeitura(Leitura(livroId = ficha.id, iniciadaEm = agora))
    }
    Unit
}

/** "Acabei este livro": fecha a leitura em curso (ou regista uma, acabada hoje). */
suspend fun UtilizadorDb.terminarLivro(titulo: String, agora: Long) = withTransaction {
    val dao = livros()
    val ficha = garantirFicha(titulo, agora)
    dao.atualizar(ficha.copy(emCurso = false))
    val atual = LivroCompleto(ficha, dao.leiturasDe(ficha.id)).atual
    if (atual != null && atual.terminadaEm == null) {
        dao.atualizarLeitura(atual.copy(terminadaEm = agora, parada = false))
    } else {
        dao.inserirLeitura(
            Leitura(livroId = ficha.id, iniciadaEm = dao.primeiraPalavra(titulo), terminadaEm = agora)
        )
    }
    Unit
}

/** "Pausar leitura": a leitura fica em pausa, e deixa de ser o livro em curso. */
suspend fun UtilizadorDb.pararLivro(titulo: String) = withTransaction {
    val dao = livros()
    val ficha = dao.porTitulo(titulo) ?: return@withTransaction
    dao.atualizar(ficha.copy(emCurso = false))
    val atual = LivroCompleto(ficha, dao.leiturasDe(ficha.id)).atual
    if (atual != null && atual.terminadaEm == null) dao.atualizarLeitura(atual.copy(parada = true))
    Unit
}

/** Só a marca de "vem já escolhido ao registar", sem mexer nas leituras. */
suspend fun UtilizadorDb.escolherEmCurso(titulo: String?) = withTransaction {
    val dao = livros()
    dao.nenhumEmCurso()
    if (titulo != null) dao.porTitulo(titulo)?.let { dao.atualizar(it.copy(emCurso = true)) }
    Unit
}

/**
 * "Em que página vais?": marca a página na leitura em curso (ou em pausa) e,
 * se vier, o número de páginas do livro. Sem leitura aberta, não faz nada.
 */
suspend fun UtilizadorDb.marcarPagina(titulo: String, pagina: Int, paginasDoLivro: Int?, agora: Long) =
    withTransaction {
        val dao = livros()
        val ficha = dao.porTitulo(titulo) ?: return@withTransaction
        if (paginasDoLivro != null && paginasDoLivro != ficha.paginas) {
            dao.atualizar(ficha.copy(paginas = paginasDoLivro))
        }
        val atual = LivroCompleto(ficha, dao.leiturasDe(ficha.id)).atual
        if (atual != null && atual.terminadaEm == null) {
            dao.atualizarLeitura(atual.copy(pagina = pagina, paginaEm = agora))
        }
        Unit
    }

/** Um livro novo, ainda sem palavras ("Adicionar livro"). Falso se o título já existe. */
suspend fun UtilizadorDb.criarLivro(
    titulo: String,
    autor: String?,
    paginas: Int?,
    comecar: Boolean,
    agora: Long,
    lingua: String = Lingua.PT,
): Boolean = withTransaction {
    val t = titulo.trim()
    if (t.isEmpty() || livros().porTitulo(t) != null) return@withTransaction false
    livros().inserir(
        FichaLivro(
            titulo = t, autor = autor?.trim()?.ifBlank { null }, paginas = paginas,
            criadoEm = agora, lingua = Lingua.deCodigo(lingua),
        )
    )
    if (comecar) comecarLivro(t, agora)
    true
}

enum class ResultadoFicha { FEITO, TITULO_EXISTE, TITULO_VAZIO }

/**
 * Corrigir a ficha: título, autor, páginas, nota, língua. O título e o autor mudam
 * também em todas as palavras do livro. Um título que já é de outro livro não
 * se aceita aqui: isso é juntar (ver `juntarLivros`).
 */
suspend fun UtilizadorDb.editarLivro(
    id: Long,
    titulo: String,
    autor: String?,
    paginas: Int?,
    nota: String?,
    lingua: String? = null,
): ResultadoFicha = withTransaction {
    val dao = livros()
    val ficha = dao.porId(id) ?: return@withTransaction ResultadoFicha.FEITO
    val t = titulo.trim()
    if (t.isEmpty()) return@withTransaction ResultadoFicha.TITULO_VAZIO
    val outro = dao.porTitulo(t)
    if (outro != null && outro.id != id) return@withTransaction ResultadoFicha.TITULO_EXISTE
    val a = autor?.trim()?.ifBlank { null }
    if (t != ficha.titulo) dao.renomearNasPalavras(ficha.titulo, t)
    if (a != ficha.autor) dao.autorNasPalavras(t, a)
    dao.atualizar(
        ficha.copy(
            titulo = t, autor = a, paginas = paginas, nota = nota?.trim()?.ifBlank { null },
            lingua = lingua?.let(Lingua::deCodigo) ?: ficha.lingua,
        )
    )
    ResultadoFicha.FEITO
}

/**
 * Juntar um livro a outro ("Os maias" em "Os Maias"): as palavras e as
 * leituras passam para o destino, as notas juntam-se, e a ficha de origem
 * desaparece. As palavras ficam com o autor do destino, se o tiver.
 */
suspend fun UtilizadorDb.juntarLivros(deId: Long, paraId: Long) = withTransaction {
    val dao = livros()
    val de = dao.porId(deId) ?: return@withTransaction
    val para = dao.porId(paraId) ?: return@withTransaction
    if (de.id == para.id) return@withTransaction
    val autor = para.autor ?: de.autor
    dao.renomearNasPalavras(de.titulo, para.titulo)
    dao.autorNasPalavras(para.titulo, autor)
    dao.moverLeituras(de.id, para.id)
    dao.atualizar(
        para.copy(
            autor = autor,
            paginas = para.paginas ?: de.paginas,
            nota = listOfNotNull(para.nota, de.nota).joinToString("\n\n").ifBlank { null },
            criadoEm = minOf(para.criadoEm, de.criadoEm),
            emCurso = para.emCurso || de.emCurso,
        )
    )
    dao.apagar(de.id)
}

/** Apagar um livro sem palavras (as fichas com palavras voltavam a nascer delas). */
suspend fun UtilizadorDb.apagarLivro(id: Long): Boolean = withTransaction {
    val dao = livros()
    val ficha = dao.porId(id) ?: return@withTransaction false
    if (dao.quantasPalavras(ficha.titulo) > 0) return@withTransaction false
    dao.apagarLeiturasDe(id)
    dao.apagar(id)
    true
}

// --- migração ----------------------------------------------------------------------

/**
 * v5 -> v6: o livro como ficha própria, com leituras.
 *
 * A v5 (só nas versões de teste da 1.22) tinha uma linha por título com uma
 * data de início e uma de fim. Aqui:
 *
 * 1. os títulos das palavras perdem os espaços à volta (" Os Maias" e
 *    "Os Maias" eram já o mesmo livro na Coleção);
 * 2. cada livro das palavras ganha uma ficha, com o autor delas, criada no dia
 *    da primeira palavra; os da v5 sem palavras também;
 * 3. só ganham leitura os livros que a v5 sabia estarem a ser lidos ou lidos
 *    (o início automático da v5, igual para todos, não diz nada de leitura).
 *
 * Nada das palavras se apaga.
 */
val MIGRACAO_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE livros RENAME TO livros_v5")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `livros` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
            + " `titulo` TEXT NOT NULL, `autor` TEXT, `paginas` INTEGER, `nota` TEXT,"
            + " `criado_em` INTEGER NOT NULL, `em_curso` INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_livros_titulo` ON `livros` (`titulo`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `leituras` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
            + " `livro_id` INTEGER NOT NULL, `iniciada_em` INTEGER, `terminada_em` INTEGER,"
            + " `parada` INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_leituras_livro_id` ON `leituras` (`livro_id`)")

        db.execSQL(
            "UPDATE saved_words SET book_title = NULLIF(TRIM(book_title), '')"
            + " WHERE book_title IS NOT NULL AND book_title <> TRIM(book_title)"
        )
        db.execSQL(
            "INSERT OR IGNORE INTO livros (titulo, autor, paginas, nota, criado_em, em_curso)"
            + " SELECT book_title, MAX(NULLIF(TRIM(book_author), '')), NULL, NULL, MIN(saved_at), 0"
            + " FROM saved_words WHERE book_title IS NOT NULL AND book_title <> ''"
            + " GROUP BY book_title"
        )
        db.execSQL(
            "INSERT OR IGNORE INTO livros (titulo, autor, paginas, nota, criado_em, em_curso)"
            + " SELECT TRIM(titulo), NULL, NULL, NULL, COALESCE(iniciado_em, terminado_em, 0), 0"
            + " FROM livros_v5 WHERE TRIM(titulo) <> ''"
        )
        db.execSQL(
            "UPDATE livros SET em_curso = 1 WHERE titulo IN"
            + " (SELECT TRIM(titulo) FROM livros_v5 WHERE em_curso = 1)"
        )
        db.execSQL(
            "INSERT INTO leituras (livro_id, iniciada_em, terminada_em, parada)"
            + " SELECT l.id, v.iniciado_em, v.terminado_em, 0"
            + " FROM livros_v5 v JOIN livros l ON l.titulo = TRIM(v.titulo)"
            + " WHERE v.em_curso = 1 OR v.terminado_em IS NOT NULL"
        )
        db.execSQL("DROP TABLE livros_v5")
    }
}

/**
 * v6 -> v7: a página onde se vai, escrita à mão, em cada leitura (com a data,
 * para se comparar com a da última palavra). Só acrescenta colunas.
 */
val MIGRACAO_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE leituras ADD COLUMN `pagina` INTEGER")
        db.execSQL("ALTER TABLE leituras ADD COLUMN `pagina_em` INTEGER")
    }
}
