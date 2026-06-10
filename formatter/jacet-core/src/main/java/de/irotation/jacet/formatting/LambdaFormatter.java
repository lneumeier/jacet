package de.irotation.jacet.formatting;

import static de.irotation.jacet.document.Document.concat;
import static de.irotation.jacet.document.Document.group;
import static de.irotation.jacet.document.Document.indent;
import static de.irotation.jacet.document.Document.line;
import static de.irotation.jacet.document.Document.softLine;
import static de.irotation.jacet.document.Document.text;
import static de.irotation.jacet.document.Document.willBreak;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.tree.TerminalNode;

import de.irotation.jacet.document.Document;
import de.irotation.jacet.document.Group;
import de.irotation.jacet.parser.JavaParser;

/**
 * Formats lambda expressions: parameters, body (block or expression), and the {@code var}-typed (LVTI) parameter list.
 */
final class LambdaFormatter implements HandlerProvider {

  private final FormattingDispatch dispatch;

  LambdaFormatter(final FormattingDispatch dispatch) {
    this.dispatch = dispatch;
  }

  @Override
  public void registerHandlers(final HandlerRegistry registry) {
    registry.register(JavaParser.ExpressionLambdaContext.class, this::visitExpressionLambda);
    registry.register(JavaParser.LambdaExpressionContext.class, this::visitLambdaExpression);
    registry.register(JavaParser.LambdaParametersContext.class, this::visitLambdaParameters);
    registry.register(JavaParser.LambdaBodyContext.class, this::visitLambdaBody);
    registry.register(JavaParser.LambdaLVTIListContext.class, this::visitLambdaLVTIList);
    registry.register(JavaParser.LambdaLVTIParameterContext.class, this::visitLambdaLVTIParameter);
  }

  Document visitExpressionLambda(final JavaParser.ExpressionLambdaContext expressionContext) {
    return dispatch.visit(expressionContext.lambdaExpression());
  }

  /**
   * Formats a lambda. A block body and a parenthesized-expression body stay inline after the arrow ({@code param -> { ... }},
   * {@code param -> (expr)}). Any other expression body hangs under the arrow ({@code param ->\n  body}) when it breaks: prettier's
   * {@code mayBreakAfterShortPrefix} is false for method calls, ternaries, etc. jacet's printer does not propagate the body's forced break
   * across its group boundary, so the lambda group's fits-check would otherwise stay flat and hug {@code param -> body.that(\n  breaks)};
   * the {@code bodyBreaks} flag forces the hang when the body — or an arrow trailing comment — will break.
   */
  Document visitLambdaExpression(final JavaParser.LambdaExpressionContext lambdaContext) {
    final Document parameters = dispatch.visit(lambdaContext.lambdaParameters());
    final Document arrow = Tokens.sourced(lambdaContext.ARROW());
    final Document arrowTrailing = dispatch.trailingComment(lambdaContext.ARROW().getSymbol().getTokenIndex());
    final Document body = dispatch.visit(lambdaContext.lambdaBody());
    if (lambdaContext.lambdaBody().block() != null || isParenExpressionBody(lambdaContext.lambdaBody())) {
      return concat(parameters, text(" "), arrow, text(" "), arrowTrailing, body);
    }
    final boolean bodyBreaks = willBreak(body) || willBreak(arrowTrailing);
    return new Group(concat(parameters, text(" "), arrow, arrowTrailing, indent(concat(line(), body))), bodyBreaks);
  }

  private static boolean isParenExpressionBody(final JavaParser.LambdaBodyContext body) {
    if (body.expression() == null) {
      return false;
    }
    if (!(body.expression() instanceof final JavaParser.PrimaryExpressionContext primExpr)) {
      return false;
    }
    return primExpr.primary().expression() != null;
  }

  Document visitLambdaParameters(final JavaParser.LambdaParametersContext lambdaContext) {
    if (lambdaContext.identifier() != null && lambdaContext.identifier().size() == 1) {
      if (lambdaContext.LPAREN() != null) {
        return concat(
          Tokens.removed(lambdaContext.LPAREN()),
          dispatch.visit(lambdaContext.identifier(0)),
          Tokens.removed(lambdaContext.RPAREN())
        );
      }
      return dispatch.visit(lambdaContext.identifier(0));
    }
    if (lambdaContext.formalParameterList() != null) {
      return this.parenthesizedGroup(lambdaContext.LPAREN(), dispatch.visit(lambdaContext.formalParameterList()), lambdaContext.RPAREN());
    }
    if (lambdaContext.lambdaLVTIList() != null) {
      return this.parenthesizedGroup(lambdaContext.LPAREN(), dispatch.visit(lambdaContext.lambdaLVTIList()), lambdaContext.RPAREN());
    }
    if (lambdaContext.identifier() != null && lambdaContext.identifier().size() > 1) {
      final List<Document> ids = lambdaContext.identifier().stream().map(dispatch::visit).toList();
      return this.parenthesizedGroup(
        lambdaContext.LPAREN(),
        Tokens.joinSourced(ids, lambdaContext.COMMA(), line()),
        lambdaContext.RPAREN()
      );
    }
    return concat(Tokens.sourced(lambdaContext.LPAREN()), Tokens.sourced(lambdaContext.RPAREN()));
  }

  private Document parenthesizedGroup(final TerminalNode lparen, final Document inner, final TerminalNode rparen) {
    return group(concat(Tokens.sourced(lparen), indent(concat(softLine(), inner)), softLine(), Tokens.sourced(rparen)));
  }

  Document visitLambdaBody(final JavaParser.LambdaBodyContext bodyContext) {
    final List<Document> parts = new ArrayList<>(dispatch.leading(bodyContext));
    if (bodyContext.block() != null) {
      parts.add(dispatch.visit(bodyContext.block()));
    } else {
      parts.add(dispatch.visit(bodyContext.expression()));
    }
    return concat(parts);
  }

  Document visitLambdaLVTIList(final JavaParser.LambdaLVTIListContext listContext) {
    final List<Document> parameters = listContext.lambdaLVTIParameter().stream().map(dispatch::visit).toList();
    return Tokens.joinSourced(parameters, listContext.COMMA(), line());
  }

  Document visitLambdaLVTIParameter(final JavaParser.LambdaLVTIParameterContext paramContext) {
    final List<Document> parts = new ArrayList<>(Modifiers.formatVariable(dispatch, paramContext.variableModifier()));
    parts.add(Tokens.sourced(paramContext.VAR()));
    parts.add(text(" "));
    parts.add(dispatch.visit(paramContext.identifier()));
    return concat(parts);
  }
}
