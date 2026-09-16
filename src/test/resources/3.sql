-- The third migration, written by hand rather than as a `.sqm`, as a project does when a statement
-- has to run outside a transaction. It continues the chain 1.sqm starts, on the book_loans table
-- and its status column, and it holds every way a name can appear in such a file.
--
-- Reported as usages: the CREATE INDEX and the UPDATE below.
-- Not reported: these comments, the string literals, and book_loans_status_created, which the word
-- index reads as one word of its own.

CREATE INDEX CONCURRENTLY book_loans_status_created ON book_loans (status);

UPDATE book_loans
SET status = 'CANCELLED'
WHERE status = 'REQUESTED';

INSERT INTO audit (message) VALUES ('book_loans status backfilled');
