package sishbi.sqldlite

import com.intellij.lang.annotation.HighlightSeverity

/**
 * Checks the whole highlighting path in a real project: the file type, the parser, the annotator and
 * the `plugin.xml` registrations that connect them.
 *
 * The unit tests check each piece. This checks they are wired together, which only a project can
 * show.
 */
class SqldLiteHighlightingIntegrationTest : SqldLitePlatformTestCase() {

    fun testOpensSqAndSqmAsSqlDelightFiles() {
        assertInstanceOf(myFixture.configureByText("Query.sq", "SELECT 1;"), SqldLiteFile::class.java)
        assertInstanceOf(myFixture.configureByText("1.sqm", "SELECT 1;"), SqldLiteFile::class.java)
    }

    fun testHighlightsARealQueryFileWithNoErrors() {
        myFixture.configureByFile("BookLoans.sq")

        val problems =
            myFixture.doHighlighting().filter { it.severity.myVal >= HighlightSeverity.WARNING.myVal }

        assertEmpty(problems.map { "${it.severity} ${it.description} at '${it.text}'" })
    }

    fun testColoursQueryLabelsAndBindArguments() {
        myFixture.configureByText(
            "Query.sq",
            """
            |updateStatus:
            |UPDATE units SET status = :status WHERE id = ?;
            """.trimMargin(),
        )

        val coloured =
            myFixture
                .doHighlighting()
                .mapNotNull { info -> info.forcedTextAttributesKey?.let { info.text to it } }

        assertContainsElements(
            coloured,
            "updateStatus" to SqldLiteTextAttributes.QUERY_LABEL,
            ":" to SqldLiteTextAttributes.QUERY_LABEL,
            ":status" to SqldLiteTextAttributes.BIND_PARAMETER,
            "?" to SqldLiteTextAttributes.BIND_PARAMETER,
        )
    }

    override fun getTestDataPath() = "src/test/resources"
}
