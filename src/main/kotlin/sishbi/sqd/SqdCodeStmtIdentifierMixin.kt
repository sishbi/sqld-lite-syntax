package sishbi.sqd

import com.alecstrong.sql.psi.core.psi.SqlCompositeElementImpl
import com.alecstrong.sql.psi.core.psi.SqlIdentifier
import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException

/**
 * Gives a query label its name. The label's identifier is a [SqlIdentifier] from the sql-psi grammar
 * and carries none, and nothing else in the tree reports it.
 *
 * Implements [PsiNamedElement] so the platform accepts a label as a search target. Without it
 * `TargetElementUtil` finds nothing under the caret and Find Usages reports that it cannot search
 * here, even with the label's handler registered. Go To Declaration is unaffected: a declaration
 * handler is given the token under the caret and walks up itself.
 *
 * Extends [SqlCompositeElementImpl] because every rule in this grammar implements
 * `SqlCompositeElement`, whose `getContainingFile` returns an `SqlFileBase`. An
 * `ASTWrapperPsiElement` does not satisfy that and the generated code fails to compile.
 *
 * Must be `open`: the generated `SqdCodeStmtIdentifierImpl` is Java and extends this class.
 */
open class SqdCodeStmtIdentifierMixin(node: ASTNode) :
    SqlCompositeElementImpl(node),
    PsiNamedElement {

    /** Null for a bare statement. Both the javadoc comment and the label itself are optional. */
    override fun getName(): String? = identifier()?.text

    /** The name's offset, so a usage points at the label rather than at its javadoc. */
    override fun getTextOffset() = identifier()?.textOffset ?: super.getTextOffset()

    /** A label generates a function, so it borrows that icon. */
    override fun getIcon(flags: Int) = AllIcons.Nodes.Method

    /**
     * Titles the target of a Find Usages search. The panel reads the icon and the text from here
     * only, never from [getIcon], so without this the target line has no icon at all.
     */
    override fun getPresentation(): ItemPresentation =
        PresentationData(name.orEmpty(), containingFile.name, getIcon(0), null)

    /**
     * Always fails. A rename has to change the generated Kotlin function and its calls too, which
     * this plugin does not generate, so a label-only rename would leave the project uncompilable.
     */
    override fun setName(name: String): PsiNamedElement =
        throw IncorrectOperationException("A SqlDelight query label cannot be renamed")

    private fun identifier() = PsiTreeUtil.getChildOfType(this, SqlIdentifier::class.java)
}
