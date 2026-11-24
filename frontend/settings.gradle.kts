plugins {
    // Позволяет Gradle скачать JDK 17 для toolchain, если на машине его нет.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "ScoreKeeper"
