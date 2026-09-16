package com.alecstrong.sql.psi.core

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import com.alecstrong.sql.psi.test.fixtures.compileFiles
import org.junit.Test

class TablesExposedTest {
  @Test
  fun `tables works correctly for include all`() {
    compileFiles(
      """
      |CREATE TABLE test1 (
      |  id TEXT NOT NULL
      |);
      |
      |CREATE TABLE test2 (
      |  id TEXT NOT NULL
      |);
      |
      |CREATE TABLE test3 (
      |  id TEXT NOT NULL
      |);
      """
        .trimMargin(),
      """
      |CREATE TABLE test4 (
      |  id TEXT NOT NULL
      |);
      |
      |ALTER TABLE test2 ADD COLUMN id2 TEXT NOT NULL;
      |
      |ALTER TABLE test3 RENAME TO test5;
      """
        .trimMargin(),
      predefined =
        listOf(
          """
          |CREATE TABLE predefined (
          |  id TEXT NOT NULL
          |);
          """
            .trimMargin()
        ),
    ) { (_, file) ->
      assertThat(file.tables(includeAll = true).map { it.tableName.text })
        .containsExactlyInAnyOrder("predefined", "test1", "test2", "test4", "test5")
    }
  }

  @Test
  fun `tables works correctly for include all=false`() {
    compileFiles(
      """
      |CREATE TABLE test1 (
      |  id TEXT NOT NULL
      |);
      |
      |CREATE TABLE test2 (
      |  id TEXT NOT NULL
      |);
      |
      |CREATE TABLE test3 (
      |  id TEXT NOT NULL
      |);
      """
        .trimMargin(),
      """
      |CREATE TABLE test4 (
      |  id TEXT NOT NULL
      |);
      |
      |ALTER TABLE test2 ADD COLUMN id2 TEXT NOT NULL;
      |
      |ALTER TABLE test3 RENAME TO test5;
      """
        .trimMargin(),
      predefined =
        listOf(
          """
          |CREATE TABLE predefined (
          |  id TEXT NOT NULL
          |);
          """
            .trimMargin()
        ),
    ) { (_, file) ->
      assertThat(file.tables(includeAll = false).map { it.tableName.text })
        .containsExactlyInAnyOrder("test2", "test4", "test5")
    }
  }

  @Test
  fun `schemaChain returns the create and every alter in migration order`() {
    compileFiles(
      """
      |CREATE TABLE test (
      |  id TEXT NOT NULL
      |);
      """
        .trimMargin(),
      "ALTER TABLE test ADD COLUMN id2 TEXT;",
      "ALTER TABLE test ADD COLUMN id3 TEXT;",
    ) { files ->
      // Every link in the chain, not just the neighbour a reference resolves to.
      assertThat(files.last().schemaChain("test").map { it.containingFile.name })
        .containsExactly("0.s", "1.s", "2.s")

      // A name nothing declares, such as a table a query invents.
      assertThat(files.last().schemaChain("missing")).isEmpty()
    }
  }
}
