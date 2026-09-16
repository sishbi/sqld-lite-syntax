rootProject.name = "sqld-lite-syntax"

// The SQL grammar, lexer and PSI. Source, not an artefact: see sql-psi/README.md.
include(":sql-psi")

pluginManagement {
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.4.20"
        id("org.jetbrains.changelog") version "2.5.0"
        id("app.cash.grammarkit-composer") version "0.2.0"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("org.jetbrains.intellij.platform.settings") version "2.18.1"
}
