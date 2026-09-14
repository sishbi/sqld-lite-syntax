package sishbi.sqd

import com.alecstrong.sql.psi.core.psi.NamedElement
import com.alecstrong.sql.psi.core.psi.SchemaContributor
import com.alecstrong.sql.psi.core.psi.SqlTableName
import com.intellij.codeInsight.TargetElementEvaluatorEx2
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiTreeUtil

/**
 * Chooses what the caret is on when the caret sits on the name a `CREATE` or `ALTER` statement
 * declares.
 *
 * The platform asks a reference what it points at, and the name in an `ALTER TABLE` points at the
 * statement below it in the migration chain. So Find Usages from the newest migration titled itself
 * with the one before it and offered that as the target, and the search looked as though it had
 * started somewhere else. A declaration is its own target.
 */
class SqdCodeTargetElementEvaluator : TargetElementEvaluatorEx2() {

    override fun getElementByReference(ref: PsiReference, flags: Int): PsiElement? {
        val element = ref.element
        if (element.containingFile !is SqdCodeFile) return null
        if (declares(element)) return element

        // A use points at the newest ALTER, which is where the current columns are but not where
        // the table comes from. The CREATE is what a reader means by the declaration.
        return (element as? SqlTableName)?.let { SqdCodeSchemaChain.creationOf(it) }
    }

    /**
     * Whether [element] is the name its own statement declares, which is the first name the
     * statement holds. A `CREATE INDEX` holds the indexed table's name as well, and that one is a
     * use of another statement's declaration.
     */
    private fun declares(element: PsiElement): Boolean {
        val statement = PsiTreeUtil.getParentOfType(element, SchemaContributor::class.java, false)
        return statement != null &&
            PsiTreeUtil.findChildOfType(statement, NamedElement::class.java) == element
    }
}
