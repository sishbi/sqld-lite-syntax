package sishbi.sqldlite.fixtures

/**
 * The types `PostgresQueries.sq` and `2.sqm` import and name in their `AS` column types.
 *
 * They sit in the package those files import, because Go To Declaration from an import or from an
 * `AS` column type resolves the name the same way Kotlin does.
 */
@Suppress("unused")
@JvmInline
value class EditionId(val value: String)

@Suppress("unused")
@JvmInline
value class Price(val pence: Long)

@Suppress("unused")
enum class ReservationState { HELD, RELEASED }
