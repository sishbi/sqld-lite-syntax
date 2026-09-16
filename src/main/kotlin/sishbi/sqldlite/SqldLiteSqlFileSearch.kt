package sishbi.sqldlite

import com.alecstrong.sql.psi.core.psi.NamedElement
import com.intellij.find.findUsages.CustomUsageSearcher
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.usageView.UsageInfo
import com.intellij.usages.UsageInfo2UsageAdapter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

internal const val SQL_FILE_EXTENSION = "sql"

/**
 * [SqldLiteSqlFileReferenceUsageSearcher] by name, so asking whether it is registered never loads
 * it. The class reaches into the Database plugin, and on an IDE without that plugin loading it
 * would fail.
 */
private const val REFERENCE_SEARCHER = "sishbi.sqldlite.SqldLiteSqlFileReferenceUsageSearcher"

/**
 * Whether the searcher that reads the IDE's own SQL PSI is registered. Only one `.sql` searcher may
 * report, or every hit appears twice.
 *
 * `sqld-lite-withDatabase.xml` registers it, and that descriptor is loaded only where the Database
 * plugin is enabled, so this one condition covers the plugin being absent, being disabled and being
 * unlicensed. Reading the extension point rather than the plugin list keeps the two in step: what
 * matters is whether the other searcher will run, not why.
 */
internal fun referenceSearcherRegistered() =
    CustomUsageSearcher.EP_NAME.extensionList.any { it.javaClass.name == REFERENCE_SEARCHER }

/**
 * Occurrences of the name a `.sq` element declares in the project's `.sql` files.
 *
 * Both `.sql` searchers share this. The word index is the only way in: a `.sql` file belongs to the
 * IDE's own SQL support, has unrelated PSI and contributes nothing to sql-psi's schema index, so
 * there is no reference to follow. [accept] is what one searcher does and the other does not - it
 * decides whether a candidate the word index offered is a usage.
 *
 * Returns nothing while the project is indexing, because the word index cannot be read then.
 */
internal fun searchSqlFiles(
    element: NamedElement,
    accept: (candidate: PsiElement) -> Boolean,
): List<UsageInfo2UsageAdapter> {
    // The search runs on a pooled thread holding no read action, and building a UsageInfo reads the
    // file it points at, so the whole list is built inside one.
    return ApplicationManager.getApplication().runReadAction(
        Computable { collect(element, accept) },
    )
}

private fun collect(
    element: NamedElement,
    accept: (PsiElement) -> Boolean,
): List<UsageInfo2UsageAdapter> {
    val name = element.name ?: return emptyList()
    val project = element.project

    if (DumbService.isDumb(project)) return emptyList()

    val scope = sqlFileScope(project) ?: return emptyList()

    // The processor runs on one thread per file, so what it writes to has to be thread safe.
    val usages = ConcurrentLinkedQueue<UsageInfo>()

    // One occurrence reaches the processor once per element that contains it: the token, then the
    // identifier, the reference, the statement and the file. All five are the same hit to a reader,
    // and reporting each of them put the same line in the panel five times. The first is the
    // innermost, so the first for a position is the one to keep.
    val reported = ConcurrentHashMap.newKeySet<Pair<VirtualFile?, Int>>()

    PsiSearchHelper
        .getInstance(project)
        .processElementsWithWord(
            { candidate, offset ->
                val position = candidate.containingFile?.virtualFile to
                    candidate.textRange.startOffset + offset
                if (reported.add(position) && accept(candidate)) {
                    usages.add(UsageInfo(candidate, offset, offset + name.length))
                }
                true
            },
            scope,
            name,
            // A name can sit in a statement, a comment or a quoted identifier. Which of those count
            // is the searcher's decision, not this one's.
            UsageSearchContext.ANY,
            true,
        )
    return usages.map { UsageInfo2UsageAdapter(it) }
}

/**
 * The project's `.sql` files, or null when no plugin claims the extension. Without a file type the
 * files are not indexed, so there is nothing for the word search to read.
 */
private fun sqlFileScope(project: Project): GlobalSearchScope? {
    val fileType = FileTypeManager.getInstance().getFileTypeByExtension(SQL_FILE_EXTENSION)
    if (fileType == UnknownFileType.INSTANCE) return null

    return GlobalSearchScope.getScopeRestrictedByFileTypes(
        GlobalSearchScope.projectScope(project),
        fileType,
    )
}
