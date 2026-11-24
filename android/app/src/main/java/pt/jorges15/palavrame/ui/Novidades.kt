package pt.jorges15.palavrame.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import pt.jorges15.palavrame.ui.theme.Cores
import pt.jorges15.palavrame.ui.theme.Estilo

/*
 * A nota de novidades: aparece uma vez, ao abrir a app depois de uma
 * atualização, e só nas versões que têm alguma coisa a contar. Correções
 * pequenas não merecem aviso; essas ficam no CHANGELOG.
 *
 * Para uma versão nova: acrescentar uma entrada a NOVIDADES com o
 * `versionCode` do build.gradle.kts. Três ou quatro pontos, curtos.
 *
 * Indexa-se pelo código e não pelo nome porque nem todas as versões se
 * publicam: quem salta da 1.19 para a 1.20.1 tem de ver as notas da 1.20.0.
 * Mostra-se UMA nota: a da versão mais recente que a pessoa ainda não viu
 * (`notasPorVer`). Por isso, quando uma publicação junta versões que não
 * saíram, a nota dela tem de contar tudo: a 1.30 conta a 1.22 e a 1.23, que
 * quem vem da 1.21.1 nunca viu.
 */

/** Para onde leva o botão da nota, quando a novidade tem um sítio na app. */
enum class AcaoNovidade(val rotulo: String) {
    ASPETO("Ver o Aspeto"),
    TUTORIAL("Ver como funciona"),
}

class Novidades(val itens: List<String>, val acao: AcaoNovidade? = null)

/** As notas por `versionCode`. Só as versões com algo a contar. */
val NOVIDADES: Map<Int, Novidades> = mapOf(
    39 to Novidades(          // 1.31.0 (o 38 é a 1.30.1)
        itens = listOf(
            "No ReadEra e noutros leitores, a Palavra-me aparece em \"procurar no dicionário\".",
            "Exporta as tuas palavras para o Anki, com o significado, a frase e o livro. Em Definições, As minhas palavras.",
        ),
    ),
    37 to Novidades(          // 1.30.0 (junta a 1.22 e a 1.23, que não saíram)
        itens = listOf(
            "Novo separador Livros: o que estás a ler e a página onde vais, os lidos por ano e as datas de cada leitura.",
            "Dicionário de inglês, com as definições em inglês. Escolhe PT ou EN na caixa de pesquisa.",
            "Uma definição errada, ou falta um sentido? \"Corrigir ou acrescentar\", por baixo das definições. Fica para ti, e podes submetê-lo ao dicionário.",
            "Na revisão, podes escrever a palavra a partir da definição. Liga-o em Definições, Rever.",
        ),
        acao = AcaoNovidade.TUTORIAL,
    ),
    36 to Novidades(          // 1.23.0
        itens = listOf(
            "Dicionário de inglês: palavras inglesas, com as definições em inglês.",
            "Ao acrescentar um livro, escolhes a língua. A pesquisa abre na língua do livro que estás a ler.",
            "Na pesquisa, PT ou EN com um toque. Se a palavra estiver no outro dicionário, a app diz.",
            "As palavras inglesas entram na coleção e na revisão, sempre contra palavras da mesma língua.",
        ),
    ),
    35 to Novidades(          // 1.22.0 (o 34 é a 1.21.1, só correções)
        itens = listOf(
            "Novo separador Livros: o que estás a ler, os lidos por ano, as datas de cada leitura, uma nota e um resumo, que se partilha como imagem.",
            "Novo na revisão: escrever a palavra a partir da definição. Liga-o em Definições, Rever.",
            "Podes esconder a palavra nos lembretes, em Definições. No ecrã bloqueado fica sempre escondida.",
            "Uma definição errada ou antiquada? Corrige-a por baixo das definições. E \"crèdor\" e afins passam à grafia de hoje.",
        ),
        acao = AcaoNovidade.TUTORIAL,
    ),
    33 to Novidades(          // 1.21.0
        itens = listOf(
            "Cada significado tem a sua classe: \"computador\" é substantivo, e os significados aparecem agrupados.",
            "\"Ver ação\" leva-te à palavra com um toque, e mostra logo a definição.",
            "Novas definições e menos palavras repetidas na pesquisa.",
            "O dicionário ocupa menos espaço no telemóvel.",
        ),
    ),
    31 to Novidades(          // 1.20.0
        itens = listOf(
            "Revê à mão no ecrã inicial, quando há palavras por rever: até 10 por dia, além dos lembretes.",
            "Depois de um lembrete, podes continuar com \"Rever mais\".",
            "As palavras antigas já não ficam para trás das novas.",
            "Os lembretes voltam sempre à hora que escolheste.",
        ),
    ),
    29 to Novidades(          // 1.19.0
        itens = listOf(
            "Escolhe as cores da app: cinco paletas, cada uma em claro e escuro.",
            "Escolhe a letra: Literata, Figtree, Atkinson Hyperlegible ou a do telemóvel.",
            "As imagens de partilha seguem as cores e a letra que escolheres.",
            "Está tudo em Definições, Aspeto.",
        ),
        acao = AcaoNovidade.ASPETO,
    ),
)

/**
 * Mostra-se a nota? Só depois de uma atualização (nunca na primeira
 * instalação, onde não há nada de "novo") e só se esta versão ainda não foi
 * vista. A existência de notas para a versão decide-se à parte.
 */
fun mostrarNovidades(versaoAtual: Int, versaoVista: Int, foiAtualizacao: Boolean): Boolean =
    foiAtualizacao && versaoVista < versaoAtual

/**
 * O código das notas a mostrar: o da versão mais recente com notas, entre a
 * última vista (exclusive) e a atual (inclusive). Nulo se não houver nenhuma.
 */
fun notasPorVer(
    versaoAtual: Int,
    versaoVista: Int,
    notas: Map<Int, Novidades> = NOVIDADES,
): Int? = notas.keys.filter { it in (versaoVista + 1)..versaoAtual }.maxOrNull()

@Composable
fun DialogoNovidades(
    versao: String,
    novidades: Novidades,
    aoFechar: () -> Unit,
    aoAgir: (AcaoNovidade) -> Unit,
) {
    AlertDialog(
        onDismissRequest = aoFechar,
        containerColor = Cores.card,
        title = {
            Column {
                Overline("Novidades", cor = Cores.accentInk)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Palavra-me $versao",
                    style = Estilo.grupoTitulo.copy(fontSize = 22.sp),
                    color = Cores.ink,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                novidades.itens.forEach { item ->
                    Row {
                        Text("•", style = Estilo.texto, color = Cores.accent)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            item,
                            style = Estilo.texto.copy(lineHeight = 1.45.em),
                            color = Cores.ink2,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            val acao = novidades.acao
            if (acao != null) {
                TextButton(
                    onClick = { aoAgir(acao) },
                    colors = ButtonDefaults.textButtonColors(contentColor = Cores.accentInk),
                ) { Text(acao.rotulo, style = Estilo.controloForte) }
            } else {
                TextButton(
                    onClick = aoFechar,
                    colors = ButtonDefaults.textButtonColors(contentColor = Cores.accentInk),
                ) { Text("Fechar", style = Estilo.controloForte) }
            }
        },
        dismissButton = if (novidades.acao != null) {
            {
                TextButton(
                    onClick = aoFechar,
                    colors = ButtonDefaults.textButtonColors(contentColor = Cores.ink2),
                ) { Text("Fechar", style = Estilo.controloForte) }
            }
        } else null,
    )
}
