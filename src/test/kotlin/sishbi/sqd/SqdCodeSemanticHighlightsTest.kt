package sishbi.sqd

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.ParsingTestCase

/**
 * Checks what the annotator colours, over a parsed file.
 *
 * The annotator itself is three lines that hand each result to an `AnnotationHolder`, which only the
 * platform can supply. [SqdCodeSemanticHighlights] holds the decision, so the decision is what is
 * tested.
 */
class SqdCodeSemanticHighlightsTest : ParsingTestCase("", "sq", SqdCodeParserDefinition()) {

    override fun getTestDataPath() = "src/test/testData"

    override fun skipSpaces() = false

    override fun includeRanges() = false

    fun testColoursTheLabelAndItsColon() {
        val file = createPsiFile("Label", "updateStatus:\nSELECT 1;")

        assertEquals(listOf("updateStatus" to QUERY_LABEL, ":" to QUERY_LABEL), highlights(file))
    }

    fun testLeavesTheJavadocAboveALabelToTheLexer() {
        val file = createPsiFile("Javadoc", "/** Doc. */\nupdateStatus:\nSELECT 1;")

        assertEquals(listOf("updateStatus" to QUERY_LABEL, ":" to QUERY_LABEL), highlights(file))
    }

    fun testColoursBothKindsOfBindArgument() {
        val file = createPsiFile("Bind", "updateStatus:\nUPDATE units SET status = :status WHERE id = ?;")

        assertEquals(
            listOf(
                "updateStatus" to QUERY_LABEL,
                ":" to QUERY_LABEL,
                ":status" to BIND_PARAMETER,
                "?" to BIND_PARAMETER,
            ),
            highlights(file),
        )
    }

    fun testColoursNothingInAFileWithNoSqlDelightSyntax() {
        val file = createPsiFile("PlainSql", loadFile("PlainSql.sq"))

        assertEmpty(highlights(file))
    }

    /** Every highlight in the file, as the text it covers and the colour it applies. */
    private fun highlights(file: PsiFile): List<Pair<String, TextAttributesKey>> =
        PsiTreeUtil.findChildrenOfAnyType(file, true, com.intellij.psi.PsiElement::class.java)
            .flatMap { element -> SqdCodeSemanticHighlights.of(element).map { it } }
            .sortedBy { it.first.startOffset }
            .map { (range, key) -> file.text.substring(range.startOffset, range.endOffset) to key }

    private companion object {
        val QUERY_LABEL = SqdCodeTextAttributes.QUERY_LABEL
        val BIND_PARAMETER = SqdCodeTextAttributes.BIND_PARAMETER
    }
}
