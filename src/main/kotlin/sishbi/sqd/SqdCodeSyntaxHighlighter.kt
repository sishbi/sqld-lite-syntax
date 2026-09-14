package sishbi.sqd

import com.alecstrong.sql.psi.core.SqlLexerAdapter
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.tree.IElementType

/**
 * Colours a `.sq` or `.sqm` file from its tokens alone, using the lexer sql-psi parses with so the
 * editor and the parser agree on where a token starts and ends.
 *
 * A query label is an ordinary identifier to that lexer, and a bind argument's `:` and `?` have no
 * lexer rule at all, so [SqdCodeHighlightAnnotator] colours both from the PSI instead.
 */
class SqdCodeSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer() = SqlLexerAdapter()

    override fun getTokenHighlights(tokenType: IElementType) = pack(SqdCodeTextAttributes.forToken(tokenType))
}

class SqdCodeSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?) = SqdCodeSyntaxHighlighter()
}
