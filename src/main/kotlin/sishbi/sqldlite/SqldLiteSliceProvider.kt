package sishbi.sqldlite

import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.lang.LanguageExtension
import com.intellij.slicer.SliceAnalysisParams
import com.intellij.slicer.SliceLanguageSupportProvider
import com.intellij.slicer.SliceTreeBuilder
import com.intellij.slicer.SliceUsage
import com.intellij.slicer.SliceUsageCellRendererBase
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.Processor

/**
 * Backs the Find Usages "Data Flow" tab for a `.sq` usage.
 *
 * That tab builds a slice for whichever usage is selected without checking its language. Without a
 * provider registered here, selecting the `.sq` source of a generated query threw a
 * `NullPointerException` out of the platform and left the tab dead for the rest of the search.
 *
 * Only one `.sq` element carries a value that flows: a bind argument, whose value is whatever each
 * Kotlin call site passes for it. See [SqldLiteSliceUsage].
 *
 * Reached through "Analyze | Data Flow to Here", not through the Find Usages tab of the same name.
 * That tab belongs to whichever language plugin owns the search target, and the Kotlin one builds a
 * `KotlinSliceUsage` for whatever row is selected without asking this extension point. Registering
 * a tab of our own would mean `SlicePanel`, `SliceRootNode` and `DuplicateMap`, all of which are
 * internal API and fail the Plugin Verifier.
 */
class SqldLiteSliceProvider : SliceLanguageSupportProvider {

    override fun createRootUsage(element: PsiElement, params: SliceAnalysisParams): SliceUsage =
        SqldLiteSliceUsage(element, params)

    /**
     * What "Analyze | Data Flow to Here" slices: the bind argument under the caret.
     *
     * Returning null here made that action report "Cannot find what to analyze", which is what the
     * platform says when a provider declines. Nothing flows out of a `.sq` file, so the forward
     * direction stays null and its action stays inert.
     */
    override fun getExpressionAtCaret(atCaret: PsiElement, dataFlowToThis: Boolean): PsiElement? =
        if (dataFlowToThis) {
            PsiTreeUtil.getParentOfType(atCaret, SqldLiteBindParameterMixin::class.java)
        } else {
            null
        }

    override fun getElementForDescription(element: PsiElement) = element

    override fun getRenderer(): SliceUsageCellRendererBase = SqldLiteSliceRenderer()

    override fun startAnalyzeLeafValues(structure: AbstractTreeStructure, finalRunnable: Runnable) =
        finalRunnable.run()

    override fun startAnalyzeNullness(structure: AbstractTreeStructure, finalRunnable: Runnable) =
        finalRunnable.run()

    override fun registerExtraPanelActions(group: DefaultActionGroup, builder: SliceTreeBuilder) = Unit
}

/**
 * The same extension point the platform's own `LanguageSlicing` reads, so this sees every registered
 * provider. Declared here rather than calling that class, which is marked internal and so fails the
 * Plugin Verifier.
 */
private val sliceProviders =
    LanguageExtension<SliceLanguageSupportProvider>("com.intellij.lang.sliceProvider")

/** Keeps its own [source], because [SliceUsage.getElement] is null once the element is invalidated. */
private class SqldLiteSliceUsage(
    private val source: PsiElement,
    params: SliceAnalysisParams,
) : SliceUsage(source, params) {

    /**
     * What the tree row says, read here rather than in the renderer. A cell renderer paints on the
     * EDT holding no read action, and asking the element for its text there soft-asserts on every
     * repaint.
     */
    val label: String = ApplicationManager.getApplication().runReadAction(
        Computable { (source as? PsiNamedElement)?.name ?: source.text },
    )

    /** Nothing. A bind argument's value goes into the SQL statement and is not seen again. */
    override fun processUsagesFlownFromThe(
        element: PsiElement,
        processor: Processor<in SliceUsage>,
    ) = Unit

    /**
     * The value each Kotlin call site passes for a bind argument. Anything else in a `.sq` file is
     * SQL text rather than a value, and has no inflow to report.
     *
     * Each child is built by the Kotlin provider, not this one. The tree picks a row's renderer from
     * that row's own language, so a Kotlin element in one of our usages would be handed to the
     * Kotlin renderer; going through Kotlin also lets the slice carry on past the call site, which
     * is the only reason to open this tab.
     */
    override fun processUsagesFlownDownTo(
        element: PsiElement,
        processor: Processor<in SliceUsage>,
    ) {
        val parameter = element as? SqldLiteBindParameterMixin ?: return
        SqldLiteBindArgumentSites.of(parameter).forEach { argument ->
            val provider = sliceProviders.forLanguage(argument.language) ?: return@forEach
            processor.process(provider.createRootUsage(argument, params))
        }
    }

    override fun copy() = SqldLiteSliceUsage(source, params)
}

private class SqldLiteSliceRenderer : SliceUsageCellRendererBase() {

    override fun customizeCellRendererFor(usage: SliceUsage) {
        val label = (usage as? SqldLiteSliceUsage)?.label ?: return
        append(label, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }
}
