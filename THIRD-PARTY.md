# Third-party code

## app.cash.sql-psi:core

Licence: Apache-2.0. Source: https://github.com/sqldelight/sql-psi

A compile and runtime dependency, whose jar is shipped in the plugin unmodified. It supplies the SQL
lexer, the SQL parser and the SQL PSI. No sql-psi source is copied into this repository.

## SqlDelight

Licence: Apache-2.0. Source: https://github.com/sqldelight/sqldelight

No SqlDelight code is shipped. Two files are adapted from SqlDelight sources:

- `src/main/kotlin/sishbi/sqd/SqdCode.bnf` is modelled on `sqldelight.bnf` and on the PostgreSQL
  dialect's `PostgreSql.bnf`. It declares the rules for query labels, bind arguments, imports and
  `AS` column types, and the PostgreSQL rules `SqdCodePostgresDialectTest` covers. Rule shapes and
  the mixin, extends, implements, elementTypeClass and stubClass attributes follow those files,
  because an overriding rule has to repeat the attributes of the rule it replaces.
- `src/main/kotlin/sishbi/sqd/SqdCodeCreateIndexElementType.kt` follows the same pattern as the
  PostgreSQL dialect's `CreateIndexElementType`.

The plugin does not generate code, so nothing else from SqlDelight is used.
