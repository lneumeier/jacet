package de.irotation.jacet.formatting;

import static de.irotation.jacet.document.Document.concat;
import static de.irotation.jacet.document.Document.group;
import static de.irotation.jacet.document.Document.indent;
import static de.irotation.jacet.document.Document.indentIfBreak;
import static de.irotation.jacet.document.Document.line;
import static de.irotation.jacet.document.Document.softLine;
import static de.irotation.jacet.document.Document.text;
import static de.irotation.jacet.document.Document.willBreak;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import de.irotation.jacet.document.Document;
import de.irotation.jacet.document.Group;
import de.irotation.jacet.parser.JavaParser;

/**
 * Formats operator expressions (binary/assignment, ternary, unary, instanceof, cast, indexing) and parenthesized primaries.
 */
final class OperatorFormatter implements HandlerProvider {

  private final FormattingDispatch dispatch;

  OperatorFormatter(final FormattingDispatch dispatch) {
    this.dispatch = dispatch;
  }

  private static boolean parentIsSameOpChain(final JavaParser.BinaryOperatorExpressionContext ctx, final String op) {
    if (!(ctx.getParent() instanceof final JavaParser.BinaryOperatorExpressionContext parent)) {
      return false;
    }
    return Operators.binaryOpText(parent).equals(op);
  }

  private static ParseTree binaryOpLastToken(final JavaParser.BinaryOperatorExpressionContext ctx) {
    return ctx.getChild(ctx.getChildCount() - 2);
  }

  /**
   * Whether the parent context already establishes the indentation level for this binary expression, so the binop must not add a second
   * indent step. True for: a parenthesized primary; a variable initializer or {@code var} local declaration; a {@code return}/{@code throw}
   * statement or an {@code if}/{@code while}/{@code do}/{@code synchronized} condition (the parentheses already indent the inner
   * expression); an operand of another binary expression (already positioned by the enclosing binop's line break — prettier only indents a
   * binary expression when it is an argument or statement value, not when nested in another binop); and a lambda body.
   */
  private static boolean parentSuppressesBinopIndent(final JavaParser.BinaryOperatorExpressionContext ctx) {
    final ParseTree parent = ctx.getParent();
    if (parent instanceof final JavaParser.PrimaryContext primary) {
      return primary.expression() != null;
    }
    if (parent instanceof JavaParser.VariableInitializerContext) {
      return true;
    }
    if (parent instanceof final JavaParser.LocalVariableDeclarationContext lvd) {
      return lvd.VAR() != null;
    }
    if (parent instanceof final JavaParser.StatementContext stmt) {
      return (
        stmt.RETURN() != null ||
        stmt.THROW() != null ||
        stmt.IF() != null ||
        stmt.WHILE() != null ||
        stmt.DO() != null ||
        stmt.SYNCHRONIZED() != null
      );
    }
    if (parent instanceof JavaParser.BinaryOperatorExpressionContext) {
      return true;
    }
    return parent instanceof JavaParser.LambdaBodyContext;
  }

  /**
   * Whether a parenthesized primary breaks its parens onto their own lines (prettier-java's indentInParentheses: {@code (\n  content\n)})
   * because it is the whole value of a "root" context — a return statement ({@code return (expr);}) or a pattern guard
   * ({@code case ... when (expr) ->}). Nested parens (operands of a larger expression) hug instead. The paren PrimaryContext sits under a
   * PrimaryExpressionContext whose parent is the StatementContext (return) or the GuardContext.
   */
  private static boolean isParenIndentRoot(final JavaParser.PrimaryContext primaryContext) {
    if (!(primaryContext.getParent() instanceof final JavaParser.PrimaryExpressionContext primExpr)) {
      return false;
    }
    final ParseTree owner = primExpr.getParent();
    if (owner instanceof final JavaParser.StatementContext stmt && stmt.RETURN() != null) {
      return true;
    }
    return owner instanceof JavaParser.GuardContext;
  }

  /**
   * Whether the parenthesized primary is the receiver of a suffix chain — {@code (expr).method()}, {@code (expr)[i]}, {@code (expr)::ref},
   * {@code (expr)++}. prettier-java keeps these on the indentInParentheses layout (parens break onto their own lines) rather than hugging,
   * since the trailing suffix reads better that way.
   */
  private static boolean isParenChainReceiver(final JavaParser.PrimaryContext primaryContext) {
    if (!(primaryContext.getParent() instanceof final JavaParser.PrimaryExpressionContext primExpr)) {
      return false;
    }
    final ParseTree owner = primExpr.getParent();
    if (owner instanceof final JavaParser.MemberReferenceExpressionContext mref) {
      return mref.expression() == primExpr;
    }
    if (owner instanceof final JavaParser.SquareBracketExpressionContext sqb) {
      return sqb.expression(0) == primExpr;
    }
    if (owner instanceof final JavaParser.MethodReferenceExpressionContext mref) {
      return mref.expression() == primExpr;
    }
    if (owner instanceof final JavaParser.PostIncrementDecrementOperatorExpressionContext post) {
      return post.expression() == primExpr;
    }
    return false;
  }

  @Override
  public void registerHandlers(final HandlerRegistry registry) {
    registry.register(JavaParser.PrimaryExpressionContext.class, this::visitPrimaryExpression);
    registry.register(JavaParser.BinaryOperatorExpressionContext.class, this::visitBinaryOperatorExpression);
    registry.register(JavaParser.TernaryExpressionContext.class, this::visitTernaryExpression);
    registry.register(JavaParser.UnaryOperatorExpressionContext.class, this::visitUnaryOperatorExpression);
    registry.register(
      JavaParser.PostIncrementDecrementOperatorExpressionContext.class,
      this::visitPostIncrementDecrementOperatorExpression
    );
    registry.register(JavaParser.SquareBracketExpressionContext.class, this::visitSquareBracketExpression);
    registry.register(JavaParser.CastExpressionContext.class, this::visitCastExpression);
    registry.register(JavaParser.InstanceOfOperatorExpressionContext.class, this::visitInstanceOfOperatorExpression);
    registry.register(JavaParser.PrimaryContext.class, this::visitPrimary);
  }

  Document visitPrimaryExpression(final JavaParser.PrimaryExpressionContext expressionContext) {
    return dispatch.visit(expressionContext.primary());
  }

  /**
   * Formats a binary or assignment expression.
   *
   * <p>Assignment ({@code lhs op rhs}) mirrors {@link StatementFormatter}'s variable declarator, branching on the RHS:
   * <ul>
   *   <li>binary RHS (not itself an assignment): a single wrap-after-{@code op} group, forced to break when the RHS operand breaks (a method
   *       chain) so the fits-check doesn't stop at the operand's own break and keeps {@code lhs = receiver} on one line.
   *   <li>leaf-like primary RHS: a single wrap-after-{@code op} group.
   *   <li>ternary RHS: the coupled-break layout from {@link Operators#formatTernaryRhs} (condition and arms break together with the
   *       assignment).
   *   <li>otherwise: the prettier split — outer group brackets the whole assignment, an inner line-only group plus {@code indentIfBreak}
   *       chooses between {@code lhs = right} (RHS keeps its own break opportunities) and {@code lhs =\n  right} (RHS at indent+1 when even
   *       the start of right doesn't fit).
   * </ul>
   *
   * <p>For a non-assignment binary expression the operands are collected into a same-operator chain. A single non-logical operator
   * ({@code chain == 0}, {@code a + b}) keeps the left as its own group and wraps {@code " op" <line> right} in a sibling group: a breaking
   * LEFT operand does NOT pull the operator onto a new line ({@code chain.orElse(-1) == 0} keeps {@code == 0} glued, the chain breaks its own
   * dots), while a long/breaking RIGHT operand wraps after the operator. Only the right continuation is indented, and only when the parent
   * doesn't already establish the level ({@link #parentSuppressesBinopIndent}). Logical {@code &&}/{@code ||} and multi-operator
   * same-precedence chains break all operands together; the group is forced to break when any operand will break (jacet's printer doesn't
   * propagate that across the operand's own group boundary), and the whole binary group is indented one level unless the parent suppresses
   * it.
   */
  Document visitBinaryOperatorExpression(final JavaParser.BinaryOperatorExpressionContext expressionContext) {
    final String op = Operators.binaryOpText(expressionContext);
    if (Operators.isAssignmentOperator(op)) {
      final JavaParser.ExpressionContext right = expressionContext.expression(1);
      final Document leftDoc = dispatch.visit(expressionContext.expression(0));
      final Document opDoc = Operators.binaryOpDoc(expressionContext);
      if (
        right instanceof final JavaParser.BinaryOperatorExpressionContext rightBin &&
        !Operators.isAssignmentOperator(Operators.binaryOpText(rightBin))
      ) {
        final Document rightDoc = dispatch.visit(right);
        return new Group(concat(leftDoc, text(" "), opDoc, indent(concat(line(), rightDoc))), willBreak(rightDoc));
      }
      if (Operators.isLeafLikePrimary(right)) {
        return group(concat(leftDoc, text(" "), opDoc, indent(concat(line(), dispatch.visit(right)))));
      }
      if (right instanceof final JavaParser.TernaryExpressionContext ternary) {
        return Operators.formatTernaryRhs(dispatch, concat(leftDoc, text(" "), opDoc), ternary);
      }
      final Group inner = new Group(indent(line()), false);
      return group(concat(leftDoc, text(" "), opDoc, inner, indentIfBreak(inner, dispatch.visit(right))));
    }
    if (parentIsSameOpChain(expressionContext, op)) {
      return concat(
        dispatch.visit(expressionContext.expression(0)),
        text(" "),
        Operators.binaryOpDoc(expressionContext),
        line(),
        dispatch.visit(expressionContext.expression(1))
      );
    }
    final List<Document> chain = new ArrayList<>();
    this.collectBinaryOpChain(expressionContext, op, chain);
    if (chain.size() <= 1) {
      return concat(chain);
    }
    final Document first = chain.getFirst();
    final List<Document> rest = chain.subList(1, chain.size());
    final boolean leftIsSameOp =
      expressionContext.expression(0) instanceof final JavaParser.BinaryOperatorExpressionContext leftBin &&
      Operators.binaryOpText(leftBin).equals(op);
    if (!leftIsSameOp && !Operators.isLogicalOp(op)) {
      final Document opSegment = new Group(concat(rest), false);
      return parentSuppressesBinopIndent(expressionContext) ? concat(first, opSegment) : concat(first, indent(opSegment));
    }
    final boolean anyOperandBreaks = chain.stream().anyMatch(Document::willBreak);
    if (parentSuppressesBinopIndent(expressionContext)) {
      return new Group(concat(first, concat(rest)), anyOperandBreaks);
    }
    return new Group(indent(concat(first, concat(rest))), anyOperandBreaks);
  }

  private void collectBinaryOpChain(final ParseTree node, final String op, final List<Document> out) {
    if (node instanceof final JavaParser.BinaryOperatorExpressionContext bin && Operators.binaryOpText(bin).equals(op)) {
      this.collectBinaryOpChain(bin.expression(0), op, out);
      out.add(text(" "));
      out.add(Operators.binaryOpDoc(bin));
      if (binaryOpLastToken(bin) instanceof final TerminalNode opNode) {
        out.add(dispatch.trailingComment(opNode.getSymbol().getTokenIndex()));
      }
      out.add(line());
      out.addAll(dispatch.leading(bin.expression(1)));
      this.collectBinaryOpChain(bin.expression(1), op, out);
      return;
    }
    out.add(dispatch.visit(node));
  }

  /**
   * Formats a ternary ({@code cond ? a : b}). A nested ternary (one that is itself a branch of another ternary) gets no group and no indent
   * on its suffix — prettier-java's isNestedTernary path returns {@code [prefix, suffix]}, so the parent branch's indent supplies each
   * level's step and sharing the parent's group co-breaks all levels together. A top-level ternary wraps the suffix in its own indented
   * group, forced to break when a branch is itself a breaking construct (a wrapping chain, a block lambda): jacet's printer doesn't
   * propagate that break across the branch's own group boundary.
   */
  Document visitTernaryExpression(final JavaParser.TernaryExpressionContext expressionContext) {
    final int questionIndex = expressionContext.QUESTION().getSymbol().getTokenIndex();
    final int colonIndex = expressionContext.COLON().getSymbol().getTokenIndex();
    final Document thenBranch = Operators.ternaryBranchWithComment(dispatch, expressionContext.expression(1), questionIndex);
    final Document elseBranch = Operators.ternaryBranchWithComment(dispatch, expressionContext.expression(2), colonIndex);
    final Document suffix = concat(
      line(),
      Tokens.sourced(expressionContext.QUESTION()),
      thenBranch,
      line(),
      Tokens.sourced(expressionContext.COLON()),
      elseBranch
    );
    if (expressionContext.getParent() instanceof JavaParser.TernaryExpressionContext) {
      return concat(dispatch.visit(expressionContext.expression(0)), suffix);
    }
    final Document contents = concat(dispatch.visit(expressionContext.expression(0)), indent(suffix));
    return new Group(contents, willBreak(thenBranch) || willBreak(elseBranch));
  }

  Document visitUnaryOperatorExpression(final JavaParser.UnaryOperatorExpressionContext expressionContext) {
    if (expressionContext.prefix == null) {
      return dispatch.visit(expressionContext.expression());
    }
    return concat(Tokens.sourced(expressionContext.prefix), dispatch.visit(expressionContext.expression()));
  }

  Document visitPostIncrementDecrementOperatorExpression(
    final JavaParser.PostIncrementDecrementOperatorExpressionContext expressionContext
  ) {
    return concat(dispatch.visit(expressionContext.expression()), Tokens.sourced((TerminalNode) expressionContext.getChild(1)));
  }

  Document visitSquareBracketExpression(final JavaParser.SquareBracketExpressionContext expressionContext) {
    return concat(
      dispatch.visit(expressionContext.expression(0)),
      Tokens.sourced(expressionContext.LBRACK()),
      dispatch.visit(expressionContext.expression(1)),
      Tokens.sourced(expressionContext.RBRACK())
    );
  }

  Document visitCastExpression(final JavaParser.CastExpressionContext expressionContext) {
    final List<Document> parts = new ArrayList<>();
    parts.add(Tokens.sourced(expressionContext.LPAREN()));
    for (final JavaParser.AnnotationContext annotation : expressionContext.annotation()) {
      parts.add(dispatch.visit(annotation));
      parts.add(text(" "));
    }
    final List<Document> types = expressionContext.typeType().stream().map(dispatch::visit).toList();
    parts.add(Tokens.joinSourced(types, expressionContext.BITAND(), text(" "), text(" ")));
    parts.add(Tokens.sourced(expressionContext.RPAREN()));
    parts.add(text(" "));
    parts.add(dispatch.visit(expressionContext.expression()));
    return concat(parts);
  }

  Document visitInstanceOfOperatorExpression(final JavaParser.InstanceOfOperatorExpressionContext expressionContext) {
    final List<Document> parts = new ArrayList<>();
    parts.add(dispatch.visit(expressionContext.expression()));
    parts.add(text(" "));
    parts.add(Tokens.sourced(expressionContext.INSTANCEOF()));
    parts.add(text(" "));
    if (expressionContext.typeType() != null) {
      parts.add(dispatch.visit(expressionContext.typeType()));
    }
    if (expressionContext.pattern() != null) {
      parts.add(dispatch.visit(expressionContext.pattern()));
    }
    return concat(parts);
  }

  /**
   * Formats a primary: {@code this}/{@code super}, literals, identifiers, {@code .class}, and parenthesized expressions.
   *
   * <p>prettier-java's parenthesized_expression breaks the parens onto their own lines (indentInParentheses: {@code (\n  content\n)}) only
   * when the paren is a return-value root ({@link #isParenIndentRoot}) or a suffix-chain receiver ({@link #isParenChainReceiver}); every
   * other nested paren hugs its content. Special cases:
   * <ul>
   *   <li>chain receiver whose inner is a ternary: hugs the open paren — {@code (test\n  ? a\n  : b\n).method()} — the test stays on the
   *       {@code (} line, the ternary self-indents its {@code ?}/{@code :}, only the close paren drops to its own line.
   *   <li>indent-root / chain-receiver: the symmetric indentInParentheses layout, forced to wrap when (for a root) the content breaks, since
   *       jacet's printer doesn't propagate the inner break across the group boundary.
   *   <li>nested operand: hug. A lambda or ternary supplies its own indentation; any other content (a binary chain) is indented one step so
   *       its continuation operands sit under the {@code (}.
   * </ul>
   */
  Document visitPrimary(final JavaParser.PrimaryContext primaryContext) {
    if (primaryContext.THIS() != null) {
      return Tokens.sourced(primaryContext.THIS());
    }
    if (primaryContext.SUPER() != null) {
      return Tokens.sourced(primaryContext.SUPER());
    }
    if (primaryContext.literal() != null) {
      return dispatch.visit(primaryContext.literal());
    }
    if (primaryContext.identifier() != null) {
      return dispatch.visit(primaryContext.identifier());
    }
    if (primaryContext.expression() != null) {
      final JavaParser.ExpressionContext innerExpr = primaryContext.expression();
      final Document inner = dispatch.visit(innerExpr);
      final boolean indentRoot = isParenIndentRoot(primaryContext);
      final boolean chainReceiver = isParenChainReceiver(primaryContext);
      if (chainReceiver && innerExpr instanceof JavaParser.TernaryExpressionContext) {
        return new Group(
          concat(Tokens.sourced(primaryContext.LPAREN()), inner, softLine(), Tokens.sourced(primaryContext.RPAREN())),
          willBreak(inner)
        );
      }
      if (indentRoot || chainReceiver) {
        final boolean forceWrap = indentRoot && willBreak(inner);
        return new Group(
          concat(
            Tokens.sourced(primaryContext.LPAREN()),
            indent(concat(softLine(), inner)),
            softLine(),
            Tokens.sourced(primaryContext.RPAREN())
          ),
          forceWrap
        );
      }
      final boolean innerSelfIndents =
        innerExpr instanceof JavaParser.ExpressionLambdaContext || innerExpr instanceof JavaParser.TernaryExpressionContext;
      return group(
        concat(Tokens.sourced(primaryContext.LPAREN()), innerSelfIndents ? inner : indent(inner), Tokens.sourced(primaryContext.RPAREN()))
      );
    }
    if (primaryContext.typeTypeOrVoid() != null) {
      return concat(
        dispatch.visit(primaryContext.typeTypeOrVoid()),
        Tokens.sourced(primaryContext.DOT()),
        Tokens.sourced(primaryContext.CLASS())
      );
    }
    return Tokens.sourced(primaryContext);
  }
}
