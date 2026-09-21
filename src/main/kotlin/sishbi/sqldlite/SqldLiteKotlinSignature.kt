package sishbi.sqldlite

import org.jetbrains.kotlin.psi.KtFunction

/**
 * A Kotlin function as its declaration reads, without its body.
 *
 * The documentation popup prints this and highlights it with Kotlin's own lexer, so a reader sees
 * the names and types the editor shows. Two popups want it: a call site shows the function holding
 * the call, and a query label shows the function SqlDelight generated from it.
 *
 * Only the layout is this class's. SqlDelight generates with KotlinPoet, which
 * wraps a parameter list only once the line passes its column limit, so a query of eight columns
 * arrives wrapped and a query of two does not. The popup wraps by the number of parameters instead,
 * so every query reads the same way.
 *
 * Read when the target or the popup is built, never while either paints. A cell renderer paints on
 * the EDT holding no read action.
 */
object SqldLiteKotlinSignature {

    /** One parameter to a line above this many, all on one line at or below it. */
    private const val INLINE_PARAMETER_LIMIT = 1

    private const val INDENT = "    "

    /** [function] as its declaration reads: `fun record(id: Long): Unit`. */
    fun of(function: KtFunction): String? {
        val name = function.name ?: return null
        val head = buildString {
            function.modifierList?.text?.let { append(oneLine(it)).append(" ") }
            append("fun ")
            function.typeParameterList?.text?.let { append(oneLine(it)).append(" ") }
            function.receiverTypeReference?.text?.let { append(oneLine(it)).append(".") }
            append(name)
        }
        val parameters = function.valueParameters.map { oneLine(it.text) }
        val returns = function.typeReference?.let { ": ${oneLine(it.text)}" }.orEmpty()
        return head + parameterList(parameters) + returns
    }

    /**
     * The parameters in brackets, one to a line once there is more than one of them.
     *
     * A reader of a query's parameters is matching a bind argument against a column, which is a
     * column of names to read down rather than a sentence to read along.
     */
    private fun parameterList(parameters: List<String>): String =
        if (parameters.size <= INLINE_PARAMETER_LIMIT) {
            parameters.joinToString(", ", "(", ")")
        } else {
            parameters.joinToString(",\n$INDENT", "(\n$INDENT", "\n)")
        }

    /** [text] on one line, so nothing but [parameterList] puts a line break in a declaration. */
    private fun oneLine(text: String): String =
        text.lines().joinToString(" ") { it.trim() }.trim()
}
