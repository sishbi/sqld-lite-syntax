package sishbi.sqd

import com.alecstrong.sql.psi.core.psi.SqlTypes
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.tree.IElementType
import java.lang.reflect.Modifier

/**
 * Every colour this plugin adds, and the token each one applies to. Each key falls back to a
 * [DefaultLanguageHighlighterColors] constant, so the user's own scheme applies, in either theme,
 * with no theme-specific definitions and no editing.
 */
object SqdCodeTextAttributes {

    val KEYWORD = key("KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)
    val IDENTIFIER = key("IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER)
    val NUMBER = key("NUMBER", DefaultLanguageHighlighterColors.NUMBER)
    val STRING = key("STRING", DefaultLanguageHighlighterColors.STRING)
    val OPERATOR = key("OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN)
    val PARENTHESES = key("PARENTHESES", DefaultLanguageHighlighterColors.PARENTHESES)
    val DOT = key("DOT", DefaultLanguageHighlighterColors.DOT)
    val COMMA = key("COMMA", DefaultLanguageHighlighterColors.COMMA)
    val SEMICOLON = key("SEMICOLON", DefaultLanguageHighlighterColors.SEMICOLON)
    val LINE_COMMENT = key("LINE_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)
    val DOC_COMMENT = key("DOC_COMMENT", DefaultLanguageHighlighterColors.DOC_COMMENT)

    /** A query label, such as `selectAll:`. Applied from the PSI. See [SqdCodeSemanticHighlights]. */
    val QUERY_LABEL = key("QUERY_LABEL", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION)

    /** A bind argument, `?` or `:name`. Applied from the PSI. See [SqdCodeSemanticHighlights]. */
    val BIND_PARAMETER = key("BIND_PARAMETER", DefaultLanguageHighlighterColors.INSTANCE_FIELD)

    /** `FAKE_EXTENSION` is an internal marker in the sql-psi grammar and never reaches a file. */
    private val UNCOLOURED = setOf(SqlTypes.FAKE_EXTENSION)

    private val OPERATORS =
        setOf(
            SqlTypes.EQ,
            SqlTypes.EQ2,
            SqlTypes.NEQ,
            SqlTypes.NEQ2,
            SqlTypes.LT,
            SqlTypes.LTE,
            SqlTypes.GT,
            SqlTypes.GTE,
            SqlTypes.PLUS,
            SqlTypes.MINUS,
            SqlTypes.MULTIPLY,
            SqlTypes.DIVIDE,
            SqlTypes.MOD,
            SqlTypes.CONCAT,
            SqlTypes.BITWISE_AND,
            SqlTypes.BITWISE_OR,
            SqlTypes.BITWISE_NOT,
            SqlTypes.SHIFT_LEFT,
            SqlTypes.SHIFT_RIGHT,
        )

    private val NAMED: Map<IElementType, TextAttributesKey> =
        buildMap {
            put(SqlTypes.COMMENT, LINE_COMMENT)
            put(SqlTypes.JAVADOC, DOC_COMMENT)
            put(SqlTypes.STRING, STRING)
            put(SqlTypes.DIGIT, NUMBER)
            put(SqlTypes.ID, IDENTIFIER)
            put(SqlTypes.COMMA, COMMA)
            put(SqlTypes.DOT, DOT)
            put(SqlTypes.SEMI, SEMICOLON)
            put(SqlTypes.LP, PARENTHESES)
            put(SqlTypes.RP, PARENTHESES)
            OPERATORS.forEach { put(it, OPERATOR) }
        }

    /**
     * Every token in the sql-psi grammar that is neither punctuation nor a literal.
     *
     * Read by reflection because `SqlTypes` declares over a hundred keywords, and because a keyword
     * a later sql-psi release adds or removes then needs no change here and cannot break the build.
     * A leaf token is an [IElementType] exactly; every grammar rule in `SqlTypes` is a subclass, so
     * the class test is what separates the two.
     */
    private val KEYWORDS: Set<IElementType> =
        SqlTypes::class
            .java
            .fields
            .filter { Modifier.isStatic(it.modifiers) && IElementType::class.java.isAssignableFrom(it.type) }
            .mapNotNull { it.get(null) as? IElementType }
            .filter { it.javaClass == IElementType::class.java }
            .filterNot { it in NAMED || it in UNCOLOURED }
            .toSet()

    /** Null for white space, a bad character, and any token listed in [UNCOLOURED]. */
    fun forToken(tokenType: IElementType): TextAttributesKey? =
        NAMED[tokenType] ?: if (tokenType in KEYWORDS) KEYWORD else null

    private fun key(name: String, fallback: TextAttributesKey) =
        TextAttributesKey.createTextAttributesKey("SQD_CODE.$name", fallback)
}
