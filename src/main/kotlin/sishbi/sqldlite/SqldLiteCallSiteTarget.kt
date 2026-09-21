package sishbi.sqldlite

import com.intellij.ide.projectView.PresentationData
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.FakePsiElement
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedDeclaration

/**
 * A Kotlin call site offered as a navigation target, named and placed.
 *
 * A row of the Choose Declaration popup, and of the gutter's caller list, is titled with the
 * target's own [ItemPresentation]. A Kotlin expression has none, so the platform falls back to the
 * element's text: a call spread over twenty argument lines became one row several screens wide.
 * Targeting the callee alone fixed the width and left every row reading only the query name.
 *
 * This wrapper keeps the callee as the place the caret lands and adds the enclosing class and
 * member beside the name, so two calls to one query are told apart.
 *
 * The presentation is built here, when the target is, and holds strings. A cell renderer paints on
 * the EDT holding no read action, so nothing it shows may be read from PSI at painting time.
 */
class SqldLiteCallSiteTarget private constructor(
    private val anchor: PsiElement,
    private val presentation: ItemPresentation,
    /** The enclosing function as it is written, for the documentation popup. Null at top level. */
    val signature: String?,
    /** The enclosing class, qualified, or the package when the call sits at the top level. */
    val qualifiedContainer: String?,
    /** What the target is, as the documentation popup titles it: a call, or one of its arguments. */
    val kind: String,
) : FakePsiElement() {

    /** The callee, so the caret lands on the call rather than on the enclosing declaration. */
    override fun getParent(): PsiElement = anchor

    override fun getNavigationElement(): PsiElement = anchor

    override fun getPresentation(): ItemPresentation = presentation

    override fun getName(): String? = presentation.presentableText

    companion object {
        /** How much of a name a row shows before the platform's ellipsis. */
        private const val NAME_LIMIT = 60

        /**
         * A target that navigates to [anchor] and shows [name], or [anchor] itself when it has no
         * enclosing declaration to name. Nothing is gained by a wrapper that adds nothing.
         *
         * [kind] is what the documentation popup titles the target. It is passed in because the
         * same wrapper carries a call and the arguments that call passes, and titling an argument
         * `query call` named it after the thing holding it.
         */
        fun of(anchor: PsiElement, name: String, kind: String): PsiElement {
            val container = containerOf(anchor) ?: return anchor
            val shortName = StringUtil.shortenTextWithEllipsis(name, NAME_LIMIT, 0, true)
            return SqldLiteCallSiteTarget(
                anchor,
                PresentationData(shortName, container, anchor.getIcon(0), null),
                signatureOf(anchor),
                qualifiedContainerOf(anchor),
                kind,
            )
        }

        /** How many enclosing names a row shows, innermost last. */
        private const val CONTAINER_DEPTH = 2

        /**
         * The declarations holding [element], as `Repository.record`. Null when nothing named holds
         * it, which a top-level call does.
         *
         * Only a declaration with a name of its own counts. A lambda is a [KtNamedDeclaration]
         * whose name is `<anonymous>`, and so is an object literal, so a call inside either named
         * the row after neither the class nor the method a reader is looking for.
         *
         * No `()` after a function name: the platform reads a location string as a parenthesised
         * container and strips a trailing bracket, which left `Repository.record(` on the row.
         */
        private fun containerOf(element: PsiElement): String? =
            owningDeclarationsOf(element)
                .mapNotNull { it.name }
                .take(CONTAINER_DEPTH)
                .toList()
                .reversed()
                .joinToString(".")
                .ifEmpty { null }

        /**
         * The function holding [element], as [SqldLiteKotlinSignature] writes it.
         *
         * Read here, when the target is built, because a popup is painted with no read action.
         */
        private fun signatureOf(element: PsiElement): String? =
            owningDeclarationsOf(element)
                .filterIsInstance<KtFunction>()
                .firstOrNull()
                ?.let { SqldLiteKotlinSignature.of(it) }

        /**
         * The class holding [element] as `com.example.BookLoanService`, which is how Kotlin's own
         * popup places a function. Null when the file declares no package and no class.
         *
         * The file name alone said less than the row above it: a repository member and the query
         * it calls are usually named alike, so the package is the part a reader does not know.
         */
        private fun qualifiedContainerOf(element: PsiElement): String? {
            val owner = owningDeclarationsOf(element)
                .filterIsInstance<KtClassOrObject>()
                .firstOrNull()
            owner?.fqName?.asString()?.let { return it }
            return (element.containingFile as? KtFile)
                ?.packageFqName
                ?.asString()
                ?.ifEmpty { null }
        }

        /** The named declarations holding [element], innermost first, anonymous ones skipped. */
        fun owningDeclarationsOf(element: PsiElement): Sequence<KtNamedDeclaration> =
            generateSequence(element.parent) { it.parent }
                .takeWhile { it !is PsiFile }
                .filterIsInstance<KtNamedDeclaration>()
                .filter { it.nameIdentifier != null }
    }
}
