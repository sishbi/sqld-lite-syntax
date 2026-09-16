package sishbi.sqldlite

import com.alecstrong.sql.psi.core.psi.SqlColumnName
import com.alecstrong.sql.psi.core.psi.SqlCteTableName
import com.alecstrong.sql.psi.core.psi.SqlNewTableName
import com.alecstrong.sql.psi.core.psi.SqlTableName
import com.alecstrong.sql.psi.core.psi.SqlViewName
import com.intellij.icons.AllIcons
import com.intellij.ide.IconProvider
import com.intellij.psi.PsiElement
import javax.swing.Icon

/**
 * The icon beside a table, view or column name.
 *
 * This is the only way to give one to a sql-psi element: the classes come from a library, so a
 * `getIcon` override is not available, and `ElementBase.getIcon` consults this extension point
 * before it falls back to the icon of the containing file. Without it every name in a `.sq` file
 * shows the same file icon, so a Find Usages target line, a Go To Symbol row and a structure view
 * row cannot be told apart.
 *
 * The icons are the platform's own database icons, so they follow the theme and need no `_dark`
 * variant of ours.
 */
class SqldLiteIconProvider : IconProvider() {

    override fun getIcon(element: PsiElement, flags: Int): Icon? =
        when (element) {
            // A view and a common table expression are read exactly as a table is, and the platform
            // ships no icon that tells them apart.
            is SqlTableName, is SqlNewTableName, is SqlCteTableName, is SqlViewName ->
                AllIcons.Nodes.DataTables
            is SqlColumnName -> AllIcons.Nodes.DataColumn
            else -> null
        }
}
