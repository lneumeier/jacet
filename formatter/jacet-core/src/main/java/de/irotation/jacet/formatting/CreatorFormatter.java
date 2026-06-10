package de.irotation.jacet.formatting;

import static de.irotation.jacet.document.Document.concat;
import static de.irotation.jacet.document.Document.text;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import de.irotation.jacet.document.Document;
import de.irotation.jacet.parser.JavaParser;

/**
 * Formats object and array creators ({@code new T(...)}, {@code new int[]{...}}), the created type name, inner creators, and the
 * diamond / non-wildcard type-argument forms used inside creators.
 */
final class CreatorFormatter implements HandlerProvider {

  private final FormattingDispatch dispatch;

  CreatorFormatter(final FormattingDispatch dispatch) {
    this.dispatch = dispatch;
  }

  @Override
  public void registerHandlers(final HandlerRegistry registry) {
    registry.register(JavaParser.ObjectCreationExpressionContext.class, this::visitObjectCreationExpression);
    registry.register(JavaParser.CreatorContext.class, this::visitCreator);
    registry.register(JavaParser.ClassCreatorRestContext.class, this::visitClassCreatorRest);
    registry.register(JavaParser.CreatedNameContext.class, this::visitCreatedName);
    registry.register(JavaParser.InnerCreatorContext.class, this::visitInnerCreator);
    registry.register(JavaParser.ArrayCreatorRestContext.class, this::visitArrayCreatorRest);
    registry.register(JavaParser.NonWildcardTypeArgumentsContext.class, this::visitNonWildcardTypeArguments);
    registry.register(JavaParser.TypeArgumentsOrDiamondContext.class, this::visitTypeArgumentsOrDiamond);
    registry.register(JavaParser.NonWildcardTypeArgumentsOrDiamondContext.class, this::visitNonWildcardTypeArgumentsOrDiamond);
  }

  Document visitObjectCreationExpression(final JavaParser.ObjectCreationExpressionContext expressionContext) {
    return concat(Tokens.sourced(expressionContext.NEW()), text(" "), dispatch.visit(expressionContext.creator()));
  }

  Document visitCreator(final JavaParser.CreatorContext creatorContext) {
    final List<Document> parts = new ArrayList<>();
    if (creatorContext.nonWildcardTypeArguments() != null) {
      parts.add(dispatch.visit(creatorContext.nonWildcardTypeArguments()));
    }
    parts.add(dispatch.visit(creatorContext.createdName()));
    if (creatorContext.classCreatorRest() != null) {
      parts.add(dispatch.visit(creatorContext.classCreatorRest()));
    } else if (creatorContext.arrayCreatorRest() != null) {
      parts.add(dispatch.visit(creatorContext.arrayCreatorRest()));
    }
    return concat(parts);
  }

  Document visitClassCreatorRest(final JavaParser.ClassCreatorRestContext restContext) {
    final List<Document> parts = new ArrayList<>();
    parts.add(dispatch.visit(restContext.arguments()));
    if (restContext.classBody() != null) {
      parts.add(text(" "));
      parts.add(dispatch.visit(restContext.classBody()));
    }
    return concat(parts);
  }

  Document visitCreatedName(final JavaParser.CreatedNameContext nameContext) {
    if (nameContext.primitiveType() != null) {
      return dispatch.visit(nameContext.primitiveType());
    }
    final List<Document> parts = new ArrayList<>();
    for (int i = 0; i < nameContext.getChildCount(); i++) {
      final ParseTree child = nameContext.getChild(i);
      if (child instanceof final TerminalNode terminal) {
        parts.add(Tokens.sourced(terminal));
      } else {
        parts.add(dispatch.visit(child));
      }
    }
    return concat(parts);
  }

  Document visitInnerCreator(final JavaParser.InnerCreatorContext creatorContext) {
    final List<Document> parts = new ArrayList<>();
    parts.add(dispatch.visit(creatorContext.identifier()));
    if (creatorContext.nonWildcardTypeArgumentsOrDiamond() != null) {
      parts.add(dispatch.visit(creatorContext.nonWildcardTypeArgumentsOrDiamond()));
    }
    parts.add(dispatch.visit(creatorContext.classCreatorRest()));
    return concat(parts);
  }

  Document visitArrayCreatorRest(final JavaParser.ArrayCreatorRestContext restContext) {
    final List<TerminalNode> lbracks = restContext.LBRACK();
    final List<TerminalNode> rbracks = restContext.RBRACK();
    final List<Document> parts = new ArrayList<>();
    if (restContext.arrayInitializer() != null) {
      for (int i = 0; i < lbracks.size(); i++) {
        parts.add(Tokens.sourced(lbracks.get(i)));
        parts.add(Tokens.sourced(rbracks.get(i)));
      }
      parts.add(text(" "));
      parts.add(dispatch.visit(restContext.arrayInitializer()));
    } else {
      final List<JavaParser.ExpressionContext> expressions = restContext.expression();
      for (int i = 0; i < expressions.size(); i++) {
        parts.add(Tokens.sourced(lbracks.get(i)));
        parts.add(dispatch.visit(expressions.get(i)));
        parts.add(Tokens.sourced(rbracks.get(i)));
      }
      for (int i = expressions.size(); i < lbracks.size(); i++) {
        parts.add(Tokens.sourced(lbracks.get(i)));
        parts.add(Tokens.sourced(rbracks.get(i)));
      }
    }
    return concat(parts);
  }

  Document visitNonWildcardTypeArguments(final JavaParser.NonWildcardTypeArgumentsContext typeContext) {
    return concat(Tokens.sourced(typeContext.LT()), dispatch.visit(typeContext.typeList()), Tokens.sourced(typeContext.GT()));
  }

  Document visitTypeArgumentsOrDiamond(final JavaParser.TypeArgumentsOrDiamondContext typeContext) {
    if (typeContext.typeArguments() != null) {
      return dispatch.visit(typeContext.typeArguments());
    }
    return concat(Tokens.sourced(typeContext.LT()), Tokens.sourced(typeContext.GT()));
  }

  Document visitNonWildcardTypeArgumentsOrDiamond(final JavaParser.NonWildcardTypeArgumentsOrDiamondContext typeContext) {
    if (typeContext.nonWildcardTypeArguments() != null) {
      return dispatch.visit(typeContext.nonWildcardTypeArguments());
    }
    return concat(Tokens.sourced(typeContext.LT()), Tokens.sourced(typeContext.GT()));
  }
}
