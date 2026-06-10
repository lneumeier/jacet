package de.irotation.jacet.formatting;

import static de.irotation.jacet.document.Document.breakIndent;
import static de.irotation.jacet.document.Document.concat;
import static de.irotation.jacet.document.Document.hardLine;
import static de.irotation.jacet.document.Document.indent;
import static de.irotation.jacet.document.Document.indentIfBreak;
import static de.irotation.jacet.document.Document.line;
import static de.irotation.jacet.document.Document.text;
import static de.irotation.jacet.document.Document.willBreak;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import de.irotation.jacet.document.Document;
import de.irotation.jacet.document.Group;
import de.irotation.jacet.document.LineSuffix;
import de.irotation.jacet.parser.JavaParser;

/**
 * Operator-level helpers shared across the expression formatters and {@link StatementFormatter}: operator-text reconstruction, operator
 * classification, and the ternary right-hand-side layout (which assignment statements reuse for {@code lhs = cond ? a : b}).
 */
final class Operators {

  private Operators() {}

  /**
   * Reconstructs the operator text of a binary expression. Most operators are a single token ({@code getChild(1)}), but the shift operators
   * {@code <<}, {@code >>}, {@code >>>} are split into individual {@code <} / {@code >} tokens by the parser rule
   * ({@code expression ('<' '<' | '>' '>' '>' | '>' '>') expression}) so generics like {@code List<List<Foo>>} can tokenize. Reading only
   * {@code getChild(1)} would silently drop the trailing {@code >}s and corrupt the source, so every middle child is concatenated.
   */
  static String binaryOpText(final JavaParser.BinaryOperatorExpressionContext ctx) {
    final int n = ctx.getChildCount();
    final StringBuilder sb = new StringBuilder();
    for (int i = 1; i < n - 1; i++) {
      sb.append(ctx.getChild(i).getText());
    }
    return sb.toString();
  }

  /**
   * Sources the operator tokens of a binary expression (the {@code getChild(1)}..{@code getChild(n-2)} span, possibly multiple terminals
   * for split shift operators — see {@link #binaryOpText}) so the operator is tracked for coverage rather than reconstructed as synthetic
   * text.
   */
  static Document binaryOpDoc(final JavaParser.BinaryOperatorExpressionContext ctx) {
    final int n = ctx.getChildCount();
    final List<Document> parts = new ArrayList<>();
    for (int i = 1; i < n - 1; i++) {
      parts.add(Tokens.sourced((TerminalNode) ctx.getChild(i)));
    }
    return concat(parts);
  }

  static boolean isLogicalOp(final String op) {
    return "&&".equals(op) || "||".equals(op);
  }

  static boolean isAssignmentOperator(final String op) {
    return switch (op) {
      case "=", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<=", ">>=", ">>>=" -> true;
      default -> false;
    };
  }

  /**
   * Whether an expression is a "leaf-like" primary — an un-parenthesized literal, identifier, {@code this}, or {@code super}. Such a
   * right-hand side has no internal break opportunity, so an assignment wrapping after the operator is its only break. Used by the
   * binary-assignment RHS layout in {@code OperatorFormatter}.
   */
  static boolean isLeafLikePrimary(final JavaParser.ExpressionContext expr) {
    if (!(expr instanceof final JavaParser.PrimaryExpressionContext primaryExpr)) {
      return false;
    }
    final JavaParser.PrimaryContext primary = primaryExpr.primary();
    if (primary.expression() != null) {
      return false;
    }
    return primary.literal() != null || primary.identifier() != null || primary.THIS() != null || primary.SUPER() != null;
  }

  /**
   * Like {@link #isLeafLikePrimary} but additionally excludes multi-line string / text-block literals: a literal that carries its own
   * newlines is not leaf-like for variable-declarator RHS layout (treating it as leaf-like would push it onto an indented line of its own),
   * so it falls through to the general assignment split instead.
   */
  static boolean isLeafLikeRhs(final JavaParser.ExpressionContext expr) {
    if (!isLeafLikePrimary(expr)) {
      return false;
    }
    final JavaParser.PrimaryContext primary = ((JavaParser.PrimaryExpressionContext) expr).primary();
    return primary.literal() == null || !containsNewline(primary.literal().getText());
  }

  private static boolean containsNewline(final String value) {
    return value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
  }

  /**
   * Lays out a ternary expression used as an assignment right-hand side ({@code prefix = cond ? a : b}), reused by binary-assignment and
   * variable-declarator formatting.
   *
   * <p>The ternary is its own group (prettier-java's {@code group([condition, indent(arms)])}), kept separate from the assignment's
   * break-after-{@code =} group. Decoupling them is the point: when only the {@code prefix = ternary} line overflows but the ternary fits
   * once moved to its own line, the arms stay flat instead of breaking with the assignment.
   *
   * <p>Two prettier-java assignment branches:
   * <ul>
   *   <li>binary condition: {@code [prefix, group(indent([line, ternary]))]}.
   *   <li>any other condition: {@code [prefix, group(indent(line), {id}), indentIfBreak(ternary, {id})]}.
   * </ul>
   *
   * <p>{@code armBreaks} is forced on the relevant groups because jacet's printer does not propagate a forced break upward the way prettier's
   * {@code propagateBreaks} does — a branch that is itself a breaking construct (wrapping chain, block lambda) must break both the assignment
   * line and the arms.
   */
  static Document formatTernaryRhs(
    final FormattingDispatch dispatch,
    final Document prefix,
    final JavaParser.TernaryExpressionContext ctx
  ) {
    final JavaParser.ExpressionContext condition = ctx.expression(0);
    final Document conditionDoc = dispatch.visit(condition);
    final int questionIndex = ctx.QUESTION().getSymbol().getTokenIndex();
    final int colonIndex = ctx.COLON().getSymbol().getTokenIndex();
    final Document thenBranch = ternaryBranchWithComment(dispatch, ctx.expression(1), questionIndex);
    final Document elseBranch = ternaryBranchWithComment(dispatch, ctx.expression(2), colonIndex);
    final Document arms = indent(
      concat(line(), Tokens.sourced(ctx.QUESTION()), thenBranch, line(), Tokens.sourced(ctx.COLON()), elseBranch)
    );
    final boolean armBreaks = willBreak(thenBranch) || willBreak(elseBranch);
    final Document ternary = new Group(concat(conditionDoc, arms), armBreaks);

    if (condition instanceof JavaParser.BinaryOperatorExpressionContext) {
      return new Group(concat(prefix, indent(concat(line(), ternary))), armBreaks);
    }

    final Group inner = new Group(indent(line()), false);
    return concat(prefix, inner, indentIfBreak(inner, ternary));
  }

  /**
   * Formats one ternary branch (the {@code ? a} or {@code : b} expression) with any trailing comment on the {@code ?}/{@code :} token.
   *
   * <p>When the operator carries a trailing same-line comment, the comment is itself a {@code lineSuffix(concat(" ", "// ..."))} whose
   * leading
   * space (added by the comment pre-pass, not here) separates it from the {@code ?}/{@code :} prefix; the branch expression then moves to
   * the next line at {@code +indent}. Otherwise the branch is indented by indent-relative (not cursor-relative) {@code indent()}: prettier
   * indents the branch by {@code align(2)} from the {@code ?}/{@code :} column, which with {@code tabWidth} 2 equals one indent step. Using
   * {@code indent()} keeps a branch that itself breaks — e.g. a method chain — from landing two columns too deep.
   */
  static Document ternaryBranchWithComment(final FormattingDispatch dispatch, final ParseTree branchExpr, final int operatorTokenIndex) {
    final Document comment = dispatch.trailingComment(operatorTokenIndex);
    final Document exprDoc = dispatch.visit(branchExpr);
    if (comment instanceof LineSuffix) {
      return concat(comment, breakIndent(concat(hardLine(), indent(exprDoc))));
    }
    return concat(text(" "), indent(exprDoc));
  }
}
