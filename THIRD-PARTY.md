# Third-party code

## sql-psi

Licence: Apache-2.0. Source: https://github.com/sqldelight/sql-psi

Copied into this repository under `sql-psi/`, and compiled into the plugin. It supplies the SQL
lexer, the SQL parser and the SQL PSI. The copy is taken from the fork at
https://github.com/sishbi/sql-psi, which modifies the upstream files; `sql-psi/README.md` lists what
was taken and what the fork changes, and `sql-psi/LICENSE.txt` is upstream's licence.

## SqlDelight

Licence: Apache-2.0. Source: https://github.com/sqldelight/sqldelight

No SqlDelight code is shipped. Two files are adapted from SqlDelight sources:

- `src/main/kotlin/sishbi/sqldlite/SqldLite.bnf` is modelled on `sqldelight.bnf` and on the PostgreSQL
  dialect's `PostgreSql.bnf`. It declares the rules for query labels, bind arguments, imports and
  `AS` column types, and the PostgreSQL rules `SqldLitePostgresDialectTest` covers. Rule shapes and
  the mixin, extends, implements, elementTypeClass and stubClass attributes follow those files,
  because an overriding rule has to repeat the attributes of the rule it replaces.
- `src/main/kotlin/sishbi/sqldlite/SqldLiteCreateIndexElementType.kt` follows the same pattern as the
  PostgreSQL dialect's `CreateIndexElementType`.

The plugin does not generate code, so nothing else from SqlDelight is used.
