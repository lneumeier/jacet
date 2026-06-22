package de.irotation.jacet.formatting;

import static de.irotation.jacet.document.Document.concat;
import static de.irotation.jacet.document.Document.hardLine;
import static de.irotation.jacet.document.Document.indent;
import static de.irotation.jacet.document.Document.softLine;
import static de.irotation.jacet.document.Document.text;
import static de.irotation.jacet.document.Document.willBreak;

import java.util.ArrayList;
import java.util.List;

import de.irotation.jacet.document.Document;
import de.irotation.jacet.document.Group;
import de.irotation.jacet.parser.JavaParser;

/**
 * Formats control flow statements: if/else, for, enhanced-for, while, do-while.
 */
final class ControlFlowFormatter implements HandlerProvider {

  private final FormattingDispatch dispatch;
  private final boolean forceBraces;

  ControlFlowFormatter(final FormattingDispatch dispatch, final boolean forceBraces) {
    this.dispatch = dispatch;
    this.forceBraces = forceBraces;
  }

  @Override
  public void registerHandlers(final HandlerRegistry registry) {
    registry.register(JavaParser.ForControlContext.class, this::visitForControl);
    registry.register(JavaParser.EnhancedForControlContext.class, this::visitEnhancedForControl);
    registry.register(JavaParser.ForInitContext.class, this::visitForInit);
  }

  private Document visitBodyStatement(final JavaParser.StatementContext statement) {
    if (forceBraces && statement.block() == null) {
      return concat(text("{"), indent(concat(hardLine(), dispatch.visit(statement))), hardLine(), text("}"));
    }
    return dispatch.visit(statement);
  }

  /**
   * Formats an {@code if}/{@code else}. A comment between the then-branch and the {@code else} keyword
   * ({@code } else // c}) attaches as the then-statement's trailing comment but sits after {@code else}, so the
   * visitor's immediate-trailing safety net skips it and it is rendered here. For a line comment (a breaking
   * LineSuffix) the else-branch breaks onto its own line so the comment keeps its line and the layout reparses
   * identically (idempotence) instead of flushing past the branch's opening brace.
   */
  Document visitIfStatement(final JavaParser.StatementContext statementContext) {
    final List<Document> parts = new ArrayList<>();
    parts.add(
      parenthesizedCondition(
        dispatch.visit(statementContext.expression(0)),
        Tokens.sourced(statementContext.IF()),
        Tokens.sourced(statementContext.LPAREN()),
        Tokens.sourced(statementContext.RPAREN())
      )
    );
    parts.add(text(" "));
    parts.add(this.visitBodyStatement(statementContext.statement(0)));

    if (statementContext.ELSE() != null) {
      parts.add(text(" "));
      parts.add(Tokens.sourced(statementContext.ELSE()));
      final Document elseComment = dispatch.trailing(statementContext.statement(0));
      parts.add(elseComment);
      parts.add(willBreak(elseComment) ? hardLine() : text(" "));
      final JavaParser.StatementContext elseStmt = statementContext.statement(1);
      if (elseStmt.IF() != null) {
        parts.add(dispatch.visit(elseStmt));
      } else {
        parts.add(this.visitBodyStatement(elseStmt));
      }
    }

    return concat(parts);
  }

  /**
   * Formats a {@code for} statement. The header stays on one line and never wraps at the parens, even when it
   * overflows the print width — neither shape has a useful parens-level break: an enhanced-for's
   * {@code Type x : expr()} is a single chain, and a C-style for's {@code init; cond; update} clauses would, if
   * broken at the parens, only produce a 3-line shell around the same long content. Either way a parens-level
   * wrap adds structure without shortening the overflowing line, so it is suppressed for both.
   */
  Document visitForStatement(final JavaParser.StatementContext statementContext) {
    return concat(
      Tokens.sourced(statementContext.FOR()),
      text(" "),
      Tokens.sourced(statementContext.LPAREN()),
      dispatch.visit(statementContext.forControl()),
      Tokens.sourced(statementContext.RPAREN()),
      text(" "),
      this.visitBodyStatement(statementContext.statement(0))
    );
  }

  Document visitWhileStatement(final JavaParser.StatementContext statementContext) {
    return concat(
      parenthesizedCondition(
        dispatch.visit(statementContext.expression(0)),
        Tokens.sourced(statementContext.WHILE()),
        Tokens.sourced(statementContext.LPAREN()),
        Tokens.sourced(statementContext.RPAREN())
      ),
      text(" "),
      this.visitBodyStatement(statementContext.statement(0))
    );
  }

  /**
   * Wraps an {@code if}/{@code while} condition prettier-style ({@code keyword (} + condition + {@code )}),
   * breaking the parens onto their own lines when the condition breaks or overflows the width. The group is
   * forced to break when the condition will break, because jacet's printer doesn't propagate the condition's
   * own break across the group boundary; a width-only break still wraps via the group's fits-check.
   */
  private static Document parenthesizedCondition(
    final Document condition,
    final Document keyword,
    final Document lparen,
    final Document rparen
  ) {
    return new Group(concat(keyword, text(" "), lparen, indent(concat(softLine(), condition)), softLine(), rparen), willBreak(condition));
  }

  Document visitDoWhileStatement(final JavaParser.StatementContext statementContext) {
    return concat(
      Tokens.sourced(statementContext.DO()),
      text(" "),
      this.visitBodyStatement(statementContext.statement(0)),
      text(" "),
      Tokens.sourced(statementContext.WHILE()),
      text(" "),
      Tokens.sourced(statementContext.LPAREN()),
      dispatch.visit(statementContext.expression(0)),
      Tokens.sourced(statementContext.RPAREN()),
      Tokens.sourced(statementContext.SEMI())
    );
  }

  Document visitSynchronizedStatement(final JavaParser.StatementContext statementContext) {
    return concat(
      Tokens.sourced(statementContext.SYNCHRONIZED()),
      text(" "),
      Tokens.sourced(statementContext.LPAREN()),
      dispatch.visit(statementContext.expression(0)),
      Tokens.sourced(statementContext.RPAREN()),
      text(" "),
      dispatch.visit(statementContext.block())
    );
  }

  Document visitForControl(final JavaParser.ForControlContext forContext) {
    if (forContext.enhancedForControl() != null) {
      return dispatch.visit(forContext.enhancedForControl());
    }
    final List<Document> parts = new ArrayList<>();
    if (forContext.forInit() != null) {
      parts.add(dispatch.visit(forContext.forInit()));
    }
    parts.add(Tokens.sourced(forContext.SEMI(0)));
    parts.add(text(" "));
    if (forContext.expression() != null) {
      parts.add(dispatch.visit(forContext.expression()));
    }
    parts.add(Tokens.sourced(forContext.SEMI(1)));
    parts.add(text(" "));
    if (forContext.expressionList() != null) {
      parts.add(dispatch.visit(forContext.expressionList()));
    }
    return concat(parts);
  }

  Document visitEnhancedForControl(final JavaParser.EnhancedForControlContext forContext) {
    final List<Document> parts = new ArrayList<>(Modifiers.formatVariable(dispatch, forContext.variableModifier()));
    if (forContext.VAR() != null) {
      parts.add(Tokens.sourced(forContext.VAR()));
    } else {
      parts.add(dispatch.visit(forContext.typeType()));
    }
    parts.add(text(" "));
    parts.add(dispatch.visit(forContext.variableDeclaratorId()));
    parts.add(text(" "));
    parts.add(Tokens.sourced(forContext.COLON()));
    parts.add(text(" "));
    parts.add(dispatch.visit(forContext.expression()));
    return concat(parts);
  }

  Document visitForInit(final JavaParser.ForInitContext forContext) {
    if (forContext.localVariableDeclaration() != null) {
      return dispatch.visit(forContext.localVariableDeclaration());
    }
    return dispatch.visit(forContext.expressionList());
  }
}
