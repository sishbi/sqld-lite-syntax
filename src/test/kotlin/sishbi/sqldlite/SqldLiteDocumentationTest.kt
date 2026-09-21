package sishbi.sqldlite

import com.alecstrong.sql.psi.core.psi.SqlColumnName
import com.alecstrong.sql.psi.core.psi.SqlTableName
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil

/**
 * Checks what the IDE shows for a `.sq` element, on cmd-hover and under Quick Documentation.
 *
 * The provider writes HTML, so each case asserts the text it must carry rather than the markup
 * around it: the markup is the platform's and changes with the platform.
 */
class SqldLiteDocumentationTest : SqldLitePlatformTestCase() {

    override fun getTestDataPath() = "src/test/resources"

    /** With nothing generated to read, the bind arguments the query names are all there is. */
    fun testShowsTheBindArgumentsOfAQueryThatGeneratedNothing() {
        val label = labelIn(
            "Loans.sq",
            "/**\n" +
                " * Every loan a member holds.\n" +
                " */\n" +
                "findByMemberId:\n" +
                "SELECT * FROM book_loans\n" +
                "WHERE member_id = :member_id;\n",
        )

        // The hover is a declaration, as Kotlin's is: the name, what a caller passes, the file.
        val hover = requireNotNull(provider.getQuickNavigateInfo(label, label))
        assertTrue(hover, hover.contains("query:"))
        assertTrue(hover, hover.contains("findByMemberId"))
        assertTrue(hover, hover.contains("member_id"))
        assertTrue(hover, hover.contains("Loans.sq"))
        assertFalse(hover, hover.contains("SELECT"))
        assertFalse(hover, hover.contains("Every loan"))

        // Quick Documentation adds the doc comment, and never the query under the caret.
        val documentation = requireNotNull(provider.generateDoc(label, label))
        assertTrue(documentation, documentation.contains("Every loan a member holds."))
        assertFalse(documentation, documentation.contains("SELECT"))
    }

    /**
     * The generated function is the part of the pair not on screen, so both popups show it, copied
     * as SqlDelight wrote it rather than worked out here.
     */
    fun testShowsTheGeneratedFunctionBehindALabel() {
        generatedQueriesClass()
        val label = labelIn(
            "Loans.sq",
            "findByMemberId:\nSELECT * FROM book_loans WHERE member_id = :member_id;\n",
        )

        // One parameter stays on the line, which is what makes a wrapped list mean something.
        assertEquals(
            "fun findByMemberId(memberId: Long): Query<BookLoan>",
            SqldLiteGeneratedQuery.signatureOf(label as SqldLiteStmtIdentifierMixin),
        )

        val hover = requireNotNull(provider.getQuickNavigateInfo(label, label))
        assertTrue(hover, hover.contains("query:"))
        assertTrue(hover, hover.contains("memberId"))
        assertTrue(hover, hover.contains("Query"))
        assertTrue(hover, hover.contains("BookLoan"))
        // The bind-argument spelling gives way to the parameter the caller actually passes.
        assertFalse(hover, hover.contains("member_id"))
        // The mapper overload is machinery, not the function a reader calls.
        assertFalse(hover, hover.contains("mapper"))

        // Quick Documentation shows the same declaration, and still not the query.
        val documentation = requireNotNull(provider.generateDoc(label, label))
        assertTrue(documentation, documentation.contains("memberId"))
        assertFalse(documentation, documentation.contains("SELECT"))
    }

    /** A bind argument shows which generated parameter it becomes, and under what Kotlin name. */
    fun testShowsTheGeneratedParameterABindArgumentBecomes() {
        generatedQueriesClass()
        val query = myFixture.addFileToProject(
            "Loans.sq",
            "findByMemberId:\nSELECT * FROM book_loans WHERE member_id = :member_id;\n",
        )
        val parameter = PsiTreeUtil
            .findChildrenOfType(query, SqldLiteBindParameterMixin::class.java)
            .first { it.name == "member_id" }

        // The highlighter writes one span per token, so the name and its `:` are never adjacent.
        val hover = requireNotNull(provider.getQuickNavigateInfo(parameter, parameter))
        assertTrue(hover, hover.contains("bind argument"))
        assertTrue(hover, hover.contains("member_id"))
        assertTrue(hover, hover.contains("memberId"))
        assertTrue(hover, hover.contains("Loans.sq"))

        // The query it fills is on screen already, so neither popup repeats it.
        val documentation = requireNotNull(provider.generateDoc(parameter, parameter))
        assertFalse(documentation, documentation.contains("SELECT"))
    }

    /**
     * The generated declaration only, wrapped as it is written and indented from its own first
     * line, the way Kotlin's own popup wraps a function.
     *
     * SqlDelight writes a doc comment above the function and one parameter to a line, so the text
     * of the function holds a sentence, the indentation of the class around it, and a trailing
     * comma.
     */
    fun testShowsTheGeneratedDeclarationOnly() {
        myFixture.addFileToProject(
            "LoansQueries.kt",
            "package com.example.library\n\n" +
                "class LoansQueries {\n" +
                "    /**\n" +
                "     * @return The number of rows updated.\n" +
                "     */\n" +
                "    public fun create(\n" +
                "        email: String,\n" +
                "        userId: Long,\n" +
                "    ): QueryResult<Long> = error(\"\")\n" +
                "}\n",
        )
        val label = labelIn("Loans.sq", "create:\nINSERT INTO audit VALUES (:email, :user_id);\n")

        // The signature itself, not the popup: the popup writes one span per token, so no assertion
        // on its HTML can show how the declaration is laid out.
        assertEquals(
            "public fun create(\n    email: String,\n    userId: Long\n): QueryResult<Long>",
            SqldLiteGeneratedQuery.signatureOf(label as SqldLiteStmtIdentifierMixin),
        )

        // `public` is a soft keyword, so the Kotlin lexer reads it as an identifier and leaves it
        // in plain text beside a coloured `fun`. The popup colours it itself.
        val hover = requireNotNull(provider.getQuickNavigateInfo(label, label))
        assertTrue(hover, Regex("<span[^>]*>public</span>").containsMatchIn(hover))
    }

    /** Stands in for what the SqlDelight Gradle plugin writes from `Loans.sq`. */
    private fun generatedQueriesClass() {
        myFixture.addFileToProject(
            "LoansQueries.kt",
            "package com.example.library\n\n" +
                "class LoansQueries {\n" +
                "    fun findByMemberId(memberId: Long): Query<BookLoan> = error(\"\")\n" +
                "    fun <T : Any> findByMemberId(memberId: Long, mapper: (Long) -> T): Query<T> =\n" +
                "        error(\"\")\n" +
                "}\n",
        )
    }

    /** A use of a table name shows the statement that declares it, which may be another file. */
    fun testShowsTheCreateTableBehindATableName() {
        myFixture.addFileToProject(
            "1.sqm",
            "CREATE TABLE book_loans (\n  loan_id TEXT NOT NULL\n);\n",
        )
        val query = myFixture.addFileToProject(
            "Loans.sq",
            "findAll:\nSELECT * FROM book_loans;\n",
        )
        val use = requireNotNull(PsiTreeUtil.findChildOfType(query, SqlTableName::class.java))

        // The head of the statement only, so a table of forty columns still fits on the line.
        val hover = requireNotNull(provider.getQuickNavigateInfo(use, use))
        assertTrue(hover, hover.contains("CREATE"))
        assertTrue(hover, hover.contains("TABLE"))
        assertTrue(hover, hover.contains("book_loans"))
        assertTrue(hover, hover.contains("1.sqm"))
        assertFalse(hover, hover.contains("loan_id"))
    }

    /** A column used by a query shows the column definition, which is in the migration. */
    fun testShowsTheColumnDefinitionBehindAColumnUsedInAQuery() {
        myFixture.addFileToProject(
            "1.sqm",
            "CREATE TABLE book_loans (\n  loan_id TEXT NOT NULL,\n  status TEXT NOT NULL\n);\n",
        )
        val query = myFixture.addFileToProject(
            "Loans.sq",
            "findByLoanId:\nSELECT loan_id, status FROM book_loans WHERE loan_id = :loan_id;\n",
        )
        val use = PsiTreeUtil
            .findChildrenOfType(query, SqlColumnName::class.java)
            .last { it.text == "loan_id" }

        val hover = requireNotNull(provider.getQuickNavigateInfo(use, use))
        assertTrue(hover, hover.contains("column:"))
        assertTrue(hover, hover.contains("loan_id"))
        assertTrue(hover, hover.contains("TEXT"))
        assertTrue(hover, hover.contains("1.sqm"))
        assertFalse(hover, hover.contains("SELECT"))
    }

    /** A column of a table that the migrations only alter has nothing behind it but its name. */
    fun testShowsOnlyTheNameOfAnUnresolvedColumn() {
        myFixture.addFileToProject(
            "1.sqm",
            "ALTER TABLE book_loans ADD COLUMN renewals INTEGER;\n",
        )
        val query = myFixture.addFileToProject(
            "Loans.sq",
            "findRequested:\nSELECT branch_name, title FROM book_loans WHERE status = 'X';\n",
        )
        val use = PsiTreeUtil
            .findChildrenOfType(query, SqlColumnName::class.java)
            .last { it.text == "status" }

        // The statement holding an unresolved name is the query, which declares nothing.
        val hover = requireNotNull(provider.getQuickNavigateInfo(use, use))
        assertTrue(hover, hover.contains("column:"))
        assertTrue(hover, hover.contains("status"))
        assertFalse(hover, hover.contains("SELECT"))
        assertFalse(hover, hover.contains("branch_name"))
    }

    /** A column added by a later migration, used by a query in another file. */
    fun testShowsTheColumnDefinitionBehindAnAlteredColumn() {
        myFixture.addFileToProject(
            "1.sqm",
            "CREATE TABLE book_loans (\n  loan_id TEXT NOT NULL\n);\n",
        )
        myFixture.addFileToProject(
            "2.sqm",
            "ALTER TABLE book_loans ADD COLUMN status TEXT NOT NULL;\n",
        )
        val query = myFixture.addFileToProject(
            "Loans.sq",
            "findRequested:\nSELECT loan_id FROM book_loans WHERE status = 'REQUESTED';\n",
        )
        val use = PsiTreeUtil
            .findChildrenOfType(query, SqlColumnName::class.java)
            .last { it.text == "status" }

        val hover = requireNotNull(provider.getQuickNavigateInfo(use, use))
        assertTrue(hover, hover.contains("column:"))
        assertTrue(hover, hover.contains("status"))
        assertFalse(hover, hover.contains("SELECT"))
    }

    /** A call site reads as a query does: what it is, what it calls, and where it sits. */
    fun testShowsTheKotlinFunctionBehindACallSite() {
        myFixture.addFileToProject(
            "Caller.kt",
            "package com.example.library\n\n" +
                "class Caller {\n" +
                "    fun loansOf(memberId: Long) {\n" +
                "        bookLoansQueries.findByMemberId(memberId)\n" +
                "    }\n" +
                "}\n",
        )
        myFixture.copyFileToProject("BookLoans.sq")
        val label = PsiTreeUtil
            .findChildrenOfType(
                myFixture.configureFromTempProjectFile("BookLoans.sq"),
                SqldLiteStmtIdentifierMixin::class.java,
            )
            .first { it.name == "findByMemberId" }
        val target = SqldLiteQueryCallSites.navigationTargetsOf(label).single()

        val hover = requireNotNull(provider.getQuickNavigateInfo(target, target))
        assertTrue(hover, hover.contains("query call"))
        assertTrue(hover, hover.contains("findByMemberId"))
        // The enclosing function, highlighted by Kotlin's lexer, so one span per token.
        assertTrue(hover, hover.contains("loansOf"))
        assertTrue(hover, hover.contains("memberId"))
        // The qualified class, as Kotlin's own popup places a function.
        assertTrue(hover, hover.contains("com.example.library.Caller"))
        // Its body is not part of the declaration.
        assertFalse(hover, hover.contains("bookLoansQueries"))

        // The value that call passes is an argument, not a call, and is titled as one.
        val parameter = PsiTreeUtil
            .findChildrenOfType(label.containingFile, SqldLiteBindParameterMixin::class.java)
            // `markRequestedLoansAsCancelled` names one too; the last is `findByMemberId`'s.
            .last { it.name == "member_id" }
        val argument = SqldLiteBindArgumentSites.navigationTargetsOf(parameter).single()

        val argumentHover = requireNotNull(provider.getQuickNavigateInfo(argument, argument))
        assertTrue(argumentHover, argumentHover.contains("query argument"))
        assertFalse(argumentHover, argumentHover.contains("query call"))
    }

    private val provider = SqldLiteDocumentationProvider()

    private fun labelIn(fileName: String, text: String): PsiElement {
        val file: PsiFile = myFixture.addFileToProject(fileName, text)
        return requireNotNull(
            PsiTreeUtil.findChildOfType(file, SqldLiteStmtIdentifierMixin::class.java),
        )
    }
}
