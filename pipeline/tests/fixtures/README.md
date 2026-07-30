# Fixtures — dados sintéticos

**Nada aqui são dados reais.** Estes ficheiros foram escritos à mão para
exercitar os parsers do pipeline e reproduzem apenas o *formato* de cada
fonte, não o seu conteúdo.

Não os uses como dicionário e não os confundas com uma amostra das fontes:
as definições são aproximações escritas para o teste, não citações do
Dicionário Aberto, do Wikcionário ou de qualquer outra obra.

A DB construída a partir daqui sai marcada com `fixtures=1` na tabela `meta`,
justamente para que uma build de teste nunca se confunda com uma build a
sério.

Quando as fontes verdadeiras estiverem em cache (`palavrame fetch`), o formato
real pode divergir do que está aqui — confirmar o formato é o passo 1 da F0
(plano, secção 7). Se divergir, o sítio a corrigir é o `parse()` da fonte, e
estas fixtures devem ser atualizadas para o formato verdadeiro.
