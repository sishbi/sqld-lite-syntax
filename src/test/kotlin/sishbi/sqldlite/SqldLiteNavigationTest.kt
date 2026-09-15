package sishbi.sqldlite

import com.intellij.psi.search.GlobalSearchScope

/**
 * Checks navigation from a Kotlin query call to its `.sq` label, and the index it reads.
 *
 * The Kotlin files here are written by hand rather than generated, because the handler never reads
 * generated code. It matches on the call's own shape, which is the same either way.
 */
class SqldLiteNavigationTest : SqldLitePlatformTestCase() {

    override fun getTestDataPath() = "src/test/testData"

    fun testIndexesEveryLabelInAFile() {
        myFixture.copyFileToProject("BookLoans.sq")

        assertEquals(
            listOf("findByMemberId"),
            labels("findByMemberId").map { it.name },
        )
        assertEmpty(labels("noSuchQuery"))
    }

    fun testIndexesNoLabelsForAFileWithNone() {
        myFixture.copyFileToProject("PlainSql.sq")

        assertEmpty(labels("book_loan"))
    }

    fun testNavigatesToTheLabelInTheFileTheReceiverNames() {
        myFixture.copyFileToProject("BookLoans.sq")

        val targets = targetsFor("bookLoansQueries.findByMemberId(id)")

        assertEquals(1, targets.size)
        assertEquals("findByMemberId", (targets.single() as SqldLiteStmtIdentifierMixin).name)
        assertEquals("BookLoans.sq", targets.single().containingFile.name)
    }

    fun testNavigatesThroughADatabaseProperty() {
        myFixture.copyFileToProject("BookLoans.sq")

        val targets = targetsFor("database.bookLoansQueries.updateStatus(id)")

        assertEquals("updateStatus", (targets.single() as SqldLiteStmtIdentifierMixin).name)
    }

    fun testOffersEveryFileWhenTwoLabelsShareAName() {
        myFixture.addFileToProject("Left.sq", "selectAll:\nSELECT 1;")
        myFixture.addFileToProject("Right.sq", "selectAll:\nSELECT 2;")

        val targets = targetsFor("queries.selectAll()")

        assertEquals(
            listOf("Left.sq", "Right.sq"),
            targets.map { it.containingFile.name }.sorted(),
        )
    }

    fun testPrefersTheNamedFileOverTheOther() {
        myFixture.addFileToProject("Left.sq", "selectAll:\nSELECT 1;")
        myFixture.addFileToProject("Right.sq", "selectAll:\nSELECT 2;")

        val targets = targetsFor("rightQueries.selectAll()")

        assertEquals(listOf("Right.sq"), targets.map { it.containingFile.name })
    }

    fun testOffersNothingWhenNoLabelMatches() {
        myFixture.copyFileToProject("BookLoans.sq")

        assertEmpty(targetsFor("bookLoansQueries.noSuchQuery(id)"))
    }

    fun testIgnoresACallWhoseReceiverIsNotAQueriesClass() {
        myFixture.copyFileToProject("BookLoans.sq")

        assertEmpty(targetsFor("repository.findByMemberId(id)"))
    }

    fun testIgnoresACallWithNoReceiver() {
        myFixture.copyFileToProject("BookLoans.sq")

        assertEmpty(targetsFor("findByMemberId(id)"))
    }

    fun testNavigatesFromANamedArgumentToTheBindArgumentItFills() {
        myFixture.copyFileToProject("BookLoans.sq")

        val targets = targetsAtCaret(
            "bookLoansQueries.updateStatus(<caret>loanId = 1, status = \"DONE\")",
        )

        assertEquals(listOf(":loan_id"), targets.map { it.text })
    }

    fun testIgnoresANamedArgumentOnACallThatIsNotAQuery() {
        myFixture.copyFileToProject("BookLoans.sq")

        assertEmpty(targetsAtCaret("repository.updateStatus(<caret>loanId = 1)"))
    }

    fun testLeavesTheArgumentValueToKotlin() {
        myFixture.copyFileToProject("BookLoans.sq")

        assertEmpty(
            targetsAtCaret("bookLoansQueries.updateStatus(loanId = <caret>id)"),
        )
    }

    private fun labels(name: String) =
        SqldLiteLabels.find(project, name, GlobalSearchScope.projectScope(project))

    /**
     * The Go To Declaration targets this plugin adds for a call, with the caret on its callee.
     *
     * `<caret>` marks where the platform puts the caret. The call is wrapped in a function so the
     * Kotlin parser sees a statement, not a top-level expression.
     */
    private fun targetsFor(call: String): List<com.intellij.psi.PsiElement> {
        val callee = call.substringAfterLast('.').substringBefore('(')
        return targetsAtCaret(call.replaceFirst(callee, "<caret>$callee"))
    }

    /** The targets this plugin adds for a call that already marks its own caret. */
    private fun targetsAtCaret(call: String): List<com.intellij.psi.PsiElement> {
        myFixture.configureByText("Caller.kt", "fun caller() {\n    $call\n}\n")

        val element = myFixture.file.findElementAt(myFixture.caretOffset) ?: return emptyList()
        return SqldLiteGotoDeclarationHandler()
            .getGotoDeclarationTargets(element, myFixture.caretOffset, myFixture.editor)
            ?.toList()
            .orEmpty()
    }
}
