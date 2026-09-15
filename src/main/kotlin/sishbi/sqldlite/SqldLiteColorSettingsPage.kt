package sishbi.sqldlite

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import javax.swing.Icon

/**
 * Settings->Editor->Color Scheme->SqlDelight.
 *
 * Every colour the plugin adds appears here, so the user can change any of them.
 * `SqldLiteColorSettingsPageTest` checks that, because a missing descriptor is invisible: the colour
 * still applies, the user just cannot find it.
 */
class SqldLiteColorSettingsPage : ColorSettingsPage {
    override fun getDisplayName() = SqldLiteMessageBundle.message("colors.page.name")

    override fun getIcon(): Icon = SqldLiteIcons.FILE

    override fun getHighlighter() = SqldLiteSyntaxHighlighter()

    override fun getAttributeDescriptors() = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    /** The tags in [getDemoText] that the lexer cannot produce. */
    override fun getAdditionalHighlightingTagToDescriptorMap() = ANNOTATED_TAGS

    /**
     * Columns are named in full, as `book_loans.id`, so the sample holds a dot.
     * `SqldLiteColorSettingsPageTest` checks that it produces every colour the page offers: one
     * missing from the sample is one the user cannot see before choosing it.
     */
    override fun getDemoText() =
        """
        |/**
        | * Counts every loan.
        | */
        |<label>countAll</label>:
        |SELECT COUNT(*) FROM book_loans;
        |
        |-- Updates one row.
        |<label>updateStatus</label>:
        |UPDATE book_loans
        |SET status = <bind>:status</bind>, attempts = attempts + 1
        |WHERE book_loans.id = <bind>?</bind> AND book_loans.name != 'unknown';
        """.trimMargin()
}

private val ANNOTATED_TAGS =
    mapOf(
        "label" to SqldLiteTextAttributes.QUERY_LABEL,
        "bind" to SqldLiteTextAttributes.BIND_PARAMETER,
    )

private val DESCRIPTORS: Array<AttributesDescriptor> =
    arrayOf(
        descriptor("colors.keyword", SqldLiteTextAttributes.KEYWORD),
        descriptor("colors.identifier", SqldLiteTextAttributes.IDENTIFIER),
        descriptor("colors.number", SqldLiteTextAttributes.NUMBER),
        descriptor("colors.string", SqldLiteTextAttributes.STRING),
        descriptor("colors.operator", SqldLiteTextAttributes.OPERATOR),
        descriptor("colors.parentheses", SqldLiteTextAttributes.PARENTHESES),
        descriptor("colors.dot", SqldLiteTextAttributes.DOT),
        descriptor("colors.comma", SqldLiteTextAttributes.COMMA),
        descriptor("colors.semicolon", SqldLiteTextAttributes.SEMICOLON),
        descriptor("colors.line.comment", SqldLiteTextAttributes.LINE_COMMENT),
        descriptor("colors.doc.comment", SqldLiteTextAttributes.DOC_COMMENT),
        descriptor("colors.query.label", SqldLiteTextAttributes.QUERY_LABEL),
        descriptor("colors.bind.parameter", SqldLiteTextAttributes.BIND_PARAMETER),
    )

private fun descriptor(
    key: String,
    attributes: TextAttributesKey,
) = AttributesDescriptor(SqldLiteMessageBundle.lazyMessage(key), attributes)
