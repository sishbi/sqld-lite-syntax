package sishbi.sqd

import com.intellij.ide.hierarchy.CallHierarchyBrowserBase
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import java.util.Comparator
import javax.swing.JTree

/**
 * The Call Hierarchy window for a query label.
 *
 * Offers the caller view only. The callee view is left unbuilt because nothing a SqlDelight query
 * calls is visible to this plugin, and an empty second tab reads as a defect rather than an answer.
 */
class SqdCodeCallHierarchyBrowser(project: Project, label: PsiElement) :
    CallHierarchyBrowserBase(project, label) {

    override fun isApplicableElement(element: PsiElement) = element is SqdCodeStmtIdentifierMixin

    override fun createTrees(trees: MutableMap<in String, in JTree>) {
        trees[getCallerType()] = createTree(false)
    }

    override fun createHierarchyTreeStructure(
        type: String,
        psiElement: PsiElement,
    ): HierarchyTreeStructure? =
        SqdCodeCallerTreeStructure(psiElement.project, psiElement).takeIf { type == getCallerType() }

    override fun getElementFromDescriptor(descriptor: HierarchyNodeDescriptor): PsiElement? =
        descriptor.psiElement

    /** Null keeps the tree in discovery order, which groups a file's callers together. */
    override fun getComparator(): Comparator<NodeDescriptor<*>>? = null
}
