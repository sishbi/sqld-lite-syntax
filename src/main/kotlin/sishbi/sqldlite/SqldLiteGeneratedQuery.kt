package sishbi.sqldlite

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.stubindex.KotlinClassShortNameIndex
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * The Kotlin function the SqlDelight Gradle plugin generated from a query label.
 *
 * The plugin reads the declaration and copies its names and types. It works out none of its own:
 * SqlDelight has
 * already resolved the column adapters, the `AS` types and the nullability, and a type this plugin
 * guessed would be wrong in cases a reader cannot tell apart from the right ones.
 *
 * Null when the project has not been built, so nothing generated exists to read. The popup then
 * falls back to the bind arguments the query itself names.
 */
object SqldLiteGeneratedQuery {

    private const val QUERIES_SUFFIX = "Queries"

    /** The generated function's declaration, without its body. Null when there is none to read. */
    fun signatureOf(label: SqldLiteStmtIdentifierMixin): String? =
        functionOf(label)?.let { SqldLiteKotlinSignature.of(it) }

    /**
     * The generated function for [label], searched by the `Foo.sq` to `FooQueries` convention that
     * [SqldLiteQueryCall] already reads in the other direction.
     */
    private fun functionOf(label: SqldLiteStmtIdentifierMixin): KtNamedFunction? {
        val name = label.name ?: return null
        val project = label.project
        // The stub index cannot be read while the project is indexing, as SqldLiteQueryCallSites
        // and SqldLiteJavaTypeReference also have to allow for.
        if (DumbService.isDumb(project)) return null

        val className = label.containingFile.name.substringBeforeLast('.') + QUERIES_SUFFIX
        return KotlinClassShortNameIndex
            .get(className, project, GlobalSearchScope.allScope(project))
            // Generated output sits in the module that owns the `.sq` file, so that module's class
            // wins when two modules generate a class of the same name.
            .sortedByDescending { sameModule(it, label) }
            .firstNotNullOfOrNull { queryFunctionIn(it, name) }
    }

    /**
     * The function called [name] that a caller writes, which is the one taking no mapper.
     *
     * SqlDelight generates a second overload taking a `(...) -> T` mapper, for a caller supplying
     * its own row type. Showing that one would put the generated machinery in front of the reader
     * instead of the function they call.
     */
    private fun queryFunctionIn(owner: KtClassOrObject, name: String): KtNamedFunction? =
        owner
            .declarations
            .filterIsInstance<KtNamedFunction>()
            .filter { it.name == name }
            .firstOrNull { function ->
                function.valueParameters.none { it.typeReference?.typeElement is KtFunctionType }
            }

    private fun sameModule(candidate: PsiElement, label: PsiElement): Boolean =
        ModuleUtilCore.findModuleForPsiElement(candidate) ==
            ModuleUtilCore.findModuleForPsiElement(label)
}
