package sishbi.sqldlite

import com.alecstrong.sql.psi.core.psi.NamedElement
import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor

/**
 * Reports the Kotlin calls to a query label as its usages, and the value each of those calls passes
 * for a bind argument as that argument's usages. The platform cannot find either itself: SqlDelight
 * writes the generated function, so nothing points from a call back to the query.
 *
 * Reuses the word-index scan navigation does, so Find Usages and Go To Declaration always agree.
 */
class SqldLiteFindUsagesHandlerFactory : FindUsagesHandlerFactory() {

    override fun canFindUsages(element: PsiElement) =
        element is SqldLiteStmtIdentifierMixin ||
            element is SqldLiteBindParameterMixin ||
            element is NamedElement

    override fun createFindUsagesHandler(
        element: PsiElement,
        forHighlightUsages: Boolean,
    ): FindUsagesHandler? {
        if (!canFindUsages(element)) return null
        if (element is NamedElement) return schemaHandler(element)
        return object : FindUsagesHandler(element) {
            override fun processElementUsages(
                element: PsiElement,
                processor: Processor<in UsageInfo>,
                options: FindUsagesOptions,
            ): Boolean {
                // The search runs on a pooled thread holding no read action, and a UsageInfo reads
                // the element's file, so the whole list is built inside one.
                val usages = ApplicationManager.getApplication().runReadAction(
                    Computable { usesOf(element).map { UsageInfo(it) } },
                )
                return usages.all { processor.process(it) }
            }
        }
    }

    /**
     * Searches a table or view from every statement that declares it, not only the one the caret is
     * on. The search itself stays the platform's own: sql-psi already gives each name a reference,
     * and only the set of starting points was wrong. See [SqldLiteSchemaChain].
     */
    private fun schemaHandler(element: NamedElement) =
        object : FindUsagesHandler(element) {
            override fun processElementUsages(
                element: PsiElement,
                processor: Processor<in UsageInfo>,
                options: FindUsagesOptions,
            ): Boolean {
                val chain = ApplicationManager.getApplication().runReadAction(
                    Computable { SqldLiteSchemaChain.of(element as NamedElement) },
                )
                // The chain is searched here rather than returned from getPrimaryElements, so the
                // panel keeps the one target the caret is on. Every link as a target titles the
                // panel with the same name once per migration.
                return chain.all { super.processElementUsages(it, processor, options) }
            }
        }

    private fun usesOf(element: PsiElement): List<PsiElement> =
        when (element) {
            is SqldLiteStmtIdentifierMixin -> SqldLiteQueryCallSites.of(element)
            is SqldLiteBindParameterMixin -> SqldLiteBindArgumentSites.usesOf(element)
            else -> emptyList()
        }
}
