package com.alecstrong.sql.psi.core

import com.alecstrong.sql.psi.core.psi.SqlTypes
import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.lang.ParserDefinition
import com.intellij.lang.ParserDefinition.SpaceRequirements.MAY
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.TokenType
import com.intellij.psi.tree.StubFileElementType
import com.intellij.psi.tree.TokenSet

abstract class SqlParserDefinition : ParserDefinition {
  private val WHITE_SPACES = TokenSet.create(TokenType.WHITE_SPACE)
  private val COMMENTS = TokenSet.create(SqlTypes.COMMENT)

  init {
    SqlElementType._language = getLanguage()
  }

  override fun createLexer(project: Project): Lexer = SqlLexerAdapter()

  override fun getWhitespaceTokens() = WHITE_SPACES

  override fun getCommentTokens() = COMMENTS

  override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY

  override fun spaceExistenceTypeBetweenTokens(p0: ASTNode, p1: ASTNode) = MAY

  override fun createElement(node: ASTNode): PsiElement = SqlParserUtil.createElement(node)

  override fun createParser(project: Project) = SqlParser()

  abstract override fun createFile(viewProvider: FileViewProvider): SqlFileBase

  /**
   * Must be a stub file element type, not a plain [com.intellij.psi.tree.IFileElementType].
   *
   * A table, view or column name resolves across files through
   * [com.alecstrong.sql.psi.core.psi.SchemaContributorIndex], which is a stub index. A file whose
   * node type builds no stubs contributes nothing to it, so every name resolves only inside its own
   * file. Nothing reports that: resolution returns null. The narrower return type here turns a
   * silent runtime failure into a compile error.
   *
   * [StubFileElementType] is the whole family, so a subclass may return
   * [com.intellij.psi.tree.IStubFileElementType] or the light variant
   * [com.intellij.psi.tree.ILightStubFileElementType]. All three carry `ApiStatus.Obsolete` on
   * 2025.2, which the platform's `LanguageStubDescriptor` replaces. Moving to that is a change to
   * how stubs are registered, not just to this type.
   */
  abstract override fun getFileNodeType(): StubFileElementType<*>

  abstract fun getLanguage(): Language
}
