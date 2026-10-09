package pt.jorges15.palavrame.jogo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import pt.jorges15.palavrame.MainActivity
import pt.jorges15.palavrame.PalavrameApp
import pt.jorges15.palavrame.R
import pt.jorges15.palavrame.data.UtilizadorDb
import java.util.concurrent.TimeUnit

/**
 * O lembrete diário.
 *
 * Três decisões que se explicam melhor juntas:
 *
 * * **`WorkManager` e não alarmes exatos.** A hora não precisa de ser ao
 *   segundo, e desde o Android 12 um alarme exato exige uma permissão
 *   especial que o utilizador vê e que esta app não tem como justificar.
 * * **Sem botões de resposta na notificação.** Com definições longas o
 *   Android trunca-as, e uma resposta truncada é uma pergunta injusta.
 * * **Não se notifica se não houver nada vencido.** É o comportamento
 *   honesto: a repetição espaçada existe precisamente para não perguntar o
 *   que já está sabido. Uma app que avisa todos os dias mesmo sem ter nada a
 *   dizer ensina a ignorar o aviso.
 *
 * Continua a **não haver permissão de rede**. Tudo isto acontece no
 * dispositivo.
 */
object Lembrete {

    const val CANAL = "revisao"
    const val EXTRA_ABRIR_JOGO = "abrir_jogo"

    /** A palavra que a notificação anunciou — é essa que o jogo tem de fazer. */
    const val EXTRA_LEMA = "lema"
    private const val TRABALHO = "lembrete-diario"
    private const val ETIQUETA = "lembretes"
    private const val ID_NOTIFICACAO = 1

    /**
     * (Re)agenda os lembretes do dia. Chamar sempre que a preferência mudar.
     *
     * Um trabalho periódico por hora, cada um de 24 em 24 horas e com o seu
     * próprio atraso inicial. É mais simples e mais robusto do que um
     * trabalho único que se reagenda a si próprio depois de cada disparo:
     * se o sistema matar um, os outros continuam, e reconfigurar é só voltar
     * a chamar isto.
     *
     * Cancelam-se as horas que deixam de existir, senão ficavam a tocar ao
     * lado das novas. As que se mantêm são atualizadas e não canceladas:
     * cancelar tudo antes de agendar podia matar o próprio lembrete que
     * estivesse a correr nesse momento (o reagendamento ao arrancar pode
     * acontecer dentro do processo aberto pelo lembrete).
     */
    fun agendar(context: Context, quantos: Int, inicio: Int, fim: Int) {
        val gestor = WorkManager.getInstance(context)
        val horas = horasDosLembretes(quantos, inicio, fim)
        (0..23).filter { it !in horas }.forEach { gestor.cancelUniqueWork("$TRABALHO-$it") }

        val restricoes = Constraints.Builder()
            // Só quando a bateria não está em apuros. Um jogo de vocabulário
            // não vale a última percentagem de ninguém.
            .setRequiresBatteryNotLow(true)
            .build()

        horas.forEach { hora ->
            val pedido = PeriodicWorkRequestBuilder<LembreteWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(minutosAte(hora), TimeUnit.MINUTES)
                .setConstraints(restricoes)
                .addTag(ETIQUETA)
                .build()
            gestor.enqueueUniquePeriodicWork(
                "$TRABALHO-$hora", ExistingPeriodicWorkPolicy.UPDATE, pedido
            )
        }
        preferenciasInternas(context).edit()
            .putString(CHAVE_AGENDADO, assinatura(quantos, inicio, fim)).apply()
    }

    fun cancelar(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(ETIQUETA)
        preferenciasInternas(context).edit().remove(CHAVE_AGENDADO).apply()
    }

    /**
     * Garante que o que está agendado corresponde às preferências. Chamar ao
     * arrancar a app.
     *
     * Só as Definições agendavam. Quem ligou os lembretes com a predefinição
     * antiga (1 por dia, às 21h) e nunca mexeu no número ficava com esse
     * agendamento para sempre, enquanto o ecrã — que lê as preferências, com a
     * predefinição nova — dizia "9h, 13h, 17h, 21h". Compara-se a assinatura do
     * que foi agendado com a das preferências e reagenda-se só se divergirem:
     * reagendar a cada arranque reiniciaria os atrasos sem necessidade.
     */
    fun garantirAgendamento(context: Context, ligado: Boolean, quantos: Int, inicio: Int, fim: Int) {
        if (!ligado) return
        val agendado = preferenciasInternas(context).getString(CHAVE_AGENDADO, null)
        if (agendado != assinatura(quantos, inicio, fim)) {
            agendar(context, quantos, inicio, fim)
        }
    }

    private fun assinatura(quantos: Int, inicio: Int, fim: Int) =
        horasDosLembretes(quantos, inicio, fim).joinToString(",")

    private fun preferenciasInternas(context: Context) =
        context.getSharedPreferences("palavrame", Context.MODE_PRIVATE)

    private const val CHAVE_AGENDADO = "lembretes_agendados"

    fun criarCanal(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val canal = NotificationChannel(
            CANAL, "Revisão diária", NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Palavras da tua coleção, nas horas que escolheste."
        }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(canal)
    }

    fun temPermissao(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    fun mostrar(context: Context, lemma: String) {
        if (!temPermissao(context)) return
        criarCanal(context)

        val abrir = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_ABRIR_JOGO, true)
            // A palavra viaja com o *intent*. Sem isto, a app voltava a
            // escolher ao abrir, e bastava registares uma palavra nova entre
            // a notificação e o toque para o jogo perguntar outra coisa — as
            // palavras acabadas de registar são as primeiras da fila.
            putExtra(EXTRA_LEMA, lemma)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendente = PendingIntent.getActivity(
            context, 0, abrir,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notificacao = NotificationCompat.Builder(context, CANAL)
            // A palavra aparece; a definição não. Se a notificação
            // respondesse à pergunta, não valia a pena abrir a app.
            .setContentTitle("Ainda te lembras de \"$lemma\"?")
            .setContentText("Uma palavra da tua coleção, à espera.")
            .setSmallIcon(R.drawable.ic_notificacao)
            .setContentIntent(pendente)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(ID_NOTIFICACAO, notificacao)
    }
}

/**
 * Corre uma vez por dia e decide se há alguma coisa a dizer.
 *
 * Toda a decisão está na `utilizador.db`: se não há palavras vencidas, não há
 * notificação. O dicionário nem sequer é aberto.
 */
class LembreteWorker(
    context: Context,
    parametros: WorkerParameters,
) : CoroutineWorker(context, parametros) {

    override suspend fun doWork(): Result {
        // O dicionário é preciso para saber se a palavra é **jogável** — sem
        // isso, o lembrete anunciava palavras que o jogo depois descartava.
        // Ver `escolherPalavra`.
        val app = applicationContext as? PalavrameApp
        // Num processo iniciado só para o worker, o dicionário pode ainda não
        // estar aberto. Sem ele, NENHUMA palavra conta como jogável e o
        // lembrete ficava mudo mesmo havendo palavras vencidas. Abre-se aqui.
        if (app != null && app.dicionario == null) app.abrirDicionario()

        val palavra = escolherPalavra(
            dicionario = app?.dicionario,
            utilizador = UtilizadorDb.obter(applicationContext),
            agora = System.currentTimeMillis(),
        ) ?: return Result.success()

        Lembrete.mostrar(applicationContext, palavra.lemma)
        return Result.success()
    }
}
