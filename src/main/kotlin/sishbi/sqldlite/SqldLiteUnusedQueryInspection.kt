package sishbi.sqldlite

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile

/**
 * Greys out a query label that no Kotlin code calls. Without it a dead query is indistinguishable
 * from a broken jump: Go To Declaration reports "Cannot find declaration to go to" either way.
 *
 * Uses the same word-index scan as navigation ([SqldLiteQueryCallSites]), not a nested Find Usages
 * run, which starts its own indexing and progress work for every label while the user types.
 */
class SqldLiteUnusedQueryInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (holder.file !is SqldLiteFile) return PsiElementVisitor.EMPTY_VISITOR
        if (DumbService.isDumb(holder.project)) return PsiElementVisitor.EMPTY_VISITOR

        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is SqldLiteStmtIdentifierMixin) return
                if (element.name == null) return
                if (SqldLiteQueryCallSites.any(element)) return

                holder.registerProblem(
                    element,
                    SqldLiteMessageBundle.message("inspection.unused.query.message"),
                    ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                    DeleteQueryFix(element),
                )
            }
        }
    }

    /** Deletes the label and everything up to the next one, so a grouped query goes as a group. */
    class DeleteQueryFix(label: SqldLiteStmtIdentifierMixin) :
        LocalQuickFixOnPsiElement(label) {

        override fun getFamilyName() = SqldLiteMessageBundle.message("inspection.unused.query.fix")

        override fun getText() = familyName

        override fun invoke(
            project: Project,
            file: PsiFile,
            startElement: PsiElement,
            endElement: PsiElement,
        ) {
            val last = generateSequence(startElement.nextSibling) { it.nextSibling }
                .takeWhile { it !is SqldLiteStmtIdentifierMixin }
                .lastOrNull()
                ?: startElement
            startElement.parent.deleteChildRange(startElement, last)
        }
    }
}
