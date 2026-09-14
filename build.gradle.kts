import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")

    // Composes SqdCode.bnf against the sql-psi grammar. Generates a parser only, never a lexer.
    id("app.cash.grammarkit-composer")
}

// The GrammarKit plugin, applied transitively by grammarkit-composer, declares project-level
// repositories. That makes Gradle ignore the settings-level ones entirely, so they are repeated
// here. Without this the IntelliJ Platform artefacts stop resolving.
repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

// Supplies the IntelliJ classes that GrammarKit's own generator runs against, at build time only.
// It has no bearing on the platform this plugin targets or ships against, and the generated parser
// does not depend on it. Same build the SqlDelight dialects generate with.
grammarKit {
    intellijRelease.set(libs.versions.grammarKitGenerator)
}

tasks.withType<org.jetbrains.grammarkit.tasks.GenerateParserTask>().configureEach {
    // The generator boots enough of the IntelliJ platform to insist on an installation home, and
    // aborts with "Could not find installation home path" when it cannot find one. Nothing is read
    // from this directory; it only has to exist.
    val generatorHome = layout.buildDirectory.dir("grammarkit-home")
    doFirst { generatorHome.get().asFile.mkdirs() }
    systemProperty("idea.home.path", generatorHome.get().asFile.absolutePath)
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    testImplementation(libs.junit)

    // SQL grammar, lexer and PSI. Compiled against platform 231, while this plugin targets 262.
    // verifyPlugin is what proves that difference has not broken binary compatibility.
    implementation(libs.sql.psi)

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        // One unified IntelliJ IDEA distribution. Community is not published since 253.
        intellijIdea(libs.versions.intellijIdea)
        testFramework(TestFrameworkType.Platform)

        // Add plugin dependencies for compilation here:
        bundledPlugin("org.jetbrains.kotlin")
    }
}

intellijPlatform {
    pluginVerification {
        ides {
            // The IDE builds the user actually runs. A clean run is mandatory before release.
            create(IntelliJPlatformType.IntellijIdeaUltimate, libs.versions.intellijIdea)
        }
    }
}
