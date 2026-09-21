package sishbi.sqldlite

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.util.PsiTreeUtil
import java.util.concurrent.ConcurrentLinkedQueue
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * Finds the Kotlin calls SqlDelight generated for a query label.
 *
 * The search runs over the word index, not the Kotlin analysis API, so it behaves the same in K1
 * and K2 mode and does not need the project to have been built. Navigation, Find Usages and the
 * unused-query inspection all come here, so all three agree on what counts as a call site.
 */
object SqldLiteQueryCallSites {

    /**
     * The calls to [label]. Empty while the project is indexing: the word index cannot be read in
     * dumb mode.
     *
     * Takes its own read action, because Find Usages calls this from a pooled thread holding none.
     * A read action already held is reentrant, so the callers that run inside one pay nothing.
     */
    fun of(label: SqldLiteStmtIdentifierMixin): List<KtCallExpression> =
        ApplicationManager.getApplication().runReadAction(Computable { search(label) })

    /** True when [label] has at least one call. */
    fun any(label: SqldLiteStmtIdentifierMixin): Boolean = of(label).isNotEmpty()

    /**
     * The calls to [label] as navigation targets: the query name, the class and member holding the
     * call, and the callee as the place the caret lands. [SqldLiteCallSiteTarget] says why.
     */
    fun navigationTargetsOf(label: SqldLiteStmtIdentifierMixin): List<PsiElement> =
        of(label).map { call ->
            val callee = call.calleeExpression ?: call
            SqldLiteCallSiteTarget.of(
                callee,
                callee.text,
                SqldLiteMessageBundle.message("callsite.type"),
            )
        }

    private fun search(label: SqldLiteStmtIdentifierMixin): List<KtCallExpression> {
        val name = label.name ?: return emptyList()
        val project = label.project
        if (DumbService.isDumb(project)) return emptyList()

        val fileBaseName = label.containingFile.name.substringBeforeLast('.')

        // The processor runs on several threads at once, one per file, so what it writes to has to
        // be thread safe. An unsynchronised LinkedHashSet here handed out nulls from its corrupted
        // table, which crashed navigation on a large project and never on a small one.
        val calls = ConcurrentLinkedQueue<KtCallExpression>()
        PsiSearchHelper
            .getInstance(project)
            .processElementsWithWord(
                { candidate, _ ->
                    calls.addAll(callsIn(candidate, name, fileBaseName))
                    true
                },
                GlobalSearchScope.getScopeRestrictedByFileTypes(
                    GlobalSearchScope.projectScope(project),
                    KotlinFileType.INSTANCE,
                ),
                name,
                UsageSearchContext.IN_CODE,
                true,
            )
        return calls.distinct()
    }

    /**
     * [candidate] and its first child, whichever of them is a call to [name] on a receiver naming
     * [fileBaseName]. Both are tried because the index reports whichever element holds the word,
     * leaf identifier or the reference around it, and can report the same call through both.
     */
    private fun callsIn(candidate: PsiElement, name: String, fileBaseName: String): List<KtCallExpression> =
        listOfNotNull(candidate, candidate.firstChild)
            .filter { at ->
                val call = SqldLiteQueryCall.of(at) ?: return@filter false
                call.queryName == name &&
                    (call.queriesFileBaseName == null || call.queriesFileBaseName == fileBaseName)
            }
            .mapNotNull { PsiTreeUtil.getParentOfType(it, KtCallExpression::class.java) }
}
