import org.jetbrains.compose.desktop.application.dsl.TargetFormat


plugins {
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.serialization") version "1.9.24"
    id("org.jetbrains.compose") version "1.6.11"
}

// Версию и репозиторий релизов подставляет GitHub Actions: -PappVersion=1.2.1 -PgithubRepo=owner/repo.
// Они попадают в app.properties и нужны приложению для проверки обновлений (update/AppInfo.kt).
val appVersion = (findProperty("appVersion") as String?) ?: "1.2.0"
// Репозиторий с релизами. На GitHub Actions подставляется автоматически (github.repository).
val githubRepo = (findProperty("githubRepo") as String?) ?: "Athariyah/ScoreKeeper"

group = "com.example"
version = appVersion

tasks.processResources {
    inputs.property("appVersion", appVersion)
    inputs.property("githubRepo", githubRepo)
    filesMatching("app.properties") {
        expand("appVersion" to appVersion, "githubRepo" to githubRepo)
    }
}

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-cio:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
    implementation("io.ktor:ktor-client-websockets:2.3.12")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
}

compose.desktop {
    application {
        mainClass = "MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "ScoreKeeper"
            packageVersion = appVersion
            // Без этого модуля в урезанной Java из установщика не работает HTTPS к github.com
            // (сертификаты GitHub на эллиптических кривых) — приложение не нашло бы обновления.
            modules("jdk.crypto.ec")
            windows {
                iconFile.set(project.file("src/main/resources/app_icon.ico"))
            }
            linux {
                iconFile.set(project.file("src/main/resources/app_icon.png"))
            }
            macOS {
                iconFile.set(project.file("src/main/resources/app_icon.icns"))
            }
        }
    }
}