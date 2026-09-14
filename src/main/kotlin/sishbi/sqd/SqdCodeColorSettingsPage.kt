package sishbi.sqd

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import javax.swing.Icon

/**
 * Settings->Editor->Color Scheme->SqlDelight.
 *
 * Every colour the plugin adds appears here, so the user can change any of them.
 * `SqdCodeColorSettingsPageTest` checks that, because a missing descriptor is invisible: the colour
 * still applies, the user just cannot find it.
 */
class SqdCodeColorSettingsPage : ColorSettingsPage {
    override fun getDisplayName() = SqdCodeMessageBundle.message("colors.page.name")

    override fun getIcon(): Icon = SqdCodeIcons.FILE

    override fun getHighlighter() = SqdCodeSyntaxHighlighter()

    override fun getAttributeDescriptors() = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    /** The tags in [getDemoText] that the lexer cannot produce. */
    override fun getAdditionalHighlightingTagToDescriptorMap() = ANNOTATED_TAGS

    /**
     * Columns are named in full, as `book_loans.id`, so the sample holds a dot.
     * `SqdCodeColorSettingsPageTest` checks that it produces every colour the page offers: one
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
        "label" to SqdCodeTextAttributes.QUERY_LABEL,
        "bind" to SqdCodeTextAttributes.BIND_PARAMETER,
    )

private val DESCRIPTORS: Array<AttributesDescriptor> =
    arrayOf(
        descriptor("colors.keyword", SqdCodeTextAttributes.KEYWORD),
        descriptor("colors.identifier", SqdCodeTextAttributes.IDENTIFIER),
        descriptor("colors.number", SqdCodeTextAttributes.NUMBER),
        descriptor("colors.string", SqdCodeTextAttributes.STRING),
        descriptor("colors.operator", SqdCodeTextAttributes.OPERATOR),
        descriptor("colors.parentheses", SqdCodeTextAttributes.PARENTHESES),
        descriptor("colors.dot", SqdCodeTextAttributes.DOT),
        descriptor("colors.comma", SqdCodeTextAttributes.COMMA),
        descriptor("colors.semicolon", SqdCodeTextAttributes.SEMICOLON),
        descriptor("colors.line.comment", SqdCodeTextAttributes.LINE_COMMENT),
        descriptor("colors.doc.comment", SqdCodeTextAttributes.DOC_COMMENT),
        descriptor("colors.query.label", SqdCodeTextAttributes.QUERY_LABEL),
        descriptor("colors.bind.parameter", SqdCodeTextAttributes.BIND_PARAMETER),
    )

private fun descriptor(
    key: String,
    attributes: TextAttributesKey,
) = AttributesDescriptor(SqdCodeMessageBundle.lazyMessage(key), attributes)
