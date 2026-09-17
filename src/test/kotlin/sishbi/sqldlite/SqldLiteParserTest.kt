package sishbi.sqldlite

import com.alecstrong.sql.psi.core.SqlFileBase
import com.alecstrong.sql.psi.core.psi.SqlBindParameter
import com.alecstrong.sql.psi.core.psi.SqlIdentifier
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.ParsingTestCase

/**
 * Checks that SqlDelight syntax parses on top of the `:sql-psi` grammar.
 *
 * These use [ParsingTestCase], not `BasePlatformTestCase`. `BasePlatformTestCase` starts a full IDE
 * and turns any error logged during startup into a test failure. On 2026.2 an unrelated Ultimate
 * module fails to load, so every such test fails for reasons that have nothing to do with this
 * plugin.
 *
 * `createPsiFile` appends the single extension this class is constructed with, so a `.sqm` file is
 * loaded by name and parsed as if it were `.sq`. Both extensions share one grammar, so that is
 * exactly what the IDE does with them too.
 */
class SqldLiteParserTest : ParsingTestCase("", "sq", SqldLiteParserDefinition()) {

    override fun getTestDataPath() = "src/test/resources"

    override fun skipSpaces() = false

    override fun includeRanges() = false

    fun testParsesRealQueryFile() {
        val file = createPsiFile("BookLoans", loadFile("BookLoans.sq"))

        assertInstanceOf(file, SqlFileBase::class.java)
        assertNoErrors(file)

        // sql-psi finds the statement list by class, so a stmt_list that does not implement
        // SqlStmtList reads as an empty file: no tables, no columns and no resolution.
        assertNotNull((file as SqlFileBase).sqlStmtList)

        assertEquals(
            listOf(
                "updateStatus",
                "markRequestedLoansAsCancelled",
                "findMembersWithOpenLoans",
                "findByLoanId",
                "findByMemberId",
            ),
            labelNames(file),
        )
    }

    fun testParsesQueryLabel() {
        val source =
            """
            |updateStatus:
            |SELECT 1;
            """.trimMargin()

        val file = createPsiFile("Label", source)

        assertNoErrors(file)

        val label = labels(file).single()
        assertEquals("updateStatus", label.name)
        assertEquals("updateStatus:", label.text)
    }

    fun testParsesQueryLabelPrecededByJavadoc() {
        val source =
            """
            |/**
            | * Updates the status.
            | */
            |updateStatus:
            |SELECT 1;
            """.trimMargin()

        val file = createPsiFile("Javadoc", source)

        assertNoErrors(file)

        // The javadoc is part of the label element, which is what lets a doc comment be shown for
        // the query it documents.
        val label = labels(file).single()
        assertEquals("updateStatus", label.name)
        assertEquals(
            """
            |/**
            | * Updates the status.
            | */
            |updateStatus:
            """.trimMargin(),
            label.text,
        )
    }

    fun testParsesNamedBindArgument() {
        val source =
            """
            |updateStatus:
            |UPDATE units SET status = :status;
            """.trimMargin()

        val file = createPsiFile("NamedBind", source)

        assertNoErrors(file)

        val bind = bindParameters(file).single()
        assertEquals(":status", bind.text)
        assertEquals("status", PsiTreeUtil.getChildOfType(bind, SqlIdentifier::class.java)?.text)
    }

    fun testParsesPositionalBindArgument() {
        val source =
            """
            |findById:
            |SELECT * FROM units WHERE id = ?;
            """.trimMargin()

        val file = createPsiFile("PositionalBind", source)

        assertNoErrors(file)

        val bind = bindParameters(file).single()
        assertEquals("?", bind.text)
        assertNull(PsiTreeUtil.getChildOfType(bind, SqlIdentifier::class.java))
    }

    fun testParsesPlainSqlFileWithNoQueryLabel() {
        val file = createPsiFile("PlainSql", loadFile("PlainSql.sq"))

        assertNoErrors(file)
        assertEmpty(labelNames(file))
    }

    fun testParsesMigrationFileWithNoQueryLabel() {
        val file = createPsiFile("2", loadFile("2.sqm"))

        assertNoErrors(file)
        assertEmpty(labelNames(file))
    }

    /**
     * `stmt_list` is pinned after its statement, so a statement missing its terminator reports an
     * error there instead of the whole rule matching nothing. Without the pin the file parses as
     * zero statements and every query in it disappears from the IDE without a warning.
     */
    fun testReportsAnErrorForAStatementMissingItsTerminator() {
        val source =
            """
            |updateStatus:
            |SELECT 1
            """.trimMargin()

        val file = createPsiFile("Unterminated", source)

        assertNotEmpty(PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java))
        assertEquals(listOf("updateStatus"), labelNames(file))
    }

    private fun assertNoErrors(file: PsiFile) {
        val errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)
        assertEmpty(errors.map { "${it.errorDescription} at '${it.text}'" })
    }

    private fun labels(file: PsiFile) =
        PsiTreeUtil.findChildrenOfType(file, SqldLiteStmtIdentifierMixin::class.java)
            .filter { it.name != null }

    private fun labelNames(file: PsiFile) = labels(file).map { it.name }

    private fun bindParameters(file: PsiFile) =
        PsiTreeUtil.findChildrenOfType(file, SqlBindParameter::class.java)
}
