<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# SqlD-Lite Syntax Changelog

## Unreleased

## 0.1.1 - 2026-09-16

### Added

- A database icon beside a table, view or column name, so a Find Usages target line, a Go To Symbol
  row and a structure view row can be told apart. Every name showed the icon of its file before.

### Fixed

- Two `.sq` files that both alter the same table no longer freeze the IDE. Each statement resolved to
  the other and the recursion ended in a `StackOverflowError`.
- Find Usages on a table, view or column reports the whole migration chain from the index, rather
  than walking one reference at a time from the statement the caret is on.
- The name and the icon on a Find Usages target line, which the platform reads from the element's
  presentation. A sql-psi name element supplied neither.

### Changed

- SQL parsing, lexing and PSI are now source in this repository, under `sql-psi/`, taken from a
  fork of `sql-psi` at <https://github.com/sishbi/sql-psi> that carries the fixes above. They were
  a published artefact before. The build needs no repository, no credential and no local publish,
  and the SQL PSI compiles against the same IDE build as the rest of the plugin.

## 0.1.0 - 2026-09-15

The initial version. It is a lightweight replacement for the official SqlDelight IntelliJ plugin, and
covers syntax highlighting and code navigation. It generates no code.

### Added

- A file type, language, icon and parser for `.sq` and `.sqm`.
- Highlighting for keywords, identifiers, literals, comments, punctuation and operators, and for the
  SqlDelight additions: query labels, `:named` and `?` bind arguments, imports and `AS` column types.
- A colour settings page under Editor | Color Scheme | SqlDelight, with an entry for every colour.
- Parsing of the SqlDelight and PostgreSQL syntax that core SQL rejects: query labels, bind
  arguments, imports, `AS` column types, `TIMESTAMP WITH TIME ZONE`, the PostgreSQL `ALTER TABLE`
  actions, the `ON CONFLICT` upsert, `RETURNING`, `SELECT DISTINCT ON`,
  `CREATE INDEX CONCURRENTLY`, the `::` cast operator, `INTERVAL`, `ILIKE`, `AT TIME ZONE`,
  `FOR UPDATE`, window functions with `OVER (PARTITION BY ...)`, `ORDER BY ... NULLS LAST`, the
  `->>` JSON operators, `DATE '...'` typed literals, `JOIN LATERAL`, data-modifying CTEs,
  `SET LOCAL`, SqlDelight grouped statements, `DEFAULT now()`, a bare `NULL` column constraint,
  `DOUBLE PRECISION`, array types, `GENERATED ALWAYS AS IDENTITY`, `ADD COLUMN IF NOT EXISTS`,
  every PostgreSQL `ALTER COLUMN` action and `CREATE INDEX ... USING btree`.
