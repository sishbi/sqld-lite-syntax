package sishbi.sqd

import com.intellij.ide.hierarchy.CallHierarchyBrowserBase
import com.intellij.ide.hierarchy.HierarchyBrowser
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/**
 * Answers "what runs this query" for a `.sq` label.
 *
 * Without this the Call Hierarchy window has nothing to build and sits on "Select item to preview".
 * The platform cannot work the answer out itself, because SqlDelight generates the Kotlin function
 * and nothing points from a call back to the query.
 */
class SqdCodeCallHierarchyProvider : com.intellij.ide.hierarchy.HierarchyProvider {

    override fun getTarget(dataContext: DataContext): PsiElement? {
        // The Find Usages "Call Hierarchy" tab passes the selected row's element, which for a
        // SqlDelight usage is either the label itself or a bind argument inside the query.
        dataContext.getData(CommonDataKeys.PSI_ELEMENT)?.let { element ->
            labelOf(element)?.let { return it }
        }

        // The action can also fire from the editor, where the data is the caret's leaf token.
        val file = dataContext.getData(CommonDataKeys.PSI_FILE) ?: return null
        val editor = dataContext.getData(CommonDataKeys.EDITOR) ?: return null
        return file.findElementAt(editor.caretModel.offset)?.let { labelOf(it) }
    }

    /**
     * The query [element] belongs to. A bind argument resolves to its own query rather than to
     * nothing: "what runs this query" is the only call question a `.sq` file can answer, and it is
     * the question worth asking from an argument too.
     *
     * A label with no name is not a query. The grammar puts an empty `stmt_identifier` before every
     * statement, so an unlabelled one, such as every statement in a migration, has a label that
     * names nothing and generates nothing. Offering it left the window showing a blank row.
     */
    private fun labelOf(element: PsiElement): SqdCodeStmtIdentifierMixin? =
        (
            PsiTreeUtil.getParentOfType(element, SqdCodeStmtIdentifierMixin::class.java, false)
                ?: SqdCodeQuery.containing(element)?.label
            )?.takeIf { it.name != null }

    override fun createHierarchyBrowser(target: PsiElement): HierarchyBrowser =
        SqdCodeCallHierarchyBrowser(target.project, target)

    override fun browserActivated(hierarchyBrowser: HierarchyBrowser) {
        (hierarchyBrowser as SqdCodeCallHierarchyBrowser)
            .changeView(CallHierarchyBrowserBase.getCallerType())
    }
}
