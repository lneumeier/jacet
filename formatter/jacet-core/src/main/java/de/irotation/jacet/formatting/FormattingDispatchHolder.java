package de.irotation.jacet.formatting;

import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.jspecify.annotations.Nullable;

import de.irotation.jacet.document.Document;

/**
 * Lazily initialized {@link FormattingDispatch} proxy that breaks the circular dependency between the visitor and the delegate
 * formatters: delegates are constructed against the holder before the {@link DocumentVisitor} backing it exists.
 */
final class FormattingDispatchHolder implements FormattingDispatch {
  private @Nullable FormattingDispatch delegate;

  void initialize(final FormattingDispatch delegate) {
    this.delegate = delegate;
  }

  private FormattingDispatch delegate() {
    if (delegate == null) {
      throw new IllegalStateException("FormattingDispatchHolder not yet initialized");
    }
    return delegate;
  }

  @Override
  public Document visit(final ParseTree tree) {
    return this.delegate().visit(tree);
  }

  @Override
  public List<Document> leadingComments(final int tokenIndex) {
    return this.delegate().leadingComments(tokenIndex);
  }

  @Override
  public Document trailingComment(final int tokenIndex) {
    return this.delegate().trailingComment(tokenIndex);
  }

  @Override
  public boolean hasBlankLineBefore(final ParserRuleContext context) {
    return this.delegate().hasBlankLineBefore(context);
  }

  @Override
  public List<Document> leading(final ParserRuleContext context) {
    return this.delegate().leading(context);
  }

  @Override
  public Document trailing(final ParserRuleContext context) {
    return this.delegate().trailing(context);
  }

  @Override
  public List<Document> dangling(final ParserRuleContext context) {
    return this.delegate().dangling(context);
  }

  @Override
  public List<Comment> danglingComments(final ParserRuleContext context) {
    return this.delegate().danglingComments(context);
  }

  @Override
  public boolean hasTrailing(final ParserRuleContext context) {
    return this.delegate().hasTrailing(context);
  }
}
