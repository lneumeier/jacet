package de.irotation.jacet.formatting;

import static de.irotation.jacet.document.Document.concat;
import static de.irotation.jacet.document.Document.empty;
import static de.irotation.jacet.document.Document.text;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;

import de.irotation.jacet.document.Document;
import de.irotation.jacet.parser.JavaParserBaseVisitor;

/**
 * The ANTLR visitor that turns a parsed CST into the {@link Document} IR. Each rule context is dispatched to the
 * delegate formatter registered for its type in the {@link HandlerRegistry}; unhandled contexts fall through to
 * ANTLR's child aggregation. Implements {@link FormattingDispatch} so the delegate formatters can re-enter the
 * visitor and read attached comments without seeing the configuration. Per-format state only (token stream,
 * handlers, comment attachment) — created fresh per format call by {@code DocumentVisitorFactory}.
 */
public final class DocumentVisitor extends JavaParserBaseVisitor<Document> implements FormattingDispatch {
  private final CommentHelper commentHelper;
  private final HandlerRegistry handlers;
  private final Map<ParserRuleContext, NodeComments> commentAttachment;

  DocumentVisitor(
    final CommonTokenStream tokens,
    final HandlerRegistry handlers,
    final Map<ParserRuleContext, NodeComments> commentAttachment
  ) {
    this.commentHelper = new CommentHelper(tokens);
    this.handlers = handlers;
    this.commentAttachment = commentAttachment;
  }

  @Override
  public Document visit(final ParseTree tree) {
    final Document result = super.visit(tree);
    if (result != null) {
      return result;
    }
    if (tree instanceof final ParserRuleContext ruleContext) {
      throw new IllegalStateException(
        "No Document produced for " +
          ruleContext.getClass().getSimpleName() +
          " at line " +
          ruleContext.getStart().getLine() +
          ":" +
          ruleContext.getStart().getCharPositionInLine()
      );
    }
    if (tree instanceof final TerminalNode terminal) {
      return Tokens.sourced(terminal);
    }
    return text(tree.getText());
  }

  /** The comment renderer backing this visitor, exposed so {@code CoverageVerifier} can read printed indices. */
  public CommentHelper commentHelper() {
    return commentHelper;
  }

  @Override
  public List<Document> leadingComments(final int tokenIndex) {
    return commentHelper.leadingComments(tokenIndex);
  }

  @Override
  public Document trailingComment(final int tokenIndex) {
    return commentHelper.trailingComment(tokenIndex);
  }

  @Override
  public boolean hasBlankLineBefore(final ParserRuleContext context) {
    return commentHelper.hasBlankLineBefore(context);
  }

  @Override
  public List<Document> leading(final ParserRuleContext context) {
    final NodeComments attached = commentAttachment.get(context);
    if (attached == null) {
      return List.of();
    }
    return commentHelper.renderLeading(attached);
  }

  @Override
  public Document trailing(final ParserRuleContext context) {
    final NodeComments attached = commentAttachment.get(context);
    if (attached == null) {
      return empty();
    }
    return commentHelper.renderTrailing(attached);
  }

  @Override
  public List<Document> dangling(final ParserRuleContext context) {
    final NodeComments attached = commentAttachment.get(context);
    if (attached == null) {
      return List.of();
    }
    return commentHelper.renderDangling(attached);
  }

  @Override
  public List<Comment> danglingComments(final ParserRuleContext context) {
    final NodeComments attached = commentAttachment.get(context);
    if (attached == null) {
      return List.of();
    }
    return attached.dangling();
  }

  @Override
  public boolean hasTrailing(final ParserRuleContext context) {
    final NodeComments attached = commentAttachment.get(context);
    return attached != null && !attached.trailing().isEmpty();
  }

  @Override
  protected Document defaultResult() {
    return empty();
  }

  @Override
  protected Document aggregateResult(final Document aggregate, final Document nextResult) {
    if (Document.EMPTY.equals(aggregate)) {
      return nextResult;
    }
    if (Document.EMPTY.equals(nextResult)) {
      return aggregate;
    }
    return concat(aggregate, nextResult);
  }

  @Override
  public Document visitChildren(final RuleNode node) {
    if (node instanceof final ParserRuleContext ruleContext) {
      final Function<ParserRuleContext, Document> handler = handlers.get(ruleContext.getClass());
      final Document result = handler != null ? handler.apply(ruleContext) : super.visitChildren(node);
      return appendImmediateTrailing(ruleContext, result);
    }
    return super.visitChildren(node);
  }

  /**
   * Safety net for a comment {@link CommentAttacher} attaches as a node's trailing comment that no delegate
   * formatter renders — a comment sitting mid-expression directly after an operand (before a {@code )}, before a
   * {@code .} in a method chain, before a binary operator). Without this the comment is silently dropped and
   * {@code CoverageVerifier} skips the whole file. Restricted to comments <em>immediately</em> following the node
   * (see {@link CommentHelper#renderTrailingImmediate}) so it never steals a comment a delegate renders via the
   * token-index API at its own position. A trailing line comment renders as a {@code LineSuffix}, which
   * {@code willBreak} treats as a forced break, so an enclosing group that consults it keeps the comment's line.
   */
  private Document appendImmediateTrailing(final ParserRuleContext context, final Document result) {
    final NodeComments attached = commentAttachment.get(context);
    if (attached == null || attached.trailing().isEmpty() || context.getStop() == null) {
      return result;
    }
    final Document trailing = commentHelper.renderTrailingImmediate(attached, context.getStop().getTokenIndex());
    if (Document.EMPTY.equals(trailing)) {
      return result;
    }
    return concat(result, trailing);
  }
}
