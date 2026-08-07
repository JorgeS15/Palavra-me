package pt.jorges15.palavrame.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * `utilizador.db` — a coleção de quem usa a app.
 *
 * A regra que o plano chama inviolável: esta base **nunca** é tocada por uma
 * atualização do dicionário. Vive noutro ficheiro, é a única com escrita, e
 * tem cópia de segurança.
 *
 * `lemma` é guardado como TEXTO e não como chave estrangeira para
 * `dicionario.db`. Uma atualização do dicionário pode reordenar os ids; as
 * palavras registadas não podem depender disso.
 */
@Entity(
    tableName = "saved_words",
    // Uma palavra é uma palavra: a coleção é o registo do que já se
    // aprendeu, não um diário de encontros. Sem este índice, registar
    // 'sumptuoso' duas vezes dava duas linhas iguais na lista.
    indices = [Index(value = ["lemma"], unique = true)],
)
data class PalavraGuardada(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lemma: String,
    @ColumnInfo(name = "saved_at") val guardadaEm: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "book_title") val livro: String? = null,
    @ColumnInfo(name = "book_author") val autor: String? = null,
    val page: Int? = null,
    @ColumnInfo(name = "context_snippet") val frase: String? = null,
    val note: String? = null,
    /** Caixa da repetição espaçada, 0 a 5. Ver `docs/jogo.md`. */
    val mastery: Int = 0,
    @ColumnInfo(name = "last_reviewed") val revistaEm: Long? = null,
    /**
     * Quando esta palavra volta ao jogo. Nulo = ainda nunca foi jogada,
     * portanto está vencida desde sempre.
     */
    @ColumnInfo(name = "proxima_revisao") val proximaRevisao: Long? = null,
)

/**
 * O progresso do jogo. Uma linha só, com id fixo.
 *
 * Vive na base e não nas preferências para entrar na cópia de segurança do
 * Android **e** no ficheiro de exportação. Progresso que se perde ao mudar
 * de telemóvel não é progresso.
 */
@Entity(tableName = "progresso")
data class Progresso(
    @PrimaryKey val id: Int = 1,
    val pontos: Int = 0,
    val sequencia: Int = 0,
    @ColumnInfo(name = "ultimo_dia") val ultimoDia: String? = null,
    val acertos: Int = 0,
    val erros: Int = 0,
)

@Dao
interface PalavrasDao {

    @Query("SELECT * FROM saved_words ORDER BY saved_at DESC")
    fun todas(): Flow<List<PalavraGuardada>>

    /** Leitura pontual, para exportar. O Flow serve a interface, não isto. */
    @Query("SELECT * FROM saved_words ORDER BY saved_at DESC")
    suspend fun todasAgora(): List<PalavraGuardada>

    @Query("SELECT * FROM saved_words WHERE lemma = :lemma LIMIT 1")
    suspend fun porLema(lemma: String): PalavraGuardada?

    @Query("SELECT EXISTS(SELECT 1 FROM saved_words WHERE lemma = :lemma)")
    fun estaGuardada(lemma: String): Flow<Boolean>

    @Query("SELECT DISTINCT book_title FROM saved_words WHERE book_title IS NOT NULL ORDER BY book_title")
    fun livros(): Flow<List<String>>

    /** IGNORE e não REPLACE: registar de novo não apaga o livro já anotado. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun guardar(palavra: PalavraGuardada): Long

    @Query("DELETE FROM saved_words WHERE id = :id")
    suspend fun apagar(id: Long)

    @Update
    suspend fun atualizar(palavra: PalavraGuardada)

    @Query("DELETE FROM saved_words WHERE lemma = :lemma")
    suspend fun esquecer(lemma: String)

    /**
     * Palavras vencidas, a mais atrasada primeiro.
     *
     * `proxima_revisao IS NULL` são as que nunca foram jogadas: estão
     * vencidas desde sempre, e é por elas que se começa.
     */
    @Query(
        "SELECT * FROM saved_words WHERE proxima_revisao IS NULL"
        + " OR proxima_revisao <= :agora"
        + " ORDER BY proxima_revisao IS NOT NULL, proxima_revisao, saved_at"
    )
    suspend fun vencidas(agora: Long): List<PalavraGuardada>
}

@Dao
interface ProgressoDao {
    @Query("SELECT * FROM progresso WHERE id = 1")
    fun observar(): Flow<Progresso?>

    @Query("SELECT * FROM progresso WHERE id = 1")
    suspend fun atual(): Progresso?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(progresso: Progresso)
}

/**
 * v1 -> v2: uma palavra passa a ser única na coleção.
 *
 * Escrita à mão porque já há dados reais no telemóvel do Jorge — incluindo
 * um 'sumptuoso' registado duas vezes. Apagar para simplificar seria
 * exatamente o que o plano proíbe: **guarda-se sempre o registo mais
 * antigo**, que é o que traz o livro e a frase da primeira vez.
 */
val MIGRACAO_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            DELETE FROM saved_words WHERE id NOT IN (
                SELECT MIN(id) FROM saved_words GROUP BY lemma
            )
            """
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_saved_words_lemma"
            + " ON saved_words (lemma)"
        )
    }
}

/**
 * v2 -> v3: o modo jogo.
 *
 * Acrescenta quando cada palavra volta a ser perguntada e a tabela do
 * progresso. Nada é apagado nem reescrito: as palavras já registadas ficam
 * com `proxima_revisao` a nulo, que significa "vencida desde sempre" — ou
 * seja, entram no jogo desde o primeiro dia, que é o que se quer.
 */
val MIGRACAO_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE saved_words ADD COLUMN proxima_revisao INTEGER")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS progresso ("
            + "id INTEGER NOT NULL PRIMARY KEY, pontos INTEGER NOT NULL,"
            + " sequencia INTEGER NOT NULL, ultimo_dia TEXT,"
            + " acertos INTEGER NOT NULL, erros INTEGER NOT NULL)"
        )
        db.execSQL(
            "INSERT OR IGNORE INTO progresso (id, pontos, sequencia,"
            + " ultimo_dia, acertos, erros) VALUES (1, 0, 0, NULL, 0, 0)"
        )
    }
}

@Database(
    entities = [PalavraGuardada::class, Progresso::class],
    version = 3,
    exportSchema = true,
)
abstract class UtilizadorDb : RoomDatabase() {
    abstract fun palavras(): PalavrasDao
    abstract fun progresso(): ProgressoDao

    companion object {
        @Volatile private var instancia: UtilizadorDb? = null

        fun obter(context: Context): UtilizadorDb =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    context.applicationContext, UtilizadorDb::class.java, "utilizador.db"
                )
                    // Sem fallbackToDestructiveMigration: apagar dados do
                    // utilizador por causa de uma migração é exatamente o
                    // que o plano proíbe. As migrações escrevem-se à mão.
                    .addMigrations(MIGRACAO_1_2, MIGRACAO_2_3)
                    .build().also { instancia = it }
            }
    }
}
