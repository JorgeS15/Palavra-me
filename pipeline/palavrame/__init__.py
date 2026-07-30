"""Pipeline de construção do dicionário da app Palavra-me.

O pipeline corre offline, na máquina do programador, e produz um ficheiro
`dicionario-vN.db` (SQLite + FTS5) que a app embarca em modo só-leitura.

Regra estrutural: só os módulos em `palavrame.sources` tocam na rede. Todo o
resto do pipeline lê do cache local. Ver `tests/test_no_network.py`.
"""

__version__ = "0.1.1"

# Versão do esquema de `dicionario.db`. Incrementa quando o esquema muda de
# forma que a app precise de saber. Vai para a tabela `meta`.
SCHEMA_VERSION = 1
