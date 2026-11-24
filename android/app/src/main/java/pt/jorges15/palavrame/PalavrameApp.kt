package pt.jorges15.palavrame

import android.app.Application
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pt.jorges15.palavrame.data.Dicionario
import pt.jorges15.palavrame.data.Lingua
import pt.jorges15.palavrame.data.Preferencias
import pt.jorges15.palavrame.data.UtilizadorDb
import pt.jorges15.palavrame.jogo.Lembrete
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream

class PalavrameApp : Application() {

    /**
     * Onde vive o dicionário de cada língua. Fica na pasta privada da app e
     * não nos assets porque a base tem 200 MB e porque o plano quer poder
     * **substituí-la inteira** numa atualização, sem reinstalar a app.
     *
     * O português mantém o nome de sempre (`dicionario.db`), para as
     * instalações que já o têm; o inglês fica ao lado, `dicionario-en.db`.
     */
    fun ficheiroDe(lingua: String): File =
        File(getExternalFilesDir(null) ?: filesDir, Dicionario.nomeDoFicheiro(lingua))

    val ficheiroDicionario: File get() = ficheiroDe(Lingua.PT)

    /**
     * Não é `by lazy` porque uma base pode ser instalada ou trocada com a app
     * já a correr — é exatamente o que o botão de importar faz.
     */
    private val dicionarios = ConcurrentHashMap<String, Dicionario>()

    /** O dicionário português, que é o de sempre e o que a app precisa para arrancar. */
    val dicionario: Dicionario? get() = dicionarios[Lingua.PT]

    /** O dicionário de uma língua, se estiver instalado. */
    fun dicionario(lingua: String): Dicionario? = dicionarios[Lingua.deCodigo(lingua)]

    /** As línguas com dicionário instalado, pela ordem de `Lingua.todas`. */
    fun linguasInstaladas(): List<String> = Lingua.todas.filter { dicionarios.containsKey(it) }

    val utilizador: UtilizadorDb by lazy { UtilizadorDb.obter(this) }

    override fun onCreate() {
        super.onCreate()
        // Uma cópia interrompida (app morta a meio, telemóvel desligado)
        // deixava aqui um ficheiro temporário de centenas de MB, para sempre.
        // Ao arrancar não há instalação nenhuma a correr: pode ir.
        Lingua.todas.forEach { temporarioDe(ficheiroDe(it)).delete() }
        temporarioDe(File(ficheiroDicionario.parentFile, "importado.db")).delete()
        Lingua.todas.forEach { abrirDicionario(it) }
        // Repõe o agendamento dos lembretes se não corresponder às
        // preferências (ver `Lembrete.garantirAgendamento`).
        val pref = Preferencias(this)
        Lembrete.garantirAgendamento(
            this,
            ligado = pref.jogoLigado.value,
            quantos = pref.quantosPorDia.value,
            inicio = pref.inicioDaJanela.value,
            fim = pref.fimDaJanela.value,
        )
    }

    private val prefs by lazy {
        getSharedPreferences("palavrame", MODE_PRIVATE)
    }

    /**
     * A marca de versão do dicionário que veio no APK, se houver.
     *
     * Abre o ficheiro diretamente em vez de perguntar ao `assets.list("")`.
     * A listagem é frágil — não garante ver tudo o que está empacotado — e
     * usá-la para decidir custou-nos várias compilações em que a app jurava
     * não ter dicionário nenhum com o dicionário lá dentro.
     */
    private fun versaoEmpacotada(lingua: String): String? = try {
        assets.open(assetVersao(lingua)).bufferedReader().use { it.readText().trim() }
            .takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        if (lingua == Lingua.PT) ultimoErroAsset = "${e.javaClass.simpleName}: ${e.message}"
        null
    }

    /** Para o diálogo de diagnóstico dizer *porquê*, e não só que não há. */
    var ultimoErroAsset: String? = null
        private set

    /** O que está mesmo empacotado, para diagnóstico. */
    fun listaDeAssets(): String = try {
        assets.list("").orEmpty().joinToString(", ").ifEmpty { "(vazio)" }
    } catch (e: Exception) {
        "erro: ${e.message}"
    }

    /** Há um dicionário dentro do APK, independentemente do que está instalado? */
    fun temDicionarioEmpacotadoNoApk(lingua: String = Lingua.PT): Boolean =
        versaoEmpacotada(lingua) != null

    /**
     * Há um dicionário no APK **por instalar**?
     *
     * Verdadeiro em duas situações: não há base nenhuma instalada, ou a que
     * veio no APK é diferente da que está instalada. A segunda é a que
     * importa na prática — sem ela, uma reconstrução do dicionário nunca
     * chegava a quem já tinha a app a funcionar.
     */
    fun temDicionarioEmpacotado(lingua: String = Lingua.PT): Boolean {
        val empacotada = versaoEmpacotada(lingua) ?: return false
        return dicionario(lingua) == null || prefs.getString(chaveVersao(lingua), null) != empacotada
    }

    /** As línguas com um dicionário no APK por instalar (o português primeiro). */
    fun linguasPorInstalar(): List<String> = Lingua.todas.filter { temDicionarioEmpacotado(it) }

    /**
     * Instala o dicionário que veio dentro do APK.
     *
     * Vai comprimido (200 MB passam a ~60) e descomprime-se aqui, uma vez.
     * É o que permite a app funcionar mal se instala, sem passo manual.
     * A alternativa — abrir o SQLite a partir do asset — não existe: o
     * SQLite precisa de um ficheiro real no sistema de ficheiros.
     */
    suspend fun instalarDicionarioEmpacotado(lingua: String = Lingua.PT): ResultadoImportacao =
        withContext(Dispatchers.IO) {
            val destino = ficheiroDe(lingua)
            val temporario = temporarioDe(destino)
            // Antes de copiar, ver se cabe. A cópia nova vive ao lado da
            // antiga até à troca, por isso é preciso o tamanho inteiro livre;
            // descobri-lo a meio, com o disco cheio, era pior para todos.
            faltaDeEspaco(destino, lingua)?.let { return@withContext ResultadoImportacao.Erro(it) }
            try {
                abrirAssetDicionario(lingua).use { entrada ->
                    temporario.outputStream().use { saida ->
                        entrada.copyTo(saida, 1 shl 20)
                    }
                }
                trocar(temporario, lingua).also { r ->
                    if (r is ResultadoImportacao.Feito) {
                        prefs.edit().putString(chaveVersao(lingua), versaoEmpacotada(lingua)).apply()
                    }
                }
            } catch (e: Exception) {
                temporario.delete()
                // Um FileNotFoundException do AssetManager traz só o nome do
                // ficheiro como mensagem, o que sozinho não explica nada.
                // Dizer o que ESTÁ empacotado poupa uma tarde de suposições.
                ResultadoImportacao.Erro(
                    when {
                        e is java.io.FileNotFoundException ->
                            "O APK não traz o ${assetDicionario(lingua)}.\n\nAssets: ${listaDeAssets()}"
                        eFaltaDeEspaco(e) -> mensagemDeEspaco(destino, lingua)
                        else -> e.message ?: "Falhou a instalação."
                    }
                )
            }
        }

    /**
     * Abre o dicionário empacotado, seja qual for o nome e o formato.
     *
     * O AGP trata os assets terminados em `.gz` de forma especial: guarda-os
     * já descomprimidos e **tira-lhes a extensão**. Foi isto — e nada de
     * ownCloud, versões ou compilações incrementais — que fez a app jurar
     * durante uma tarde que o APK não trazia dicionário: trazia, com outro
     * nome. Aqui aceitam-se os dois nomes e detecta-se o formato pelos bytes
     * mágicos, que é a única coisa que não depende de convenções alheias.
     */
    private fun abrirAssetDicionario(lingua: String): java.io.InputStream {
        val nomes = nomesDoAsset(lingua)
        val bruto = nomes.firstNotNullOfOrNull { nome ->
            try {
                assets.open(nome)
            } catch (e: java.io.FileNotFoundException) {
                null
            }
        } ?: throw java.io.FileNotFoundException(
            "Nenhum de ${nomes.joinToString(" ou ")} está no APK."
        )

        val comMarca = java.io.BufferedInputStream(bruto, 1 shl 16)
        comMarca.mark(2)
        val gzip = comMarca.read() == 0x1f && comMarca.read() == 0x8b
        comMarca.reset()
        return if (gzip) GZIPInputStream(comMarca, 1 shl 16) else comMarca
    }

    fun abrirDicionario(lingua: String = Lingua.PT): Boolean {
        dicionarios.remove(lingua)?.fechar()
        val aberto = Dicionario.abrir(ficheiroDe(lingua)) ?: return false
        dicionarios[lingua] = aberto
        return true
    }

    /**
     * Copia para dentro da app a base escolhida no seletor de ficheiros.
     *
     * A língua sai da própria base (`meta.lingua`): importar o dicionário de
     * inglês instala-o ao lado do português, sem tocar neste.
     *
     * Escreve primeiro para um ficheiro temporário e só troca no fim: se a
     * cópia falhar a meio — bateria, espaço, cabo — a base que já lá estava
     * continua boa. Vale a pena o cuidado num ficheiro de 200 MB.
     */
    suspend fun importarDicionario(origem: Uri): ResultadoImportacao =
        withContext(Dispatchers.IO) {
            val temporario = temporarioDe(File(ficheiroDicionario.parentFile, "importado.db"))
            try {
                contentResolver.openInputStream(origem)?.use { entrada ->
                    temporario.outputStream().use { saida -> entrada.copyTo(saida, 1 shl 20) }
                } ?: return@withContext ResultadoImportacao.Erro(
                    "Não consegui abrir o ficheiro escolhido."
                )

                trocar(temporario, lingua = null)
            } catch (e: Exception) {
                temporario.delete()
                ResultadoImportacao.Erro(
                    if (eFaltaDeEspaco(e)) mensagemDeEspaco(ficheiroDicionario, Lingua.PT)
                    else e.message ?: "Falhou a cópia."
                )
            }
        }

    /**
     * Valida o candidato e só depois substitui o que lá está.
     *
     * `lingua` nula: é a da própria base (o caso da importação). Um ficheiro
     * que diz ser de outra língua do que a pedida não entra.
     *
     * Se a cópia falhou a meio (bateria, espaço, cabo), a base anterior
     * continua boa. Vale o cuidado num ficheiro de centenas de MB.
     *
     * A troca é **atómica**: um `rename` por cima do ficheiro antigo, que o
     * sistema faz de uma vez. Antes apagava-se a base antiga e só depois se
     * mudava o nome da nova; se a mudança de nome falhasse, ficava sem base
     * nenhuma. Agora, ou fica a nova, ou fica a antiga, sempre uma das duas.
     */
    private fun trocar(temporario: File, lingua: String?): ResultadoImportacao {
        val candidato = Dicionario.abrir(temporario)
        val versao = candidato?.versao
        val daBase = candidato?.lingua
        candidato?.fechar()
        if (versao == null || daBase == null) {
            temporario.delete()
            return ResultadoImportacao.Erro(
                "Esse ficheiro não parece um dicionário do Palavra-me."
            )
        }
        if (lingua != null && lingua != daBase) {
            temporario.delete()
            return ResultadoImportacao.Erro("Esse dicionário é de outra língua.")
        }
        val destino = ficheiroDe(daBase)
        dicionarios.remove(daBase)?.fechar()
        try {
            Files.move(
                temporario.toPath(), destino.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (e: Exception) {
            temporario.delete()
            // A antiga ficou onde estava: volta a abrir-se, para a app seguir.
            abrirDicionario(daBase)
            return ResultadoImportacao.Erro("Não consegui guardar a base nova.")
        }
        return if (abrirDicionario(daBase)) ResultadoImportacao.Feito(versao, daBase)
        else ResultadoImportacao.Erro("A base foi copiada mas não abriu.")
    }

    private fun temporarioDe(destino: File) = File(destino.parentFile, "${destino.name}.a-copiar")

    // --- espaço ---------------------------------------------------------------

    /** Folga além do tamanho da base: o SQLite e o sistema precisam de algum. */
    private val FOLGA = 20L shl 20

    /**
     * O tamanho do dicionário depois de descomprimido, lido do próprio APK sem
     * o descomprimir: o gzip guarda-o nos últimos 4 bytes (até 4 GB, que é o
     * que chega aqui). Se o AGP o tiver guardado já descomprimido, é o
     * tamanho do asset. Nulo se não se conseguir saber.
     */
    fun tamanhoDoDicionarioEmpacotado(lingua: String = Lingua.PT): Long? {
        for (nome in nomesDoAsset(lingua)) {
            try {
                assets.openFd(nome).use { fd ->
                    val canal = java.io.FileInputStream(fd.fileDescriptor).channel
                    val cabeca = ByteBuffer.allocate(2)
                    canal.read(cabeca, fd.startOffset)
                    val gzip = cabeca.get(0) == 0x1f.toByte() && cabeca.get(1) == 0x8b.toByte()
                    if (!gzip) return fd.length
                    val fim = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                    canal.read(fim, fd.startOffset + fd.length - 4)
                    return fim.getInt(0).toLong() and 0xFFFFFFFFL
                }
            } catch (e: Exception) {
                // Comprimido dentro do APK (não dá `openFd`) ou inexistente:
                // tenta-se o outro nome.
            }
        }
        return null
    }

    /** O que falta instalar do APK, em MB, para o ecrã de instalação. */
    fun tamanhoPorInstalarMb(): Long? {
        val total = linguasPorInstalar().sumOf { tamanhoDoDicionarioEmpacotado(it) ?: 0L }
        return if (total == 0L) null else (total + (1 shl 20) - 1) shr 20
    }

    /** A mensagem de falta de espaço, ou nula se cabe (ou não se sabe). */
    private fun faltaDeEspaco(destino: File, lingua: String): String? {
        val precisa = (tamanhoDoDicionarioEmpacotado(lingua) ?: return null) + FOLGA
        val livre = destino.parentFile?.usableSpace ?: return null
        return if (livre < precisa) mensagemDeEspaco(destino, lingua, precisa) else null
    }

    private fun mensagemDeEspaco(destino: File, lingua: String, precisa: Long? = null): String {
        val mb = { b: Long -> (b + (1 shl 20) - 1) shr 20 }
        val livre = destino.parentFile?.usableSpace
        val pedido = precisa ?: tamanhoDoDicionarioEmpacotado(lingua)?.plus(FOLGA)
        return buildString {
            append("Falta espaço no telemóvel para atualizar o dicionário")
            if (lingua != Lingua.PT) append(" de ${Lingua.nome(lingua).lowercase()}")
            if (pedido != null && livre != null) {
                append(": são precisos cerca de ${mb(pedido)} MB livres e há ${mb(livre)} MB")
            }
            append(". Liberta algum espaço e volta a abrir a app.")
        }
    }

    /** Um erro de escrita por disco cheio (o Android diz ENOSPC). */
    private fun eFaltaDeEspaco(e: Throwable): Boolean {
        val m = (e.message.orEmpty() + " " + e.cause?.message.orEmpty()).lowercase()
        return "enospc" in m || "no space" in m
    }

    companion object {
        const val ASSET_DICIONARIO = "dicionario.db.gz"
        const val ASSET_VERSAO = "dicionario.versao"
        private const val CHAVE_VERSAO = "versao_dicionario_instalado"

        /** `dicionario.db.gz` para o português; `dicionario-en.db.gz` para o inglês. */
        fun assetDicionario(lingua: String): String =
            if (lingua == Lingua.PT) ASSET_DICIONARIO else "dicionario-$lingua.db.gz"

        fun assetVersao(lingua: String): String =
            if (lingua == Lingua.PT) ASSET_VERSAO else "dicionario-$lingua.versao"

        private fun nomesDoAsset(lingua: String): List<String> =
            assetDicionario(lingua).let { listOf(it, it.removeSuffix(".gz")) }

        private fun chaveVersao(lingua: String): String =
            if (lingua == Lingua.PT) CHAVE_VERSAO else "${CHAVE_VERSAO}_$lingua"
    }
}

sealed interface ResultadoImportacao {
    data class Feito(val versao: String?, val lingua: String = Lingua.PT) : ResultadoImportacao
    data class Erro(val motivo: String) : ResultadoImportacao
}
