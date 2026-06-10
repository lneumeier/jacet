package de.irotation.jacet.formatting;

import static de.irotation.jacet.document.Document.concat;
import static de.irotation.jacet.document.Document.group;
import static de.irotation.jacet.document.Document.indent;
import static de.irotation.jacet.document.Document.line;
import static de.irotation.jacet.document.Document.softLine;
import static de.irotation.jacet.document.Document.text;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.jspecify.annotations.Nullable;

import de.irotation.jacet.document.Document;
import de.irotation.jacet.parser.JavaParser;

/**
 * Formats modifiers, identifiers, literals, and pattern matching nodes.
 */
final class LeafFormatter implements HandlerProvider {

  private final FormattingDispatch dispatch;

  LeafFormatter(final FormattingDispatch dispatch) {
    this.dispatch = dispatch;
  }

  @Override
  public void registerHandlers(final HandlerRegistry registry) {
    registry.register(JavaParser.ModifierContext.class, this::visitModifier);
    registry.register(JavaParser.ClassOrInterfaceModifierContext.class, this::visitClassOrInterfaceModifier);
    registry.register(JavaParser.VariableModifierContext.class, this::visitVariableModifier);
    registry.register(JavaParser.InterfaceMethodModifierContext.class, this::visitInterfaceMethodModifier);
    registry.register(JavaParser.IdentifierContext.class, this::visitIdentifier);
    registry.register(JavaParser.TypeIdentifierContext.class, this::visitTypeIdentifier);
    registry.register(JavaParser.QualifiedNameContext.class, this::visitQualifiedName);
    registry.register(JavaParser.QualifiedNameListContext.class, this::visitQualifiedNameList);
    registry.register(JavaParser.LiteralContext.class, this::visitLiteral);
    registry.register(JavaParser.PatternContext.class, this::visitPattern);
    registry.register(JavaParser.GuardContext.class, this::visitGuard);
    registry.register(JavaParser.IntegerLiteralContext.class, this::visitIntegerLiteral);
    registry.register(JavaParser.FloatLiteralContext.class, this::visitFloatLiteral);
    registry.register(JavaParser.CasePatternContext.class, this::visitCasePattern);
    registry.register(JavaParser.ComponentPatternContext.class, this::visitComponentPattern);
    registry.register(JavaParser.ComponentPatternListContext.class, this::visitComponentPatternList);
  }

  Document visitModifier(final JavaParser.ModifierContext modifierContext) {
    return this.delegateOrSourced(modifierContext, modifierContext.classOrInterfaceModifier());
  }

  Document visitClassOrInterfaceModifier(final JavaParser.ClassOrInterfaceModifierContext modifierContext) {
    return this.delegateOrSourced(modifierContext, modifierContext.annotation());
  }

  Document visitVariableModifier(final JavaParser.VariableModifierContext modifierContext) {
    return this.delegateOrSourced(modifierContext, modifierContext.annotation());
  }

  Document visitInterfaceMethodModifier(final JavaParser.InterfaceMethodModifierContext modifierContext) {
    return this.delegateOrSourced(modifierContext, modifierContext.annotation());
  }

  /**
   * Formats a modifier that may wrap a richer child: when {@code delegate} is present (a nested {@code classOrInterfaceModifier} or an
   * {@code annotation}) it is dispatched, otherwise the modifier keyword is sourced verbatim.
   */
  private Document delegateOrSourced(final ParserRuleContext modifierContext, final @Nullable ParseTree delegate) {
    return delegate != null ? dispatch.visit(delegate) : Tokens.sourced(modifierContext);
  }

  Document visitIdentifier(final JavaParser.IdentifierContext identifierContext) {
    return Tokens.sourced(identifierContext);
  }

  Document visitTypeIdentifier(final JavaParser.TypeIdentifierContext identifierContext) {
    return Tokens.sourced(identifierContext);
  }

  Document visitQualifiedName(final JavaParser.QualifiedNameContext nameContext) {
    return Tokens.sourced(nameContext);
  }

  Document visitQualifiedNameList(final JavaParser.QualifiedNameListContext nameContext) {
    final List<Document> names = nameContext.qualifiedName().stream().map(dispatch::visit).toList();
    return Tokens.joinSourced(names, nameContext.COMMA(), line());
  }

  Document visitLiteral(final JavaParser.LiteralContext literalContext) {
    return Tokens.sourced(literalContext);
  }

  Document visitPattern(final JavaParser.PatternContext patternContext) {
    final List<Document> parts = new ArrayList<>();
    if (patternContext.variableDeclarators() != null) {
      parts.addAll(Modifiers.formatVariable(dispatch, patternContext.variableModifier()));
      final JavaParser.TypeTypeContext typeType = patternContext.typeType();
      parts.add(typeType != null ? dispatch.visit(typeType) : Tokens.sourced(patternContext.VAR()));
      for (final JavaParser.AnnotationContext annotation : patternContext.annotation()) {
        parts.add(text(" "));
        parts.add(dispatch.visit(annotation));
      }
      parts.add(text(" "));
      parts.add(dispatch.visit(patternContext.variableDeclarators()));
    } else {
      parts.add(dispatch.visit(patternContext.typeType()));
      if (patternContext.componentPatternList() == null) {
        parts.add(Tokens.sourced(patternContext.LPAREN()));
        parts.add(Tokens.sourced(patternContext.RPAREN()));
      } else {
        parts.add(
          group(
            concat(
              Tokens.sourced(patternContext.LPAREN()),
              indent(concat(softLine(), dispatch.visit(patternContext.componentPatternList()))),
              softLine(),
              Tokens.sourced(patternContext.RPAREN())
            )
          )
        );
      }
    }
    return concat(parts);
  }

  Document visitGuard(final JavaParser.GuardContext guardContext) {
    return concat(Tokens.sourced(guardContext.WHEN()), text(" "), dispatch.visit(guardContext.expression()));
  }

  Document visitIntegerLiteral(final JavaParser.IntegerLiteralContext literalContext) {
    return Tokens.sourced(literalContext);
  }

  Document visitFloatLiteral(final JavaParser.FloatLiteralContext literalContext) {
    return Tokens.sourced(literalContext);
  }

  Document visitCasePattern(final JavaParser.CasePatternContext casePatternContext) {
    return dispatch.visit(casePatternContext.pattern());
  }

  Document visitComponentPattern(final JavaParser.ComponentPatternContext componentContext) {
    return dispatch.visit(componentContext.pattern());
  }

  Document visitComponentPatternList(final JavaParser.ComponentPatternListContext listContext) {
    final List<Document> patterns = listContext.componentPattern().stream().map(dispatch::visit).toList();
    return Tokens.joinSourced(patterns, listContext.COMMA(), line());
  }
}
