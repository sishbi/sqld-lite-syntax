package sishbi.sqd

import com.alecstrong.sql.psi.core.psi.SqlBindParameter
import com.alecstrong.sql.psi.core.psi.SqlIdentifier
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.TokenType
import com.intellij.psi.util.PsiTreeUtil

/** Colours the two pieces of SqlDelight syntax the lexer cannot recognise: a label and a bind. */
class SqdCodeHighlightAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        for ((range, key) in SqdCodeSemanticHighlights.of(element)) {
            holder
                .newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(range)
                .textAttributes(key)
                .create()
        }
    }
}

/**
 * What [SqdCodeHighlightAnnotator] colours, as plain data, so it can be tested against a parsed file
 * without an [AnnotationHolder], which only the platform can supply.
 */
object SqdCodeSemanticHighlights {

    fun of(element: PsiElement): List<Pair<TextRange, TextAttributesKey>> =
        when (element) {
            is SqdCodeStmtIdentifierMixin -> label(element)
            is SqlBindParameter -> listOf(element.textRange to SqdCodeTextAttributes.BIND_PARAMETER)
            else -> emptyList()
        }

    /**
     * The label's name and its trailing `:`, which arrives as a bad character because the sql-psi
     * lexer has no rule for a colon. The javadoc above a label is left to the lexer, which already
     * colours it as a doc comment.
     */
    private fun label(element: SqdCodeStmtIdentifierMixin): List<Pair<TextRange, TextAttributesKey>> {
        val identifier = PsiTreeUtil.getChildOfType(element, SqlIdentifier::class.java) ?: return emptyList()
        val colons =
            element.node
                .getChildren(null)
                .filter { it.elementType == TokenType.BAD_CHARACTER }
                .map { it.textRange }
        return (listOf(identifier.textRange) + colons).map { it to SqdCodeTextAttributes.QUERY_LABEL }
    }
}
