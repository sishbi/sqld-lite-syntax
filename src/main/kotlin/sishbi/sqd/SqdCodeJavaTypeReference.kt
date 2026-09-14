package sishbi.sqd

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import sishbi.sqd.psi.SqdCodeImportStmt

/**
 * The Kotlin class a `.sq` type name refers to.
 *
 * A name with no dots is qualified through the file's own `import` statements, which is how
 * SqlDelight itself reads it. A name that is already qualified is looked up as written.
 *
 * The range covers the whole name, package included, so Ctrl-click anywhere on a qualified import
 * navigates. Find Usages still matches: it looks up the reference at the offset of the short name,
 * which lies inside that range.
 */
class SqdCodeJavaTypeReference(javaType: SqdCodeJavaTypeMixin) :
    PsiReferenceBase<SqdCodeJavaTypeMixin>(javaType, TextRange(0, javaType.textLength)) {

    override fun resolve(): PsiElement? {
        val project = element.project
        if (DumbService.isDumb(project)) return null

        val written = element.text.filterNot { it.isWhitespace() }
        val qualified = if ('.' in written) written else qualify(written) ?: return null

        return KotlinFullClassNameIndex
            .get(qualified, project, GlobalSearchScope.allScope(project))
            .firstOrNull()
    }

    /** The import that ends in [name], as a fully qualified name. Null when nothing imports it. */
    private fun qualify(name: String): String? =
        PsiTreeUtil
            .findChildrenOfType(element.containingFile, SqdCodeImportStmt::class.java)
            .mapNotNull { it.javaType?.text?.filterNot { character -> character.isWhitespace() } }
            .firstOrNull { it.substringAfterLast('.') == name }

    /**
     * Always fails. Renaming the class has to rewrite the `.sq` file and the generated Kotlin
     * together, and this plugin generates nothing, so a rename here would leave the project
     * uncompilable.
     */
    override fun handleElementRename(newElementName: String): PsiElement =
        throw IncorrectOperationException("A SqlDelight type name cannot be renamed")
}
