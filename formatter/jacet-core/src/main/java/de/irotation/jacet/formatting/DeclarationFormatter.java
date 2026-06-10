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
 * Formats type declarations: class, interface, enum, record, and annotation type headers. Body formatting is handled by
 * {@link ClassBodyFormatter}.
 */
final class DeclarationFormatter implements HandlerProvider {

  private final FormattingDispatch dispatch;

  DeclarationFormatter(final FormattingDispatch dispatch) {
    this.dispatch = dispatch;
  }

  @Override
  public void registerHandlers(final HandlerRegistry registry) {
    registry.register(JavaParser.TypeDeclarationContext.class, this::visitTypeDeclaration);
    registry.register(JavaParser.ClassDeclarationContext.class, this::visitClassDeclaration);
    registry.register(JavaParser.EnumDeclarationContext.class, this::visitEnumDeclaration);
    registry.register(JavaParser.EnumConstantsContext.class, this::visitEnumConstants);
    registry.register(JavaParser.EnumConstantContext.class, this::visitEnumConstant);
    registry.register(JavaParser.InterfaceDeclarationContext.class, this::visitInterfaceDeclaration);
    registry.register(JavaParser.RecordDeclarationContext.class, this::visitRecordDeclaration);
    registry.register(JavaParser.RecordHeaderContext.class, this::visitRecordHeader);
    registry.register(JavaParser.RecordComponentListContext.class, this::visitRecordComponentList);
    registry.register(JavaParser.RecordComponentContext.class, this::visitRecordComponent);
    registry.register(JavaParser.AnnotationTypeDeclarationContext.class, this::visitAnnotationTypeDeclaration);
    registry.register(JavaParser.AnnotationTypeBodyContext.class, this::visitAnnotationTypeBody);
    registry.register(JavaParser.AnnotationTypeElementDeclarationContext.class, this::visitAnnotationTypeElementDeclaration);
    registry.register(JavaParser.AnnotationTypeElementRestContext.class, this::visitAnnotationTypeElementRest);
    registry.register(JavaParser.AnnotationMethodOrConstantRestContext.class, this::visitAnnotationMethodOrConstantRest);
    registry.register(JavaParser.AnnotationMethodRestContext.class, this::visitAnnotationMethodRest);
    registry.register(JavaParser.AnnotationConstantRestContext.class, this::visitAnnotationConstantRest);
    registry.register(JavaParser.DefaultValueContext.class, this::visitDefaultValue);
    registry.register(JavaParser.AltAnnotationQualifiedNameContext.class, this::visitAltAnnotationQualifiedName);
  }

  Document visitTypeDeclaration(final JavaParser.TypeDeclarationContext declarationContext) {
    final List<Document> parts = new ArrayList<>(dispatch.leading(declarationContext));
    parts.addAll(Modifiers.format(dispatch, declarationContext.classOrInterfaceModifier(), Modifiers.IS_CLASS_ANNOTATION));
    if (declarationContext.classDeclaration() != null) {
      parts.add(dispatch.visit(declarationContext.classDeclaration()));
    } else if (declarationContext.enumDeclaration() != null) {
      parts.add(dispatch.visit(declarationContext.enumDeclaration()));
    } else if (declarationContext.interfaceDeclaration() != null) {
      parts.add(dispatch.visit(declarationContext.interfaceDeclaration()));
    } else if (declarationContext.annotationTypeDeclaration() != null) {
      parts.add(dispatch.visit(declarationContext.annotationTypeDeclaration()));
    } else if (declarationContext.recordDeclaration() != null) {
      parts.add(dispatch.visit(declarationContext.recordDeclaration()));
    }
    return concat(parts);
  }

  /**
   * Formats a class declaration. With type parameters and exactly one clause, the type-parameter list shares the header group, so it is
   * what breaks when the header overflows while the single clause stays attached after {@code >} and the brace stays on the line. Other
   * shapes (no type parameters, or multiple clauses) use the prependSeparator / preBraceSeparator layout, where the clauses and brace break
   * instead.
   */
  Document visitClassDeclaration(final JavaParser.ClassDeclarationContext classContext) {
    final List<Document> clauses = new ArrayList<>();
    if (classContext.EXTENDS() != null) {
      clauses.add(group(concat(Tokens.sourced(classContext.EXTENDS()), indent(concat(line(), dispatch.visit(classContext.typeType()))))));
    }
    if (classContext.IMPLEMENTS() != null) {
      clauses.add(this.keywordWithTypeList(classContext.IMPLEMENTS(), classContext.typeList(0)));
    }
    if (classContext.PERMITS() != null) {
      final int permitsIdx = classContext.IMPLEMENTS() != null ? 1 : 0;
      clauses.add(this.keywordWithTypeList(classContext.PERMITS(), classContext.typeList(permitsIdx)));
    }
    final boolean hasTypeParams = classContext.typeParameters() != null;

    final List<Document> headerParts = new ArrayList<>();
    headerParts.add(Tokens.sourced(classContext.CLASS()));
    headerParts.add(text(" "));
    headerParts.add(dispatch.visit(classContext.identifier()));

    if (hasTypeParams && clauses.size() == 1) {
      headerParts.add(this.typeParametersShared(classContext.typeParameters()));
      headerParts.add(text(" "));
      headerParts.add(clauses.getFirst());
      headerParts.add(text(" "));
      return concat(group(concat(headerParts)), dispatch.visit(classContext.classBody()));
    }

    if (hasTypeParams) {
      headerParts.add(dispatch.visit(classContext.typeParameters()));
    }
    if (!clauses.isEmpty()) {
      headerParts.add(indent(concat(prependSeparator(clauses))));
    }
    final boolean emptyBody = classContext.classBody().classBodyDeclaration().isEmpty();
    final List<Document> parts = new ArrayList<>();
    parts.add(group(concat(concat(headerParts), preBraceSeparator(!clauses.isEmpty(), emptyBody))));
    parts.add(dispatch.visit(classContext.classBody()));
    return concat(parts);
  }

  /**
   * The type-parameter list as a break point that participates in the enclosing header group (no group of its own), so it breaks when the
   * header overflows. Mirrors {@code TypeFormatter.visitTypeParameters} without the surrounding group.
   */
  private Document typeParametersShared(final JavaParser.TypeParametersContext typeContext) {
    final List<Document> params = typeContext.typeParameter().stream().map(dispatch::visit).toList();
    return concat(
      Tokens.sourced(typeContext.LT()),
      indent(concat(softLine(), Tokens.joinSourced(params, typeContext.COMMA(), line()))),
      softLine(),
      Tokens.sourced(typeContext.GT())
    );
  }

  private static Document preBraceSeparator(final boolean hasClauses, final boolean emptyBody) {
    if (hasClauses && !emptyBody) {
      return ifBreak(hardLine(), text(" "));
    }
    return text(" ");
  }

  private static List<Document> prependSeparator(final Iterable<Document> clauses) {
    final List<Document> parts = new ArrayList<>();
    for (final Document clause : clauses) {
      parts.add(ifBreak(hardLine(), text(" ")));
      parts.add(clause);
    }
    return parts;
  }

  /**
   * Formats an enum declaration. Dangling stand-alone comments inside the body but past the last child rule (constants or body
   * declarations) attach to the enum node and are rendered at body indent. An empty enum body stays {@code {}} (matching
   * class/interface/annotation types) unless such a dangling comment sits inside it; otherwise the trailing hardLine would expand it to an
   * empty two-line block.
   */
  Document visitEnumDeclaration(final JavaParser.EnumDeclarationContext enumContext) {
    final List<Document> headerParts = new ArrayList<>();
    headerParts.add(Tokens.sourced(enumContext.ENUM()));
    headerParts.add(text(" "));
    headerParts.add(dispatch.visit(enumContext.identifier()));
    final boolean hasImplements = enumContext.IMPLEMENTS() != null;
    if (hasImplements) {
      headerParts.add(
        indent(concat(prependSeparator(List.of(this.keywordWithTypeList(enumContext.IMPLEMENTS(), enumContext.typeList())))))
      );
    }
    final boolean enumEmpty = enumContext.enumConstants() == null && enumContext.enumBodyDeclarations() == null;
    final List<Document> dangling = dispatch.dangling(enumContext);

    if (enumEmpty && dangling.isEmpty()) {
      return group(
        concat(
          concat(headerParts),
          preBraceSeparator(hasImplements, true),
          Tokens.sourced(enumContext.LBRACE()),
          Tokens.sourced(enumContext.RBRACE())
        )
      );
    }

    final List<Document> parts = new ArrayList<>();
    parts.add(group(concat(concat(headerParts), preBraceSeparator(hasImplements, enumEmpty), Tokens.sourced(enumContext.LBRACE()))));

    final List<Document> bodyParts = new ArrayList<>();
    if (enumContext.enumConstants() != null) {
      bodyParts.add(hardLine());
      bodyParts.add(dispatch.visit(enumContext.enumConstants()));
      if (enumContext.enumBodyDeclarations() != null) {
        if (enumContext.COMMA() != null) {
          bodyParts.add(Tokens.removed(enumContext.COMMA()));
        }
        bodyParts.add(dispatch.visit(enumContext.enumBodyDeclarations()));
      } else if (enumContext.COMMA() != null) {
        bodyParts.add(Tokens.sourced(enumContext.COMMA()));
        bodyParts.add(dispatch.trailingComment(enumContext.COMMA().getSymbol().getTokenIndex()));
      } else {
        bodyParts.add(text(","));
        final List<JavaParser.EnumConstantContext> consts = enumContext.enumConstants().enumConstant();
        bodyParts.add(dispatch.trailing(consts.getLast()));
      }
    }
    if (!dangling.isEmpty()) {
      bodyParts.add(hardLine());
      bodyParts.addAll(dangling);
    }
    parts.add(indent(concat(bodyParts)));

    parts.add(hardLine());
    parts.add(Tokens.sourced(enumContext.RBRACE()));
    return concat(parts);
  }

  /**
   * Formats the enum constant list. Comments between the opening brace and the first constant attach to this rule (their token index falls
   * outside {@code enumConstant(0)}'s range), so they are emitted here.
   */
  Document visitEnumConstants(final JavaParser.EnumConstantsContext enumContext) {
    final List<JavaParser.EnumConstantContext> constants = enumContext.enumConstant();
    final List<TerminalNode> commas = enumContext.COMMA();
    final List<Document> parts = new ArrayList<>(dispatch.leading(enumContext));
    if (dispatch.hasBlankLineBefore(constants.getFirst())) {
      parts.add(hardLine());
    }
    for (int i = 0; i < constants.size(); i++) {
      if (i > 0) {
        parts.add(Tokens.sourced(commas.get(i - 1)));
        parts.add(
          dispatch.trailingComment(
            commas
              .get(i - 1)
              .getSymbol()
              .getTokenIndex()
          )
        );
        parts.add(hardLine());
        if (dispatch.hasBlankLineBefore(constants.get(i))) {
          parts.add(hardLine());
        }
      }
      parts.add(dispatch.visit(constants.get(i)));
    }
    return concat(parts);
  }

  Document visitEnumConstant(final JavaParser.EnumConstantContext enumContext) {
    final List<Document> parts = new ArrayList<>(dispatch.leading(enumContext));
    for (final JavaParser.AnnotationContext annotation : enumContext.annotation()) {
      parts.add(dispatch.visit(annotation));
      parts.add(text(" "));
    }
    parts.add(dispatch.visit(enumContext.identifier()));
    if (enumContext.arguments() != null) {
      parts.add(dispatch.visit(enumContext.arguments()));
    }
    if (enumContext.classBody() != null) {
      parts.add(text(" "));
      parts.add(dispatch.visit(enumContext.classBody()));
    }
    return concat(parts);
  }

  Document visitInterfaceDeclaration(final JavaParser.InterfaceDeclarationContext interfaceContext) {
    final List<Document> parts = new ArrayList<>();
    parts.add(Tokens.sourced(interfaceContext.INTERFACE()));
    parts.add(text(" "));
    parts.add(dispatch.visit(interfaceContext.identifier()));

    if (interfaceContext.typeParameters() != null) {
      parts.add(dispatch.visit(interfaceContext.typeParameters()));
    }

    final Collection<Document> clauses = new ArrayList<>();
    if (interfaceContext.EXTENDS() != null) {
      clauses.add(this.keywordWithTypeList(interfaceContext.EXTENDS(), interfaceContext.typeList(0)));
    }
    if (interfaceContext.PERMITS() != null) {
      final int permitsIdx = interfaceContext.EXTENDS() != null ? 1 : 0;
      clauses.add(this.keywordWithTypeList(interfaceContext.PERMITS(), interfaceContext.typeList(permitsIdx)));
    }
    if (!clauses.isEmpty()) {
      parts.add(indent(concat(prependSeparator(clauses))));
    }
    final boolean emptyBody = interfaceContext.interfaceBody().interfaceBodyDeclaration().isEmpty();
    final List<Document> wrapped = new ArrayList<>();
    wrapped.add(group(concat(concat(parts), preBraceSeparator(!clauses.isEmpty(), emptyBody))));
    wrapped.add(dispatch.visit(interfaceContext.interfaceBody()));
    return concat(wrapped);
  }

  /**
   * Formats a record declaration. The record components and the header share one group while the {@code implements} clause keeps its own:
   * when the header overflows, the components break and the implements clause — re-measured from the now-short {@code )} line — stays flat.
   * Sharing one group is the point; an independently grouped component list would stay flat and push the implements clause to break
   * instead.
   */
  Document visitRecordDeclaration(final JavaParser.RecordDeclarationContext recordContext) {
    final List<Document> header = new ArrayList<>();
    header.add(Tokens.sourced(recordContext.RECORD()));
    header.add(text(" "));
    header.add(dispatch.visit(recordContext.identifier()));

    if (recordContext.typeParameters() != null) {
      header.add(dispatch.visit(recordContext.typeParameters()));
    }

    header.add(this.recordHeaderComponents(recordContext.recordHeader()));

    if (recordContext.IMPLEMENTS() != null) {
      header.add(
        group(
          concat(text(" "), Tokens.sourced(recordContext.IMPLEMENTS()), indent(concat(line(), this.joinTypeList(recordContext.typeList()))))
        )
      );
    }

    final List<Document> parts = new ArrayList<>();
    parts.add(group(concat(concat(header), text(" "))));
    parts.add(dispatch.visit(recordContext.recordBody()));
    return concat(parts);
  }

  /**
   * The record component list as a break point that participates in the enclosing header group rather than its own. See
   * {@link #visitRecordDeclaration} for why the grouping matters.
   */
  private Document recordHeaderComponents(final JavaParser.RecordHeaderContext recordContext) {
    if (recordContext.recordComponentList() == null) {
      return concat(Tokens.sourced(recordContext.LPAREN()), Tokens.sourced(recordContext.RPAREN()));
    }
    return concat(
      Tokens.sourced(recordContext.LPAREN()),
      indent(concat(softLine(), dispatch.visit(recordContext.recordComponentList()))),
      softLine(),
      Tokens.sourced(recordContext.RPAREN())
    );
  }

  /**
   * Standalone dispatch of a record header groups the component list on its own; within a record declaration the components share the
   * header group instead (see {@link #recordHeaderComponents}).
   */
  Document visitRecordHeader(final JavaParser.RecordHeaderContext recordContext) {
    return group(this.recordHeaderComponents(recordContext));
  }

  Document visitRecordComponentList(final JavaParser.RecordComponentListContext recordContext) {
    final List<Document> components = recordContext.recordComponent().stream().map(dispatch::visit).toList();
    return Tokens.joinSourced(components, recordContext.COMMA(), line());
  }

  Document visitRecordComponent(final JavaParser.RecordComponentContext recordContext) {
    final List<Document> parts = new ArrayList<>(dispatch.leading(recordContext));
    for (final JavaParser.AnnotationContext annotation : recordContext.annotation()) {
      parts.add(dispatch.visit(annotation));
      parts.add(text(" "));
    }
    parts.add(dispatch.visit(recordContext.typeType()));
    parts.add(text(" "));
    parts.add(dispatch.visit(recordContext.identifier()));
    parts.add(dispatch.trailing(recordContext));
    return concat(parts);
  }

  Document visitAnnotationTypeDeclaration(final JavaParser.AnnotationTypeDeclarationContext annotationContext) {
    return concat(
      Tokens.sourced(annotationContext.AT()),
      Tokens.sourced(annotationContext.INTERFACE()),
      text(" "),
      dispatch.visit(annotationContext.identifier()),
      text(" "),
      dispatch.visit(annotationContext.annotationTypeBody())
    );
  }

  /**
   * Renders an annotation-type body. Dangling stand-alone comments past the last element are rendered at body indent before the closing
   * brace.
   */
  Document visitAnnotationTypeBody(final JavaParser.AnnotationTypeBodyContext annotationContext) {
    final List<JavaParser.AnnotationTypeElementDeclarationContext> declarations = annotationContext.annotationTypeElementDeclaration();
    if (declarations.isEmpty()) {
      return concat(Tokens.sourced(annotationContext.LBRACE()), Tokens.sourced(annotationContext.RBRACE()));
    }

    final List<Document> parts = new ArrayList<>();
    for (int i = 0; i < declarations.size(); i++) {
      if (i > 0) {
        parts.add(hardLine());
        parts.add(hardLine());
      }
      parts.add(dispatch.visit(declarations.get(i)));
    }
    final List<Document> dangling = dispatch.dangling(annotationContext);
    if (!dangling.isEmpty()) {
      parts.add(hardLine());
      parts.addAll(dangling);
    }

    return concat(
      Tokens.sourced(annotationContext.LBRACE()),
      indent(concat(hardLine(), concat(parts))),
      hardLine(),
      Tokens.sourced(annotationContext.RBRACE())
    );
  }

  Document visitAnnotationTypeElementDeclaration(final JavaParser.AnnotationTypeElementDeclarationContext annotationContext) {
    if (annotationContext.annotationTypeElementRest() == null) {
      return Tokens.sourced(annotationContext.SEMI());
    }
    final List<Document> parts = new ArrayList<>(dispatch.leading(annotationContext));
    parts.addAll(Modifiers.format(dispatch, annotationContext.modifier(), Modifiers.IS_ANNOTATION));
    parts.add(dispatch.visit(annotationContext.annotationTypeElementRest()));
    parts.add(dispatch.trailing(annotationContext));
    return concat(parts);
  }

  Document visitAnnotationTypeElementRest(final JavaParser.AnnotationTypeElementRestContext annotationContext) {
    if (annotationContext.annotationMethodOrConstantRest() != null) {
      return concat(
        dispatch.visit(annotationContext.typeType()),
        text(" "),
        dispatch.visit(annotationContext.annotationMethodOrConstantRest()),
        Tokens.sourced(annotationContext.SEMI())
      );
    }
    return dispatch.visit(annotationContext.getChild(0));
  }

  Document visitAnnotationMethodOrConstantRest(final JavaParser.AnnotationMethodOrConstantRestContext annotationContext) {
    return dispatch.visit(annotationContext.getChild(0));
  }

  Document visitAnnotationMethodRest(final JavaParser.AnnotationMethodRestContext annotationContext) {
    final List<Document> parts = new ArrayList<>();
    parts.add(dispatch.visit(annotationContext.identifier()));
    parts.add(Tokens.sourced(annotationContext.LPAREN()));
    parts.add(Tokens.sourced(annotationContext.RPAREN()));
    if (annotationContext.defaultValue() != null) {
      parts.add(text(" "));
      parts.add(dispatch.visit(annotationContext.defaultValue()));
    }
    return concat(parts);
  }

  Document visitAnnotationConstantRest(final JavaParser.AnnotationConstantRestContext annotationContext) {
    return dispatch.visit(annotationContext.variableDeclarators());
  }

  Document visitDefaultValue(final JavaParser.DefaultValueContext defaultContext) {
    return concat(Tokens.sourced(defaultContext.DEFAULT()), text(" "), dispatch.visit(defaultContext.elementValue()));
  }

  Document visitAltAnnotationQualifiedName(final JavaParser.AltAnnotationQualifiedNameContext annotationContext) {
    return Tokens.sourced(annotationContext);
  }

  private Document joinTypeList(final JavaParser.TypeListContext typeListCtx) {
    final List<Document> types = typeListCtx.typeType().stream().map(dispatch::visit).toList();
    return group(Tokens.joinSourced(types, typeListCtx.COMMA(), line()));
  }

  /**
   * Wraps an {@code extends}/{@code implements}/{@code permits} keyword with its type list as a single group whose break form puts the
   * keyword on its own line and each type on an indented line — `extends\n  X,\n  Y,\n  Z`. Prettier-java's `extends_interfaces` layout.
   */
  private Document keywordWithTypeList(final TerminalNode keyword, final JavaParser.TypeListContext typeListCtx) {
    final List<Document> types = typeListCtx.typeType().stream().map(dispatch::visit).toList();
    return group(concat(Tokens.sourced(keyword), indent(concat(line(), Tokens.joinSourced(types, typeListCtx.COMMA(), line())))));
  }
}
