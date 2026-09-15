package sishbi.sqldlite

import com.alecstrong.sql.psi.core.psi.NamedElement
import com.intellij.find.findUsages.CustomUsageSearcher
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.usageView.UsageInfo
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.util.Processor
import java.util.concurrent.ConcurrentLinkedQueue

internal const val SQL_FILE_EXTENSION = "sql"

/**
 * Adds occurrences of a table, view or column name in `.sql` files to its usages.
 *
 * The match is on the name alone. A `.sql` file belongs to the IDE's own SQL support, has unrelated
 * PSI and contributes nothing to sql-psi's schema index, so there is no reference to follow and no
 * way to tell the table this name declares from another table of the same name. For a migration
 * written by hand next to the `.sq` schema the name is nearly always the same object.
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

        // The search runs on a pooled thread holding no read action, and building a UsageInfo reads
        // the file it points at, so the whole list is built inside one.
        val usages = ApplicationManager.getApplication().runReadAction(Computable { search(element) })
        usages.forEach { processor.process(it) }
    }

    private fun search(element: NamedElement): List<UsageInfo2UsageAdapter> {
        val name = element.name ?: return emptyList()
        val project = element.project

        // The word index cannot be read while the project is indexing.
        if (DumbService.isDumb(project)) return emptyList()

        val scope = sqlFileScope(project) ?: return emptyList()

        // The processor runs on one thread per file, so what it writes to has to be thread safe.
        val usages = ConcurrentLinkedQueue<UsageInfo>()
        PsiSearchHelper
            .getInstance(project)
            .processElementsWithWord(
                { candidate, offset ->
                    usages.add(UsageInfo(candidate, offset, offset + name.length))
                    true
                },
                scope,
                name,
                // A name can sit in a statement, a comment or a quoted identifier, and all three
                // are the same object to a reader.
                UsageSearchContext.ANY,
                true,
            )
        return usages.map { UsageInfo2UsageAdapter(it) }
    }

    /**
     * The project's `.sql` files, or null when no plugin claims the extension. Without a file type
     * the files are not indexed, so there is nothing for the word search to read.
     */
    private fun sqlFileScope(project: Project): GlobalSearchScope? {
        val fileType = FileTypeManager.getInstance().getFileTypeByExtension(SQL_FILE_EXTENSION)
        if (fileType == UnknownFileType.INSTANCE) return null

        return GlobalSearchScope.getScopeRestrictedByFileTypes(
            GlobalSearchScope.projectScope(project),
            fileType,
        )
    }
}
