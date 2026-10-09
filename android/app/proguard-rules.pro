# Regras do R8 para a versão de lançamento da Palavra-me.
#
# Quase tudo vem das próprias bibliotecas: o Room, o WorkManager e o Compose
# trazem as suas regras. Aqui ficam só os casos desta app em que algo é
# encontrado pelo nome e o R8 poderia julgar que não está em uso.

# O lembrete é criado pelo WorkManager a partir do nome da classe.
-keep class pt.jorges15.palavrame.jogo.LembreteWorker { <init>(...); }

# O tema escolhido guarda-se pelo nome da constante (SISTEMA, CLARO, ESCURO).
# Se mudasse, quem atualiza perdia a escolha.
-keepclassmembers enum pt.jorges15.palavrame.data.Tema { *; }

# Erros na Play Console com ficheiro e linha. O .aab leva o ficheiro de
# correspondência (mapping) e a Play usa-o para desofuscar sozinha.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
