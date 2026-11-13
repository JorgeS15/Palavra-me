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
 * Para uma versão nova: acrescentar uma entrada a NOVIDADES, com a chave
 * igual ao `versionName` do build.gradle.kts. Três ou quatro pontos, curtos.
 */

/** Para onde leva o botão da nota, quando a novidade tem um sítio na app. */
enum class AcaoNovidade(val rotulo: String) {
    ASPETO("Ver o Aspeto"),
}

class Novidades(val itens: List<String>, val acao: AcaoNovidade? = null)

/** As notas por versão (`versionName`). Só as versões com algo a contar. */
val NOVIDADES: Map<String, Novidades> = mapOf(
    "1.19.0" to Novidades(
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
