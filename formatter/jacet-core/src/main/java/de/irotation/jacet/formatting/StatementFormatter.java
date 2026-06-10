package de.irotation.jacet.formatting;

import static de.irotation.jacet.document.Document.breakIndent;
import static de.irotation.jacet.document.Document.concat;
import static de.irotation.jacet.document.Document.empty;
import static de.irotation.jacet.document.Document.group;
import static de.irotation.jacet.document.Document.hardLine;
import static de.irotation.jacet.document.Document.ifBreak;
import static de.irotation.jacet.document.Document.indent;
import static de.irotation.jacet.document.Document.indentIfBreak;
import static de.irotation.jacet.document.Document.line;
import static de.irotation.jacet.document.Document.softLine;
import static de.irotation.jacet.document.Document.text;
import static de.irotation.jacet.document.Document.willBreak;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.tree.TerminalNode;

import de.irotation.jacet.document.Document;
import de.irotation.jacet.document.Group;
import de.irotation.jacet.document.LineSuffix;
import de.irotation.jacet.parser.JavaParser;

/**
 * Formats blocks, variable declarations, and simple statements (return, throw, break, continue, yield, assert). Dispatches control flow,
 * switch, and exception handling to dedicated formatters.
 */
final class StatementFormatter implements HandlerProvider {

  private final FormattingDispatch dispatch;
  private final ControlFlowFormatter controlFlow;
  private final SwitchFormatter switchFormatter;
  private final ExceptionFormatter exceptionFormatter;

  StatementFormatter(
    final FormattingDispatch dispatch,
    final ControlFlowFormatter controlFlow,
    final SwitchFormatter switchFormatter,
    final ExceptionFormatter exceptionFormatter
  ) {
    this.dispatch = dispatch;
    this.controlFlow = controlFlow;
    this.switchFormatter = switchFormatter;
    this.exceptionFormatter = exceptionFormatter;
  }

  /**
   * An empty block hugs its comment only for a lone single-line block comment that the author wrote on the brace line. Everything else —
   * line comments (which would swallow the closing brace), multi-line comments, own-line placement — keeps the broken layout.
   */
  private static boolean isHuggableEmptyBlockComment(final List<Comment> comments) {
    if (comments.size() != 1) {
      return false;
    }
    final Comment comment = comments.getFirst();
    return comment.isBlockComment() && !comment.precedingNewline() && !comment.text().contains("\n") && !comment.text().contains("\r");
  }

  @Override
  public void registerHandlers(final HandlerRegistry registry) {
    registry.register(JavaParser.FieldDeclarationContext.class, this::visitFieldDeclaration);
    registry.register(JavaParser.ConstDeclarationContext.class, this::visitConstDeclaration);
    registry.register(JavaParser.VariableDeclaratorsContext.class, this::visitVariableDeclarators);
    registry.register(JavaParser.VariableDeclaratorContext.class, this::visitVariableDeclarator);
    registry.register(JavaParser.VariableDeclaratorIdContext.class, this::visitVariableDeclaratorId);
    registry.register(JavaParser.VariableInitializerContext.class, this::visitVariableInitializer);
    registry.register(JavaParser.ArrayInitializerContext.class, this::visitArrayInitializer);
    registry.register(JavaParser.BlockContext.class, this::visitBlock);
    registry.register(JavaParser.BlockStatementContext.class, this::visitBlockStatement);
    registry.register(JavaParser.LocalVariableDeclarationContext.class, this::visitLocalVariableDeclaration);
    registry.register(JavaParser.StatementContext.class, this::visitStatement);
    registry.register(JavaParser.LocalTypeDeclarationContext.class, this::visitLocalTypeDeclaration);
    registry.register(JavaParser.ConstantDeclaratorContext.class, this::visitConstantDeclarator);
  }

  Document visitFieldDeclaration(final JavaParser.FieldDeclarationContext fieldContext) {
    return concat(
      dispatch.visit(fieldContext.typeType()),
      text(" "),
      dispatch.visit(fieldContext.variableDeclarators()),
      Tokens.sourced(fieldContext.SEMI())
    );
  }

  Document visitConstDeclaration(final JavaParser.ConstDeclarationContext constantContext) {
    final List<Document> declarators = constantContext.constantDeclarator().stream().map(dispatch::visit).toList();
    return group(
      concat(
        dispatch.visit(constantContext.typeType()),
        text(" "),
        Tokens.joinSourced(declarators, constantContext.COMMA(), line()),
        Tokens.sourced(constantContext.SEMI())
      )
    );
  }

  Document visitVariableDeclarators(final JavaParser.VariableDeclaratorsContext declarationContext) {
    final List<Document> declarators = declarationContext.variableDeclarator().stream().map(dispatch::visit).toList();
    if (declarators.size() == 1) {
      return declarators.getFirst();
    }
    return group(Tokens.joinSourced(declarators, declarationContext.COMMA(), line()));
  }

  /**
   * Formats a variable declarator, mirroring prettier-java's assignment layout. The RHS shape picks the strategy:
   * <ul>
   *   <li><b>Ternary:</b> a single-outer-group layout couples the assignment break with the arm break, so
   *       when the assignment overflows the condition wraps to its own line and the arms wrap underneath;
   *       inlining prevents the inner ternary group from re-deciding independently.</li>
   *   <li><b>Binary, or leaf-like primary (literal, identifier, this/super):</b> wrap after {@code =}
   *       directly. Binop continuations align under the first operand (the binop's own indent step is
   *       suppressed here), and a leaf RHS has no internal break opportunity so this is its only wrap. When
   *       the RHS will break (a binary operand that is itself a breaking construct, e.g. a method chain) the
   *       break-after-{@code =} is forced — otherwise the group's fits-check stops at the operand's own break
   *       and keeps {@code id = receiver} on one line; a width-only break still wraps via fits.</li>
   *   <li><b>Method calls, chains, creators:</b> prettier's never-break-after-operator layout. An inner
   *       line-only group decides {@code id = right} (the right keeps its own break opportunities) versus
   *       breaking after {@code =}, only when even the start of the right doesn't fit; an IndentIfBreak ties
   *       the RHS base indent to that inner group's decision so a broken RHS renders at indent+1 instead of
   *       leaving its closing {@code )} orphaned at the outer indent.</li>
   * </ul>
   */
  Document visitVariableDeclarator(final JavaParser.VariableDeclaratorContext declarationContext) {
    final Document id = dispatch.visit(declarationContext.variableDeclaratorId());
    if (declarationContext.variableInitializer() == null) {
      return id;
    }
    final JavaParser.VariableInitializerContext init = declarationContext.variableInitializer();
    if (init.expression() != null) {
      final JavaParser.ExpressionContext rhs = init.expression();
      final Document assign = Tokens.sourced(declarationContext.ASSIGN());
      if (rhs instanceof final JavaParser.TernaryExpressionContext ternary) {
        return Operators.formatTernaryRhs(dispatch, concat(id, text(" "), assign), ternary);
      }
      if (isNonAssignmentBinaryExpression(rhs) || Operators.isLeafLikeRhs(rhs)) {
        final Document rhsDoc = dispatch.visit(rhs);
        return new Group(concat(id, text(" "), assign, indent(concat(line(), rhsDoc))), willBreak(rhsDoc));
      }
    }
    final Group inner = new Group(indent(line()), false);
    return group(concat(id, text(" "), Tokens.sourced(declarationContext.ASSIGN()), inner, indentIfBreak(inner, dispatch.visit(init))));
  }

  Document visitVariableDeclaratorId(final JavaParser.VariableDeclaratorIdContext identifierContext) {
    return Tokens.sourced(identifierContext);
  }

  Document visitVariableInitializer(final JavaParser.VariableInitializerContext initializerContext) {
    return dispatch.visit(initializerContext.getChild(0));
  }

  /**
   * Formats an array initializer ({@code {a, b, c}}). Kept separate from {@link TypeFormatter#formatBracedList} (annotation / element-value
   * arrays) even though both render a braced comma list: this path additionally surfaces brace-level dangling comments and forces a hard
   * break after a comma carrying a same-line trailing comment ({@code LineSuffix}) so the following element does not collapse back onto the
   * comment's line — comment behaviour the braced-list variant does not need.
   */
  Document visitArrayInitializer(final JavaParser.ArrayInitializerContext arrayContext) {
    final List<JavaParser.VariableInitializerContext> initializers = arrayContext.variableInitializer();
    if (initializers.isEmpty()) {
      return concat(Tokens.sourced(arrayContext.LBRACE()), Tokens.sourced(arrayContext.RBRACE()));
    }

    final List<TerminalNode> commas = arrayContext.COMMA();
    final boolean hasTrailingComma = commas.size() == initializers.size();
    final List<Document> parts = new ArrayList<>();
    parts.addAll(dispatch.leading(arrayContext));
    parts.addAll(dispatch.leading(initializers.getFirst()));
    parts.add(dispatch.visit(initializers.getFirst()));

    for (int i = 1; i < initializers.size(); i++) {
      final int commaIdx = commas
        .get(i - 1)
        .getSymbol()
        .getTokenIndex();
      final Document commaTrailing = dispatch.trailingComment(commaIdx);
      parts.add(Tokens.sourced(commas.get(i - 1)));
      parts.add(commaTrailing);
      parts.add(commaTrailing instanceof LineSuffix ? hardLine() : line());
      parts.addAll(dispatch.leading(initializers.get(i)));
      parts.add(dispatch.visit(initializers.get(i)));
    }
    parts.add(dispatch.trailing(initializers.getLast()));
    final Document trailingComma = hasTrailingComma ? Tokens.removed(commas.getLast()) : empty();

    return group(
      concat(
        Tokens.sourced(arrayContext.LBRACE()),
        indent(concat(line(), concat(parts), trailingComma, ifBreak(text(","), empty()))),
        dispatch.trailing(arrayContext),
        line(),
        Tokens.sourced(arrayContext.RBRACE())
      )
    );
  }

  /**
   * Formats a brace-delimited block. Dangling comments past the last statement (stand-alone lines that {@code CommentAttacher} couldn't
   * attach as same-line trailing) are rendered at body indent before the closing brace so they aren't glued to the previous statement.
   *
   * <p>In an empty block every inner comment is dangling — there is no child statement to attach to. A lone single-line block comment the
   * author wrote on the brace line hugs the braces on one line while the line fits; line comments, multi-line comments and comments on
   * their own line render at body indent between broken braces.
   */
  Document visitBlock(final JavaParser.BlockContext blockContext) {
    if (blockContext.blockStatement().isEmpty()) {
      final List<Comment> attached = dispatch.danglingComments(blockContext);
      if (attached.isEmpty()) {
        return concat(Tokens.sourced(blockContext.LBRACE()), Tokens.sourced(blockContext.RBRACE()));
      }
      final boolean huggable = isHuggableEmptyBlockComment(attached);
      return group(
        concat(
          Tokens.sourced(blockContext.LBRACE()),
          indent(concat(huggable ? line() : hardLine(), concat(dispatch.dangling(blockContext)))),
          huggable ? line() : hardLine(),
          Tokens.sourced(blockContext.RBRACE())
        )
      );
    }

    final List<Document> parts = new ArrayList<>();
    final List<JavaParser.BlockStatementContext> statements = blockContext.blockStatement();
    parts.add(hardLine());
    if (dispatch.hasBlankLineBefore(statements.getFirst())) {
      parts.add(hardLine());
    }
    for (int i = 0; i < statements.size(); i++) {
      if (i > 0) {
        parts.add(hardLine());
        if (dispatch.hasBlankLineBefore(statements.get(i))) {
          parts.add(hardLine());
        }
      }
      parts.add(dispatch.visit(statements.get(i)));
    }
    final List<Document> dangling = dispatch.dangling(blockContext);
    if (!dangling.isEmpty()) {
      parts.add(hardLine());
      parts.addAll(dangling);
    }

    return group(concat(Tokens.sourced(blockContext.LBRACE()), indent(concat(parts)), hardLine(), Tokens.sourced(blockContext.RBRACE())));
  }

  Document visitBlockStatement(final JavaParser.BlockStatementContext statementContext) {
    final List<Document> parts = new ArrayList<>(dispatch.leading(statementContext));
    if (statementContext.localVariableDeclaration() != null) {
      parts.add(dispatch.visit(statementContext.localVariableDeclaration()));
      parts.add(Tokens.sourced(statementContext.SEMI()));
    } else if (statementContext.localTypeDeclaration() != null) {
      parts.add(dispatch.visit(statementContext.localTypeDeclaration()));
    } else if (statementContext.statement() != null) {
      parts.add(dispatch.visit(statementContext.statement()));
    }
    parts.add(dispatch.trailing(statementContext));
    return concat(parts);
  }

  Document visitLocalVariableDeclaration(final JavaParser.LocalVariableDeclarationContext localVariableContext) {
    final List<Document> parts = new ArrayList<>(Modifiers.formatVariable(dispatch, localVariableContext.variableModifier(), true));
    if (localVariableContext.VAR() != null) {
      parts.add(Tokens.sourced(localVariableContext.VAR()));
      parts.add(text(" "));
      final Document assign = Tokens.sourced(localVariableContext.ASSIGN());
      final Document idDoc = dispatch.visit(localVariableContext.identifier());
      final JavaParser.ExpressionContext expr = localVariableContext.expression();
      if (expr instanceof final JavaParser.TernaryExpressionContext ternary) {
        parts.add(Operators.formatTernaryRhs(dispatch, concat(idDoc, text(" "), assign), ternary));
      } else {
        parts.add(idDoc);
        if (isNonAssignmentBinaryExpression(expr)) {
          parts.add(group(concat(text(" "), assign, indent(concat(line(), dispatch.visit(expr))))));
        } else {
          parts.add(text(" "));
          parts.add(assign);
          parts.add(text(" "));
          parts.add(dispatch.visit(expr));
        }
      }
    } else {
      parts.add(dispatch.visit(localVariableContext.typeType()));
      parts.add(text(" "));
      parts.add(dispatch.visit(localVariableContext.variableDeclarators()));
    }
    return concat(parts);
  }

  Document visitStatement(final JavaParser.StatementContext statementContext) {
    if (statementContext.block() != null && statementContext.getChildCount() == 1) {
      return dispatch.visit(statementContext.block());
    }

    if (statementContext.IF() != null) {
      return controlFlow.visitIfStatement(statementContext);
    }
    if (statementContext.FOR() != null) {
      return controlFlow.visitForStatement(statementContext);
    }
    if (statementContext.WHILE() != null && statementContext.DO() == null) {
      return controlFlow.visitWhileStatement(statementContext);
    }
    if (statementContext.DO() != null) {
      return controlFlow.visitDoWhileStatement(statementContext);
    }
    if (statementContext.SYNCHRONIZED() != null) {
      return controlFlow.visitSynchronizedStatement(statementContext);
    }

    if (statementContext.TRY() != null) {
      return exceptionFormatter.visitTryStatement(statementContext);
    }

    if (statementContext.switchExpression() != null) {
      return dispatch.visit(statementContext.switchExpression());
    }
    if (statementContext.SWITCH() != null) {
      return switchFormatter.visitSwitchStatement(statementContext);
    }

    if (statementContext.RETURN() != null) {
      return this.visitReturnStatement(statementContext);
    }
    if (statementContext.THROW() != null) {
      return this.visitThrowStatement(statementContext);
    }
    if (statementContext.BREAK() != null) {
      return this.visitBreakStatement(statementContext);
    }
    if (statementContext.CONTINUE() != null) {
      return this.visitContinueStatement(statementContext);
    }
    if (statementContext.YIELD() != null) {
      return this.visitYieldStatement(statementContext);
    }
    if (statementContext.ASSERT() != null) {
      return this.visitAssertStatement(statementContext);
    }

    if (statementContext.identifier() != null && statementContext.COLON() != null) {
      return concat(
        dispatch.visit(statementContext.identifier()),
        Tokens.sourced(statementContext.COLON()),
        text(" "),
        dispatch.visit(statementContext.statement(0))
      );
    }

    if (!statementContext.expression().isEmpty()) {
      return concat(dispatch.visit(statementContext.expression(0)), Tokens.sourced(statementContext.SEMI()));
    }

    if (statementContext.SEMI() != null) {
      return Tokens.sourced(statementContext.SEMI());
    }

    throw new IllegalStateException(
      "Unhandled statement shape at line " + statementContext.getStart().getLine() + ": " + statementContext.getText()
    );
  }

  /**
   * Formats a {@code return} statement, auto-paren-wrapping a binary expression on break to match prettier-java. When an operand is itself
   * a breaking construct (a method chain) the binop force-breaks and the wrap group's fits-check would otherwise stop at that inner break
   * and stay flat (no parens), so the wrap is forced when the expression will break.
   */
  private Document visitReturnStatement(final JavaParser.StatementContext statementContext) {
    if (statementContext.expression().isEmpty()) {
      return concat(Tokens.sourced(statementContext.RETURN()), Tokens.sourced(statementContext.SEMI()));
    }
    final JavaParser.ExpressionContext expr = statementContext.expression(0);
    if (isNonAssignmentBinaryExpression(expr)) {
      final Document exprDoc = dispatch.visit(expr);
      return concat(
        Tokens.sourced(statementContext.RETURN()),
        new Group(
          concat(ifBreak(text(" ("), text(" ")), breakIndent(concat(softLine(), exprDoc)), softLine(), ifBreak(text(")"), empty())),
          willBreak(exprDoc)
        ),
        Tokens.sourced(statementContext.SEMI())
      );
    }
    return concat(Tokens.sourced(statementContext.RETURN()), text(" "), dispatch.visit(expr), Tokens.sourced(statementContext.SEMI()));
  }

  private static boolean isNonAssignmentBinaryExpression(final JavaParser.ExpressionContext expr) {
    if (!(expr instanceof final JavaParser.BinaryOperatorExpressionContext bin)) {
      return false;
    }
    return !Operators.isAssignmentOperator(bin.getChild(1).getText());
  }

  private Document visitThrowStatement(final JavaParser.StatementContext statementContext) {
    return concat(
      Tokens.sourced(statementContext.THROW()),
      text(" "),
      dispatch.visit(statementContext.expression(0)),
      Tokens.sourced(statementContext.SEMI())
    );
  }

  private Document visitBreakStatement(final JavaParser.StatementContext statementContext) {
    if (statementContext.identifier() != null) {
      return concat(
        Tokens.sourced(statementContext.BREAK()),
        text(" "),
        dispatch.visit(statementContext.identifier()),
        Tokens.sourced(statementContext.SEMI())
      );
    }
    return concat(Tokens.sourced(statementContext.BREAK()), Tokens.sourced(statementContext.SEMI()));
  }

  private Document visitContinueStatement(final JavaParser.StatementContext statementContext) {
    if (statementContext.identifier() != null) {
      return concat(
        Tokens.sourced(statementContext.CONTINUE()),
        text(" "),
        dispatch.visit(statementContext.identifier()),
        Tokens.sourced(statementContext.SEMI())
      );
    }
    return concat(Tokens.sourced(statementContext.CONTINUE()), Tokens.sourced(statementContext.SEMI()));
  }

  private Document visitYieldStatement(final JavaParser.StatementContext statementContext) {
    return concat(
      Tokens.sourced(statementContext.YIELD()),
      text(" "),
      dispatch.visit(statementContext.expression(0)),
      Tokens.sourced(statementContext.SEMI())
    );
  }

  Document visitLocalTypeDeclaration(final JavaParser.LocalTypeDeclarationContext localTypeContext) {
    final List<Document> parts = new ArrayList<>(
      Modifiers.format(dispatch, localTypeContext.classOrInterfaceModifier(), Modifiers.IS_CLASS_ANNOTATION)
    );
    if (localTypeContext.classDeclaration() != null) {
      parts.add(dispatch.visit(localTypeContext.classDeclaration()));
    } else if (localTypeContext.interfaceDeclaration() != null) {
      parts.add(dispatch.visit(localTypeContext.interfaceDeclaration()));
    } else if (localTypeContext.recordDeclaration() != null) {
      parts.add(dispatch.visit(localTypeContext.recordDeclaration()));
    } else if (localTypeContext.enumDeclaration() != null) {
      parts.add(dispatch.visit(localTypeContext.enumDeclaration()));
    }
    return concat(parts);
  }

  Document visitConstantDeclarator(final JavaParser.ConstantDeclaratorContext declaratorContext) {
    final List<TerminalNode> lbracks = declaratorContext.LBRACK();
    final List<TerminalNode> rbracks = declaratorContext.RBRACK();
    final List<Document> parts = new ArrayList<>();
    parts.add(dispatch.visit(declaratorContext.identifier()));
    for (int i = 0; i < lbracks.size(); i++) {
      parts.add(Tokens.sourced(lbracks.get(i)));
      parts.add(Tokens.sourced(rbracks.get(i)));
    }
    parts.add(text(" "));
    parts.add(Tokens.sourced(declaratorContext.ASSIGN()));
    parts.add(text(" "));
    parts.add(dispatch.visit(declaratorContext.variableInitializer()));
    return concat(parts);
  }

  private Document visitAssertStatement(final JavaParser.StatementContext statementContext) {
    final List<Document> parts = new ArrayList<>();
    parts.add(Tokens.sourced(statementContext.ASSERT()));
    parts.add(text(" "));
    parts.add(dispatch.visit(statementContext.expression(0)));
    if (statementContext.expression().size() > 1) {
      parts.add(text(" "));
      parts.add(Tokens.sourced(statementContext.COLON()));
      parts.add(text(" "));
      parts.add(dispatch.visit(statementContext.expression(1)));
    }
    parts.add(Tokens.sourced(statementContext.SEMI()));
    return concat(parts);
  }
}
