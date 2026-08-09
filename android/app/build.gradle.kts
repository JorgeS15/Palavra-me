import java.util.Properties
import java.util.zip.ZipFile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

/**
 * Assinatura da versao de lancamento.
 *
 * A chave e as palavras-passe ficam em `keystore.properties`, que NAO e
 * versionado. A chave decide se uma instalacao pode ser atualizada: trocar
 * de chave obriga a desinstalar, e desinstalar leva a coleccao de palavras
 * do utilizador. Por isso vale a pena criar a chave cedo e guarda-la bem.
 *
 * Sem o ficheiro, a build de lancamento cai para a chave de depuracao e
 * avisa — para quem clonar o projeto poder compilar sem ter a chave.
 */
val propriedadesDaChave = rootProject.file("keystore.properties")
val chave = Properties().apply {
    if (propriedadesDaChave.exists()) {
        propriedadesDaChave.inputStream().use { load(it) }
    }
}
val temChave = chave.getProperty("storeFile") != null

android {
    namespace = "pt.jorges15.palavrame"
    compileSdk = 36

    defaultConfig {
        applicationId = "pt.jorges15.palavrame"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "1.8.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (temChave) {
            create("lancamento") {
                storeFile = rootProject.file(chave.getProperty("storeFile"))
                storePassword = chave.getProperty("storePassword")
                keyAlias = chave.getProperty("keyAlias")
                keyPassword = chave.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Sem R8 por agora: o ganho num APK dominado por 60 MB de
            // dicionario e residual, e o risco de partir o Room ou o
            // Compose por reflexao nao compensa enquanto a app for pessoal.
            isMinifyEnabled = false
            if (temChave) {
                signingConfig = signingConfigs.getByName("lancamento")
            } else {
                logger.warn(
                    "  Sem keystore.properties: a build de lancamento vai "
                    + "assinada com a chave de depuracao."
                )
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true   // o rodapé mostra VERSION_NAME
    }

    androidResources {
        // O dicionário já vai gzipado; comprimi-lo outra vez no APK só
        // gastava tempo de build sem ganhar um byte.
        noCompress += "gz"
    }

    packaging {
        // Um asset de ~60 MB precisa disto para o Gradle não o partir.
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    // `kotlinOptions { jvmTarget = ... }` está depreciado desde o Kotlin 2.0;
    // esta é a forma atual, e tipada em vez de string.
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

/**
 * Confere que o dicionário está mesmo nos assets antes de compilar.
 *
 * Existe porque já aconteceu: o `.gz` estava na pasta, o `empacotar` tinha
 * corrido, e mesmo assim o APK saía sem ele — e a app instalava a base
 * antiga sem se queixar. Uma compilação que falha com uma instrução clara
 * vale mais do que uma app que mente sobre o que tem lá dentro.
 */
val verificarDicionario by tasks.registering {
    val asset = layout.projectDirectory.file("src/main/assets/dicionario.db.gz")
    doFirst {
        val f = asset.asFile
        if (!f.exists() || f.length() < 1_000_000) {
            throw GradleException(
                """
                Falta o dicionário nos assets: ${f.path}

                Constrói-o e empacota-o antes de compilar a app:
                    cd pipeline
                    python -m palavrame.cli f1
                    python -m palavrame.cli empacotar --db out/dicionario-1.db
                """.trimIndent()
            )
        }
        logger.lifecycle("  dicionário: ${f.length() / 1_048_576} MB nos assets")
    }
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }
    .configureEach { dependsOn(verificarDicionario) }

/**
 * Confere que o dicionário ficou mesmo **dentro do APK**.
 *
 * A verificação anterior só olhava para a pasta de origem, e por isso
 * passava enquanto a app instalada continuava sem dicionário — que é
 * precisamente o modo de falha que nos custou uma tarde. Um APK é um zip:
 * abre-se e procura-se lá dentro. Sem isto, a única forma de saber era
 * instalar e reparar.
 */
tasks.register("verificarApk") {
    dependsOn("assembleDebug")
    doLast {
        val apk = layout.buildDirectory
            .file("outputs/apk/debug/app-debug.apk").get().asFile
        val entradas = ZipFile(apk).use { zip ->
            zip.entries().asSequence()
                .filter { entrada -> entrada.name.startsWith("assets/") }
                .joinToString("\n") { entrada ->
                    "    ${entrada.name} — ${entrada.size / 1024} KiB"
                }
        }
        val temDicionario = ZipFile(apk).use { zip ->
            zip.entries().asSequence().any { entrada ->
                // O AGP tira o `.gz` aos assets que o tenham; os dois nomes
                // contam como sucesso.
                entrada.name == "assets/dicionario.db.gz" ||
                    entrada.name == "assets/dicionario.db"
            }
        }
        if (!temDicionario) {
            throw GradleException(
                "O APK saiu sem o dicionário. Assets encontrados:\n$entradas"
            )
        }
        logger.lifecycle("  assets dentro do APK:\n$entradas")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Room só para utilizador.db. O dicionario.db é SQLite em bruto, só
    // leitura — não precisa de ORM e não pode ser migrado por engano.
    implementation("androidx.room:room-runtime:2.8.2")
    implementation("androidx.room:room-ktx:2.8.2")
    ksp("androidx.room:room-compiler:2.8.2")

    // Lembrete diario do modo jogo. Trabalho periodico, nao alarme exato:
    // a hora nao precisa de ser ao segundo e um alarme exato exigiria uma
    // permissao especial que esta app nao tem como justificar.
    //
    // `work-runtime` e nao `work-runtime-ktx`: o artefacto -ktx esta vazio
    // desde que o CoroutineWorker e o resto das APIs de corrotinas passaram
    // para o artefacto principal. Declarar o -ktx so acrescentava um POM
    // sem codigo nenhum.
    implementation("androidx.work:work-runtime:2.11.2")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
