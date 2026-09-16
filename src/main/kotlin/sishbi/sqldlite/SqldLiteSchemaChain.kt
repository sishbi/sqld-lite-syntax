package sishbi.sqldlite

import com.alecstrong.sql.psi.core.psi.NamedElement
import com.alecstrong.sql.psi.core.psi.SqlCreateTableStmt
import com.alecstrong.sql.psi.core.psi.SqlCreateViewStmt
import com.alecstrong.sql.psi.core.psi.SqlCreateVirtualTableStmt
import com.intellij.openapi.project.DumbService
import com.intellij.psi.util.PsiTreeUtil

/**
 * Every statement that declares one table or view: the `CREATE`, and each `ALTER` after it.
 *
 * A migration chain is a linked list, not a set. sql-psi gives each `ALTER TABLE` a table of its
 * own, so the name in one `ALTER` resolves to the `ALTER` below it and a query resolves only to the
 * newest one. A search from any single link therefore reports one neighbour and stops, which hides
 * most of a table's history.
 *
 * `SqlFileBase.schemaChain` reads the whole chain, oldest first. This turns each statement it
 * returns into the name that statement declares, which is what a search and a declaration jump both
 * work on.
 */
object SqldLiteSchemaChain {

    /**
     * [name] first, then every statement that declares what it names. Just [name] on its own when
     * nothing declares it, such as a table a query invents, or while the IDE indexes.
     */
    fun of(name: NamedElement): List<NamedElement> {
        // The chain is read from a stub index, which cannot be read while the project indexes.
        if (DumbService.isDumb(name.project)) return listOf(name)
        val file = name.containingFile as? SqldLiteFile ?: return listOf(name)

        val declarations =
            file
                .schemaChain(name.name)
                // The statement's own name, which is the first of its kind: an ALTER TABLE ...
                // RENAME carries a second one, and that names the table the rename produces, not
                // this one.
                .mapNotNull { PsiTreeUtil.findChildOfType(it, name.javaClass) }

        // [name] leads, and is here even when the index has no statement holding it: it is the one
        // the caret is on, and a table a query invents is declared nowhere.
        return (listOf(name) + declarations).distinct()
    }

    /**
     * The `CREATE` that first declares whatever [name] names, and null when nothing does.
     *
     * This is what a use of the name points at. sql-psi resolves a use to the newest `ALTER`
     * instead, because that is the statement holding the current column set, which sends Go To
     * Declaration and the Find Usages target to a migration halfway up the chain.
     */
    fun creationOf(name: NamedElement): NamedElement? =
        of(name).firstOrNull { it != name && creates(it) }

    private fun creates(name: NamedElement) =
        PsiTreeUtil.getParentOfType(name, SqlCreateTableStmt::class.java, true) != null ||
            PsiTreeUtil.getParentOfType(name, SqlCreateViewStmt::class.java, true) != null ||
            PsiTreeUtil.getParentOfType(name, SqlCreateVirtualTableStmt::class.java, true) != null
}
