# `:sql-psi`

The SQL grammar, lexer and PSI this plugin is built on. It is source in this repository, not a
dependency.

## Where it came from

| | |
|---|---|
| Upstream | <https://github.com/sqldelight/sql-psi>, Apache-2.0 |
| Taken from | <https://github.com/sishbi/sql-psi>, tag `0.9.0`, commit `129d5a1` |
| Taken | `core/src/main`, plus `core/src/test`, `core/src/testFixtures` and `environment/src/main` as this module's tests |
| Left behind | The publishing, Dokka and Spotless configuration, and the `sample-core`, `sample-headless` and `sample-plugin` modules |

`sishbi/sql-psi` is a fork of `sqldelight/sql-psi`. It carries four changes this plugin needs, all
described in its `CHANGELOG.md` under 0.9.0: the recursion guard in `AlterTableMixin` that stopped
two unordered files altering one table from killing the IDE, indexing a statement by the name it
declares as well as by its kind, an `ItemPresentation` on `SqlNamedElementImpl`, and narrowing
`SqlParserDefinition.getFileNodeType` to a stub file element type.

The package names are upstream's, `com.alecstrong.sql.psi.*`. They are referenced from
`SqldLite.bnf` and from `plugin.xml`, and renaming them would buy nothing.

`LICENSE.txt` is upstream's, and applies to every file under `src/`. The rest of this repository is
licensed separately.

## Keeping it in step with upstream

This is a copy, so a change upstream does not arrive on its own. That is the point: the plugin
consumes these files, and nothing outside this repository can move them.

The fork stays in place as the tracking mirror, so the route is:

1. In a clone of `sishbi/sql-psi`, add `sqldelight/sql-psi` as a remote and merge or cherry-pick the
   upstream change. That repository still holds the full upstream history, so git does the work.
2. Run its own tests there, then tag the result.
3. Copy `core/src/main` over `sql-psi/src/main` here, and the three test directories over
   `sql-psi/src/test`, then update the table above with the new tag and commit.
4. Run `./gradlew test` and `./gradlew verifyPlugin` in this repository. They are the gate: the SQL
   PSI now compiles against the same platform as the plugin, so an upstream change that breaks this
   plugin breaks the build here rather than at run time.

A change this plugin needs and upstream does not can be made here directly. Note it in this file so
the next merge does not silently drop it.

## Building

Nothing to do. `sql.bnf` and `SqlLexer.flex` generate the parser and the lexer into `build/`, so
there is no generated source in git. The root project composes `SqldLite.bnf` on top of the
`SqlParser` class this module produces.
