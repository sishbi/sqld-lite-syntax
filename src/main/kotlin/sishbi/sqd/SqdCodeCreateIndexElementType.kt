package sishbi.sqd

import com.alecstrong.sql.psi.core.psi.SchemaContributorStub
import com.alecstrong.sql.psi.core.psi.mixins.CreateIndexElementType
import sishbi.sqd.psi.impl.SqdCodeCreateIndexStmtImpl

/**
 * The element type of the overlay grammar's `create_index_stmt` rule.
 *
 * `CREATE INDEX` is a schema contributor, so the IDE rebuilds its PSI from a stub without
 * re-parsing. The core element type rebuilds a core `SqlCreateIndexStmtImpl`, which does not know
 * `CONCURRENTLY`; this one rebuilds the generated implementation instead.
 *
 * [getExternalId] is overridden because sql-psi builds its own from a constructor property that is
 * still null when the platform reads it, so every sql-psi schema element type answers
 * `sqldelight.null` and a second type with that ID is rejected as a collision. It reads the name
 * from `toString`, which is `IElementType`'s debug name, for the same reason: the platform reads the
 * external ID from inside `IElementType`'s constructor, before this class has assigned anything.
 */
class SqdCodeCreateIndexElementType(name: String) : CreateIndexElementType(name) {
    override fun getExternalId() = "$EXTERNAL_ID_PREFIX$this"

    override fun createPsi(stub: SchemaContributorStub) = SqdCodeCreateIndexStmtImpl(stub, this)

    companion object {
        /** Must equal the `externalIdPrefix` of the `SqdCodeTypes` stubElementTypeHolder. */
        const val EXTERNAL_ID_PREFIX = "SqdCode.OVERLAY"
    }
}
