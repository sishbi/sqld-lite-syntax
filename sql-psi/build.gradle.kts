import org.jetbrains.grammarkit.tasks.GenerateParserTask

plugins {
    id("org.jetbrains.kotlin.jvm")

    // Generates SqlParser and the whole PSI tree from sql.bnf. The root project applies the same
    // plugin to compose SqldLite.bnf on top of what this one produces.
    id("app.cash.grammarkit-composer")

    // Puts the IntelliJ Platform on the compile and test classpath without making this a plugin.
    id("org.jetbrains.intellij.platform.module")
}

// The bytecode ceiling is the platform's, as in the root project. See the comment there.
kotlin {
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(21)
        vendor = JvmVendorSpec.ADOPTIUM
    }

    compilerOptions {
        apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2
        languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2
    }
}

// The GrammarKit plugin declares project-level repositories, which makes Gradle ignore the
// settings-level ones. Same workaround as the root project.
repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

// Supplies the IntelliJ classes GrammarKit's own generator runs against, at build time only.
grammarKit {
    intellijRelease.set(libs.versions.grammarKitGenerator)
}

dependencies {
    intellijPlatform {
        intellijIdea(libs.versions.intellijIdea)
    }

    testImplementation(libs.junit)
    testImplementation(libs.assertk)

    // SqlCoreEnvironment reads a directory of files off the main thread.
    testImplementation(libs.coroutines.core)
}

// The lexer is JFlex, not GrammarKit, so it is declared rather than inferred. The generated source
// lands in build/, unlike the upstream project which committed it to a `gen` directory.
val generatedLexerDir = layout.buildDirectory.dir("generated/lexer")

tasks.generateLexer {
    sourceFile.set(file("src/main/kotlin/com/alecstrong/sql/psi/core/SqlLexer.flex"))
    targetOutputDir.set(generatedLexerDir.map { it.dir("com/alecstrong/sql/psi/core/lexer") })
    purgeOldFiles.set(true)
}

sourceSets {
    main {
        java.srcDir(tasks.generateLexer.map { generatedLexerDir })
    }
}

tasks.withType<GenerateParserTask>().configureEach {
    // The generator boots enough of the platform to insist on an installation home and a config
    // path, and aborts without them. Nothing is read from either. Same workaround as the root
    // project, plus the config path, which the upstream project needs for the same reason.
    val generatorHome = layout.buildDirectory.dir("grammarkit-home")
    doFirst { generatorHome.get().asFile.mkdirs() }
    systemProperty("idea.home.path", generatorHome.get().asFile.absolutePath)
    systemProperty("idea.config.path", "some/non/existent/path")
}

// JUnit 4, as written upstream. The root project runs its IntelliJ fixtures on the JUnit 5
// platform instead, which these tests do not need: they run headless.
tasks.test {
    systemProperty("idea.config.path", "some/non/existent/path")
}
