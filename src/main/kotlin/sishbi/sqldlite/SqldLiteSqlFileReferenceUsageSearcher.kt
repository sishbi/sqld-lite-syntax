package sishbi.sqldlite

import com.alecstrong.sql.psi.core.psi.NamedElement
import com.alecstrong.sql.psi.core.psi.SqlColumnName
import com.intellij.database.model.ObjectKind
import com.intellij.find.findUsages.CustomUsageSearcher
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.psi.SqlDefinition
import com.intellij.sql.psi.SqlReferenceExpression
import com.intellij.sql.psi.SqlStringLiteralExpression
import com.intellij.usages.Usage
import com.intellij.util.Processor

/** The kinds a `.sq` column name may match. */
private val COLUMN_KINDS = setOf(ObjectKind.COLUMN)

/**
 * The kinds a `.sq` table, view or CTE name may match. A view and a materialised view are declared
 * and read exactly as a table is, and sql-psi resolves all three to the same kind of name.
 */
private val TABLE_KINDS = setOf(ObjectKind.TABLE, ObjectKind.VIEW, ObjectKind.MAT_VIEW)

/**
 * Adds occurrences of a table, view or column name in `.sql` files to its usages, but only where
 * SQL itself reads the name as a table or a column.
 *
 * This is registered from `sqld-lite-withDatabase.xml`, so the class loads only where the Database
 * plugin does, and [SqldLiteSqlFileUsageSearcher] stands down whenever it is loaded. The class
 * names come from the IDE's own SQL support, which carries no `ApiStatus` annotation: usable, and
 * unsupported. `verifyPlugin` runs against IntelliJ IDEA Ultimate, so it does check this path.
 *
 * What this buys over the name alone: a hit in a comment, in a string literal, or on an identifier
 * that merely shares the name is no longer a usage. What it cannot buy is identity.
 * `SqlReferenceExpression.resolve()` answers from a data source or a DDL data source, and a `.sq`
 * file is neither, so two tables of one name are still indistinguishable.
 */
class SqldLiteSqlFileReferenceUsageSearcher : CustomUsageSearcher() {

    override fun processElementUsages(
        element: PsiElement,
        processor: Processor<in Usage>,
        options: FindUsagesOptions,
    ) {
        if (element !is NamedElement) return

        val kinds = if (element is SqlColumnName) COLUMN_KINDS else TABLE_KINDS
        searchSqlFiles(element) { candidate -> isReferenceOfKind(candidate, kinds) }
            .forEach { processor.process(it) }
    }

    /**
     * Whether SQL reads the candidate as one of [kinds], either as a reference to such an object or
     * as the statement that declares one. The nearest enclosing reference is the one that counts:
     * in `schema.table` the qualifier is a reference of its own, of kind `SCHEMA`.
     *
     * A comment and a string literal are never usages, whatever else is true of the statement
     * around them. A statement SQL could not parse is the opposite case: its classification cannot
     * be trusted, so the hit is kept rather than lost, which is why [inUnparsedStatement] is asked
     * first.
     */
    private fun isReferenceOfKind(candidate: PsiElement, kinds: Set<ObjectKind>): Boolean {
        if (isCommentOrLiteral(candidate)) return false
        if (inUnparsedStatement(candidate)) return true

        val reference = PsiTreeUtil.getParentOfType(candidate, SqlReferenceExpression::class.java, false)
        if (reference != null) return reference.referenceElementType?.targetKind in kinds

        val definition = PsiTreeUtil.getParentOfType(candidate, SqlDefinition::class.java, false)
        return definition != null && definition.kind in kinds
    }

    private fun isCommentOrLiteral(candidate: PsiElement) =
        PsiTreeUtil.getParentOfType(candidate, PsiComment::class.java, false) != null ||
            PsiTreeUtil.getParentOfType(candidate, SqlStringLiteralExpression::class.java, false) != null

    /**
     * Whether the statement holding the candidate failed to parse.
     *
     * The dialect the IDE gives a `.sql` file is the generic one until a data source says otherwise,
     * and generic SQL rejects a good deal of real PostgreSQL. `CREATE INDEX CONCURRENTLY ... ON
     * table (column)` is one: the names after the error land in a recovery block inside a statement
     * SQL does class, as an index, so classifying alone drops both names a reader can see. The cost
     * is that any identifier inside a statement that failed to parse counts, which is what the
     * name-only searcher does everywhere.
     */
    private fun inUnparsedStatement(candidate: PsiElement): Boolean {
        val statement = generateSequence(candidate) { it.parent }
            .firstOrNull { it.parent is PsiFile }
            ?: return false

        return PsiTreeUtil.findChildOfType(statement, PsiErrorElement::class.java) != null
    }
}
