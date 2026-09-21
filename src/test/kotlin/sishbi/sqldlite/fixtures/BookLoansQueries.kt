package sishbi.sqldlite.fixtures

/**
 * Stands in for the class the SqlDelight Gradle plugin generates from `BookLoans.sq`.
 *
 * This plugin generates no code, so a checkout holds no generated queries class to navigate to.
 * Without one, `.sq` to Kotlin navigation cannot be tried by hand in this repository. The function
 * names and the parameter names match the query labels and the bind arguments in `BookLoans.sq`,
 * because that is what the navigation matches on.
 *
 * The return types match as well. SqlDelight hands every `SELECT` back as a `Query<T>` and leaves
 * the caller to choose `executeAsList`, `executeAsOne` or `executeAsOneOrNull`. It never decides
 * between one row and many. The documentation popup copies what it finds here, so a fixture
 * returning `List<String>` would show a shape SqlDelight does not generate.
 */
@Suppress("unused", "UNUSED_PARAMETER")
class BookLoansQueries {

    fun updateStatus(status: String, loan_id: Long) = Unit

    fun markRequestedLoansAsCancelled(member_id: Long) = Unit

    fun findMembersWithOpenLoans(): Query<FindMembersWithOpenLoans> = Query(emptyList())

    fun findByLoanId(loan_id: Long): Query<BookLoan> = Query(emptyList())

    /**
     * The mapper overload SqlDelight generates beside each query, for a caller supplying its own
     * row type. The documentation popup must show the other one: this is machinery, not the
     * function a reader calls.
     */
    fun <T : Any> findByLoanId(
        loan_id: Long,
        mapper: (String, String, Long, Long, String, Long) -> T,
    ): Query<T> = Query(emptyList())

    fun findByMemberId(member_id: Long): Query<BookLoan> = Query(emptyList())
}

/** Stands in for `app.cash.sqldelight.Query`, which this repository does not depend on. */
@Suppress("unused")
class Query<T : Any>(private val rows: List<T>) {

    fun executeAsList(): List<T> = rows

    fun executeAsOne(): T = rows.first()

    fun executeAsOneOrNull(): T? = rows.firstOrNull()
}

/** The whole row, as SqlDelight generates it for a query selecting every column of a table. */
data class BookLoan(
    val branch_name: String,
    val title: String,
    val member_id: Long,
    val loan_id: Long,
    val status: String,
    val renewals: Long,
)

/** A projection, which SqlDelight names after the query rather than after the table. */
data class FindMembersWithOpenLoans(
    val branch_name: String,
    val title: String,
    val member_id: Long,
)
