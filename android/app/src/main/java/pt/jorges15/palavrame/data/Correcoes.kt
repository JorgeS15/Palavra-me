package pt.jorges15.palavrame.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Uma correção a uma aceção do dicionário (1.30): "o significado 2 de
 * crèdor está antiquado; devia ser…".
 *
 * Até à 1.30 uma correção só existia no email do momento. Agora guarda-se:
 * a pessoa vê-a por baixo da aceção corrigida e pode enviá-la quando quiser,
 * com as outras sugestões, em Definições, Contribuir.
 *
 * Como as palavras, liga-se ao dicionário por TEXTO (o lema e a definição tal
 * como estava), nunca por id: uma atualização do dicionário reordena ids.
 * `acecaoOrd` serve para a mostrar no sítio certo enquanto o dicionário não
 * muda; `acecaoTexto` diz à curadoria o que estava escrito.
 *
 * Não depende da coleção: corrige-se uma definição de uma palavra que não se
 * registou.
 */
@Entity(
    tableName = "correcoes",
    indices = [Index(value = ["lemma", "lingua", "acecao_ord"], unique = true)],
)
data class CorrecaoGuardada(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lemma: String,
    val lingua: String = Lingua.PT,
    @ColumnInfo(name = "acecao_ord") val acecaoOrd: Int,
    @ColumnInfo(name = "acecao_texto") val acecaoTexto: String,
    @ColumnInfo(name = "acecao_fonte") val acecaoFonte: String? = null,
    /** A classe da aceção, como se diz a uma pessoa ("substantivo"). */
    val classe: String? = null,
    /** O que a pessoa escreveu: o que está mal, ou como devia ser. */
    val texto: String,
    @ColumnInfo(name = "criada_em") val criadaEm: Long = System.currentTimeMillis(),
    /** Quando foi enviada por email. Nula = por enviar. */
    @ColumnInfo(name = "enviada_em") val enviadaEm: Long? = null,
)

@Dao
interface CorrecoesDao {
    @Query("SELECT * FROM correcoes WHERE lemma = :lemma AND lingua = :lingua ORDER BY acecao_ord")
    fun daPalavra(lemma: String, lingua: String): Flow<List<CorrecaoGuardada>>

    @Query("SELECT * FROM correcoes WHERE enviada_em IS NULL ORDER BY criada_em")
    suspend fun porEnviar(): List<CorrecaoGuardada>

    @Query("SELECT COUNT(*) FROM correcoes WHERE enviada_em IS NULL")
    fun quantasPorEnviar(): Flow<Int>

    @Query("SELECT * FROM correcoes ORDER BY criada_em")
    suspend fun todas(): List<CorrecaoGuardada>

    @Query("SELECT * FROM correcoes WHERE lemma = :lemma AND lingua = :lingua AND acecao_ord = :ord LIMIT 1")
    suspend fun da(lemma: String, lingua: String, ord: Int): CorrecaoGuardada?

    /** REPLACE: escrever outra vez a correção da mesma aceção substitui-a. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(c: CorrecaoGuardada): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun inserirSeNova(c: CorrecaoGuardada): Long

    @Query("DELETE FROM correcoes WHERE lemma = :lemma AND lingua = :lingua AND acecao_ord = :ord")
    suspend fun apagar(lemma: String, lingua: String, ord: Int)

    @Query("UPDATE correcoes SET enviada_em = :quando WHERE id IN (:ids)")
    suspend fun marcarEnviadas(ids: List<Long>, quando: Long = System.currentTimeMillis())
}

/**
 * v8 -> v9 (1.30): as sugestões ao dicionário.
 *
 * - A tabela das correções (ver `CorrecaoGuardada`).
 * - `saved_words.enviado`: o significado tal como foi enviado à curadoria
 *   ("" para uma palavra enviada sem significado). Uma palavra em falta deixa
 *   de se propor depois de enviada; se o significado mudar, volta à lista.
 *
 * Só acrescenta; nada se apaga nem se reescreve. As palavras de antes ficam
 * todas por enviar, como estavam.
 */
val MIGRACAO_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE saved_words ADD COLUMN `enviado` TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `correcoes` ("
                + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `lemma` TEXT NOT NULL,"
                + " `lingua` TEXT NOT NULL, `acecao_ord` INTEGER NOT NULL,"
                + " `acecao_texto` TEXT NOT NULL, `acecao_fonte` TEXT, `classe` TEXT,"
                + " `texto` TEXT NOT NULL, `criada_em` INTEGER NOT NULL, `enviada_em` INTEGER)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_correcoes_lemma_lingua_acecao_ord`"
                + " ON `correcoes` (`lemma`, `lingua`, `acecao_ord`)"
        )
    }
}
