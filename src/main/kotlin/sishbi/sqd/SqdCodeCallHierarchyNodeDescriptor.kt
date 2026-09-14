package sishbi.sqd

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.util.CompositeAppearance
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement

/** One row of the call hierarchy: the query, or a Kotlin function that reaches it. */
class SqdCodeCallHierarchyNodeDescriptor(
    project: Project,
    parent: NodeDescriptor<*>?,
    element: PsiElement,
    isBase: Boolean,
) : HierarchyNodeDescriptor(project, parent, element, isBase) {

    override fun update(): Boolean {
        val changed = super.update()
        val element = psiElement ?: return changed

        icon = element.getIcon(0)
        val text = CompositeAppearance()
        text.ending.addText((element as? PsiNamedElement)?.name ?: element.text)
        element.containingFile?.let { text.ending.addText("  ${it.name}", getPackageNameAttributes()) }

        if (myHighlightedText.toString() == text.toString()) return changed
        myHighlightedText = text
        return true
    }
}
