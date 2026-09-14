package sishbi.sqd

import com.alecstrong.sql.psi.core.psi.NamedElement
import com.alecstrong.sql.psi.core.psi.SchemaContributorIndex
import com.alecstrong.sql.psi.core.psi.SqlCreateTableStmt
import com.alecstrong.sql.psi.core.psi.SqlCreateViewStmt
import com.alecstrong.sql.psi.core.psi.SqlCreateVirtualTableStmt
import com.intellij.openapi.project.DumbService
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiTreeUtil

/**
 * Every statement that declares one table or view: the `CREATE`, and each `ALTER` after it.
 *
 * A migration chain is a linked list, not a set. sql-psi gives each `ALTER TABLE` a table of its
 * own, so the name in one `ALTER` resolves to the `ALTER` below it and a query resolves only to the
 * newest one. A search from any single link therefore reports one neighbour and stops, which hides
 * most of a table's history. Searching from every link at once reports the whole of it.
 */
object SqdCodeSchemaChain {

    /**
     * [name] first, then every statement that declares what it names. Just [name] on its own when
     * nothing declares it, such as a table a query invents, or while the IDE indexes.
     */
    fun of(name: NamedElement): List<NamedElement> {
        val project = name.project
        val key = name.name
        if (DumbService.isDumb(project)) return listOf(name)

        val index = SchemaContributorIndex.getInstance(project)
        val scope = GlobalSearchScope.allScope(project)

        // The index is keyed by the kind of statement, such as TableElement, not by what it
        // declares, so every kind is asked and the answers are filtered by name. There is one key
        // per kind, so this reads a handful of short lists.
        val declarations = StubIndex
            .getInstance()
            .getAllKeys(SchemaContributorIndex.KEY, project)
            .flatMap { index.get(it, project, scope) }
            .filter { it.name() == key }
            // The statement's own name, which is the first of its kind: an ALTER TABLE ... RENAME
            // carries a second one, and that names the table the rename produces, not this one.
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
