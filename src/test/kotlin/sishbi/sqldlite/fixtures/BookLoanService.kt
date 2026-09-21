package sishbi.sqldlite.fixtures

/**
 * Calls every query in `BookLoans.sq`, so each navigation the plugin offers has a starting point.
 *
 * The calls differ on purpose. Some name their arguments and some pass them by position, because
 * the plugin matches a bind argument to a parameter by name when the call names it and by position
 * when it does not.
 */
@Suppress("unused")
class BookLoanService(private val bookLoansQueries: BookLoansQueries) {

    fun reserve(loanId: Long) {
        bookLoansQueries.updateStatus(status = "RESERVED", loan_id = loanId)
    }

    fun cancel(loanId: Long) {
        bookLoansQueries.updateStatus(status = "CANCELLED", loan_id = loanId)
    }

    fun cancelRequestsFor(memberId: Long) {
        bookLoansQueries.markRequestedLoansAsCancelled(memberId)
    }

    fun openLoans(): List<FindMembersWithOpenLoans> =
        bookLoansQueries.findMembersWithOpenLoans().executeAsList()

    fun loan(loanId: Long): BookLoan? =
        bookLoansQueries.findByLoanId(loan_id = loanId).executeAsOneOrNull()

    fun loansOf(memberId: Long): List<BookLoan> =
        bookLoansQueries.findByMemberId(memberId).executeAsList()
}
