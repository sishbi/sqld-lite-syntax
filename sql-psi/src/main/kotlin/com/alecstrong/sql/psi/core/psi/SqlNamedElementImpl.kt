package com.alecstrong.sql.psi.core.psi

import com.alecstrong.sql.psi.core.SqlParser
import com.intellij.lang.ASTNode
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiBuilderFactory
import com.intellij.lang.parser.GeneratedParserUtilBase
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.impl.GeneratedMarkerVisitor
import com.intellij.psi.impl.source.tree.TreeElement

abstract class SqlNamedElementImpl(node: ASTNode) :
  SqlCompositeElementImpl(node), PsiNameIdentifierOwner {
  abstract val parseRule: (builder: PsiBuilder, level: Int) -> Boolean

  override fun getText(): String {
    return (node.findChildByType(SqlTypes.ID)?.text ?: node.findChildByType(SqlTypes.STRING)!!.text)
      .trim('\'', '"', '`', '[', ']')
  }

  override fun getName() = text

  override fun setName(name: String): PsiElement {
    // This whole thing is a hack. Its copied from an internal implementation of creating a fake
    // file tree, with some changes here so that we're not creating the entire fake file and just
    // doing the replacement inline. If this needs changing check out how Properties plugin
    // is implemented since it's inspired by that, which is documented online in IntelliJ's
    // official documentation for writing a language plugin. Good luck!

    val parserDefinition: ParserDefinition =
      LanguageParserDefinitions.INSTANCE.forLanguage(language)
    var builder =
      PsiBuilderFactory.getInstance()
        .createBuilder(project, parent.node, parserDefinition.createLexer(project), language, name)
    builder =
      GeneratedParserUtilBase.adapt_builder_(
        node.elementType,
        builder,
        SqlParser(),
        SqlParser.EXTENDS_SETS_,
      )
    GeneratedParserUtilBase.ErrorState.get(builder).currentFrame = GeneratedParserUtilBase.Frame()

    parseRule(builder, 0)
    val element = builder.treeBuilt
    (element as TreeElement).acceptTree(GeneratedMarkerVisitor())
    parent.node.replaceChild(node, element)
    return this
  }

  override fun getNameIdentifier(): PsiElement? {
    return findChildByType(SqlTypes.ID)
  }

  /**
   * How this name is drawn in a tree, such as the target line of the Find Usages panel.
   *
   * [com.intellij.psi.impl.PsiElementBase] returns null here, and
   * [com.intellij.usages.PsiElement2UsageTargetAdapter] reads the presentation directly rather than
   * calling [getIcon] or consulting the `itemPresentationProvider` extension point. Without this
   * override the target line shows neither a name nor an icon.
   *
   * Every field is read now, not when the cell is painted. A tree cell renderer paints on the EDT,
   * which holds no read action.
   */
  override fun getPresentation(): ItemPresentation {
    val presentableText = name
    val locationString = if (isValid) containingFile.name else null
    val presentationIcon = getIcon(0)
    return object : ItemPresentation {
      override fun getPresentableText() = presentableText

      override fun getLocationString() = locationString

      override fun getIcon(unused: Boolean) = presentationIcon
    }
  }
}
