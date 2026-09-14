package sishbi.sqd

import com.alecstrong.sql.psi.core.psi.SqlBindParameter
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/**
 * Go To Declaration inside a `.sq` or `.sqm` file.
 *
 * Two jumps, both the reverse of what [SqdCodeGotoDeclarationHandler] does from Kotlin:
 * - a query label goes to the Kotlin calls SqlDelight generated for it;
 * - a bind argument goes to the value each of those calls passes for it.
 *
 * A Kotlin type name is not here. [SqdCodeJavaTypeReference] resolves it, which navigates and is
 * searchable, where a handler navigates only.
 *
 * None of them uses the Kotlin analysis API, so all behave the same in K1 and K2 mode and none
 * needs the project to have been built.
 */
class SqdCodeSqGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?,
    ): Array<PsiElement>? {
        val element = sourceElement ?: return null
        if (element.containingFile !is SqdCodeFile) return null
        if (DumbService.isDumb(element.project)) return null

        val targets = bindArgumentTargets(element)
            ?: labelTargets(element)
            ?: return null
        return targets.takeIf { it.isNotEmpty() }?.toTypedArray()
    }

    /**
     * The Kotlin calls to the query label under the caret, or null when the caret is not on one.
     *
     * The target is the whole call, not its callee. Both land the caret on the same line, and a
     * call always exists where a callee may not, so this cannot fail on a call the parser recovered
     * from.
     */
    private fun labelTargets(element: PsiElement): List<PsiElement>? {
        val label = PsiTreeUtil.getParentOfType(element, SqdCodeStmtIdentifierMixin::class.java)
            ?: return null
        return SqdCodeQueryCallSites.of(label)
    }

    /**
     * The value each call passes for the bind argument under the caret, or null when the caret is
     * not on one.
     */
    private fun bindArgumentTargets(element: PsiElement): List<PsiElement>? {
        val parameter = PsiTreeUtil.getParentOfType(element, SqlBindParameter::class.java) ?: return null
        return SqdCodeBindArgumentSites.of(parameter)
    }
}
