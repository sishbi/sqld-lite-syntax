package com.alecstrong.sql.psi.core

import com.alecstrong.sql.psi.core.psi.LazyQuery
import com.alecstrong.sql.psi.core.psi.Schema
import com.alecstrong.sql.psi.core.psi.SchemaContributor
import com.alecstrong.sql.psi.core.psi.SchemaContributorIndex
import com.alecstrong.sql.psi.core.psi.SqlCreateTableStmt
import com.alecstrong.sql.psi.core.psi.SqlCreateTriggerStmt
import com.alecstrong.sql.psi.core.psi.SqlCreateViewStmt
import com.alecstrong.sql.psi.core.psi.SqlStmtList
import com.alecstrong.sql.psi.core.psi.TableElement
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.Language
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import kotlin.reflect.KClass

abstract class SqlFileBase(viewProvider: FileViewProvider, language: Language) :
  PsiFileBase(viewProvider, language) {
  /**
   * This file's position in the migration chain, and null when it is a queries file.
   *
   * It is not decoration. A migration's schema is built only from the statements above the one
   * being read, and only from files numbered lower, and that is decided from this value. Null means
   * "not a migration", so every statement in the file contributes to every other, and two files
   * that both alter one table resolve to each other.
   *
   * A non-null value must be unique across the chain. It also sets the order statements are read in
   * by [schema] and [schemaChain].
   */
  abstract val order: Long?

  val sqlStmtList
    get() = findChildByClass(SqlStmtList::class.java)

  /**
   * Every statement that declares [name], oldest first: the `CREATE` and each `ALTER` after it.
   *
   * A migration chain is a linked list, not a set. The name in an `ALTER TABLE` resolves to the
   * statement below it, and a query resolves only to the newest one, so following references from
   * any single link reports one neighbour and stops. This reads the whole chain in one call.
   *
   * Ordered by the containing file's [order], then by position within the file. A statement in a
   * file with no order has no place in a chain and comes last.
   *
   * A statement is indexed under the name it declares, so an `ALTER TABLE a RENAME TO b` is part of
   * the chain for `a`, not for `b`.
   */
  fun schemaChain(name: String): List<SchemaContributor> {
    return SchemaContributorIndex.getInstance(project)
      .byName(name, project, searchScope())
      .sortedWith(compareBy({ it.containingFile.order ?: Long.MAX_VALUE }, { it.textOffset }))
  }

  fun tablesAvailable(child: PsiElement) = schema<TableElement>(child).map { it.tableExposed() }

  fun triggers(sqlStmtElement: PsiElement?): Collection<SqlCreateTriggerStmt> =
    schema(sqlStmtElement)

  internal inline fun <reified T : SchemaContributor> schema(
    sqlStmtElement: PsiElement? = null,
    includeAll: Boolean = true,
  ): Collection<T> {
    return schema(T::class, sqlStmtElement, includeAll)
  }

  fun <T : SchemaContributor> schema(
    type: KClass<T>,
    sqlStmtElement: PsiElement? = null,
    includeAll: Boolean = true,
  ): Collection<T> {
    val schema = Schema()
    iteratePreviousStatements(type, sqlStmtElement, includeAll) { statement ->
      if (sqlStmtElement != null && PsiTreeUtil.isAncestor(sqlStmtElement, statement, false)) {
        if (order == null && (statement is TableElement && statement !is SqlCreateTableStmt)) {
          // If we're in a queries file, the table is not available to itself (unless its a create).
          return@iteratePreviousStatements
        }
        if (order != null) {
          // If we're in a migration file, only return the tables up to this point.
          if (statement is SqlCreateTableStmt) statement.modifySchema(schema)
          return@schema schema.values(type)
        }
      }

      statement.modifySchema(schema)
    }
    return schema.values(type)
  }

  /**
   * @param includeAll If true, also return tables that other files expose.
   * @return Tables this file exposes as LazyQuery.
   */
  fun tables(includeAll: Boolean): Collection<LazyQuery> {
    val tables = schema<TableElement>().map { it.tableExposed() }
    return if (includeAll) tables else tables.filter { it.tableName.containingFile == this }
  }

  internal fun viewForName(name: String): SqlCreateViewStmt? {
    return schema<TableElement>().filterIsInstance<SqlCreateViewStmt>().singleOrNull {
      it.name() == name
    }
  }

  private fun views(): List<SqlCreateViewStmt> {
    return sqlStmtList?.stmtList?.mapNotNull { it.createViewStmt }.orEmpty()
  }

  private fun tables(): List<TableElement> {
    return sqlStmtList
      ?.stmtList
      ?.mapNotNull {
        (it.createViewStmt as TableElement?) ?: it.createTableStmt ?: it.createVirtualTableStmt
      }
      .orEmpty()
  }

  private inline fun <T : SchemaContributor> iteratePreviousStatements(
    type: KClass<T>,
    until: PsiElement?,
    includeAll: Boolean = true,
    block: (SchemaContributor) -> Unit,
  ) {
    if (includeAll) {
      val orderedContributors = sortedMapOf<Long, LinkedHashSet<SchemaContributor>>()
      val topContributors = LinkedHashSet<SchemaContributor>()
      val index = SchemaContributorIndex.getInstance(project)

      index.byKey(type.java.name, project, searchScope()).forEach {
        val file = it.containingFile

        if (file == originalFile) {
          return@forEach
        } else if (order != null && file.order == null) {
          return@forEach
        } else if (order == null && file.order == null) {
          topContributors.add(it)
        } else if (order == null || (file.order != null && file.order!! < order!!)) {
          orderedContributors.getOrPut(file.order!!, { linkedSetOf() }).add(it)
        }
      }

      val baseContributorFiles = baseContributorFiles()
      for ((baseContributorIndex, baseContributor) in baseContributorFiles.withIndex()) {
        // Put the last file to index -1, and the previous files to previous index.
        val orderedIndex = -1 - baseContributorFiles.lastIndex + baseContributorIndex.toLong()
        baseContributor.contributors()?.let { contributors ->
          orderedContributors[orderedIndex] = linkedSetOf(elements = contributors.toTypedArray())
        }
      }

      orderedContributors.forEach { (_, contributors) ->
        contributors.sortedBy { it.textOffset }.forEach(block)
      }
      topContributors.forEach(block)
    }

    contributors()
      ?.takeWhile { order == null || until == null || it.textOffset <= until.textOffset }
      ?.forEach { block(it) }
  }

  private fun contributors() =
    sqlStmtList?.stmtList?.mapNotNull { it.firstChild as? SchemaContributor }

  /**
   * Optional files which can be used for extra Schema Contributors that are unindexed. The files
   * are added to the schema in the provided order.
   */
  protected open fun baseContributorFiles(): List<SqlFileBase> = emptyList()

  protected open fun searchScope(): GlobalSearchScope {
    return GlobalSearchScope.everythingScope(project)
  }
}
