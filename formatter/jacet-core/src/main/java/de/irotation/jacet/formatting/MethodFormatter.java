package de.irotation.jacet.formatting;

import static de.irotation.jacet.document.Document.concat;
import static de.irotation.jacet.document.Document.group;
import static de.irotation.jacet.document.Document.hardLine;
import static de.irotation.jacet.document.Document.ifBreak;
import static de.irotation.jacet.document.Document.indent;
import static de.irotation.jacet.document.Document.line;
import static de.irotation.jacet.document.Document.softLine;
import static de.irotation.jacet.document.Document.text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.antlr.v4.runtime.tree.TerminalNode;

import de.irotation.jacet.document.Document;
import de.irotation.jacet.parser.JavaParser;

/**
 * Formats method declarations, constructors, and parameter lists.
 */
final class MethodFormatter implements HandlerProvider {

  private final FormattingDispatch dispatch;

  MethodFormatter(final FormattingDispatch dispatch) {
    this.dispatch = dispatch;
  }

  @Override
  public void registerHandlers(final HandlerRegistry registry) {
    registry.register(JavaParser.CompactConstructorDeclarationContext.class, this::visitCompactConstructorDeclaration);
    registry.register(JavaParser.ReceiverParameterContext.class, this::visitReceiverParameter);
    registry.register(JavaParser.GenericInterfaceMethodDeclarationContext.class, this::visitGenericInterfaceMethodDeclaration);
    registry.register(JavaParser.MethodDeclarationContext.class, this::visitMethodDeclaration);
    registry.register(JavaParser.MethodBodyContext.class, this::visitMethodBody);
    registry.register(JavaParser.ConstructorDeclarationContext.class, this::visitConstructorDeclaration);
    registry.register(JavaParser.GenericMethodDeclarationContext.class, this::visitGenericMethodDeclaration);
    registry.register(JavaParser.GenericConstructorDeclarationContext.class, this::visitGenericConstructorDeclaration);
    registry.register(JavaParser.InterfaceMethodDeclarationContext.class, this::visitInterfaceMethodDeclaration);
    registry.register(JavaParser.InterfaceCommonBodyDeclarationContext.class, this::visitInterfaceCommonBodyDeclaration);
    registry.register(JavaParser.FormalParametersContext.class, this::visitFormalParameters);
    registry.register(JavaParser.FormalParameterListContext.class, this::visitFormalParameterList);
    registry.register(JavaParser.FormalParameterContext.class, this::visitFormalParameter);
  }

  Document visitMethodDeclaration(final JavaParser.MethodDeclarationContext methodContext) {
    final List<Document> parts = new ArrayList<>();
    parts.add(dispatch.visit(methodContext.typeTypeOrVoid()));
    parts.add(text(" "));
    parts.add(dispatch.visit(methodContext.identifier()));
    parts.add(dispatch.visit(methodContext.formalParameters()));

    arrayBrackets(parts, methodContext.LBRACK(), methodContext.RBRACK());

    if (methodContext.THROWS() != null) {
      parts.add(throwsClause(methodContext.THROWS(), dispatch.visit(methodContext.qualifiedNameList())));
    }

    parts.add(dispatch.visit(methodContext.methodBody()));
    return concat(parts);
  }

  private static void arrayBrackets(final Collection<Document> parts, final List<TerminalNode> lbracks, final List<TerminalNode> rbracks) {
    for (int i = 0; i < lbracks.size(); i++) {
      parts.add(Tokens.sourced(lbracks.get(i)));
      parts.add(Tokens.sourced(rbracks.get(i)));
    }
  }

  private static Document throwsClause(final TerminalNode throwsKeyword, final Document names) {
    return group(indent(concat(ifBreak(hardLine(), text(" ")), Tokens.sourced(throwsKeyword), text(" "), group(names))));
  }

  Document visitMethodBody(final JavaParser.MethodBodyContext bodyContext) {
    if (bodyContext.block() == null) {
      return Tokens.sourced(bodyContext.SEMI());
    }
    return concat(text(" "), dispatch.visit(bodyContext.block()));
  }

  Document visitConstructorDeclaration(final JavaParser.ConstructorDeclarationContext constructorContext) {
    final List<Document> parts = new ArrayList<>();
    parts.add(dispatch.visit(constructorContext.identifier()));
    parts.add(dispatch.visit(constructorContext.formalParameters()));

    if (constructorContext.THROWS() != null) {
      parts.add(throwsClause(constructorContext.THROWS(), dispatch.visit(constructorContext.qualifiedNameList())));
    }

    parts.add(text(" "));
    parts.add(dispatch.visit(constructorContext.block()));
    return concat(parts);
  }

  Document visitGenericMethodDeclaration(final JavaParser.GenericMethodDeclarationContext genericContext) {
    return concat(dispatch.visit(genericContext.typeParameters()), text(" "), dispatch.visit(genericContext.methodDeclaration()));
  }

  Document visitGenericConstructorDeclaration(final JavaParser.GenericConstructorDeclarationContext genericContext) {
    return concat(dispatch.visit(genericContext.typeParameters()), text(" "), dispatch.visit(genericContext.constructorDeclaration()));
  }

  Document visitInterfaceMethodDeclaration(final JavaParser.InterfaceMethodDeclarationContext interfaceContext) {
    final List<Document> parts = new ArrayList<>(
      Modifiers.format(dispatch, interfaceContext.interfaceMethodModifier(), Modifiers.IS_INTERFACE_METHOD_ANNOTATION)
    );
    parts.add(dispatch.visit(interfaceContext.interfaceCommonBodyDeclaration()));
    return concat(parts);
  }

  Document visitInterfaceCommonBodyDeclaration(final JavaParser.InterfaceCommonBodyDeclarationContext bodyContext) {
    final List<Document> parts = new ArrayList<>();
    for (final JavaParser.AnnotationContext annotation : bodyContext.annotation()) {
      parts.add(dispatch.visit(annotation));
      parts.add(text(" "));
    }
    parts.add(dispatch.visit(bodyContext.typeTypeOrVoid()));
    parts.add(text(" "));
    parts.add(dispatch.visit(bodyContext.identifier()));
    parts.add(dispatch.visit(bodyContext.formalParameters()));

    arrayBrackets(parts, bodyContext.LBRACK(), bodyContext.RBRACK());

    if (bodyContext.THROWS() != null) {
      parts.add(throwsClause(bodyContext.THROWS(), dispatch.visit(bodyContext.qualifiedNameList())));
    }

    parts.add(dispatch.visit(bodyContext.methodBody()));
    return concat(parts);
  }

  Document visitFormalParameters(final JavaParser.FormalParametersContext parametersContext) {
    if (
      parametersContext.formalParameter() == null &&
      parametersContext.receiverParameter() == null &&
      parametersContext.formalParameterList().isEmpty()
    ) {
      return concat(Tokens.sourced(parametersContext.LPAREN()), Tokens.sourced(parametersContext.RPAREN()));
    }

    final List<TerminalNode> topCommas = parametersContext.COMMA();
    final List<Document> allParams = new ArrayList<>();
    final List<TerminalNode> separators = new ArrayList<>();
    if (parametersContext.receiverParameter() != null) {
      allParams.add(dispatch.visit(parametersContext.receiverParameter()));
    }
    if (parametersContext.formalParameter() != null) {
      allParams.add(dispatch.visit(parametersContext.formalParameter()));
    }
    int topCommaIdx = 0;
    for (final JavaParser.FormalParameterListContext paramList : parametersContext.formalParameterList()) {
      if (!allParams.isEmpty()) {
        separators.add(topCommas.get(topCommaIdx));
        topCommaIdx++;
      }
      final List<JavaParser.FormalParameterContext> params = paramList.formalParameter();
      final List<TerminalNode> listCommas = paramList.COMMA();
      for (int i = 0; i < params.size(); i++) {
        if (i > 0) {
          separators.add(listCommas.get(i - 1));
        }
        final List<Document> paramParts = new ArrayList<>();
        if (i == 0) {
          paramParts.addAll(dispatch.leading(paramList));
        }
        paramParts.add(dispatch.visit(params.get(i)));
        if (i == params.size() - 1) {
          paramParts.add(dispatch.trailing(paramList));
        }
        allParams.add(concat(paramParts));
      }
    }

    return group(
      concat(
        Tokens.sourced(parametersContext.LPAREN()),
        indent(concat(softLine(), Tokens.joinSourced(allParams, separators, line()))),
        softLine(),
        Tokens.sourced(parametersContext.RPAREN())
      )
    );
  }

  Document visitFormalParameterList(final JavaParser.FormalParameterListContext listContext) {
    final List<Document> parameters = listContext.formalParameter().stream().map(dispatch::visit).toList();
    return Tokens.joinSourced(parameters, listContext.COMMA(), line());
  }

  Document visitFormalParameter(final JavaParser.FormalParameterContext parameterContext) {
    final List<Document> parts = new ArrayList<>(dispatch.leading(parameterContext));
    parts.addAll(Modifiers.formatVariable(dispatch, parameterContext.variableModifier()));
    parts.add(dispatch.visit(parameterContext.typeType()));
    if (parameterContext.ELLIPSIS() != null) {
      for (final JavaParser.AnnotationContext annotation : parameterContext.annotation()) {
        parts.add(text(" "));
        parts.add(dispatch.visit(annotation));
      }
      parts.add(Tokens.sourced(parameterContext.ELLIPSIS()));
    }
    parts.add(text(" "));
    parts.add(dispatch.visit(parameterContext.variableDeclaratorId()));
    parts.add(dispatch.trailing(parameterContext));
    return concat(parts);
  }

  Document visitReceiverParameter(final JavaParser.ReceiverParameterContext receiverContext) {
    final List<TerminalNode> dots = receiverContext.DOT();
    final List<Document> parts = new ArrayList<>();
    parts.add(dispatch.visit(receiverContext.typeType()));
    parts.add(text(" "));
    final List<JavaParser.IdentifierContext> identifiers = receiverContext.identifier();
    for (int i = 0; i < identifiers.size(); i++) {
      parts.add(dispatch.visit(identifiers.get(i)));
      parts.add(Tokens.sourced(dots.get(i)));
    }
    parts.add(Tokens.sourced(receiverContext.THIS()));
    return concat(parts);
  }

  Document visitGenericInterfaceMethodDeclaration(final JavaParser.GenericInterfaceMethodDeclarationContext genericContext) {
    final List<Document> parts = new ArrayList<>(
      Modifiers.format(dispatch, genericContext.interfaceMethodModifier(), Modifiers.IS_INTERFACE_METHOD_ANNOTATION)
    );
    parts.add(dispatch.visit(genericContext.typeParameters()));
    parts.add(text(" "));
    parts.add(dispatch.visit(genericContext.interfaceCommonBodyDeclaration()));
    return concat(parts);
  }

  // Unlike other members, a compact constructor is a direct recordBody child — not wrapped in a
  // classBodyDeclaration — so its attached comments are not emitted by ClassBodyFormatter.
  Document visitCompactConstructorDeclaration(final JavaParser.CompactConstructorDeclarationContext compactContext) {
    final List<Document> parts = new ArrayList<>(dispatch.leading(compactContext));
    parts.addAll(Modifiers.format(dispatch, compactContext.modifier(), Modifiers.IS_ANNOTATION));
    parts.add(dispatch.visit(compactContext.identifier()));
    parts.add(text(" "));
    parts.add(dispatch.visit(compactContext.block()));
    parts.add(dispatch.trailing(compactContext));
    return concat(parts);
  }
}
