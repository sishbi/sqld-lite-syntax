package sishbi.sqd

import com.alecstrong.sql.psi.core.SqlLexerAdapter
import com.alecstrong.sql.psi.core.psi.NamedElement
import com.alecstrong.sql.psi.core.psi.SqlColumnAlias
import com.alecstrong.sql.psi.core.psi.SqlColumnName
import com.alecstrong.sql.psi.core.psi.SqlTableAlias
import com.alecstrong.sql.psi.core.psi.SqlTypes
import com.alecstrong.sql.psi.core.psi.SqlViewName
import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.tree.TokenSet

/**
 * Lets Find Usages run on a query label, on a bind argument, and on the names sql-psi owns: a table,
 * a view, a column and an alias. The platform refuses to search an element no provider claims, and
 * reports "Cannot search for usages" instead.
 *
 * sql-psi already gives each of its names a reference that resolves across the whole module, so the
 * platform's own handler finds every use of a table once the name is claimed here. Only the
 * claiming was missing.
 */
class SqdCodeFindUsagesProvider : FindUsagesProvider {

    /**
     * Indexes an identifier as code rather than as plain text, so a search for a Kotlin class finds
     * [SqdCodeJavaTypeReference] at the `import` and at the `AS` site. Without a scanner the
     * platform falls back to scanning the file as undifferentiated text, and both only ever appear
     * under "Non-code usages".
     */
    override fun getWordsScanner() =
        DefaultWordsScanner(
            SqlLexerAdapter(),
            TokenSet.create(SqlTypes.ID),
            TokenSet.create(SqlTypes.COMMENT, SqlTypes.JAVADOC),
            TokenSet.create(SqlTypes.STRING),
        )

    override fun canFindUsagesFor(element: PsiElement) =
        element is SqdCodeStmtIdentifierMixin ||
            element is SqdCodeBindParameterMixin ||
            element is NamedElement

    override fun getHelpId(element: PsiElement) = null

    /** Names what the search dialog and the results title call the target. */
    override fun getType(element: PsiElement) =
        when (element) {
            is SqdCodeBindParameterMixin -> "usages.type.bind.argument"
            is SqlViewName -> "usages.type.view"
            is SqlColumnName -> "usages.type.column"
            is SqlColumnAlias, is SqlTableAlias -> "usages.type.alias"
            is NamedElement -> "usages.type.table"
            else -> "usages.type.query"
        }.let { SqdCodeMessageBundle.message(it) }

    override fun getDescriptiveName(element: PsiElement) =
        (element as? PsiNamedElement)?.name.orEmpty()

    override fun getNodeText(element: PsiElement, useFullName: Boolean) =
        getDescriptiveName(element)
}
