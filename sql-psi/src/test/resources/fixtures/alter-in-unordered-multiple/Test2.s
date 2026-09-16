-- Two unordered files altering one table used to resolve to each other and recurse until the
-- IDE died of a StackOverflowError. Both statements must report the error and neither may hang.
-- error[col 0]: Alter table statements are forbidden outside of migration files.
ALTER TABLE test
  ADD COLUMN id3 TEXT;
