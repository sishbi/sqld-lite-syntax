package sishbi.sqldlite

/**
 * Checks that a `.sqm` file always reports a migration number.
 *
 * A null one is not a cosmetic defect. sql-psi then treats the migration as a queries file, so an
 * `ALTER TABLE` asks the table it alters for its columns, and that table asks the same
 * `ALTER TABLE` what it exposes. The recursion ends in a `StackOverflowError` that takes the IDE
 * down, which the plugin's first hard constraint forbids.
 */
class SqldLiteMigrationOrderTest : SqldLitePlatformTestCase() {

    fun testNumbersAMigrationFromTheDigitsInItsName() {
        assertEquals(12L, orderOf("12.sqm"))
        assertEquals(3L, orderOf("V3__add_a_column.sqm"))
        assertEquals(20260115L, orderOf("20260115-add-a-column.sqm"))
    }

    fun testNumbersAMigrationWhoseNameHoldsNoDigits() {
        // The fallback matters more than the value. Any number keeps sql-psi treating the file as a
        // migration; a null makes it a queries file and the resolution recurses forever.
        assertNotNull(orderOf("Migration.sqm"))
    }

    fun testLeavesAQueriesFileUnnumbered() {
        // A .sq file has no place in the migration chain, and sql-psi expects null to say so.
        assertNull(orderOf("BookLoans.sq"))
    }

    fun testResolvesAChainOfAltersInAMigrationWhoseNameHoldsNoDigits() {
        myFixture.addFileToProject(
            "Schema.sq",
            "CREATE TABLE book_loans (\n    loan_id INTEGER NOT NULL\n);\n",
        )
        myFixture.configureByText(
            "Migration.sqm",
            """
            ALTER TABLE book_loans
            ADD COLUMN renewals INTEGER;

            ALTER TABLE book_loans
            ADD COLUMN last_note TEXT;
            """.trimIndent(),
        )

        // Before the fix this never returned: it exhausted the stack instead.
        myFixture.doHighlighting()
    }

    private fun orderOf(name: String) =
        (myFixture.addFileToProject(name, "") as SqldLiteFile).order
}
