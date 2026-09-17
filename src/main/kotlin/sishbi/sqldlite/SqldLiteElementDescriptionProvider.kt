package sishbi.sqldlite

import com.intellij.psi.ElementDescriptionLocation
import com.intellij.psi.ElementDescriptionProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.usageView.UsageViewTypeLocation

/**
 * What the platform calls this plugin's elements.
 *
 * Cmd-hover shows the target's description wherever no documentation provider answers, which
 * `SingleTargetElementInfo` builds as `<type> "<name>" [<file>]`. The type comes from this
 * extension point. Without it a call site read `Sqld Lite Call Site Target "create"`, because the
 * default provider words the element's class name, and a query label read `"findAllEvents"` with no
 * type at all.
 *
 * [SqldLiteFindUsagesProvider] names the same three things for the Find Usages panel. Both are
 * needed: the panel asks the language's provider, and this path does not.
 */
class SqldLiteElementDescriptionProvider : ElementDescriptionProvider {

    override fun getElementDescription(element: PsiElement, location: ElementDescriptionLocation): String? {
        val type = typeOf(element) ?: return null
        return when (location) {
            is UsageViewTypeLocation -> type
            else -> (element as? PsiNamedElement)?.name
        }
    }

    private fun typeOf(element: PsiElement): String? =
        when (element) {
            is SqldLiteCallSiteTarget -> SqldLiteMessageBundle.message("callsite.type")
            is SqldLiteStmtIdentifierMixin -> SqldLiteMessageBundle.message("usages.type.query")
            is SqldLiteBindParameterMixin -> SqldLiteMessageBundle.message("usages.type.bind.argument")
            else -> null
        }
}
