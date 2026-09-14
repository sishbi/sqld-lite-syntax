package sishbi.sqd

import com.alecstrong.sql.psi.core.psi.SchemaContributor
import com.alecstrong.sql.psi.core.psi.SqlBindParameter
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageTypeProvider
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import sishbi.sqd.psi.SqdCodeImportStmt

private val QUERY = UsageType { SqdCodeMessageBundle.message("usages.group.query") }
private val BIND_ARGUMENT = UsageType { SqdCodeMessageBundle.message("usages.group.bind.argument") }
private val QUERY_CALL = UsageType { SqdCodeMessageBundle.message("usages.group.query.call") }
private val QUERY_ARGUMENT = UsageType { SqdCodeMessageBundle.message("usages.group.query.argument") }
private val TYPE_IMPORT = UsageType { SqdCodeMessageBundle.message("usages.group.type.import") }
private val COLUMN_TYPE = UsageType { SqdCodeMessageBundle.message("usages.group.column.type") }
private val SCHEMA = UsageType { SqdCodeMessageBundle.message("usages.group.schema") }
private val STATEMENT = UsageType { SqdCodeMessageBundle.message("usages.group.statement") }

/**
 * Names the group a usage of a query appears under in the Find Usages panel, on both sides: the
 * `.sq` declaration a Kotlin search reports, and the Kotlin call a `.sq` search reports. A usage
 * with no type falls into "Unclassified".
 *
 * Every occurrence inside a `.sq` file gets a group, so nothing this plugin owns lands in
 * "Unclassified". A table name had none, which put every `CREATE TABLE`, `ALTER TABLE` and `SELECT`
 * site there together, with no way to tell a declaration from a use.
 */
class SqdCodeUsageTypeProvider : UsageTypeProvider {

    override fun getUsageType(element: PsiElement): UsageType? =
        when (element.containingFile) {
            is SqdCodeFile -> sqUsageType(element)
            else -> generatedCallUsageType(element)
        }

    private fun sqUsageType(element: PsiElement): UsageType? =
        when {
            PsiTreeUtil.getParentOfType(element, SqdCodeImportStmt::class.java, false) != null ->
                TYPE_IMPORT

            PsiTreeUtil.getParentOfType(element, SqdCodeJavaTypeMixin::class.java, false) != null ->
                COLUMN_TYPE

            PsiTreeUtil.getParentOfType(element, SqlBindParameter::class.java, false) != null ->
                BIND_ARGUMENT

            PsiTreeUtil.getParentOfType(element, SqdCodeStmtIdentifierMixin::class.java, false) != null ->
                QUERY

            PsiTreeUtil.getParentOfType(element, SchemaContributor::class.java, false) != null ->
                SCHEMA

            else -> STATEMENT
        }

    /**
     * The group for a call to a generated query, or for one argument of such a call. Null for
     * anything else, so a usage the Kotlin plugin found itself keeps the group Kotlin gave it.
     */
    private fun generatedCallUsageType(element: PsiElement): UsageType? {
        if (isGeneratedCall(element)) return QUERY_CALL

        val argument = PsiTreeUtil.getParentOfType(element, KtValueArgument::class.java, false)
        return QUERY_ARGUMENT.takeIf { isGeneratedCall(argument?.parent?.parent) }
    }

    private fun isGeneratedCall(element: PsiElement?) =
        (element as? KtCallExpression)?.let { SqdCodeQueryCall.forCall(it) } != null
}
