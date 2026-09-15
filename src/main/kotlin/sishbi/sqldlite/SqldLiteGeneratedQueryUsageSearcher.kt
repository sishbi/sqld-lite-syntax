package sishbi.sqldlite

import com.intellij.find.findUsages.CustomUsageSearcher
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.usageView.UsageInfo
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.util.Processor
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

/**
 * Adds the `.sq` source of a generated query to its own usages: the label a generated function came
 * from, and the bind argument one of its parameters came from. Nothing links either pair, because
 * SqlDelight writes the Kotlin and the generated code holds no reference back to the query.
 *
 * A [CustomUsageSearcher] adds to the Kotlin plugin's own handler rather than replacing it, so
 * Kotlin still reports everything it reported before.
 *
 * Naming the query a usage of the generated function is backwards, source reported as usage of
 * output, but Find Usages is the only list that puts both ends of the pair in one place.
 */
class SqldLiteGeneratedQueryUsageSearcher : CustomUsageSearcher() {

    override fun processElementUsages(
        element: PsiElement,
        processor: Processor<in Usage>,
        options: FindUsagesOptions,
    ) {
        // The search runs on a pooled thread holding no read action, and building a UsageInfo reads
        // the element's file, so the whole list is built inside one.
        val usages = ApplicationManager.getApplication().runReadAction(
            Computable { sourcesOf(element).map { UsageInfo2UsageAdapter(UsageInfo(it)) } },
        )
        usages.forEach { processor.process(it) }
    }

    /** What [element] was generated from, empty unless it is a generated query or its parameter. */
    private fun sourcesOf(element: PsiElement): List<PsiElement> =
        when (element) {
            is KtParameter -> bindArgumentsFor(element)
            is KtNamedFunction -> labelsFor(element)
            else -> emptyList()
        }

    /** The labels [function] was generated from. */
    private fun labelsFor(function: KtNamedFunction): List<PsiElement> {
        val (call, labels) = queryOf(function) ?: return emptyList()
        return call.preferred(labels)
    }

    /** The bind arguments [parameter] was generated from. */
    private fun bindArgumentsFor(parameter: KtParameter): List<PsiElement> {
        val function = parameter.ownerFunction as? KtNamedFunction ?: return emptyList()
        val (call, labels) = queryOf(function) ?: return emptyList()

        val position = function.valueParameters.indexOf(parameter)
        return call
            .preferred(labels)
            .mapNotNull { SqldLiteQuery.of(it).parameterFor(parameter.name, position) }
    }

    /**
     * The query [function] looks generated from, with every label of that name in the project.
     *
     * Null unless the function sits in a class whose name ends in `Queries`, so a hand-written
     * class holding a function named after a query is left alone.
     */
    private fun queryOf(
        function: KtNamedFunction,
    ): Pair<SqldLiteQueryCall, List<SqldLiteStmtIdentifierMixin>>? {
        val queryName = function.name ?: return null
        val queriesName = function.containingClassOrObject?.name ?: return null
        val call = SqldLiteQueryCall.forQueriesClass(queryName, queriesName) ?: return null

        val project = function.project
        val labels = SqldLiteLabels.find(project, queryName, GlobalSearchScope.projectScope(project))
        return call to labels
    }
}
