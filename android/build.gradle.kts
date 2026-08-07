// Versões escolhidas para serem coerentes entre si e com o Gradle 9.3 que
// o Android Studio instala. A combinação anterior (AGP 8.5.2, de 2024) nunca
// foi testada com o Gradle 9 — e suprimir o aviso, como cheguei a fazer, é
// esconder um problema real em vez de o resolver.
//
// Se alguma destas versões não resolver, o Android Studio tem o caminho
// certo: Tools -> AGP Upgrade Assistant, que escolhe versões garantidamente
// compatíveis com a versão do Studio instalada.
plugins {
    id("com.android.application") version "9.2.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
    id("com.google.devtools.ksp") version "2.2.21-2.0.5" apply false
}
