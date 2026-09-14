package sishbi.sqd

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Who runs a query, and who runs them.
 *
 * The first level comes from the same word-index scan Find Usages and navigation use, so all three
 * agree on what counts as a call. Past that the callers are ordinary Kotlin functions, which the
 * reference index can follow, so the tree keeps going instead of stopping at the generated call.
 */
class SqdCodeCallerTreeStructure(project: Project, label: PsiElement) :
    HierarchyTreeStructure(project, SqdCodeCallHierarchyNodeDescriptor(project, null, label, true)) {

    override fun buildChildren(descriptor: HierarchyNodeDescriptor): Array<Any> =
        when (val element = descriptor.psiElement) {
            is SqdCodeStmtIdentifierMixin -> callersOfQuery(element)
            is KtNamedFunction -> callersOfFunction(element)
            else -> emptyList()
        }.map { SqdCodeCallHierarchyNodeDescriptor(myProject, descriptor, it, false) }
            .toTypedArray()

    private fun callersOfQuery(label: SqdCodeStmtIdentifierMixin): List<PsiElement> =
        SqdCodeQueryCallSites.of(label).map { enclosingFunctionOr(it) }.distinct()

    private fun callersOfFunction(function: KtNamedFunction): List<PsiElement> =
        ReferencesSearch
            .search(function, GlobalSearchScope.projectScope(myProject))
            .mapNotNull { PsiTreeUtil.getParentOfType(it.element, KtNamedFunction::class.java) }
            .filter { it != function }
            .distinct()

    /**
     * The function a call sits in. A call written at the top level of a file has none, so the call
     * itself stands in: a row the reader can click beats a caller silently dropped.
     */
    private fun enclosingFunctionOr(call: KtCallExpression): PsiElement =
        PsiTreeUtil.getParentOfType(call, KtNamedFunction::class.java) ?: call
}
