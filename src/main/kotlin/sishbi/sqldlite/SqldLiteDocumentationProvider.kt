package sishbi.sqldlite

import com.alecstrong.sql.psi.core.psi.SchemaContributor
import com.alecstrong.sql.psi.core.psi.SqlColumnDef
import com.alecstrong.sql.psi.core.psi.SqlNamedElementImpl
import com.alecstrong.sql.psi.core.psi.SqlColumnName
import com.alecstrong.sql.psi.core.psi.SqlStmtList
import com.alecstrong.sql.psi.core.psi.SqlTableName
import com.alecstrong.sql.psi.core.psi.SqlViewName
import com.alecstrong.sql.psi.core.psi.SqlTypes
import com.intellij.lang.Language
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/**
 * What the IDE shows for a `.sq` element, on cmd-hover and under Quick Documentation.
 *
 * Both read the same declaration line: a query as `findById(:id)`, a column as its column
 * definition, a table as the head of the statement that declares it. Quick Documentation adds the
 * doc comment and the statement itself, highlighted by this language's lexer. The hover stays one
 * line, the way Kotlin's does: a hover inside a `.sq` file that repeats the query under the caret
 * tells the reader nothing.
 *
 * Without a provider the platform falls back to `SingleTargetElementInfo`, which can only write
 * `query "findAllEvents" [AdminAppAudit.sq]`.
 */
class SqldLiteDocumentationProvider : AbstractDocumentationProvider() {

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? =
        callSiteInfo(element) ?: subjectOf(element)?.let { render(it, withBody = false) }

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? =
        callSiteInfo(element) ?: subjectOf(element)?.let { render(it, withBody = true) }

    /**
     * A Kotlin call site, in the same shape as a query: what it is, what it calls, and where. The
     * platform would otherwise fall back to a plain string, because Kotlin's own provider declines
     * an element it did not make.
     *
     * Null for anything that is not a call site, which is what lets the rest of this class run.
     */
    private fun callSiteInfo(element: PsiElement?): String? {
        if (element !is SqldLiteCallSiteTarget) return null
        val anchor = element.navigationElement
        return render(
            Subject(
                element = anchor,
                kind = SqldLiteMessageBundle.message("callsite.type"),
                declaration = element.name.orEmpty(),
                // The enclosing function, highlighted by Kotlin's own lexer rather than written
                // out as grey text: it is a declaration, and it reads as one in the editor. A grey
                // `Class.member` line beside it said the same thing twice, because a generated
                // query function and the member calling it are often named alike.
                signature = element.signature,
                signatureLanguage = anchor.language,
                // The qualified class, the way Kotlin's popup places a function. The file name
                // repeats the class for a Kotlin file and leaves out the package.
                location = element.qualifiedContainer,
                body = null,
                comment = null,
            ),
            withBody = false,
        )
    }

    /**
     * What is shown, read from PSI when the popup is asked for rather than while it paints.
     *
     * @param kind what the element is, as the Find Usages panel names it.
     * @param declaration the one line at the top.
     * @param signature the declaration holding it, for a call site the enclosing function.
     * @param signatureLanguage what lexer highlights [signature]; Kotlin for a call site.
     * @param location the grey line at the bottom; the file name when nothing better is known.
     * @param body the whole statement, which only Quick Documentation shows.
     * @param comment the query's doc comment, as HTML.
     */
    private class Subject(
        val element: PsiElement,
        val kind: String?,
        val declaration: String,
        val body: String?,
        val comment: String?,
        val signature: String? = null,
        val signatureLanguage: Language = SqldLiteLanguage,
        val location: String? = null,
    )

    private fun subjectOf(element: PsiElement?): Subject? =
        when (element) {
            is SqldLiteStmtIdentifierMixin -> querySubject(element)
            is SqldLiteBindParameterMixin -> bindArgumentSubject(element)
            is SqlNamedElementImpl -> schemaSubject(element)
            else -> null
        }

    /** A query, as the generated function reads: its name and the arguments a caller passes. */
    private fun querySubject(label: SqldLiteStmtIdentifierMixin): Subject? {
        val name = label.name ?: return null
        val arguments = SqldLiteQuery
            .of(label)
            .parameterNames
            .filter { it.isNotEmpty() }
            .joinToString(", ") { ":$it" }
        return Subject(
            element = label,
            kind = SqldLiteMessageBundle.message("usages.type.query"),
            declaration = "$name($arguments)",
            body = statementsUnder(label).ifEmpty { null },
            comment = docComment(label),
        )
    }

    /** A bind argument, with the query that declares it behind it. */
    private fun bindArgumentSubject(parameter: SqldLiteBindParameterMixin): Subject? {
        val name = parameter.name ?: return null
        val label = SqldLiteQuery.containing(parameter)?.label
        return Subject(
            element = parameter,
            kind = SqldLiteMessageBundle.message("usages.type.bind.argument"),
            declaration = ":$name",
            body = label?.let { statementsUnder(it).ifEmpty { null } },
            comment = null,
        )
    }

    /**
     * A table, view or column name: the statement that declares it. A column shows its own column
     * definition, which is the declaration a reader means; anything else shows the head of the
     * statement, so a `CREATE TABLE` of forty columns still fits on the line.
     *
     * The name under the caret is usually a use, so it is resolved first. A declaration resolves to
     * itself.
     */
    private fun schemaSubject(name: SqlNamedElementImpl): Subject? {
        val declaration = name.reference?.resolve() ?: name
        val statement = statementOf(declaration)
        val column = PsiTreeUtil.getParentOfType(declaration, SqlColumnDef::class.java)
        // Nothing but the name itself when the name does not resolve into a schema statement. A
        // column of a table that the migrations only ever alter is the case that showed this: the
        // statement holding the unresolved name is the query, so the popup read
        // `column: SELECT branch_name, title, ...`, which is not a declaration of anything.
        // A `SchemaContributor` is the statement that declares something; the ancestor the
        // statement list holds is a `SqlStmt` wrapper around it, so the test has to look down.
        val declares = PsiTreeUtil.getParentOfType(declaration, SchemaContributor::class.java, false)
        if (column == null && (statement == null || declares == null)) {
            return Subject(
                element = name,
                kind = schemaKindOf(name),
                declaration = name.text,
                body = null,
                comment = null,
            )
        }
        val head = column?.text ?: statement?.text?.lineSequence()?.first() ?: name.text
        return Subject(
            element = declaration,
            kind = schemaKindOf(name),
            declaration = head.trim().removeSuffix("(").trim(),
            body = statement?.text?.trim(),
            comment = null,
        )
    }

    /** What a schema name declares, as the Find Usages panel names it. */
    private fun schemaKindOf(name: SqlNamedElementImpl): String? =
        when (name) {
            is SqlTableName -> SqldLiteMessageBundle.message("usages.type.table")
            is SqlViewName -> SqldLiteMessageBundle.message("usages.type.view")
            is SqlColumnName -> SqldLiteMessageBundle.message("usages.type.column")
            else -> null
        }

    /** Everything between [label] and the next label, which is the query's own text. */
    private fun statementsUnder(label: SqldLiteStmtIdentifierMixin): String =
        generateSequence(label.nextSibling) { it.nextSibling }
            .takeWhile { it !is SqldLiteStmtIdentifierMixin }
            .joinToString("") { it.text }
            .trim()

    /** The statement holding [element]: the ancestor the statement list holds directly. */
    private fun statementOf(element: PsiElement): PsiElement? {
        val statements = PsiTreeUtil.getParentOfType(element, SqlStmtList::class.java) ?: return null
        return generateSequence(element) { it.parent }.firstOrNull { it.parent == statements }
    }

    /** The label's `/** ... */` comment, as HTML. Null when the query carries none. */
    private fun docComment(label: SqldLiteStmtIdentifierMixin): String? {
        val javadoc = label.node.findChildByType(SqlTypes.JAVADOC)?.text ?: return null
        return javadoc
            .removePrefix("/**")
            .removeSuffix("*/")
            .lines()
            .map { it.trim().removePrefix("*").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("<br/>") { StringUtil.escapeXmlEntities(it) }
            .ifEmpty { null }
    }

    /**
     * The popup: a definition line, then the file, with the doc comment and the statement between
     * them under Quick Documentation.
     *
     * The file name is a grey line of its own rather than a section. A section is a two-column
     * table, so its header sat beside a value that wrapped under it, which read as a label out of
     * place.
     */
    private fun render(subject: Subject, withBody: Boolean): String {
        val html = StringBuilder(DocumentationMarkup.DEFINITION_START)
        subject.kind?.let {
            // Styled as a keyword, the way Kotlin writes `fun` before a name. Lexing it with the
            // SQL text instead would colour it as an identifier, because that is what it is. The
            // colon separates it from a declaration that starts with a word of its own, such as
            // `table: CREATE TABLE ...`.
            HtmlSyntaxInfoUtil.appendStyledSpan(html, SqldLiteTextAttributes.KEYWORD, "$it:", SATURATION)
            html.append(" ")
        }
        highlight(html, subject, subject.declaration, SqldLiteLanguage)
        html.append(DocumentationMarkup.DEFINITION_END)

        html.append(DocumentationMarkup.CONTENT_START)
        // In a `<pre>`, so the signature is monospaced as the editor writes it. Outside one the
        // platform's own proportional font applies, and `fun loan(loanId: Long)` reads as prose.
        subject.signature?.let {
            html.append("<pre>")
            highlight(html, subject, it, subject.signatureLanguage)
            html.append("</pre>")
        }
        if (withBody) {
            subject.comment?.let { html.append("<p>").append(it).append("</p>") }
            subject.body?.let {
                html.append("<pre>")
                highlight(html, subject, it, SqldLiteLanguage)
                html.append("</pre>")
            }
        }
        // An element with no file is not a case anything reaches today, but a popup that throws
        // logs an error on every hover, and this plugin exists to not do that.
        val location = subject.location ?: subject.element.containingFile?.name
        location?.let {
            html
                .append(DocumentationMarkup.GRAYED_START)
                .append(StringUtil.escapeXmlEntities(it))
                .append(DocumentationMarkup.GRAYED_END)
        }
        html.append(DocumentationMarkup.CONTENT_END)
        return html.toString()
    }

    private fun highlight(html: StringBuilder, subject: Subject, text: String, language: Language) {
        HtmlSyntaxInfoUtil.appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(
            html,
            subject.element.project,
            language,
            text,
            SATURATION,
        )
    }

    private companion object {
        /** Full colour, as the editor paints it. */
        private const val SATURATION = 1.0f
    }
}
