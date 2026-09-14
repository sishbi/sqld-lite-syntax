package sishbi.sqd

import com.alecstrong.sql.psi.core.psi.SqlCompositeElementImpl
import com.intellij.lang.ASTNode

/**
 * Makes a Kotlin type name written in a `.sq` or `.sqm` file a real reference, in an `import` and
 * after `AS` alike.
 *
 * Without one the name resolves only through [SqdCodeSqGotoDeclarationHandler], which the platform
 * asks for navigation and for nothing else. Find Usages then refuses to search from the name, and a
 * search for the Kotlin class reports the `import` line as a plain text match and the `AS` site not
 * at all.
 *
 * Extends [SqlCompositeElementImpl] for the reason [SqdCodeStmtIdentifierMixin] gives, and must be
 * `open` for the same reason.
 */
open class SqdCodeJavaTypeMixin(node: ASTNode) : SqlCompositeElementImpl(node) {

    override fun getReference() = SqdCodeJavaTypeReference(this)
}
