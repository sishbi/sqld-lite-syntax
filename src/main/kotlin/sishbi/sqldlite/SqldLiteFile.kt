package sishbi.sqldlite

import com.alecstrong.sql.psi.core.SqlFileBase
import com.intellij.psi.FileViewProvider

class SqldLiteFile(viewProvider: FileViewProvider) : SqlFileBase(viewProvider, SqldLiteLanguage) {

    /**
     * A `.sqm` file's migration number, and null for a `.sq` file.
     *
     * Not decoration. sql-psi builds a migration's schema only from the statements before the one
     * it is looking at, and only from migrations numbered lower, and it decides that from this
     * value. A null here makes every migration a queries file, so an `ALTER TABLE` asks for the
     * table it is altering, which asks that `ALTER TABLE` for the table it exposes, and the IDE
     * dies of a `StackOverflowError`.
     *
     * The number is the first run of digits in the name, which covers both `1.sqm` and the
     * timestamped `V<number>__<description>.sqm` that Flyway-style projects use.
     */
    override val order: Long? by lazy {
        if (!name.endsWith(".sqm")) return@lazy null
        name.dropWhile { !it.isDigit() }.takeWhile { it.isDigit() }.toLongOrNull()
    }

    override fun getFileType() = SqldLiteFileType
}
