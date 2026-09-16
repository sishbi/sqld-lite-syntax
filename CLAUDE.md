# sqld-lite-syntax

An IntelliJ IDEA plugin for SqlDelight `.sq` and `.sqm` files. It is a lightweight replacement for
the official SqlDelight IntelliJ plugin.

## Hard constraints

- **The plugin must never crash or freeze the IDE.** Stability is why this plugin exists, so a
  library or dependency problem that brings the IDE down is a failure, however small the feature.
- `verifyPlugin` must report `Compatible` before any release.
- The plugin generates no code. The SqlDelight Gradle plugin still does that.

## Layout

| Path | Holds |
|---|---|
| `src/main/kotlin/sishbi/sqldlite/` | Every class. One flat package, `SqldLite` prefix on each name. |
| `src/main/kotlin/sishbi/sqldlite/SqldLite.bnf` | The overlay grammar, composed against the sql-psi grammar. |
| `src/main/resources/META-INF/plugin.xml` | Extension registrations. |
| `src/main/resources/META-INF/sqld-lite-withDatabase.xml` | Registrations that need the IDE's own SQL support. Loaded only where the Database plugin is. |
| `src/main/resources/messages/SqldLiteMessageBundle.properties` | Every user-visible string. |
| `src/test/resources/` | `.sq` and `.sqm` fixtures. A `.sqm` fixture is named for its number, as a real migration is. |
| `src/test/kotlin/sishbi/sqldlite/fixtures/` | Kotlin that stands in for the generated queries class, so navigation can be tried by hand. |
| `sql-psi/` | The SQL grammar, lexer and PSI, as source. Upstream's code, upstream's package names. |
| `.ai-local-plans/` | Plans and drafts. Git-ignored. |

## Adding a grammar rule

A `.sq` or `.sqm` file using a construct the overlay does not cover is painted red. Add the rule to
`SqldLite.bnf` and a case to `SqldLitePostgresDialectTest`. `PostgreSql.bnf` in the SqlDelight
repository is the reference for the rule's shape. Read the header of `SqldLite.bnf` first: it holds
the attribute rules and the traps, all of which fail silently.

## Conventions

- Every user-visible string goes in the message bundle, never inline.
- Names in test fixtures are generic. No employer, product or customer names.
- Comments say why, not what. A comment that restates the code is noise.
- Parser tests extend `ParsingTestCase`; anything needing the platform extends
  `SqldLitePlatformTestCase`.
- Prefer extending an existing test over adding a new one.

## The `:sql-psi` subproject

The SQL grammar, lexer and PSI are source in this repository, under `sql-psi/`, taken from the fork
at https://github.com/sishbi/sql-psi. They are not a dependency: there is no local publish, no
remote repository and no credential, and they compile against the same platform as the plugin.
`sql-psi/README.md` holds the provenance and the route back to upstream. Read it before changing
anything under `sql-psi/src`.

The package names are upstream's, `com.alecstrong.sql.psi.*`, because `SqldLite.bnf` and
`plugin.xml` name them.

`kotlin-stdlib` is absent from the whole build on purpose. The platform supplies it on the plugin
classloader's parent, and shipping a second copy is forbidden:
https://jb.gg/intellij-platform-kotlin-stdlib

## Things that have already bitten

- `PsiSearchHelper.processElementsWithWord` runs its processor on one thread per file. The
  collection it writes to must be thread safe, or it corrupts itself and hands out nulls.
- A Find Usages searcher runs on a pooled thread holding no read action. Both the PSI search and the
  `UsageInfo` construction must sit inside one. The platform only soft-asserts, so the defect logs
  an error and still appears to work.
- The "Data Flow" tab in the Find Usages panel belongs to whichever language plugin owns the search
  target, not to the selected row. The Kotlin one builds a `KotlinSliceUsage` for any row without
  asking `lang.sliceProvider`, so a `.sq` row there is always empty. Ours is reached through
  "Analyze | Data Flow to Here" instead. A tab of our own would need `SlicePanel`, `SliceRootNode`
  and `DuplicateMap`, all internal API, which fails the Plugin Verifier.
- A tree cell renderer paints on the EDT, which holds no read action. Anything a row shows must be
  read when the usage is built, not when the cell is painted. `SliceUsage.getPresentation()` is not a
  safe substitute: its text is a cache the platform fills elsewhere, so it can be empty.
- `ReadAction.compute` and the `runReadAction {}` extension are deprecated on the target build. Use
  `ApplicationManager.getApplication().runReadAction(Computable { ... })`.
- Find Usages only offers an element as a target when it is a `PsiNamedElement`. Go To Declaration
  does not need that, so one can work while the other says "Cannot search for usages".
- `SqlFileBase.order` is what orders a migration chain. sql-psi builds a `.sqm` file's schema only
  from the statements before the one it is reading, and only from migrations numbered lower, and it
  reads that number from `order`. A null there makes every migration a queries file, so two
  migrations altering one table each resolve to the other. That once killed the IDE with a
  `StackOverflowError`; the fork guards the recursion, so a wrong `order` now resolves names
  incompletely instead. `SqldLiteFile.order` still falls back to zero rather than null, because a
  migration numbered wrongly resolves some names, while one numbered not at all resolves none.
- sql-psi resolves a table, view or column across files through `SchemaContributorIndex`, a stub
  index. The file node type must build stubs or the file contributes nothing to it, and every name
  resolves only inside its own file. Nothing reported this: the resolution just returned null.
  `SqlParserDefinition.getFileNodeType` is now narrowed to `StubFileElementType<*>`, so it is a
  compile error.
- `SchemaContributorIndex.byKey` is keyed by the kind of statement, such as
  `com.alecstrong.sql.psi.core.psi.TableElement`. Use `byName` to ask by the name a statement
  declares; `byName` is a local addition, because upstream answered only the first question. The
  method is `byKey` and not `get` because `get` erased to the deprecated `AbstractStubIndex.get`,
  and the Plugin Verifier reported that against this plugin.
- A migration chain is a linked list, not a set. The name in an `ALTER TABLE` resolves to the
  statement below it, and a query resolves only to the newest one, so a search from any single link
  reports one neighbour. `SqlFileBase.schemaChain` reads the whole chain oldest first,
  `SqldLiteSchemaChain` turns it into names, and `SqldLiteTargetElementEvaluator` keeps a
  declaration as its own target: without it, a search from the newest migration titles the panel
  with the one before it.
- A line marker must hang off a leaf element, and an index-reading marker belongs in
  `collectSlowLineMarkers`.
- The plugin registers a file type, a parser definition and stub element type holders, so it cannot
  be hot-reloaded. Restart the sandbox IDE after a change.
- Every icon needs a `_dark` variant beside it. The platform finds it by the `_dark` suffix and
  never derives one from the light file, so a missing variant shows nothing in a dark theme. This
  has bitten twice: `sqldLiteFile_dark.svg` for the file type, and `META-INF/pluginIcon_dark.svg`
  for the tile in Settings | Plugins, which showed the generic placeholder without it.
- The icon on a Find Usages target line comes from `NavigationItem.getPresentation()` on the target
  element, never from `getIcon(flags)` and never through the `itemPresentationProvider` extension
  point. `PsiElement2UsageTargetAdapter` reads the presentation directly, so the extension point is
  never consulted. `PsiElementBase.getPresentation()` returns null, which is why a sql-psi name
  element showed no icon. `SqlNamedElementImpl` now overrides `getPresentation()`, which is the only
  place it can go.
- `PsiSearchHelper.processElementsWithWord` calls the processor once for the token holding the word
  and once for every element above it, up to the file. Five rows in the Find Usages panel for one
  occurrence in a `.sql` file came from taking each of them. `searchSqlFiles` keeps the first
  candidate at a position, which is the innermost.
- The Database plugin's SQL PSI may be named only from classes registered in
  `sqld-lite-withDatabase.xml`, which `plugin.xml` loads through an optional `<depends>`. A
  reference to one of those classes from the main descriptor's code fails to load in any IDE without
  the plugin, and Community is one. `referenceSearcherRegistered` therefore asks the extension point
  by class name, never by class.
- The Database plugin is loaded in the platform test fixture, because `bundledPlugin` puts it on the
  test classpath. A `.sql` file in a test therefore has real SQL PSI, which is what makes the
  precise searcher testable.
- A `.sql` file with no data source attached is parsed as generic SQL, which rejects much of real
  PostgreSQL. `CREATE INDEX CONCURRENTLY x ON t (c)` is one: the names after the error land in a
  recovery block, while the statement itself is still classed as an index definition, so filtering
  by kind alone dropped two visible usages. `SqldLiteSqlFileReferenceUsageSearcher` asks whether the
  statement holds a `PsiErrorElement` before it classifies, and keeps every name inside one.
- The icon inside that presentation comes from `ElementBase.getIcon`, which asks the `iconProvider`
  extension point and otherwise falls back to the icon of the containing file.
  `SqldLiteIconProvider` is what puts a database icon on a table, view or column name.

## Commands

| Task | Purpose |
|---|---|
| `./gradlew test` | Unit tests. |
| `./gradlew verifyPlugin` | Plugin Verifier against the target build. |
| `./gradlew runIde` | Sandbox IDE with the plugin installed. This is the development loop. |
