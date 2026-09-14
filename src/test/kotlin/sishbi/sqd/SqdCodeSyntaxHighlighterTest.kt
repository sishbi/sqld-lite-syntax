package sishbi.sqd

import com.alecstrong.sql.psi.core.psi.SqlTypes
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.testFramework.ParsingTestCase
import java.lang.reflect.Modifier

/**
 * Checks the colour each token gets.
 *
 * Extends [ParsingTestCase] only for the core environment it starts. [TextAttributesKey] needs an
 * application to register itself against, and nothing here parses.
 */
class SqdCodeSyntaxHighlighterTest : ParsingTestCase("", "sq", SqdCodeParserDefinition()) {

    override fun getTestDataPath() = "src/test/testData"

    private val highlighter by lazy { SqdCodeSyntaxHighlighter() }

    fun testColoursEachClassOfToken() {
        assertKey(SqdCodeTextAttributes.KEYWORD, SqlTypes.SELECT)
        assertKey(SqdCodeTextAttributes.KEYWORD, SqlTypes.WHERE)
        assertKey(SqdCodeTextAttributes.IDENTIFIER, SqlTypes.ID)
        assertKey(SqdCodeTextAttributes.NUMBER, SqlTypes.DIGIT)
        assertKey(SqdCodeTextAttributes.STRING, SqlTypes.STRING)
        assertKey(SqdCodeTextAttributes.OPERATOR, SqlTypes.EQ)
        assertKey(SqdCodeTextAttributes.OPERATOR, SqlTypes.CONCAT)
        assertKey(SqdCodeTextAttributes.PARENTHESES, SqlTypes.LP)
        assertKey(SqdCodeTextAttributes.DOT, SqlTypes.DOT)
        assertKey(SqdCodeTextAttributes.COMMA, SqlTypes.COMMA)
        assertKey(SqdCodeTextAttributes.SEMICOLON, SqlTypes.SEMI)
        assertKey(SqdCodeTextAttributes.LINE_COMMENT, SqlTypes.COMMENT)
        assertKey(SqdCodeTextAttributes.DOC_COMMENT, SqlTypes.JAVADOC)
    }

    /**
     * A token with no colour is a token the user sees in the default foreground. Only the three
     * below are meant to look that way, so the rest are listed as a whole to catch a token that
     * loses its colour later.
     */
    fun testEveryOtherTokenIsColoured() {
        val uncoloured =
            leafTokens()
                .filter { highlighter.getTokenHighlights(it).isEmpty() }
                .map { it.toString() }
                .sorted()

        // `___` is the debug name of SqlTypes.FAKE_EXTENSION, an internal grammar marker.
        assertEquals(listOf("___"), uncoloured)
    }

    fun testLeavesWhiteSpaceAndBadCharactersAlone() {
        assertEmpty(highlighter.getTokenHighlights(TokenType.WHITE_SPACE).toList())

        // The sql-psi lexer has no rule for `:` or `?`, so a bind argument arrives here as a bad
        // character. SqdCodeHighlightAnnotator colours those from the PSI, where a real bind
        // argument can be told apart from a stray character.
        assertEmpty(highlighter.getTokenHighlights(TokenType.BAD_CHARACTER).toList())
    }

    fun testUsesTheParsersOwnLexer() {
        val lexer = highlighter.highlightingLexer
        lexer.start("SELECT 1;")

        assertEquals(SqlTypes.SELECT, lexer.tokenType)
    }

    private fun assertKey(expected: TextAttributesKey, token: IElementType) {
        assertEquals(token.toString(), listOf(expected), highlighter.getTokenHighlights(token).toList())
    }

    /** Every token the lexer can produce. Grammar rules are subclasses and are filtered out. */
    private fun leafTokens(): List<IElementType> =
        SqlTypes::class
            .java
            .fields
            .filter { Modifier.isStatic(it.modifiers) }
            .mapNotNull { it.get(null) as? IElementType }
            .filter { it.javaClass == IElementType::class.java }
}
