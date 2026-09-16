-- The other file's ALTER contributes a table of this name too, because an unordered file has no
-- position in a chain, so the CREATE sees two.
-- error[col 13]: Table already defined with name test
CREATE TABLE test (
  id TEXT NOT NULL
);

-- error[col 0]: Alter table statements are forbidden outside of migration files.
ALTER TABLE test
  ADD COLUMN id2 TEXT;
