package pt.jorges15.palavrame.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.jorges15.palavrame.data.Entrada
import pt.jorges15.palavrame.data.rotuloPos
import pt.jorges15.palavrame.jogo.EstadoJogo
import pt.jorges15.palavrame.jogo.JogoViewModel
import pt.jorges15.palavrame.jogo.MINIMO_PARA_JOGAR
import pt.jorges15.palavrame.jogo.Niveis
import pt.jorges15.palavrame.ui.theme.Cores
import pt.jorges15.palavrame.ui.theme.Estilo

/**
 * O ecrã do jogo.
 *
 * Uma palavra, três definições. Depois de responder mostra-se a entrada
 * inteira e, o que mais importa, **o livro onde a palavra foi encontrada**.
 * Não se lembra a definição; lembra-se o momento.
 *
 * Sem sons, sem animações, sem contadores a correr: a troca de pergunta para
 * resposta é instantânea. O valor desta app é a calma, e um jogo diário que
 * grita não se joga duas semanas.
 */
@Composable
fun EcraJogo(vm: JogoViewModel, aoVoltar: () -> Unit) {
    val estado by vm.estado.collectAsState()
    val progresso by vm.progresso.collectAsState(initial = null)

    Scaffold(containerColor = Cores.bg) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            BarraVoltar(
                aoVoltar,
                icone = Icons.Rounded.Close,
                descricao = "Fechar",
                centro = {
                    Text("Rever", style = Estilo.controloForte, color = Cores.ink)
                },
                direita = {
                    progresso?.let {
                        // O nível, e não os pontos: é o número que diz onde se está.
                        Pilula(
                            "Nível ${Niveis.para(it.acertos).numero}",
                            fundo = Cores.sunk,
                            cor = Cores.ink2,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                },
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (val e = estado) {
                    is EstadoJogo.ACarregar -> Centrado {
                        CircularProgressIndicator(color = Cores.accent)
                    }

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
                            + "voltam sozinhas quando for altura. Quanto melhor as "
                            + "souberes, mais tempo demoram.",
                        aoVoltar = aoVoltar,
                    )

                    is EstadoJogo.AJogar -> Pergunta(e, vm, aoVoltar)
                }
            }
        }
    }
}

@Composable
private fun Pergunta(e: EstadoJogo.AJogar, vm: JogoViewModel, aoVoltar: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 28.dp),
    ) {
        if (e.treinoLivre) {
            Text(
                "Treino livre: não há nada a rever hoje, e por isso o "
                    + "calendário não mexe.",
                style = Estilo.ajuda,
                color = Cores.ink3,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }

        Overline("O que quer dizer")
        Spacer(Modifier.height(8.dp))
        TextoMarcado(e.pergunta.lemma, Estilo.lema)
        if (e.pergunta.pos.isNotBlank() && e.pergunta.pos != "desconhecido") {
            Spacer(Modifier.height(6.dp))
            Text(rotuloPos(e.pergunta.pos), style = Estilo.classe, color = Cores.ink2)
        }
        Spacer(Modifier.height(24.dp))

        e.pergunta.opcoes.forEachIndexed { i, texto ->
            Opcao(
                letra = ('A' + i).toString(),
                texto = texto,
                estado = when {
                    e.respondida == null -> EstadoOpcao.POR_RESPONDER
                    i == e.pergunta.indiceCerto -> EstadoOpcao.CERTA
                    i == e.respondida -> EstadoOpcao.ESCOLHIDA_ERRADA
                    else -> EstadoOpcao.DESCARTADA
                },
                // Depois de responder, revela-se de que palavra era cada
                // opção: as distrações deixam de ser definições soltas.
                palavra = if (e.respondida != null) e.pergunta.lemasOpcoes.getOrNull(i) else null,
                aoTocar = { vm.responder(i) },
            )
        }

        if (e.respondida != null) {
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (e.acertou == true) "Certo." else "Era a outra.",
                    style = Estilo.resultado,
                    color = Cores.ink,
                    modifier = Modifier.weight(1f),
                )
                // Só a parte base, o acerto ou o erro. O bónus tem uma linha
                // só dele, para o número grande não juntar duas coisas.
                val base = e.ganho - e.bonus
                Text(
                    if (base >= 0) "+$base" else "−${-base}",
                    style = Estilo.ganho,
                    color = if (e.acertou == true) Cores.ok else Cores.ink2,
                )
            }
            // O bónus separado, para se ver de onde vem: é o que puxa a pessoa
            // a voltar amanhã.
            if (e.bonus > 0) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${e.sequencia} dias seguidos",
                        style = Estilo.controloForte,
                        color = Cores.accentInk,
                        modifier = Modifier.weight(1f),
                    )
                    Text("+${e.bonus}", style = Estilo.controloForte, color = Cores.accentInk)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("${e.total} pontos ao todo", style = Estilo.ajuda, color = Cores.ink2)
            // Subir de nível merece uma linha, e só uma: sem medalhas nem
            // animações, como o resto da app.
            e.subiuDeNivel?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Subiste ao nível ${it.numero}: ${it.nome}.",
                    style = Estilo.grupoTitulo,
                    color = Cores.accentInk,
                )
            }
            DepoisDeResponder(e)
            Spacer(Modifier.height(24.dp))
            // Uma pergunta, e acabou: a sessão tem de caber no intervalo em
            // que se pousa o livro. O encadeamento é do Modo Desenvolvedor,
            // para verificar perguntas sem fechar e reabrir a app.
            if (e.podeSeguir) {
                BotaoPrincipal("Palavra seguinte", vm::comecar, Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = aoVoltar,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = Cores.ink2),
                ) { Text("Feito", style = Estilo.botao) }
            } else {
                BotaoPrincipal("Feito", aoVoltar, Modifier.fillMaxWidth())
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
    Spacer(Modifier.height(18.dp))
    HorizontalDivider(color = Cores.line, thickness = 1.dp)
    Spacer(Modifier.height(18.dp))

    e.entrada?.takeIf { it.acecoes.isNotEmpty() }?.let { entrada ->
        Overline("Todos os significados")
        Spacer(Modifier.height(10.dp))
        Acecoes(entrada)
    }

    if (e.livro != null || e.frase != null) {
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Cores.sunk)
                .padding(16.dp),
        ) {
            // A capa do livro, como na estante do ecrã inicial: com o título e
            // o autor, e não só a cor, que sozinha não diz de que livro é.
            e.livro?.let { livro ->
                CapaLivro(
                    livro, e.autor,
                    largura = 84.dp, altura = 116.dp,
                    compacta = true,
                )
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Overline("Onde a encontraste", cor = Cores.accentInk)
                e.frase?.let {
                    Spacer(Modifier.height(6.dp))
                    Text("\"$it\"", style = Estilo.citacao, color = Cores.ink)
                }
                // O título pode ficar cortado na capa pequena, por isso repete-se
                // aqui por extenso, com o autor e a página.
                val origem = listOfNotNull(
                    e.livro,
                    e.autor?.takeIf { it.isNotBlank() },
                    e.pagina?.let { "p. $it" },
                )
                if (origem.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(origem.joinToString(" · "), style = Estilo.meta, color = Cores.ink2)
                }
            }
        }
    }
}

@Composable
private fun Acecoes(entrada: Entrada) {
    entrada.acecoes.take(6).forEachIndexed { i, acecao ->
        Row(Modifier.padding(bottom = 10.dp)) {
            Text(
                "${i + 1}",
                style = Estilo.numeroAcecao.copy(fontSize = 20.sp),
                color = Cores.accent,
                modifier = Modifier.width(24.dp),
            )
            Spacer(Modifier.width(10.dp))
            // Sem a fonte: aqui a pessoa está a rever uma palavra, não a
            // auditar o dicionário. A atribuição cumpre-se no ecrã de licenças.
            Text(
                acecao.definicao,
                style = Estilo.acecao.copy(fontSize = 16.sp),
                color = Cores.ink,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private enum class EstadoOpcao { POR_RESPONDER, CERTA, ESCOLHIDA_ERRADA, DESCARTADA }

@Composable
private fun Opcao(
    letra: String,
    texto: String,
    estado: EstadoOpcao,
    palavra: String?,
    aoTocar: () -> Unit,
) {
    val forma = RoundedCornerShape(18.dp)
    val fundo = when (estado) {
        EstadoOpcao.CERTA -> Cores.okSoft
        EstadoOpcao.ESCOLHIDA_ERRADA -> Cores.errSoft
        else -> Cores.card
    }
    val (espessura, borda) = when (estado) {
        EstadoOpcao.CERTA -> 2.dp to Cores.ok
        EstadoOpcao.ESCOLHIDA_ERRADA -> 2.dp to Cores.err
        else -> 1.5.dp to Cores.line
    }
    val corTexto = if (estado == EstadoOpcao.DESCARTADA) Cores.ink2 else Cores.ink
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(forma)
            .background(fundo)
            .border(espessura, borda, forma)
            .clickable(enabled = estado == EstadoOpcao.POR_RESPONDER, onClick = aoTocar)
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Distintivo(letra, estado)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(texto, style = Estilo.opcao, color = corTexto)
            // A palavra que esta opção define, revelada depois de responder.
            if (!palavra.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    palavra,
                    style = Estilo.rotulo.copy(fontSize = 13.sp),
                    color = Cores.accentInk,
                )
            }
        }
    }
}

/** O círculo com a letra; depois de responder, ✓ na certa e ✕ na errada. */
@Composable
private fun Distintivo(letra: String, estado: EstadoOpcao) {
    val (fundo, contorno, conteudo) = when (estado) {
        EstadoOpcao.CERTA -> Triple(Cores.ok, Cores.ok, Cores.okSoft)
        EstadoOpcao.ESCOLHIDA_ERRADA -> Triple(Color.Transparent, Cores.err, Cores.err)
        else -> Triple(Color.Transparent, Cores.line, Cores.ink2)
    }
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(fundo)
            .border(1.5.dp, contorno, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        when (estado) {
            EstadoOpcao.CERTA ->
                Icon(Icons.Rounded.Check, "certa", tint = conteudo, modifier = Modifier.size(18.dp))
            EstadoOpcao.ESCOLHIDA_ERRADA ->
                Icon(Icons.Rounded.Close, "errada", tint = conteudo, modifier = Modifier.size(16.dp))
            else -> Text(letra, style = Estilo.rotulo.copy(fontSize = 13.sp), color = conteudo)
        }
    }
}

@Composable
private fun Explicacao(titulo: String, texto: String, aoVoltar: () -> Unit) {
    Centrado {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(titulo, style = Estilo.tituloEcra, color = Cores.ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(
                texto,
                style = Estilo.texto.copy(lineHeight = 22.sp),
                color = Cores.ink2,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            BotaoPrincipal("Voltar", aoVoltar)
        }
    }
}

@Composable
private fun Centrado(conteudo: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { conteudo() }
}
