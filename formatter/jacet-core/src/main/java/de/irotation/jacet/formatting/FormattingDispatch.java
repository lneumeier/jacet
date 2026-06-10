package de.irotation.jacet.formatting;

import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import de.irotation.jacet.document.Document;

/**
 * The capabilities a delegate formatter receives from the visitor: dispatch a CST node to its handler, and read
 * the comments attached to the surrounding tokens. Deliberately narrow — configuration (print width, options) is
 * not exposed, so delegates emit layout-neutral {@link Document} IR and the printer owns every width decision.
 *
 * <p>Two comment-access styles coexist by necessity:
 * <ul>
 *   <li><b>Node attachment</b> ({@link #leading}/{@link #trailing}/{@link #dangling}/{@link #hasTrailing}) —
 *       backed by the {@code CommentAttacher} pre-pass, for comments owned by a {@link ParserRuleContext}.</li>
 *   <li><b>Token index</b> ({@link #leadingComments(int)}/{@link #trailingComment(int)}) — for a comment anchored
 *       to a bare terminal with no enclosing rule context to attach to: a separator comma, a lambda/switch arrow,
 *       a binary operator, a {@code ?}/{@code :}. Read on demand at the emission site.</li>
 * </ul>
 *
 * <p>Both styles mark each emitted comment printed (via the shared printed-index set) so it can never be emitted
 * twice across the two paths.
 */
public interface FormattingDispatch {

  /** Dispatch a CST node to its registered handler and return the resulting {@link Document} IR. */
  Document visit(ParseTree tree);

  /** Leading stand-alone comments on the hidden channel to the left of {@code tokenIndex}. */
  List<Document> leadingComments(int tokenIndex);

  /** The same-line trailing comment to the right of {@code tokenIndex}, or empty if there is none. */
  Document trailingComment(int tokenIndex);

  /** Whether a blank-line separator should be emitted before the node starting at {@code context}. */
  boolean hasBlankLineBefore(ParserRuleContext context);

  /**
   * Node-attachment comment API backed by the {@code CommentAttacher} pre-pass. Each emission marks the
   * comment printed so the token-index API can't re-emit it.
   */
  List<Document> leading(ParserRuleContext context);

  Document trailing(ParserRuleContext context);

  List<Document> dangling(ParserRuleContext context);

  /**
   * Peek: the raw dangling comments attached to the given node, without rendering or marking them printed.
   * Lets a body renderer choose a layout (hug vs. break) from the comment kinds and positions before
   * committing to an emission via {@link #dangling}.
   */
  List<Comment> danglingComments(ParserRuleContext context);

  /** Peek: does the given node have any unprinted trailing comments attached? */
  boolean hasTrailing(ParserRuleContext context);
}
