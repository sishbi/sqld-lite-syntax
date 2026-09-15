package sishbi.sqldlite

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.alecstrong.sql.psi.core.psi.SqlIdentifier
import com.intellij.psi.util.PsiTreeUtil

/**
 * Puts a gutter icon on every query label, and lists that query's Kotlin callers behind it. The
 * count is in the tooltip because a gutter icon is an image and cannot draw text.
 *
 * A [LineMarkerProviderDescriptor], not a plain provider, so the icon can be switched off under
 * Settings | Editor | General | Gutter Icons. A plain provider has no name to list there.
 *
 * The search runs in [collectSlowLineMarkers] because it reads the word index. In
 * [getLineMarkerInfo] it would hold up highlighting on every keystroke.
 */
class SqldLiteCallerLineMarkerProvider : LineMarkerProviderDescriptor() {

    override fun getName() = SqldLiteMessageBundle.message("gutter.callers.name")

    override fun getIcon() = AllIcons.Actions.Find

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(
        elements: List<PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>,
    ) {
        val file = elements.firstOrNull()?.containingFile
        if (file !is SqldLiteFile || DumbService.isDumb(file.project)) return

        elements.mapNotNull { markerFor(it) }.forEach { result.add(it) }
    }

    /**
     * The marker for [element], or null when it does not start a query label. It has to hang off a
     * leaf: the platform logs an error for a marker on a composite element, whose line is ambiguous.
     */
    private fun markerFor(element: PsiElement): LineMarkerInfo<*>? {
        if (element.firstChild != null) return null
        val label = PsiTreeUtil.getParentOfType(element, SqldLiteStmtIdentifierMixin::class.java)
            ?: return null
        if (label.name == null || element != label.identifierLeaf()) return null

        val callers = SqldLiteQueryCallSites.of(label)
        return NavigationGutterIconBuilder
            .create(AllIcons.Actions.Find)
            .setTargets(callers)
            .setTooltipText(tooltipFor(callers.size))
            .setEmptyPopupText(SqldLiteMessageBundle.message("gutter.callers.none"))
            .createLineMarkerInfo(element)
    }

    private fun tooltipFor(count: Int) =
        if (count == 0) {
            SqldLiteMessageBundle.message("gutter.callers.none")
        } else {
            SqldLiteMessageBundle.message("gutter.callers.count", count)
        }

    /**
     * The first leaf of the label's own name, not of the label: a documented query starts with its
     * javadoc, and a marker there would sit several lines above the query it describes.
     */
    private fun SqldLiteStmtIdentifierMixin.identifierLeaf(): PsiElement? =
        PsiTreeUtil.getChildOfType(this, SqlIdentifier::class.java)?.let(PsiTreeUtil::getDeepestFirst)
}
