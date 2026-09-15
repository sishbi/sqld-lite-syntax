package sishbi.sqldlite

import com.alecstrong.sql.psi.core.psi.SqlBindParameter
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * Finds the value each Kotlin call passes for a bind argument.
 *
 * Navigation and Find Usages both come here, so both agree on what counts as a use of a bind
 * argument, the way [SqldLiteQueryCallSites] makes them agree on a label's call sites.
 */
object SqldLiteBindArgumentSites {

    /**
     * The value each call passes for [parameter]. Empty when the query mixes in a positional `?`
     * argument, for the reason [SqldLiteQuery.positionOf] gives.
     *
     * SqlDelight gives the generated function one parameter per distinct named bind argument, in
     * the order the query first mentions each name, so the argument's position in the query is the
     * parameter's position in the call.
     */
    fun of(parameter: SqlBindParameter): List<PsiElement> {
        val query = SqldLiteQuery.containing(parameter) ?: return emptyList()
        val position = query.positionOf(parameter) ?: return emptyList()

        return SqldLiteQueryCallSites.of(query.label).mapNotNull { argumentFor(it, query, position) }
    }

    /**
     * Everything that reads [parameter]'s value: the query's own other mentions of the name, and
     * then [of].
     *
     * A sibling mention is a usage but not a navigation target. Go To Declaration offers one target
     * or a popup of them, and a jump from `:id` to the `:id` three lines up tells the reader nothing.
     */
    fun usesOf(parameter: SqlBindParameter): List<PsiElement> {
        val siblings = SqldLiteQuery.containing(parameter)?.siblingsOf(parameter).orEmpty()
        return siblings + of(parameter)
    }

    /** The value [call] passes at [position], by argument name where the call names its arguments. */
    private fun argumentFor(call: KtCallExpression, query: SqldLiteQuery, position: Int): PsiElement? {
        val arguments = call.valueArguments
        val wanted = SqldLiteQuery.camelCase(query.parameterNames[position])

        val named = arguments.firstOrNull {
            it.getArgumentName()?.asName?.identifier?.let(SqldLiteQuery::camelCase) == wanted
        }
        return (named ?: arguments.getOrNull(position))?.getArgumentExpression()
    }
}
