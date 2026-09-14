package sishbi.sqd

import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.util.EnumSet

/**
 * Base class for tests that need a real project: indexes, annotators and navigation.
 *
 * [BasePlatformTestCase] turns any error logged while the test runs into a test failure. On 2026.2
 * an Ultimate module fails to instantiate during post-startup, which fails every such test for a
 * reason that has nothing to do with this plugin:
 * `Cannot create extension (class=B.B.B.B.s) [Plugin: com.intellij.modules.ultimate]`.
 *
 * [setUp] therefore logs that one error instead of failing on it. Every other error still fails the
 * test, which is the point of the check.
 *
 * Tests that only need to parse should use `ParsingTestCase` instead. It starts a core environment,
 * which is faster and does not hit this at all. See `SqdCodeParserTest`.
 */
abstract class SqdCodePlatformTestCase : BasePlatformTestCase() {

    override fun setUp() {
        LoggedErrorProcessor.executeWith<Throwable>(IgnoreUltimateStartupFailure) { super.setUp() }
    }

    private object IgnoreUltimateStartupFailure : LoggedErrorProcessor() {
        override fun processError(
            category: String,
            message: String,
            details: Array<out String>,
            t: Throwable?,
        ): Set<Action> =
            if (message.contains("com.intellij.modules.ultimate")) {
                EnumSet.of(Action.LOG)
            } else {
                Action.ALL
            }
    }
}
