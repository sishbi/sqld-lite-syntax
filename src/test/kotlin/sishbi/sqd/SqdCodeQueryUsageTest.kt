package sishbi.sqd

import com.alecstrong.sql.psi.core.psi.SqlBindParameter
import com.alecstrong.sql.psi.core.psi.SqlTableName
import com.intellij.analysis.AnalysisScope
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.icons.AllIcons
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.slicer.LanguageSlicing
import com.intellij.slicer.SliceAnalysisParams
import com.intellij.slicer.SliceUsage
import com.intellij.usageView.UsageInfo
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.util.CommonProcessors
import java.util.concurrent.TimeUnit
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtValueArgument

/**
 * Checks the three things that depend on knowing a query's Kotlin call sites: the unused-query
 * inspection, Find Usages on a label, and navigation from a bind argument to the value each call
 * passes for it.
 *
 * All three read the same scan, so a query that navigates must also report a usage and must not be
 * greyed out. The tests assert that agreement rather than each feature in isolation.
 */
class SqdCodeQueryUsageTest : SqdCodePlatformTestCase() {

    override fun getTestDataPath() = "src/test/testData"

    fun testGreysOutOnlyTheQueriesNoKotlinCalls() {
        myFixture.addFileToProject(
            "Caller.kt",
            "fun caller() {\n    bookLoansQueries.findByMemberId(1)\n}\n",
        )
        myFixture.copyFileToProject("BookLoans.sq")
        myFixture.enableInspections(SqdCodeUnusedQueryInspection())

        myFixture.configureFromTempProjectFile("BookLoans.sq")

        assertEquals(
            listOf(
                "updateStatus:",
                "markRequestedLoansAsCancelled:",
                "findMembersWithOpenLoans:",
                "findByLoanId:",
            ),
            myFixture.doHighlighting().filter { it.description != null }.map { it.text },
        )
    }

    fun testDeletesTheWholeQueryWhenTheFixRuns() {
        myFixture.copyFileToProject("BookLoans.sq")
        myFixture.enableInspections(SqdCodeUnusedQueryInspection())
        val file = myFixture.configureFromTempProjectFile("BookLoans.sq")

        myFixture.doHighlighting()
        myFixture.launchAction(myFixture.findSingleIntention("Delete query"))

        assertFalse(file.text.contains("updateStatus"))
        assertTrue(file.text.contains("findByLoanId"))
    }

    fun testReportsTheKotlinCallAsAUsage() {
        myFixture.addFileToProject(
            "Caller.kt",
            "fun caller() {\n    bookLoansQueries.findByMemberId(1)\n}\n",
        )
        val label = findByMemberIdLabel()

        val usages = CommonProcessors.CollectProcessor<UsageInfo>()
        val factory = SqdCodeFindUsagesHandlerFactory()
        assertTrue(factory.canFindUsages(label))
        val handler = requireNotNull(factory.createFindUsagesHandler(label, false))
        handler.processElementUsages(label, usages, handler.findUsagesOptions)

        assertEquals(
            listOf("findByMemberId(1)"),
            usages.results.map { it.element?.text },
        )
    }

    fun testPutsACallerCountOnEveryLabel() {
        myFixture.addFileToProject(
            "Caller.kt",
            "fun caller() {\n" +
                "    bookLoansQueries.findByMemberId(1)\n" +
                "    bookLoansQueries.findByMemberId(2)\n" +
                "}\n",
        )
        myFixture.copyFileToProject("BookLoans.sq")
        myFixture.configureFromTempProjectFile("BookLoans.sq")

        myFixture.doHighlighting()

        assertEquals(
            listOf(
                "No usages",
                "No usages",
                "No usages",
                "No usages",
                "2 usages",
            ),
            myFixture.findAllGutters().map { it.tooltipText },
        )
    }

    fun testOffersTheLabelAsASearchTarget() {
        myFixture.copyFileToProject("BookLoans.sq")
        myFixture.configureFromTempProjectFile("BookLoans.sq")
        myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf("findByMemberId"))

        val target = com.intellij.codeInsight.TargetElementUtil
            .findTargetElement(
                myFixture.editor,
                com.intellij.codeInsight.TargetElementUtil.ELEMENT_NAME_ACCEPTED,
            )

        assertEquals("findByMemberId", (target as? SqdCodeStmtIdentifierMixin)?.name)
        assertEquals(
            AllIcons.Nodes.Method,
            (target as? SqdCodeStmtIdentifierMixin)?.presentation?.getIcon(true),
        )
    }

    fun testOffersABindArgumentAsASearchTarget() {
        myFixture.copyFileToProject("BookLoans.sq")
        myFixture.configureFromTempProjectFile("BookLoans.sq")
        myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf(":loan_id") + 1)

        val target = com.intellij.codeInsight.TargetElementUtil
            .findTargetElement(
                myFixture.editor,
                com.intellij.codeInsight.TargetElementUtil.ELEMENT_NAME_ACCEPTED,
            )

        assertEquals("loan_id", (target as? SqdCodeBindParameterMixin)?.name)
        assertEquals(
            AllIcons.Nodes.Parameter,
            (target as? SqdCodeBindParameterMixin)?.presentation?.getIcon(true),
        )
    }

    fun testReportsTheCallArgumentAsAUsageOfABindArgument() {
        myFixture.addFileToProject(
            "Caller.kt",
            "fun caller() {\n    bookLoansQueries.updateStatus(\"DONE\", 42)\n}\n",
        )
        myFixture.copyFileToProject("BookLoans.sq")
        val file = myFixture.configureFromTempProjectFile("BookLoans.sq")
        val parameter = PsiTreeUtil
            .findChildrenOfType(file, SqdCodeBindParameterMixin::class.java)
            .first { it.name == "loan_id" }

        val usages = CommonProcessors.CollectProcessor<UsageInfo>()
        val factory = SqdCodeFindUsagesHandlerFactory()
        assertTrue(factory.canFindUsages(parameter))
        val handler = requireNotNull(factory.createFindUsagesHandler(parameter, false))
        handler.processElementUsages(parameter, usages, handler.findUsagesOptions)

        assertEquals(listOf("42"), usages.results.map { it.element?.text })
    }

    fun testNavigatesFromABindArgumentToTheValueTheCallPasses() {
        myFixture.addFileToProject(
            "Caller.kt",
            "fun caller() {\n    bookLoansQueries.updateStatus(\"DONE\", 42)\n}\n",
        )

        assertEquals(listOf("42"), bindArgumentTargets("loan_id"))
        assertEquals(listOf("\"DONE\""), bindArgumentTargets("status"))
    }

    fun testNavigatesToANamedArgumentWhateverItsPosition() {
        myFixture.addFileToProject(
            "Caller.kt",
            "fun caller() {\n" +
                "    bookLoansQueries.updateStatus(loanId = 42, status = \"DONE\")\n" +
                "}\n",
        )

        assertEquals(listOf("42"), bindArgumentTargets("loan_id"))
    }

    fun testReportsTheBindArgumentAsAUsageOfTheGeneratedParameter() {
        val parameter = loanIdParameterIn(
            "BookLoansQueries.kt",
            "class BookLoansQueries {\n" +
                "    fun updateStatus(status: String, loanId: Long) = Unit\n" +
                "}\n",
        )
        myFixture.copyFileToProject("BookLoans.sq")

        val usages = CommonProcessors.CollectProcessor<Usage>()
        SqdCodeGeneratedQueryUsageSearcher()
            .processElementUsages(parameter, usages, FindUsagesOptions(project))

        assertEquals(listOf(":loan_id"), sourceNames(usages.results))
    }

    fun testIgnoresAParameterOfAClassThatIsNotAQueriesClass() {
        val parameter = loanIdParameterIn(
            "Repository.kt",
            "class Repository {\n    fun updateStatus(loanId: Long) = Unit\n}\n",
        )
        myFixture.copyFileToProject("BookLoans.sq")

        val usages = CommonProcessors.CollectProcessor<Usage>()
        SqdCodeGeneratedQueryUsageSearcher()
            .processElementUsages(parameter, usages, FindUsagesOptions(project))

        assertEmpty(usages.results)
    }

    fun testReportsTheLabelAsAUsageOfTheGeneratedFunction() {
        val queries = myFixture.addFileToProject(
            "BookLoansQueries.kt",
            "class BookLoansQueries {\n" +
                "    fun findByMemberId(id: Long) = Unit\n" +
                "}\n",
        )
        myFixture.copyFileToProject("BookLoans.sq")
        val function = PsiTreeUtil
            .findChildrenOfType(queries, KtNamedFunction::class.java)
            .first { it.name == "findByMemberId" }

        val usages = CommonProcessors.CollectProcessor<Usage>()
        SqdCodeGeneratedQueryUsageSearcher()
            .processElementUsages(function, usages, FindUsagesOptions(project))

        assertEquals(listOf("findByMemberId:"), sourceNames(usages.results))
    }

    fun testReportsEveryOtherMentionOfTheSameBindArgument() {
        val file = myFixture.addFileToProject(
            "Repeated.sq",
            "findOverdue:\nSELECT * FROM book_loans\nWHERE loan_id = :id OR renewals = :id;\n",
        )
        val parameter = PsiTreeUtil
            .findChildrenOfType(file, SqdCodeBindParameterMixin::class.java)
            .first()

        val usages = CommonProcessors.CollectProcessor<UsageInfo>()
        val handler = requireNotNull(
            SqdCodeFindUsagesHandlerFactory().createFindUsagesHandler(parameter, false),
        )
        handler.processElementUsages(parameter, usages, handler.findUsagesOptions)

        assertEquals(listOf(":id"), usages.results.map { it.element?.text })
    }

    fun testNamesTheGroupEveryUsageAppearsUnder() {
        val caller = myFixture.addFileToProject(
            "Caller.kt",
            "fun caller() {\n    bookLoansQueries.updateStatus(\"DONE\", 42)\n}\n",
        )
        myFixture.copyFileToProject("BookLoans.sq")
        val file = myFixture.configureFromTempProjectFile("BookLoans.sq")
        val provider = SqdCodeUsageTypeProvider()

        val label = PsiTreeUtil
            .findChildrenOfType(file, SqdCodeStmtIdentifierMixin::class.java)
            .first { it.name == "updateStatus" }
        val parameter = requireNotNull(
            PsiTreeUtil.findChildrenOfType(file, SqlBindParameter::class.java).firstOrNull(),
        )
        val call = PsiTreeUtil.findChildrenOfType(caller, KtCallExpression::class.java).first()
        val argument = PsiTreeUtil.findChildrenOfType(caller, KtValueArgument::class.java).last()

        // A table name, on both sides of the split: where the schema defines it, and where a query
        // uses it. Neither had a group, so every such row landed together under "Unclassified".
        val migration = myFixture.addFileToProject(
            "BookLoans.sqm",
            "CREATE TABLE book_loans (\n  loan_id TEXT NOT NULL\n);\n",
        )
        val declaration = PsiTreeUtil.findChildOfType(migration, SqlTableName::class.java)
        val use = PsiTreeUtil.findChildOfType(file, SqlTableName::class.java)

        assertEquals("SqlDelight query", provider.getUsageType(label)?.toString())
        assertEquals("SqlDelight bind argument", provider.getUsageType(parameter)?.toString())
        assertEquals(
            "SqlDelight schema definition",
            provider.getUsageType(requireNotNull(declaration))?.toString(),
        )
        assertEquals("SqlDelight statement", provider.getUsageType(requireNotNull(use))?.toString())
        assertEquals("SqlDelight query call", provider.getUsageType(call)?.toString())
        assertEquals(
            "SqlDelight query argument",
            provider.getUsageType(requireNotNull(argument.getArgumentExpression()))?.toString(),
        )
    }

    fun testRegistersTheSliceProviderTheDataFlowTabAsksFor() {
        myFixture.copyFileToProject("BookLoans.sq")
        val file = myFixture.configureFromTempProjectFile("BookLoans.sq")
        val label = PsiTreeUtil
            .findChildrenOfType(file, SqdCodeStmtIdentifierMixin::class.java)
            .first { it.name == "findByMemberId" }

        // The tab asks the element's language for a provider. The class existing is not enough:
        // without the registration the lookup returns null and the tab throws.
        val provider = LanguageSlicing.getProvider(label)

        assertInstanceOf(provider, SqdCodeSliceProvider::class.java)
        val params = SliceAnalysisParams().apply { scope = AnalysisScope(project) }
        assertEquals(label, provider.createRootUsage(label, params).element)
    }

    /**
     * Drives the two places that run outside a read action in production: the searcher, which the
     * platform calls on a pooled thread, and the cell renderer, which paints on the EDT. Both only
     * soft-assert, so a missing read action shows up as a logged error rather than a failure, and
     * [SqdCodePlatformTestCase] turns that into one.
     */
    fun testTouchesNoPsiWithoutAReadAction() {
        myFixture.addFileToProject(
            "Caller.kt",
            "fun caller() {\n    bookLoansQueries.findByMemberId(1)\n}\n",
        )
        val label = findByMemberIdLabel()
        val handler = requireNotNull(
            SqdCodeFindUsagesHandlerFactory().createFindUsagesHandler(label, false),
        )
        val params = SliceAnalysisParams().apply { scope = AnalysisScope(project) }
        val renderer = SqdCodeSliceProvider().renderer
        val usage = SqdCodeSliceProvider().createRootUsage(label, params)

        // Built here, not below: constructing them is the platform's own read-action work, and the
        // point of the pooled block is to hold only this plugin's code.
        val options = handler.findUsagesOptions
        val usages = CommonProcessors.CollectProcessor<UsageInfo>()

        offTheEventThread {
            handler.processElementUsages(label, usages, options)
            renderer.customizeCellRendererFor(usage)
        }

        assertEquals(listOf("findByMemberId(1)"), usages.results.map { it.element?.text })
        assertEquals("findByMemberId", renderer.toString())
    }

    fun testFlowsABindArgumentBackToTheValueEachCallPasses() {
        myFixture.addFileToProject(
            "Caller.kt",
            "fun caller() {\n    bookLoansQueries.updateStatus(\"DONE\", 42)\n}\n",
        )
        myFixture.copyFileToProject("BookLoans.sq")
        val file = myFixture.configureFromTempProjectFile("BookLoans.sq")
        val parameter = PsiTreeUtil
            .findChildrenOfType(file, SqdCodeBindParameterMixin::class.java)
            .first { it.name == "loan_id" }
        val label = PsiTreeUtil
            .findChildrenOfType(file, SqdCodeStmtIdentifierMixin::class.java)
            .first { it.name == "updateStatus" }

        // What "Analyze | Data Flow to Here" picks up. Null here made the action say it could not
        // find anything to analyze, which is the whole feature failing silently.
        val caret = requireNotNull(file.findElementAt(file.text.indexOf(":loan_id") + 1))
        assertEquals(
            parameter,
            SqdCodeSliceProvider().getExpressionAtCaret(caret, dataFlowToThis = true),
        )
        assertNull(SqdCodeSliceProvider().getExpressionAtCaret(caret, dataFlowToThis = false))

        assertEquals(listOf("42"), inflowOf(parameter))
        // A label names a statement, not a value, so the tab correctly has nothing to show for one.
        assertEmpty(inflowOf(label))
    }

    fun testBuildsTheCallHierarchyOfAQuery() {
        myFixture.addFileToProject(
            "Caller.kt",
            "fun caller() {\n    bookLoansQueries.findByMemberId(1)\n}\n" +
                "fun outer() {\n    caller()\n}\n",
        )
        val label = findByMemberIdLabel()

        val structure = SqdCodeCallerTreeStructure(project, label)
        val callers = structure.getChildElements(structure.rootElement)
        val callersOfCaller = structure.getChildElements(callers.single())

        assertEquals(listOf("caller"), descriptorNames(callers))
        assertEquals(listOf("outer"), descriptorNames(callersOfCaller))
    }

    fun testFindsTheQueryUnderTheCaretForTheHierarchyWindow() {
        myFixture.copyFileToProject("BookLoans.sq")
        myFixture.configureFromTempProjectFile("BookLoans.sq")
        myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf("findByMemberId"))
        val context = SimpleDataContext.builder()
            .add(CommonDataKeys.PSI_FILE, myFixture.file)
            .add(CommonDataKeys.EDITOR, myFixture.editor)
            .build()

        val target = SqdCodeCallHierarchyProvider().getTarget(context)

        assertEquals("findByMemberId", (target as? SqdCodeStmtIdentifierMixin)?.name)
    }

    fun testTakesABindArgumentToBeAQuestionAboutItsQuery() {
        myFixture.copyFileToProject("BookLoans.sq")
        val file = myFixture.configureFromTempProjectFile("BookLoans.sq")
        val parameter = PsiTreeUtil
            .findChildrenOfType(file, SqdCodeBindParameterMixin::class.java)
            .first { it.name == "loan_id" }

        // The Find Usages "Call Hierarchy" tab hands over the selected row's element, and a
        // SqlDelight usage row is often a bind argument. Returning null there left the tab blank.
        val context = SimpleDataContext.builder()
            .add(CommonDataKeys.PSI_ELEMENT, parameter)
            .build()

        val target = SqdCodeCallHierarchyProvider().getTarget(context)

        assertEquals("updateStatus", (target as? SqdCodeStmtIdentifierMixin)?.name)
    }

    fun testHasNoHierarchyForAnUnlabelledStatement() {
        val file = myFixture.addFileToProject(
            "Reservations.sqm",
            "ALTER TABLE reservations\nADD COLUMN journey TEXT;\n",
        )
        // Every statement is preceded by a stmt_identifier, empty when nothing labels it. Offering
        // that as a target filled the hierarchy window with a nameless row.
        val context = SimpleDataContext.builder()
            .add(CommonDataKeys.PSI_ELEMENT, requireNotNull(file.findElementAt(file.text.indexOf("journey"))))
            .build()

        assertNull(SqdCodeCallHierarchyProvider().getTarget(context))
    }

    /** The name each hierarchy row shows. */
    private fun descriptorNames(descriptors: Array<Any>) =
        descriptors.map { (it as HierarchyNodeDescriptor).psiElement?.let(::nameOf) }

    private fun nameOf(element: PsiElement) =
        (element as? PsiNamedElement)?.name ?: element.text

    /** The text of everything the Data Flow tab reports as flowing into [element]. */
    private fun inflowOf(element: PsiElement): List<String> {
        val params = SliceAnalysisParams().apply {
            scope = AnalysisScope(project)
            dataFlowToThis = true
        }
        val root = SqdCodeSliceProvider().createRootUsage(element, params)
        val children = CommonProcessors.CollectProcessor<SliceUsage>()

        // processChildren reads the indicator of the thread it runs on, and throws without one.
        ProgressManager.getInstance().runProcess(
            { root.processChildren(children) },
            ProgressIndicatorBase(),
        )

        return children.results.map { it.element?.text.orEmpty() }
    }

    /** Runs [body] with no read action held, which is what the platform gives both callers. */
    private fun offTheEventThread(body: () -> Unit) {
        ApplicationManager.getApplication()
            .executeOnPooledThread<Unit> {
                assertFalse(ApplicationManager.getApplication().isReadAccessAllowed)
                body()
            }
            .get(30, TimeUnit.SECONDS)
    }

    /** The text of each reported usage, which is the `.sq` element it points at. */
    private fun sourceNames(usages: Collection<Usage>) =
        usages.map { (it as UsageInfo2UsageAdapter).element?.text }

    /** The `loanId` parameter in [source], written to [fileName]. */
    private fun loanIdParameterIn(fileName: String, source: String): KtParameter {
        val file = myFixture.addFileToProject(fileName, source)
        return PsiTreeUtil
            .findChildrenOfType(file, KtParameter::class.java)
            .first { it.name == "loanId" }
    }

    /** The values the call sites pass for the bind argument written `:[name]` in `updateStatus`. */
    private fun bindArgumentTargets(name: String): List<String> {
        myFixture.copyFileToProject("BookLoans.sq")
        val file = myFixture.configureFromTempProjectFile("BookLoans.sq")
        val offset = file.text.indexOf(":$name") + 1
        val element = requireNotNull(file.findElementAt(offset))

        return targets(element, offset).map { it.text }
    }

    private fun targets(element: PsiElement, offset: Int): List<PsiElement> =
        SqdCodeSqGotoDeclarationHandler()
            .getGotoDeclarationTargets(element, offset, myFixture.editor)
            ?.toList()
            .orEmpty()

    private fun findByMemberIdLabel(): SqdCodeStmtIdentifierMixin {
        myFixture.copyFileToProject("BookLoans.sq")
        val file = myFixture.configureFromTempProjectFile("BookLoans.sq")
        return PsiTreeUtil
            .findChildrenOfType(file, SqdCodeStmtIdentifierMixin::class.java)
            .first { it.name == "findByMemberId" }
    }
}
