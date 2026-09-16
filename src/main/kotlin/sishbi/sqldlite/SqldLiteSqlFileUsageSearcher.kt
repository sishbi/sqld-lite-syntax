package sishbi.sqldlite

import com.alecstrong.sql.psi.core.psi.NamedElement
import com.intellij.find.findUsages.CustomUsageSearcher
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.psi.PsiElement
import com.intellij.usages.Usage
import com.intellij.util.Processor

/**
 * Adds every occurrence of a table, view or column name in `.sql` files to its usages.
 *
 * The match is on the name alone, so a hit in a comment, in a string literal or on an unrelated
 * column of the same name is reported too. For a migration written by hand next to the `.sq` schema
 * the name is nearly always the same object, and a name search is the only thing available: a `.sql`
 * file has unrelated PSI and no reference back to a `.sq` declaration.
 *
 * [SqldLiteSqlFileReferenceUsageSearcher] replaces this wherever the IDE's own SQL support is
 * present, because it can tell a reference from a comment. This one is what Community and every
 * other IDE without the Database plugin get, and it steps aside rather than report a second time.
 *
 * A [CustomUsageSearcher] adds to the platform's own handler, so nothing already reported is lost.
 */
class SqldLiteSqlFileUsageSearcher : CustomUsageSearcher() {

    override fun processElementUsages(
        element: PsiElement,
        processor: Processor<in Usage>,
        options: FindUsagesOptions,
    ) {
        if (element !is NamedElement) return
        if (referenceSearcherRegistered()) return

        searchSqlFiles(element) { true }.forEach { processor.process(it) }
    }
}
