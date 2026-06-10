package de.irotation.jacet.formatting;

import static de.irotation.jacet.document.Document.concat;
import static de.irotation.jacet.document.Document.group;
import static de.irotation.jacet.document.Document.indent;
import static de.irotation.jacet.document.Document.line;
import static de.irotation.jacet.document.Document.softLine;
import static de.irotation.jacet.document.Document.text;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.tree.TerminalNode;

import de.irotation.jacet.document.Document;
import de.irotation.jacet.parser.JavaParser;

/**
 * Formats try-catch-finally, catch clauses, and resource specifications.
 */
final class ExceptionFormatter implements HandlerProvider {

  private final FormattingDispatch dispatch;

  ExceptionFormatter(final FormattingDispatch dispatch) {
    this.dispatch = dispatch;
  }

  @Override
  public void registerHandlers(final HandlerRegistry registry) {
    registry.register(JavaParser.CatchClauseContext.class, this::visitCatchClause);
    registry.register(JavaParser.CatchTypeContext.class, this::visitCatchType);
    registry.register(JavaParser.FinallyBlockContext.class, this::visitFinallyBlock);
    registry.register(JavaParser.ResourceSpecificationContext.class, this::visitResourceSpecification);
    registry.register(JavaParser.ResourcesContext.class, this::visitResources);
    registry.register(JavaParser.ResourceContext.class, this::visitResource);
  }

  Document visitTryStatement(final JavaParser.StatementContext statementContext) {
    final List<Document> parts = new ArrayList<>();
    parts.add(Tokens.sourced(statementContext.TRY()));

    if (statementContext.resourceSpecification() != null) {
      parts.add(text(" "));
      parts.add(dispatch.visit(statementContext.resourceSpecification()));
    }

    parts.add(text(" "));
    parts.add(dispatch.visit(statementContext.block()));

    for (final JavaParser.CatchClauseContext catchClause : statementContext.catchClause()) {
      parts.add(text(" "));
      parts.add(dispatch.visit(catchClause));
    }

    if (statementContext.finallyBlock() != null) {
      parts.add(text(" "));
      parts.add(dispatch.visit(statementContext.finallyBlock()));
    }

    return concat(parts);
  }

  /**
   * Formats a catch clause. A multi-catch wraps operator-leading ({@code | Type}) when the parenthesized content can't stay on one line —
   * flat {@code final A | B | C ex}, broken {@code final A} / {@code | B} / {@code | C ex} — matching the IntelliJ/Spotless multi-catch
   * layout.
   */
  Document visitCatchClause(final JavaParser.CatchClauseContext catchContext) {
    final List<Document> inner = new ArrayList<>(Modifiers.formatVariable(dispatch, catchContext.variableModifier()));
    final List<TerminalNode> bitors = catchContext.catchType().BITOR();
    final List<Document> types = catchContext.catchType().qualifiedName().stream().map(dispatch::visit).toList();
    inner.add(types.getFirst());
    for (int i = 1; i < types.size(); i++) {
      inner.add(line());
      inner.add(Tokens.sourced(bitors.get(i - 1)));
      inner.add(text(" "));
      inner.add(types.get(i));
    }
    inner.add(text(" "));
    inner.add(dispatch.visit(catchContext.identifier()));
    return concat(
      group(
        concat(
          Tokens.sourced(catchContext.CATCH()),
          text(" "),
          Tokens.sourced(catchContext.LPAREN()),
          indent(concat(softLine(), concat(inner))),
          softLine(),
          Tokens.sourced(catchContext.RPAREN())
        )
      ),
      text(" "),
      dispatch.visit(catchContext.block())
    );
  }

  /**
   * Standalone {@code catchType} formatting for legacy / non-{@code catchClause} paths; {@link #visitCatchClause} inlines its own
   * wrap-aware version.
   */
  Document visitCatchType(final JavaParser.CatchTypeContext catchContext) {
    final List<Document> types = catchContext.qualifiedName().stream().map(dispatch::visit).toList();
    return Tokens.joinSourced(types, catchContext.BITOR(), text(" "), text(" "));
  }

  Document visitFinallyBlock(final JavaParser.FinallyBlockContext finallyContext) {
    return concat(Tokens.sourced(finallyContext.FINALLY()), text(" "), dispatch.visit(finallyContext.block()));
  }

  Document visitResourceSpecification(final JavaParser.ResourceSpecificationContext resourceContext) {
    final Document trailingSemi = resourceContext.SEMI() != null ? Tokens.removed(resourceContext.SEMI()) : Document.empty();
    return group(
      concat(
        Tokens.sourced(resourceContext.LPAREN()),
        indent(concat(softLine(), dispatch.visit(resourceContext.resources()), trailingSemi)),
        softLine(),
        Tokens.sourced(resourceContext.RPAREN())
      )
    );
  }

  Document visitResources(final JavaParser.ResourcesContext resourceContext) {
    final List<Document> resources = resourceContext.resource().stream().map(dispatch::visit).toList();
    return Tokens.joinSourced(resources, resourceContext.SEMI(), line());
  }

  Document visitResource(final JavaParser.ResourceContext resourceContext) {
    if (resourceContext.qualifiedName() != null && resourceContext.ASSIGN() == null) {
      return dispatch.visit(resourceContext.qualifiedName());
    }
    final List<Document> parts = new ArrayList<>(Modifiers.formatVariable(dispatch, resourceContext.variableModifier()));
    if (resourceContext.classOrInterfaceType() != null) {
      parts.add(dispatch.visit(resourceContext.classOrInterfaceType()));
      parts.add(text(" "));
      parts.add(dispatch.visit(resourceContext.variableDeclaratorId()));
    } else if (resourceContext.VAR() != null) {
      parts.add(Tokens.sourced(resourceContext.VAR()));
      parts.add(text(" "));
      parts.add(dispatch.visit(resourceContext.identifier()));
    }
    parts.add(text(" "));
    parts.add(Tokens.sourced(resourceContext.ASSIGN()));
    parts.add(text(" "));
    parts.add(dispatch.visit(resourceContext.expression()));
    return concat(parts);
  }
}
