package sishbi.sqldlite

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.indexing.ScalarIndexExtension
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor

private val NAME: ID<String, Void?> = ID.create("sishbi.sqldlite.SqldLiteLabelIndex")

/**
 * Every query label in the project, keyed by name. Navigation from Kotlin has to find a label
 * without the project having been built, so it cannot read the generated sources.
 *
 * Indexing runs over the PSI, via `FileContent.getPsiFile`, so a `:` inside a string literal or a
 * comment is never taken for a label. The `PsiDependentIndex` marker interface that used to declare
 * that no longer exists on platform 262.
 */
class SqldLiteLabelIndex : ScalarIndexExtension<String>() {

    override fun getVersion() = 1

    override fun dependsOnFileContent() = true

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getInputFilter() = DefaultFileTypeSpecificInputFilter(SqldLiteFileType)

    override fun getName(): ID<String, Void?> = NAME

    override fun getIndexer() =
        DataIndexer<String, Void?, FileContent> { content ->
            val file = content.psiFile as? SqldLiteFile ?: return@DataIndexer emptyMap()
            PsiTreeUtil
                .findChildrenOfType(file, SqldLiteStmtIdentifierMixin::class.java)
                .mapNotNull { it.name }
                .associateWith { null }
        }
}

/** Reads [SqldLiteLabelIndex]. */
object SqldLiteLabels {

    /**
     * Every query label called [name] in [scope]. Empty while the project is indexing: reading an
     * index in dumb mode throws, and a navigation handler that throws takes Go To Declaration with
     * it.
     */
    fun find(project: Project, name: String, scope: GlobalSearchScope): List<SqldLiteStmtIdentifierMixin> {
        if (DumbService.isDumb(project)) return emptyList()

        val manager = PsiManager.getInstance(project)
        return FileBasedIndex
            .getInstance()
            .getContainingFiles(NAME, name, scope)
            .mapNotNull { manager.findFile(it) }
            .flatMap { PsiTreeUtil.findChildrenOfType(it, SqldLiteStmtIdentifierMixin::class.java) }
            .filter { it.name == name }
    }
}
