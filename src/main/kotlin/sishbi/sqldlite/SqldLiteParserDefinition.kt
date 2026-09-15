package sishbi.sqldlite

import com.alecstrong.sql.psi.core.SqlParser
import com.alecstrong.sql.psi.core.SqlParserDefinition
import com.alecstrong.sql.psi.core.SqlParserUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.stubs.PsiFileStub
import com.intellij.psi.tree.IStubFileElementType

/**
 * Stub-backed, not a plain [IFileElementType].
 *
 * sql-psi answers "which table does this name mean" out of `SchemaContributorIndex`, a stub index.
 * A file whose node type builds no stubs contributes nothing to it, so every table resolved to
 * itself and to nothing else: a query in one file never found the `CREATE TABLE` in another.
 */
object SqldLiteFileElementType : IStubFileElementType<PsiFileStub<SqldLiteFile>>(SqldLiteLanguage) {
    override fun getExternalId() = "SqldLite.FILE"

    /** Bump when a change here alters what the stubs hold, so the IDE reindexes. */
    override fun getStubVersion() = 1
}

val SQLD_LITE_FILE = SqldLiteFileElementType

/**
 * Supplies the lexer, parser and PSI factory from `app.cash.sql-psi:core`.
 *
 * `plugin.xml` must also register `SqlTypes` as a `stubElementTypeHolder` and
 * `SchemaContributorIndexImpl` as a `stubIndex`, or the platform fails at index initialisation with
 * "All stub element types should be created before index initialization is complete."
 */
class SqldLiteParserDefinition : SqlParserDefinition() {
    override fun getFileNodeType() = SQLD_LITE_FILE

    override fun getLanguage() = SqldLiteLanguage

    override fun createFile(viewProvider: FileViewProvider) = SqldLiteFile(viewProvider)

    override fun createParser(project: Project): SqlParser {
        installParserOverride()
        return super.createParser(project)
    }
}

private val installLock = Any()

/**
 * Swaps this plugin's `stmt_list` and `bind_parameter` into the sql-psi parser, so query labels and
 * `:named` bind arguments parse. Installs at most once, and never calls [SqlParserUtil.reset].
 *
 * [SqlParserUtil] is a global mutable object shared by every SQL-derived language in the IDE, and
 * the platform calls `createParser` once per file parsed, on many threads. A reset there would open
 * a window in which this plugin's element types are unknown to the PSI factory, because
 * [SqlParserUtil.reset] also removes the `createElement` wrapper `overrideSqlParser` installs, and
 * any concurrent PSI build in that window throws an uncaught `AssertionError`. The reset exists
 * upstream only for switching SQL dialect at runtime, which this plugin never does.
 *
 * An `stmt_list` another SQL-derived language has already installed is left alone, so `.sq` files
 * fall back to core SQL rather than breaking that language. See the README.
 */
private fun installParserOverride() {
    if (SqlParserUtil.stmt_list != null && SqlParserUtil.bind_parameter != null) return

    synchronized(installLock) {
        if (SqlParserUtil.stmt_list != null && SqlParserUtil.bind_parameter != null) return
        SqldLiteParserUtil.overrideSqlParser()
    }
}
