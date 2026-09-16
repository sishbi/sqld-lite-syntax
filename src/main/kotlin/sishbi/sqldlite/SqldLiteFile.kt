package sishbi.sqldlite

import com.alecstrong.sql.psi.core.SqlFileBase
import com.intellij.psi.FileViewProvider

class SqldLiteFile(viewProvider: FileViewProvider) : SqlFileBase(viewProvider, SqldLiteLanguage) {

    /**
     * A `.sqm` file's migration number, and null for a `.sq` file.
     *
     * Not decoration. sql-psi builds a migration's schema only from the statements before the one
     * it is looking at, and only from migrations numbered lower, and it decides that from this
     * value. A null here makes every migration a queries file, so two migrations that alter one
     * table each resolve to the other.
     *
     * The number is the first run of digits in the name, which covers both `1.sqm` and the
     * timestamped `V<number>__<description>.sqm` that Flyway-style projects use. A name holding no
     * digits falls back to zero rather than to null: a migration numbered wrongly resolves some
     * names incompletely, while a migration numbered not at all resolves nothing correctly.
     *
     * That null once killed the IDE with a `StackOverflowError`, because the two `ALTER TABLE`
     * statements asked each other for the table they alter without end. The fork guards against
     * the recursion, so the fallback is now about resolving names, not about staying alive.
     */
    override val order: Long? by lazy {
        if (!name.endsWith(".sqm")) return@lazy null
        name.dropWhile { !it.isDigit() }.takeWhile { it.isDigit() }.toLongOrNull() ?: 0L
    }

    override fun getFileType() = SqldLiteFileType
}
