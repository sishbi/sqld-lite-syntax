package sishbi.sqldlite

import com.alecstrong.sql.psi.core.psi.SqlTypes
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.tree.IElementType
import com.intellij.testFramework.ParsingTestCase
import java.lang.reflect.Modifier

/** Checks that the settings page offers every colour the plugin can apply. */
class SqldLiteColorSettingsPageTest : ParsingTestCase("", "sq", SqldLiteParserDefinition()) {

    override fun getTestDataPath() = "src/test/resources"

    private val page by lazy { SqldLiteColorSettingsPage() }

    fun testOffersEveryColourThePluginApplies() {
        val offered = page.attributeDescriptors.map { it.key }.toSet()

        assertEmpty((appliedColours() - offered).map { it.externalName })
    }

    fun testNamesEveryColourItOffers() {
        val unnamed = page.attributeDescriptors.filter { it.displayName.isBlank() }

        assertEmpty(unnamed.map { it.key.externalName })
    }

    fun testDemoTextTagsAreTheOnesTheLexerCannotProduce() {
        assertEquals(
            setOf(SqldLiteTextAttributes.QUERY_LABEL, SqldLiteTextAttributes.BIND_PARAMETER),
            page.additionalHighlightingTagToDescriptorMap.values.toSet(),
        )

        page.additionalHighlightingTagToDescriptorMap.keys.forEach {
            assertTrue("<$it> is not in the demo text", page.demoText.contains("<$it>"))
        }
    }

    fun testDemoTextShowsEveryColourThePageOffers() {
        val shown = coloursIn(page.demoText) +
            page.additionalHighlightingTagToDescriptorMap.values

        assertEmpty(
            "not shown by the demo text",
            (page.attributeDescriptors.map { it.key } - shown.toSet()).map { it.externalName },
        )
    }

    /** The colours the lexer applies to [text], whose highlighting tags are stripped first. */
    private fun coloursIn(text: String): List<TextAttributesKey> {
        val lexer = SqldLiteSyntaxHighlighter().highlightingLexer

        lexer.start(text.replace(Regex("</?[a-z]+>"), ""))
        return generateSequence { lexer.tokenType?.also { lexer.advance() } }
            .mapNotNull { SqldLiteTextAttributes.forToken(it) }
            .toList()
    }

    /** Every colour the lexer can apply, plus the two the annotator applies. */
    private fun appliedColours(): Set<TextAttributesKey> =
        SqlTypes::class
            .java
            .fields
            .filter { Modifier.isStatic(it.modifiers) }
            .mapNotNull { it.get(null) as? IElementType }
            .mapNotNull { SqldLiteTextAttributes.forToken(it) }
            .toSet() + SqldLiteTextAttributes.QUERY_LABEL + SqldLiteTextAttributes.BIND_PARAMETER
}
