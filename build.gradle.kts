import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")

    // Composes SqldLite.bnf against the sql-psi grammar. Generates a parser only, never a lexer.
    id("app.cash.grammarkit-composer")
}

// The IDE builds this plugin targets all run on JetBrains Runtime 21, so 21 is the bytecode
// ceiling, not a preference. A newer target throws UnsupportedClassVersionError as the IDE loads
// the plugin. Pinning the vendor as well stops the build silently using whatever JDK happens to
// run the Gradle daemon, which was Temurin locally and Zulu in CI.
kotlin {
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(21)
        vendor = JvmVendorSpec.ADOPTIUM
    }

    // The platform supplies kotlin-stdlib at run time, so the plugin must not compile against a
    // newer Kotlin than the target IDE bundles. 2025.2 bundles Kotlin 2.2 and its Kotlin plugin
    // reads metadata up to 2.2.0; the Kotlin compiler here is 2.4.20 and defaults to emitting 2.4.
    // Left alone, the classes load but anything reading their metadata reports "unsupported binary
    // format", and stdlib calls added after 2.2 fail with NoSuchMethodError on the floor build.
    compilerOptions {
        apiVersion = KotlinVersion.KOTLIN_2_2
        languageVersion = KotlinVersion.KOTLIN_2_2
    }
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
    testImplementation(platform(libs.junit.bom))

    // junit.framework.TestCase, which every IntelliJ test fixture extends.
    testImplementation(libs.junit)
    // Runs those JUnit 3 fixtures on the JUnit 5 platform.
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    // For new tests that do not need an IntelliJ fixture.
    testImplementation(libs.junit.jupiter)

    // SQL grammar, lexer and PSI. Source in this repo rather than an artefact, so the build needs
    // no local publish, no remote repository and no credential, and it compiles against the same
    // platform as the rest of the plugin. Provenance and the route back upstream: sql-psi/README.md.
    //
    // kotlin-stdlib is absent by design: the platform supplies it on the plugin classloader's
    // parent, and shipping a second copy is forbidden
    // -> https://jb.gg/intellij-platform-kotlin-stdlib
    implementation(project(":sql-psi"))

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        // One unified IntelliJ IDEA distribution. Community is not published since 253.
        intellijIdea(libs.versions.intellijIdea)
        testFramework(TestFrameworkType.Platform)

        // Add plugin dependencies for compilation here:
        bundledPlugin("org.jetbrains.kotlin")
    }
}

tasks.test {
    useJUnitPlatform()
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            // Stated rather than inherited from the compile target. Without it the plugin claims
            // compatibility with 262 only, because the Gradle plugin derives since-build from
            // whatever intellijIdea() resolves to.
            sinceBuild = libs.versions.intellijIdeaSinceBuild

            // Capped at the newest build verifyPlugin actually checks. Left open, the plugin would
            // claim compatibility with IDE builds that did not exist when it was verified, and
            // this plugin reaches into sql-psi and Kotlin plugin internals that do move.
            untilBuild = libs.versions.intellijIdeaUntilBuild
        }
    }

    pluginVerification {
        ides {
            // Every release build from the declared floor upwards. Verifying only the compile
            // target proves nothing about the older builds since-build promises to support.
            select {
                types = listOf(IntelliJPlatformType.IntellijIdeaUltimate)
                sinceBuild = libs.versions.intellijIdeaSinceBuild
                untilBuild = libs.versions.intellijIdeaUntilBuild
            }
        }
    }
}
