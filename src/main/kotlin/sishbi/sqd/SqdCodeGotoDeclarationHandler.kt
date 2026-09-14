package sishbi.sqd

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList

/**
 * Go To Declaration on `fooQueries.selectAll()` in Kotlin opens `selectAll:` in `Foo.sq`, and on
 * the name of a named argument it opens the bind argument that argument fills.
 *
 * Nothing resolves through the Kotlin analysis API: the handler reads the call's own PSI and looks
 * the name up in [SqdCodeLabelIndex], so it behaves the same in K1 and K2 mode and does not need
 * the project to have been built. The platform adds these targets to the ones Kotlin finds rather
 * than replacing them, so the generated function stays reachable.
 */
class SqdCodeGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?,
    ): Array<PsiElement>? {
        val element = sourceElement ?: return null
        val targets = labelTargets(element) ?: bindArgumentTargets(element) ?: return null
        return targets.takeIf { it.isNotEmpty() }?.toTypedArray()
    }

    /** The `.sq` labels the call under the caret was generated from. */
    private fun labelTargets(element: PsiElement): List<PsiElement>? {
        val call = SqdCodeQueryCall.of(element) ?: return null
        return call.preferred(labelsFor(call, element)).toList()
    }

    /**
     * The bind argument the named argument under the caret fills, or null when the caret is not on
     * an argument name. Only the name counts: the value beside it already resolves on its own, so
     * claiming it too would add an unrelated target to every argument of every query call.
     */
    private fun bindArgumentTargets(element: PsiElement): List<PsiElement>? {
        val argument = PsiTreeUtil.getParentOfType(element, KtValueArgument::class.java)
            ?: return null
        val name = argument.getArgumentName() ?: return null
        if (!PsiTreeUtil.isAncestor(name, element, false)) return null

        val arguments = argument.parent as? KtValueArgumentList ?: return null
        val call = arguments.parent as? KtCallExpression ?: return null
        val queryCall = SqdCodeQueryCall.forCall(call) ?: return emptyList()

        val position = arguments.arguments.indexOf(argument)
        return queryCall
            .preferred(labelsFor(queryCall, element))
            .mapNotNull { SqdCodeQuery.of(it).parameterFor(name.asName.identifier, position) }
    }

    private fun labelsFor(call: SqdCodeQueryCall, element: PsiElement): List<SqdCodeStmtIdentifierMixin> {
        val project = element.project
        return SqdCodeLabels.find(project, call.queryName, GlobalSearchScope.projectScope(project))
    }
}
