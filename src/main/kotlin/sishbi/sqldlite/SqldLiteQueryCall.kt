package sishbi.sqldlite

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

/**
 * A Kotlin call that looks like a SqlDelight generated query.
 *
 * @param queryName the called function, which is the query label SqlDelight generated it from.
 * @param queriesFileBaseName the `.sq` file the receiver names, if the receiver names one.
 */
data class SqldLiteQueryCall(val queryName: String, val queriesFileBaseName: String?) {

    /**
     * The labels worth offering.
     *
     * The file the receiver names wins when it holds a match. Otherwise every match is offered, so
     * two files with a label of the same name both appear rather than one being chosen wrongly.
     */
    fun preferred(matches: List<SqldLiteStmtIdentifierMixin>): List<SqldLiteStmtIdentifierMixin> {
        val fromNamedFile =
            matches.filter { it.containingFile.name.substringBeforeLast('.') == queriesFileBaseName }
        return fromNamedFile.ifEmpty { matches }
    }

    companion object {
        private const val QUERIES_SUFFIX = "Queries"

        /**
         * Null unless [element] is the callee of a Kotlin call whose receiver's name ends in
         * `Queries`, ignoring case.
         *
         * SqlDelight generates `FooQueries` from `Foo.sq`, held in a `fooQueries` property. Every
         * factory here requires that suffix, so a hand-written function named after a query gains
         * no nonsense target.
         */
        fun of(element: PsiElement): SqldLiteQueryCall? {
            val callee = element.parent as? KtNameReferenceExpression ?: return null
            val call = callee.parent as? KtCallExpression ?: return null
            if (call.calleeExpression !== callee) return null

            return forCall(call)
        }

        /** The call [queryName] on a `Queries` class called [queriesName]. */
        fun forQueriesClass(queryName: String, queriesName: String): SqldLiteQueryCall? {
            if (!queriesName.endsWith(QUERIES_SUFFIX, ignoreCase = true)) return null
            return SqldLiteQueryCall(queryName, fileBaseNameOf(queriesName))
        }

        /** Null unless [call] is a call on a receiver whose name ends in `Queries`. */
        fun forCall(call: KtCallExpression): SqldLiteQueryCall? {
            val callee = call.calleeExpression as? KtNameReferenceExpression ?: return null
            val receiver = receiverOf(call) ?: return null
            val receiverName = simpleNameOf(receiver) ?: return null
            if (!receiverName.endsWith(QUERIES_SUFFIX, ignoreCase = true)) return null

            return SqldLiteQueryCall(callee.getReferencedName(), fileBaseNameOf(receiverName))
        }

        private fun receiverOf(call: KtCallExpression): KtExpression? =
            (call.parent as? KtDotQualifiedExpression)
                ?.takeIf { it.selectorExpression === call }
                ?.receiverExpression

        /** The last name in the receiver: `db.fooQueries` and `fooQueries` both give `fooQueries`. */
        private fun simpleNameOf(receiver: KtExpression): String? =
            when (receiver) {
                is KtNameReferenceExpression -> receiver.getReferencedName()
                is KtDotQualifiedExpression ->
                    receiver.selectorExpression?.let { simpleNameOf(it) }
                is KtCallExpression ->
                    receiver.calleeExpression?.let { simpleNameOf(it) }
                else -> null
            }

        /** `fooQueries` gives `Foo`. Null when the receiver is called only `queries`. */
        private fun fileBaseNameOf(receiverName: String): String? =
            receiverName
                .dropLast(QUERIES_SUFFIX.length)
                .replaceFirstChar { it.uppercaseChar() }
                .ifEmpty { null }
    }
}
