package sishbi.sqd

import com.alecstrong.sql.psi.core.psi.SqlTableName
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.usageView.UsageInfo
import com.intellij.util.CommonProcessors
import org.jetbrains.kotlin.psi.KtClassOrObject

/**
 * Checks navigation out of a `.sq` or `.sqm` file: a label to its Kotlin call sites, and a Kotlin
 * type name to the class it names.
 *
 * The Kotlin files are written by hand, as in [SqdCodeNavigationTest], because the handler reads the
 * call's own shape rather than anything generated.
 */
class SqdCodeReverseNavigationTest : SqdCodePlatformTestCase() {

    override fun getTestDataPath() = "src/test/testData"

    fun testFindsTheKotlinCallForALabel() {
        myFixture.addFileToProject(
            "Caller.kt",
            "fun caller(queries: Any) {\n    bookLoansQueries.findByMemberId(1)\n}\n",
        )

        val targets = findByMemberIdTargets()

        assertEquals(listOf("Caller.kt"), targets.map { it.containingFile.name })
    }

    fun testIgnoresACallOnSomethingThatIsNotAQueriesClass() {
        myFixture.addFileToProject(
            "Caller.kt",
            "fun caller(repository: Any) {\n    repository.findByMemberId(1)\n}\n",
        )

        assertEmpty(findByMemberIdTargets())
    }

    fun testIgnoresACallOnAQueriesClassForAnotherFile() {
        myFixture.addFileToProject(
            "Caller.kt",
            "fun caller() {\n    otherQueries.findByMemberId(1)\n}\n",
        )

        assertEmpty(findByMemberIdTargets())
    }

    fun testNavigatesFromAnAsTypeToTheKotlinClass() {
        myFixture.addFileToProject(
            "ReservationState.kt",
            "package com.example.library\n\nenum class ReservationState { HELD }\n",
        )
        myFixture.addFileToProject(
            "Reservations.sqm",
            """
            import com.example.library.ReservationState;

            ALTER TABLE reservations
            ADD COLUMN journey TEXT AS ReservationState NOT NULL DEFAULT 'HELD';
            """.trimIndent(),
        )

        val target = referenceAt("Reservations.sqm", "AS ReservationState", "ReservationState")

        assertEquals("ReservationState.kt", target?.containingFile?.name)
    }

    fun testNavigatesFromAnImportToTheKotlinClass() {
        myFixture.addFileToProject(
            "EditionId.kt",
            "package com.example.library\n\n@JvmInline value class EditionId(val value: String)\n",
        )
        myFixture.addFileToProject(
            "Editions.sq",
            "import com.example.library.EditionId;\n\nselectAll:\nSELECT 1;\n",
        )

        val target = referenceAt("Editions.sq", "import com.example.library.EditionId", "EditionId")

        assertEquals("EditionId.kt", target?.containingFile?.name)
    }

    /**
     * The other direction, and the reason the type name is a reference rather than another go-to
     * handler: a search from the Kotlin class has to reach both sites, and has to classify them
     * rather than report them as text matches.
     */
    fun testFindsBothSqSitesFromTheKotlinClass() {
        val kotlin = myFixture.addFileToProject(
            "ReservationState.kt",
            "package com.example.library\n\nenum class ReservationState { HELD }\n",
        )
        myFixture.addFileToProject(
            "Reservations.sqm",
            """
            import com.example.library.ReservationState;

            ALTER TABLE reservations
            ADD COLUMN journey TEXT AS ReservationState NOT NULL DEFAULT 'HELD';
            """.trimIndent(),
        )

        val enum = requireNotNull(PsiTreeUtil.findChildOfType(kotlin, KtClassOrObject::class.java))
        val references = ReferencesSearch
            .search(enum, GlobalSearchScope.allScope(project))
            .findAll()

        // Sorted, because the order is whatever order the word index hands the occurrences back in.
        assertEquals(
            listOf("ReservationState", "com.example.library.ReservationState"),
            references.map { it.element.text }.sorted(),
        )
    }

    /**
     * A table declared in a migration, altered by later migrations, used by a query in another file.
     *
     * sql-psi resolves its own names across the module, so the platform's own handler answers this
     * once [SqdCodeFindUsagesProvider] claims the name. Claiming only this plugin's own two elements
     * made Find Usages refuse a table with "Cannot search for usages from this location".
     *
     * The `ALTER TABLE` migrations are here for [SqdCodeFile.order]: each one asks the table it
     * alters what columns it has, and without an order to cut the search off that question comes
     * back to the same statement and the search dies of a `StackOverflowError`.
     *
     * A search from the newest migration still has to report the whole chain, which is why the
     * handler searches every link: on its own, the newest one resolves to the migration below it
     * and to nothing older.
     */
    fun testFindsTheSqUsesOfATableDeclaredInAMigration() {
        myFixture.addFileToProject(
            "V1__create_reservations.sqm",
            "CREATE TABLE reservations (\n  id TEXT NOT NULL\n);\n",
        )
        myFixture.addFileToProject(
            "V2__add_journey.sqm",
            "ALTER TABLE reservations ADD COLUMN journey TEXT;\n",
        )
        val last = myFixture.addFileToProject(
            "V3__add_state.sqm",
            "ALTER TABLE reservations ADD COLUMN state TEXT;\n",
        )
        val query = myFixture.addFileToProject(
            "Reservations.sq",
            "selectAll:\nSELECT * FROM reservations;\n",
        )
        val table = requireNotNull(PsiTreeUtil.findChildOfType(last, SqlTableName::class.java))

        assertTrue(SqdCodeFindUsagesProvider().canFindUsagesFor(table))

        // The name an ALTER TABLE declares points at the migration below it, so without this the
        // panel titles a search from here with that one and looks to have started somewhere else.
        val evaluator = SqdCodeTargetElementEvaluator()
        assertSame(table, evaluator.getElementByReference(requireNotNull(table.reference), 0))
        // A use points at the newest migration, which is not what a reader means by the declaration.
        val use = requireNotNull(PsiTreeUtil.findChildOfType(query, SqlTableName::class.java))
        val target = evaluator.getElementByReference(requireNotNull(use.reference), 0)
        assertEquals("V1__create_reservations.sqm", target?.containingFile?.name)

        assertEquals(
            listOf(
                "Reservations.sq",
                "V1__create_reservations.sqm",
                "V2__add_journey.sqm",
                "V3__add_state.sqm",
            ),
            usagesOf(table).mapNotNull { it.element?.containingFile?.name }.distinct().sorted(),
        )
    }

    /** What the Find Usages panel shows for [element], across every element the handler searches. */
    private fun usagesOf(element: PsiElement): List<UsageInfo> {
        val handler = requireNotNull(
            SqdCodeFindUsagesHandlerFactory().createFindUsagesHandler(element, false),
        )
        val usages = CommonProcessors.CollectProcessor<UsageInfo>()
        handler.primaryElements.forEach {
            handler.processElementUsages(it, usages, handler.findUsagesOptions)
        }
        return usages.results.toList()
    }

    /** The targets offered with the caret on the `findByMemberId` label in `BookLoans.sq`. */
    private fun findByMemberIdTargets(): List<PsiElement> {
        myFixture.copyFileToProject("BookLoans.sq")
        return targetsAt("BookLoans.sq", "findByMemberId:", "findByMemberId")
    }

    /**
     * The targets offered with the caret on [word], searched for inside the first occurrence of
     * [context] in [fileName].
     */
    private fun targetsAt(fileName: String, context: String, word: String): List<PsiElement> {
        val (file, offset) = caretAt(fileName, context, word)

        val element = file.findElementAt(offset) ?: return emptyList()
        return SqdCodeSqGotoDeclarationHandler()
            .getGotoDeclarationTargets(element, offset, myFixture.editor)
            ?.toList()
            .orEmpty()
    }

    /** What the reference at the same position resolves to. A type name is reached this way. */
    private fun referenceAt(fileName: String, context: String, word: String): PsiElement? {
        val (file, offset) = caretAt(fileName, context, word)
        return file.findReferenceAt(offset)?.resolve()
    }

    private fun caretAt(fileName: String, context: String, word: String): Pair<PsiFile, Int> {
        val file = myFixture.configureFromTempProjectFile(fileName)
        val text = file.text
        return file to text.indexOf(context).let { it + text.substring(it).indexOf(word) }
    }
}
