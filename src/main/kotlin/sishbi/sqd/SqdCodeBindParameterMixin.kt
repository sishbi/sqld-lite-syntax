package sishbi.sqd

import com.alecstrong.sql.psi.core.psi.SqlIdentifier
import com.alecstrong.sql.psi.core.psi.impl.SqlBindParameterImpl
import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException

/**
 * Gives a bind argument its name.
 *
 * Implements [PsiNamedElement] so the platform accepts one as a search target. Without it
 * `TargetElementUtil` finds nothing under the caret and Find Usages reports that it cannot search
 * here, even with the handler registered. Go To Declaration is unaffected, for the reason
 * [SqdCodeStmtIdentifierMixin] gives.
 *
 * Extends [SqlBindParameterImpl] because a mixin replaces the rule's `extends`, and the parent's own
 * PSI code expects that class.
 *
 * Must be `open`: the generated `SqdCodeBindParameterImpl` is Java and extends this class.
 */
open class SqdCodeBindParameterMixin(node: ASTNode) :
    SqlBindParameterImpl(node),
    PsiNamedElement {

    /** Null for a positional `?`, which carries no name of its own. */
    override fun getName(): String? = identifier()?.text

    /** The name's offset, so a usage points at the name rather than at its `:`. */
    override fun getTextOffset() = identifier()?.textOffset ?: super.getTextOffset()

    /** A bind argument generates a parameter, so it borrows that icon. */
    override fun getIcon(flags: Int) = AllIcons.Nodes.Parameter

    /** Titles the target of a Find Usages search, for the reason [SqdCodeStmtIdentifierMixin] gives. */
    override fun getPresentation(): ItemPresentation =
        PresentationData(name.orEmpty(), containingFile.name, getIcon(0), null)

    /** Always fails, for the reason [SqdCodeStmtIdentifierMixin.setName] gives. */
    override fun setName(name: String): PsiNamedElement =
        throw IncorrectOperationException("A SqlDelight bind argument cannot be renamed")

    private fun identifier() = PsiTreeUtil.getChildOfType(this, SqlIdentifier::class.java)
}
