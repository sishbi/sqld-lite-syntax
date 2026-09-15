package sishbi.sqldlite

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.ParsingTestCase

/**
 * Checks the PostgreSQL constructs the overlay grammar adds on top of core SQL.
 *
 * Two fixtures hold them all, one for query syntax and one for migration syntax, written for these
 * tests from a survey of real `.sq` and `.sqm` files. A file that parses with an error is worse than
 * no plugin at all, because the IDE paints it red.
 */
class SqldLitePostgresDialectTest : ParsingTestCase("", "sq", SqldLiteParserDefinition()) {

    override fun getTestDataPath() = "src/test/testData"

    override fun skipSpaces() = false

    override fun includeRanges() = false

    /**
     * Covers the query syntax: SqlDelight imports and `AS` column types, the upsert in both its
     * forms, `RETURNING`, `SELECT DISTINCT ON`, a `::` cast, the `->>` JSON operator, a `DATE '...'`
     * typed literal, `JOIN LATERAL`, a data-modifying CTE, `INTERVAL`, `ILIKE`, `AT TIME ZONE`,
     * `FOR UPDATE`, `OVER (PARTITION BY ...)`, `ORDER BY ... NULLS LAST`, and a grouped statement,
     * whose label carries no colon.
     */
    fun testParsesPostgresQueries() {
        val file = createPsiFile("PostgresQueries", loadFile("PostgresQueries.sq"))

        assertNoErrors(file)
        assertEquals(
            listOf(
                "upsert",
                "insertIgnoringDuplicates",
                "insertReturningRow",
                "findLatestPerBranch",
                "findCastToTimestamp",
                "findByPayloadFormat",
                "findWithLateral",
                "insertThenSelect",
                "membersToRemind",
                "findTodaysTimeouts",
                "markCollected",
                "selectMemberReservationsForUpdate",
                "collectedToday",
                "groupedUpsert",
            ),
            PsiTreeUtil.findChildrenOfType(file, SqldLiteStmtIdentifierMixin::class.java)
                .mapNotNull { it.name },
        )
    }

    /**
     * Covers the migration syntax: `TIMESTAMP WITH TIME ZONE`, a column typed with an imported
     * Kotlin class, every PostgreSQL `ALTER TABLE` and `ALTER COLUMN` action core SQL lacks,
     * `RENAME` and `DROP` without the `COLUMN` keyword, `ADD COLUMN IF NOT EXISTS`, `DEFAULT now()`,
     * a bare `NULL` constraint, `DOUBLE PRECISION`, an array type, `GENERATED ALWAYS AS IDENTITY`,
     * `SET LOCAL`, `CREATE INDEX CONCURRENTLY`, `USING btree` and a `::` cast.
     */
    fun testParsesPostgresMigration() {
        assertNoErrors(createPsiFile("PostgresMigration", loadFile("PostgresMigration.sqm")))
    }

    private fun assertNoErrors(file: PsiFile) {
        val errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)
        assertEmpty(errors.map { "${it.errorDescription} at '${it.text}'" })
    }
}
