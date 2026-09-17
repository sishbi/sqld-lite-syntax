<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# SqlD-Lite Syntax Changelog

## Unreleased

## 0.1.4 - 2026-09-17

### Fixed

- Installing or updating the plugin asks for a restart immediately. The IDE first tried to load the
  plugin without one, stalled, and then asked for the restart anyway.

## 0.1.3 - 2026-09-17

### Added

- Cmd-hover and Quick Documentation on a name in a `.sq` file show what it declares: a query as the
  function a caller writes, a bind argument, a column as its column definition, a table or view as
  the statement that creates it. Quick Documentation adds the query's doc comment and the statement
  itself, coloured as the editor colours it.
- The same popup on a Kotlin call to a query shows the function holding the call, with the class it
  belongs to under it.

### Fixed

- The list of Kotlin callers, from a query label or from the gutter icon, shows one short row for
  each call, named after the class and the method holding it. A call written over several argument
  lines filled its row with the whole call, and every row read alike.
- A call inside a lambda is named after the method around it. It read `<anonymous>`.
- The hover popup says what it is showing: a query, a query call, a bind argument, a table, a view
  or a column. It showed the name of an internal class for a Kotlin call, and nothing at all for a
  query.
- A column of a table that the migrations only ever alter shows its own name. It showed the first
  line of the query using it.

## 0.1.2 - 2026-09-16

The same plugin as 0.1.1. This release carries no change to the code; it exists to restate what
0.1.1 brought without the build detail that belonged to contributors rather than to users.

### Added

- A database icon beside a table, view or column name, so a Find Usages target line, a Go To Symbol
  row and a structure view row can be told apart. Every name showed the icon of its file before.

### Fixed

- Two `.sq` files that both alter the same table no longer freeze the IDE. Each statement resolved to
  the other and the recursion ended in a `StackOverflowError`.
- Find Usages on a table, view or column reports the whole migration chain from the index, rather
  than walking one reference at a time from the statement the caret is on.
- The name and the icon on a Find Usages target line, which the platform reads from the element's
  presentation.

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
- Go To Declaration from a Kotlin query call to its `.sq` query label, and from the name of a named
  argument to the bind argument it fills.
- Go To Declaration from a `.sq` query label to the Kotlin calls to that query, from a bind argument
  to the value each call passes for it, and from a Kotlin type name to the class it names.
- A Kotlin type name in an import or an `AS` column type as a real reference, so a search from the
  Kotlin class reports both sites.
- An index of query labels, so navigation works without building the project.
- Bind arguments matched to parameters by position, or by name where the Kotlin call names its
  arguments. A query mixing in a positional `?` is skipped, because SqlDelight then names that
  parameter after its column.
- Navigation triggered only by a call whose receiver name ends in `Queries`, so unrelated Kotlin is
  untouched.
- Find Usages on a table or view name, reporting its whole history: the `CREATE`, every `ALTER`
  after it, and every statement that uses it. The same list comes back from any of those starting
  points.
- A table or view name in a query as a reference to the `CREATE` that declares it, so Go To
  Declaration reaches the table's origin rather than the newest migration.
- Find Usages on a column name and on a table alias.
- Occurrences of a table, view or column name in `.sql` files included in its usages, grouped under
  "SQL file". The match is on the name alone, because a `.sql` file belongs to the IDE's own SQL
  support and shares no PSI with `.sq`.
- Find Usages on a query label, reporting the Kotlin calls to that query.
- The `.sq` query listed among the usages of the generated Kotlin it produced: the label for a query
  function, and the bind argument for one of its parameters.
- Named groups for every `.sq` usage in the Find Usages panel, so nothing this plugin owns appears
  under "Unclassified". A schema declaration and a statement using it are grouped apart.
- A call hierarchy on a query label, listing its Kotlin callers. An unlabelled statement offers none.
- Analyze | Data Flow to Here on a bind argument, following the values the Kotlin calls pass.
- A gutter icon on every query label, listing that query's Kotlin callers, with the count in its
  tooltip. It has an entry under Settings | Editor | General | Gutter Icons.
- An inspection that greys out a query label no Kotlin code calls, with a quick fix that deletes the
  query.
