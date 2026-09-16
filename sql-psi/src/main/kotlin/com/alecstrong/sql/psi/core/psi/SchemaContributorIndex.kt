package com.alecstrong.sql.psi.core.psi

import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.AbstractStubIndex
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey

interface SchemaContributorIndex {
  fun getKey(): StubIndexKey<String, SchemaContributor>

  /**
   * Every statement indexed under [key], which is either the name of a kind of statement, such as
   * `com.alecstrong.sql.psi.core.psi.TableElement`, or a [nameKey].
   *
   * Not named `get`. [AbstractStubIndex.get] takes the same arguments after erasure and is
   * deprecated, so an implementation extending [StringStubIndexExtension] overrode it by accident
   * and the Plugin Verifier reported a deprecated API usage in every consumer.
   */
  fun byKey(key: String, project: Project, scope: GlobalSearchScope): Collection<SchemaContributor>

  /**
   * Every statement that declares [name], in no particular order.
   *
   * [get] is keyed by the kind of statement, such as [TableElement], because that is what building
   * a schema asks for. Asking it for a name returns nothing. This asks the other way round.
   *
   * A statement is indexed under the name it declares, so an `ALTER TABLE a RENAME TO b` answers to
   * `a` and not to `b`. Use [com.alecstrong.sql.psi.core.SqlFileBase.schemaChain] for a chain in
   * order.
   */
  fun byName(
    name: String,
    project: Project,
    scope: GlobalSearchScope,
  ): Collection<SchemaContributor> =
    // The filter is what makes this correct for an implementation that ignores the key, such as
    // the headless one in the environment module.
    byKey(nameKey(name), project, scope).filter { it.name() == name }

  companion object {
    val KEY by lazy { StubIndexKey.createIndexKey<String, SchemaContributor>("sqldelight.schema") }

    /**
     * The key a statement declaring [name] is indexed under.
     *
     * Both kinds of key share one index, so there is one stub index to register. They cannot
     * collide: the other key is a fully qualified Java class name, which holds no colon.
     */
    fun nameKey(name: String) = "name:$name"

    fun getInstance(project: Project): SchemaContributorIndex {
      return project.serviceOrNull() ?: SchemaContributorIndexImpl.instance
    }
  }
}

internal class SchemaContributorIndexImpl :
  SchemaContributorIndex, StringStubIndexExtension<SchemaContributor>() {
  /** Bumped to 5 when statements started being indexed by name as well as by kind. */
  override fun getVersion() = 5

  override fun getKey(): StubIndexKey<String, SchemaContributor> {
    return SchemaContributorIndex.KEY
  }

  override fun byKey(
    key: String,
    project: Project,
    scope: GlobalSearchScope,
  ): Collection<SchemaContributor> {
    if (DumbService.isDumb(project)) return emptyList()
    return StubIndex.getElements<String, SchemaContributor>(
      getKey(),
      key,
      project,
      scope,
      SchemaContributor::class.java,
    )
  }

  companion object {
    val instance: SchemaContributorIndexImpl = SchemaContributorIndexImpl()
  }
}
