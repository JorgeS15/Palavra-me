package pt.jorges15.palavrame.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pt.jorges15.palavrame.data.Entrada
import pt.jorges15.palavrame.jogo.EstadoJogo
import pt.jorges15.palavrame.jogo.JogoViewModel
import pt.jorges15.palavrame.jogo.MINIMO_PARA_JOGAR

/**
 * O ecrã do jogo.
 *
 * Uma palavra, três definições. Depois de responder mostra-se a entrada
 * inteira e — o que mais importa — **o livro onde a palavra foi encontrada**.
 * Não se lembra a definição; lembra-se o momento.
 *
 * Sem sons, sem animações, sem contadores a correr. O valor desta app é a
 * calma, e um jogo diário que grita não se joga duas semanas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcraJogo(vm: JogoViewModel, aoVoltar: () -> Unit) {
    val estado by vm.estado.collectAsState()
    val progresso by vm.progresso.collectAsState(initial = null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rever") },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                    }
                },
                actions = {
                    progresso?.let {
                        Text(
                            "${it.pontos} pontos",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                },
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val e = estado) {
                is EstadoJogo.ACarregar -> Centrado { CircularProgressIndicator() }

                is EstadoJogo.PoucasPalavras -> Explicacao(
                    titulo = "Ainda não dá",
                    texto = "O jogo precisa de pelo menos $MINIMO_PARA_JOGAR palavras "
                        + "com definição: uma para a pergunta e duas para as opções "
                        + "erradas. Tens ${e.jogaveis}.\n\nRegista mais umas quantas "
                        + "à medida que lês e volta cá.",
                    aoVoltar = aoVoltar,
                )

                is EstadoJogo.SemPerguntaPossivel -> Explicacao(
                    titulo = "Hoje não",
                    texto = "Não se conseguiu formar nenhuma pergunta honesta com as "
                        + "palavras que tens. Mais vale não perguntar do que fazer "
                        + "uma pergunta que se acerta por eliminação.",
                    aoVoltar = aoVoltar,
                )

                is EstadoJogo.NadaARever -> Explicacao(
                    titulo = "Nada a rever",
                    texto = "Já reviste tudo o que estava por rever. As palavras "
                        + "voltam sozinhas quando for altura — quanto melhor as "
                        + "souberes, mais tempo demoram.",
                    aoVoltar = aoVoltar,
                )

                is EstadoJogo.AJogar -> Pergunta(e, vm, aoVoltar)
            }
        }
    }
}

@Composable
private fun Pergunta(e: EstadoJogo.AJogar, vm: JogoViewModel, aoVoltar: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(24.dp, 8.dp, 24.dp, 32.dp),
    ) {
        if (e.treinoLivre) {
            Text(
                "Treino livre — não há nada a rever hoje, e por isso o "
                    + "calendário não mexe.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }

        Text(
            e.pergunta.lemma,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (e.pergunta.pos.isNotBlank() && e.pergunta.pos != "desconhecido") {
            Text(
                e.pergunta.pos,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Spacer(Modifier.height(28.dp))

        e.pergunta.opcoes.forEachIndexed { i, texto ->
            Opcao(
                texto = texto,
                estado = when {
                    e.respondida == null -> EstadoOpcao.POR_RESPONDER
                    i == e.pergunta.indiceCerto -> EstadoOpcao.CERTA
                    i == e.respondida -> EstadoOpcao.ESCOLHIDA_ERRADA
                    else -> EstadoOpcao.DESCARTADA
                },
                aoTocar = { vm.responder(i) },
            )
        }

        if (e.respondida != null) {
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (e.acertou == true) "Certo." else "Era a outra.",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (e.ganho >= 0) "+${e.ganho}" else "${e.ganho}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (e.acertou == true) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                )
            }
            Text(
                buildString {
                    append("${e.total} pontos")
                    if (e.sequencia > 1) append(" · ${e.sequencia} dias seguidos")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            DepoisDeResponder(e)
            Spacer(Modifier.height(28.dp))
            // Uma pergunta, e acabou: a sessão tem de caber no intervalo em
            // que se pousa o livro. O encadeamento é do Modo Desenvolvedor,
            // para verificar perguntas sem fechar e reabrir a app.
            if (e.podeSeguir) {
                Button(onClick = vm::comecar, modifier = Modifier.fillMaxWidth()) {
                    Text("Palavra seguinte")
                }
                TextButton(
                    onClick = aoVoltar,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Feito") }
            } else {
                Button(onClick = aoVoltar, modifier = Modifier.fillMaxWidth()) {
                    Text("Feito")
                }
            }
        }
    }
}

/**
 * O que se mostra depois de responder: a entrada inteira e a origem.
 *
 * A ordem é deliberada. Primeiro as aceções todas, porque a pergunta só usou
 * uma e as outras são metade da palavra. Depois o livro e a frase, que são o
 * que faz a palavra ficar.
 */
@Composable
private fun DepoisDeResponder(e: EstadoJogo.AJogar) {
    Spacer(Modifier.height(12.dp))
    HorizontalDivider()
    Spacer(Modifier.height(16.dp))

    e.entrada?.let { entrada -> Acecoes(entrada) }

    if (e.livro != null || e.frase != null) {
        Spacer(Modifier.height(16.dp))
        Text(
            "Onde a encontraste",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        e.frase?.let {
            Text(
                "«$it»",
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
            )
        }
        e.livro?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun Acecoes(entrada: Entrada) {
    entrada.acecoes.take(6).forEachIndexed { i, acecao ->
        Row(Modifier.padding(bottom = 6.dp)) {
            Text(
                "${i + 1}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.width(24.dp),
            )
            // Sem a fonte: aqui a pessoa está a rever uma palavra, não a
            // auditar o dicionário. A atribuição das obras cumpre-se no
            // ecrã "Fontes e licenças".
            Text(acecao.definicao, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private enum class EstadoOpcao { POR_RESPONDER, CERTA, ESCOLHIDA_ERRADA, DESCARTADA }

@Composable
private fun Opcao(texto: String, estado: EstadoOpcao, aoTocar: () -> Unit) {
    val esquema = MaterialTheme.colorScheme
    val contorno = when (estado) {
        EstadoOpcao.CERTA -> BorderStroke(2.dp, esquema.primary)
        EstadoOpcao.ESCOLHIDA_ERRADA -> BorderStroke(2.dp, esquema.error)
        else -> BorderStroke(1.dp, esquema.outlineVariant)
    }
    val cor = when (estado) {
        EstadoOpcao.DESCARTADA -> esquema.outline
        else -> esquema.onSurface
    }
    OutlinedCard(
        onClick = aoTocar,
        enabled = estado == EstadoOpcao.POR_RESPONDER,
        border = contorno,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    ) {
        Text(
            texto,
            style = MaterialTheme.typography.bodyLarge,
            color = cor,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun Explicacao(titulo: String, texto: String, aoVoltar: () -> Unit) {
    Centrado {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(titulo, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            Text(
                texto,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = aoVoltar) { Text("Voltar") }
        }
    }
}

@Composable
private fun Centrado(conteudo: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { conteudo() }
}
