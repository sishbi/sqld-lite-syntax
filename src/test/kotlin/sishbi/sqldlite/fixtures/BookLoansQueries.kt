package sishbi.sqldlite.fixtures

/**
 * Stands in for the class the SqlDelight Gradle plugin generates from `BookLoans.sq`.
 *
 * This plugin generates no code, so a checkout holds no generated queries class to navigate to.
 * Without one, `.sq` to Kotlin navigation cannot be tried by hand in this repository. The function
 * names and the parameter names match the query labels and the bind arguments in `BookLoans.sq`,
 * because that is what the navigation matches on.
 */
@Suppress("unused", "UNUSED_PARAMETER")
class BookLoansQueries {

    fun updateStatus(status: String, loan_id: Long) = Unit

    fun markRequestedLoansAsCancelled(member_id: Long) = Unit

    fun findMembersWithOpenLoans(): List<String> = emptyList()

    fun findByLoanId(loan_id: Long): String? = null

    fun findByMemberId(member_id: Long): List<String> = emptyList()
}
