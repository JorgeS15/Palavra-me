package pt.jorges15.palavrame.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A coleção é a única coisa na app que não se recupera, e uma migração mal
 * feita apaga-a numa atualização. Estes testes criam à mão uma `utilizador.db`
 * exatamente como era numa versão antiga, com palavras lá dentro, e abrem-na
 * com a app de agora: o Room corre as migrações (`MIGRACOES`, a mesma lista que
 * a app usa) e valida o esquema final contra as entidades. Se uma migração
 * deixar o esquema diferente, o Room recusa abrir e o teste falha; se perder
 * dados, as verificações falham.
 *
 * Os esquemas das versões 1 a 3 nunca foram exportados, por isso as bases
 * antigas escrevem-se aqui em SQL. Daqui para a frente o esquema exporta-se
 * para `app/schemas/` a cada compilação.
 *
 * Corre num telemóvel ou emulador: `.\gradlew connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class MigracoesTest {

    private val nome = "teste-migracoes.db"
    private lateinit var context: Context

    @Before
    fun preparar() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(nome)
    }

    @After
    fun limpar() {
        context.deleteDatabase(nome)
    }

    // --- as bases antigas, tal como o Room as criava ----------------------

    private val tabelaV1 = """
        CREATE TABLE IF NOT EXISTS `saved_words` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `lemma` TEXT NOT NULL,
            `saved_at` INTEGER NOT NULL,
            `book_title` TEXT,
            `book_author` TEXT,
            `page` INTEGER,
            `context_snippet` TEXT,
            `note` TEXT,
            `mastery` INTEGER NOT NULL,
            `last_reviewed` INTEGER
        )
    """

    private fun criarV1(preencher: SQLiteDatabase.() -> Unit) {
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(nome), null)
        db.execSQL(tabelaV1)
        db.preencher()
        db.version = 1
        db.close()
    }

    private fun criarV3(preencher: SQLiteDatabase.() -> Unit) {
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(nome), null)
        db.execSQL(tabelaV1)
        db.execSQL("ALTER TABLE saved_words ADD COLUMN proxima_revisao INTEGER")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_saved_words_lemma ON saved_words (lemma)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS progresso ("
                + "id INTEGER NOT NULL PRIMARY KEY, pontos INTEGER NOT NULL,"
                + " sequencia INTEGER NOT NULL, ultimo_dia TEXT,"
                + " acertos INTEGER NOT NULL, erros INTEGER NOT NULL)"
        )
        db.preencher()
        db.version = 3
        db.close()
    }

    /** Abre a base com a app de agora: corre as migrações e valida o esquema. */
    private fun abrirComAAppDeAgora(): UtilizadorDb =
        Room.databaseBuilder(context, UtilizadorDb::class.java, nome)
            .addMigrations(*MIGRACOES)
            .build()

    // --- os testes ----------------------------------------------------------

    @Test
    fun da_versao_3_para_a_atual_nao_perde_nada() = runBlocking<Unit> {
        criarV3 {
            execSQL(
                "INSERT INTO saved_words (id, lemma, saved_at, book_title, book_author,"
                    + " page, context_snippet, note, mastery, last_reviewed, proxima_revisao)"
                    + " VALUES (7, 'lânguido', 1000, 'Os Maias', 'Eça de Queirós', 42,"
                    + " 'Tinha um olhar lânguido.', 'bonita', 3, 2000, 3000)"
            )
            execSQL(
                "INSERT INTO progresso (id, pontos, sequencia, ultimo_dia, acertos, erros)"
                    + " VALUES (1, 523, 11, '2026-09-30', 46, 6)"
            )
        }

        val db = abrirComAAppDeAgora()
        try {
            val p = db.palavras().porLema("lânguido")!!
            assertEquals(1000L, p.guardadaEm)
            assertEquals("Os Maias", p.livro)
            assertEquals("Eça de Queirós", p.autor)
            assertEquals(42, p.page)
            assertEquals("Tinha um olhar lânguido.", p.frase)
            assertEquals("bonita", p.note)
            assertEquals(3, p.mastery)
            assertEquals(2000L, p.revistaEm)
            assertEquals(3000L, p.proximaRevisao)
            // A coluna nova da versão 4 chega vazia, e escreve-se.
            assertNull(p.significado)
            db.palavras().atualizar(p.copy(significado = "Sem forças."))
            assertEquals("Sem forças.", db.palavras().porLema("lânguido")!!.significado)

            val prog = db.progresso().atual()!!
            assertEquals(523, prog.pontos)
            assertEquals(11, prog.sequencia)
            assertEquals("2026-09-30", prog.ultimoDia)
        } finally {
            db.close()
        }
    }

    @Test
    fun da_versao_1_para_a_atual_guarda_o_registo_mais_antigo() = runBlocking<Unit> {
        // O caso real que deu origem à migração 1→2: "sumptuoso" registado
        // duas vezes. Fica o mais antigo, que é o que traz o livro e a frase.
        criarV1 {
            execSQL(
                "INSERT INTO saved_words (id, lemma, saved_at, book_title, context_snippet, mastery)"
                    + " VALUES (1, 'sumptuoso', 1000, 'Os Maias', 'Um jantar sumptuoso.', 0)"
            )
            execSQL(
                "INSERT INTO saved_words (id, lemma, saved_at, mastery)"
                    + " VALUES (2, 'sumptuoso', 2000, 0)"
            )
            execSQL(
                "INSERT INTO saved_words (id, lemma, saved_at, mastery)"
                    + " VALUES (3, 'ensonado', 3000, 2)"
            )
        }

        val db = abrirComAAppDeAgora()
        try {
            val todas = db.palavras().todasAgora()
            assertEquals(listOf("ensonado", "sumptuoso"), todas.map { it.lemma }.sorted())

            val sumptuoso = db.palavras().porLema("sumptuoso")!!
            assertEquals(1L, sumptuoso.id)
            assertEquals("Os Maias", sumptuoso.livro)
            assertEquals("Um jantar sumptuoso.", sumptuoso.frase)

            // As colunas que vieram depois chegam vazias: a palavra entra no
            // jogo como "vencida desde sempre", que é o que se quer.
            assertNull(sumptuoso.proximaRevisao)
            assertNull(sumptuoso.significado)
            assertEquals(2, db.palavras().porLema("ensonado")!!.mastery)

            // A migração 2→3 cria o progresso a zeros.
            val prog = db.progresso().atual()!!
            assertEquals(0, prog.pontos)
            assertEquals(0, prog.acertos)
        } finally {
            db.close()
        }
    }
}
