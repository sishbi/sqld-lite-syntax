package sishbi.sqldlite

import com.alecstrong.sql.psi.core.psi.SqlBindParameter
import com.alecstrong.sql.psi.core.psi.SqlStmtList
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/**
 * A labelled query, and the bind arguments SqlDelight turns into its function parameters.
 *
 * SqlDelight gives the generated function one parameter per distinct named bind argument, in the
 * order the query first mentions each name. That correspondence is what lets navigation cross
 * between a query and its callers without resolving the generated function.
 *
 * A grouped query, written `name { ... }`, keeps one parameter list across its whole group, so the
 * group counts as one query here.
 */
class SqldLiteQuery private constructor(
    val label: SqldLiteStmtIdentifierMixin,
    private val occurrences: List<SqlBindParameter>,
) {

    /** One per generated parameter: the first mention of each name. */
    private val parameters = occurrences.distinctBy { nameOf(it) }

    /** The generated function's parameter names, in declaration order. */
    val parameterNames: List<String> get() = parameters.map { nameOf(it) }

    /**
     * Where [parameter] sits in the generated function's parameter list.
     *
     * Null when the query also uses a positional `?` argument. SqlDelight then names that parameter
     * after the column it binds to, which this class cannot work out, so every position after it
     * would be wrong.
     */
    fun positionOf(parameter: SqlBindParameter): Int? =
        when {
            mixesPositionalArguments() -> null
            else -> parameterNames.indexOf(nameOf(parameter)).takeIf { it >= 0 }
        }

    /**
     * The bind argument a call passes at [position], or the one called [name] when the call names
     * its argument.
     *
     * Null when the query mixes in a positional `?` argument, for the reason [positionOf] gives.
     */
    fun parameterFor(name: String?, position: Int): SqlBindParameter? =
        when {
            mixesPositionalArguments() -> null
            name == null -> parameters.getOrNull(position)
            else -> parameters.firstOrNull { camelCase(nameOf(it)) == camelCase(name) }
        }

    /**
     * The query's other mentions of [parameter]'s name. A query may bind one name several times, and
     * SqlDelight still generates a single parameter, so every mention is a use of the same value.
     *
     * Empty for a positional `?`, which names nothing and so has no siblings.
     */
    fun siblingsOf(parameter: SqlBindParameter): List<SqlBindParameter> {
        val name = nameOf(parameter).ifEmpty { return emptyList() }
        return occurrences.filter { it != parameter && nameOf(it) == name }
    }

    private fun mixesPositionalArguments() = parameters.any { nameOf(it).isEmpty() }

    companion object {

        fun of(label: SqldLiteStmtIdentifierMixin): SqldLiteQuery {
            val occurrences = label
                .statementsUnderLabel()
                .flatMap { PsiTreeUtil.findChildrenOfType(it, SqlBindParameter::class.java) }
            return SqldLiteQuery(label, occurrences)
        }

        /** The query [element] belongs to, or null when it is not inside a labelled query. */
        fun containing(element: PsiElement): SqldLiteQuery? =
            topLevelAncestorOf(element)?.precedingLabel()?.let { of(it) }

        /** SqlDelight names the generated parameter in camel case, whatever the query wrote. */
        fun camelCase(name: String) =
            name
                .split('_')
                .filter { it.isNotEmpty() }
                .mapIndexed { index, word ->
                    if (index == 0) word else word.replaceFirstChar { it.uppercaseChar() }
                }
                .joinToString("")

        /** The ancestor of [element] that the statement list holds directly. */
        private fun topLevelAncestorOf(element: PsiElement): PsiElement? {
            val statements = PsiTreeUtil.getParentOfType(element, SqlStmtList::class.java)
                ?: return null
            return generateSequence(element) { it.parent }.firstOrNull { it.parent == statements }
        }

        /** The nearest label declared before [this] among the statement list's children. */
        private fun PsiElement.precedingLabel(): SqldLiteStmtIdentifierMixin? =
            generateSequence(prevSibling) { it.prevSibling }
                .filterIsInstance<SqldLiteStmtIdentifierMixin>()
                .firstOrNull()

        /** Everything between [this] label and the next one, which is the query's own text. */
        private fun SqldLiteStmtIdentifierMixin.statementsUnderLabel(): List<PsiElement> =
            generateSequence(nextSibling) { it.nextSibling }
                .takeWhile { it !is SqldLiteStmtIdentifierMixin }
                .toList()

        /** The name without its `:` marker. Empty for a positional `?` argument. */
        private fun nameOf(parameter: SqlBindParameter) = parameter.text.removePrefix(":").trim()
    }
}
