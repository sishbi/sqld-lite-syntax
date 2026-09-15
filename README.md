# sqld-lite-syntax

An IntelliJ IDEA plugin for SqlDelight `.sq` and `.sqm` files. It replaces the
[SqlDelight IntelliJ plugin](https://github.com/sqldelight/sqldelight/tree/main/sqldelight-idea-plugin),
which crashes the IDE.

It does two things: syntax highlighting, and code navigation. Navigation runs between a `.sq` file
and Kotlin in both directions, and between the `.sq` and `.sqm` files that declare and use a table.
It generates no code; the SqlDelight Gradle plugin still does that.

## Features

- Highlighting for keywords, identifiers, literals, comments, punctuation, operators, query labels
  and bind arguments. Every colour has an entry on the Editor | Color Scheme | SqlDelight page.
- Go To Declaration from Kotlin to `.sq`: from a query call to its label, and from the name of a
  named argument to the bind argument it fills.
- Go To Declaration from `.sq` to Kotlin: from a label to the calls to that query, from a bind
  argument to the value each call passes for it, and from a Kotlin type name to the class it names.
- Find Usages on a query label, listing the Kotlin calls. Find Usages on generated Kotlin, listing
  the `.sq` it came from, alongside everything the Kotlin plugin already reports.
- Find Usages on a table or view, listing its whole history: the `CREATE`, every `ALTER` after it,
  and every statement that uses it. The same list comes back from any of those starting points, and
  Go To Declaration from a query reaches the `CREATE`.
- A gutter icon on every label listing the same calls, with the count in its tooltip.
- An inspection that greys out a label no Kotlin code calls, with a quick fix that deletes it.

Navigation triggers on a call whose receiver name ends in `Queries`, so unrelated Kotlin is
untouched. Bind arguments map to parameters by position, because SqlDelight generates one parameter
per distinct named argument in first-mention order; a named Kotlin argument matches by name instead.
A query mixing in a positional `?` is skipped, because SqlDelight then names that parameter after
its column.

## Requirements

- IntelliJ IDEA 2026.2.2, build `IU-262.10315.125`.
- The PostgreSQL dialect of SqlDelight.
- The official SqlDelight plugin disabled. Both register a file type for `.sq`.

## The grammar

`app.cash.sql-psi:core` supplies the SQL lexer, parser and PSI, and covers core SQL only. An overlay
grammar, `SqldLite.bnf`, adds what SqlDelight and PostgreSQL add on top. It is not a full PostgreSQL
dialect: it covers the constructs a survey of real `.sq` and `.sqm` files found to be needed, and
every file in that survey parses with no error.

A construct the survey did not cover may still be unsupported, and a file using one is painted red.
Adding a rule is described in CLAUDE.md.

## Build

| Task | Purpose |
|---|---|
| `./gradlew test` | Unit tests. |
| `./gradlew verifyPlugin` | Plugin Verifier against the target build. Must report `Compatible`. |
| `./gradlew runIde` | Sandbox IDE with the plugin installed. |

## Licence

MIT, see [LICENSE](LICENSE). The distributed plugin also contains Apache-2.0 third-party code; see
[THIRD-PARTY.md](THIRD-PARTY.md).
