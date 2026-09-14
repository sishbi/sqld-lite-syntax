<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Sqd-code Changelog

## [Unreleased]

The initial version. It replaces the official SqlDelight IntelliJ plugin, which crashes the IDE, and
covers syntax highlighting and code navigation. It generates no code.

### Added

#### Files and syntax

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

#### Navigation between `.sq` and Kotlin

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

#### Navigation inside `.sq` and `.sqm`

- Find Usages on a table or view name, reporting its whole history: the `CREATE`, every `ALTER`
  after it, and every statement that uses it. The same list comes back from any of those starting
  points.
- A table or view name in a query as a reference to the `CREATE` that declares it, so Go To
  Declaration reaches the table's origin rather than the newest migration.
- Find Usages on a column name and on a table alias.

#### Find Usages, hierarchies and inspections

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
