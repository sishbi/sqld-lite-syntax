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

    fun testShowsTheQueryBehindALabel() {
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

        // Quick Documentation adds the doc comment and the query itself.
        val documentation = requireNotNull(provider.generateDoc(label, label))
        assertTrue(documentation, documentation.contains("Every loan a member holds."))
        assertTrue(documentation, documentation.contains("SELECT"))
        assertTrue(documentation, documentation.contains("book_loans"))
    }

    fun testShowsTheQueryABindArgumentBelongsTo() {
        myFixture.copyFileToProject("BookLoans.sq")
        val file = myFixture.configureFromTempProjectFile("BookLoans.sq")
        val parameter = PsiTreeUtil
            .findChildrenOfType(file, SqldLiteBindParameterMixin::class.java)
            .first { it.name == "loan_id" }

        // The highlighter writes one span per token, so the name and its `:` are never adjacent.
        val hover = requireNotNull(provider.getQuickNavigateInfo(parameter, parameter))
        assertTrue(hover, hover.contains("loan_id"))
        assertTrue(hover, hover.contains("BookLoans.sq"))
        assertFalse(hover, hover.contains("UPDATE"))

        // The query it fills is behind Quick Documentation.
        val documentation = requireNotNull(provider.generateDoc(parameter, parameter))
        assertTrue(documentation, documentation.contains("UPDATE"))
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
    }

    private val provider = SqldLiteDocumentationProvider()

    private fun labelIn(fileName: String, text: String): PsiElement {
        val file: PsiFile = myFixture.addFileToProject(fileName, text)
        return requireNotNull(
            PsiTreeUtil.findChildOfType(file, SqldLiteStmtIdentifierMixin::class.java),
        )
    }
}
