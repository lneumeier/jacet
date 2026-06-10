package de.irotation.jacet.formatting;

import static de.irotation.jacet.document.Document.breakIndent;
import static de.irotation.jacet.document.Document.concat;
import static de.irotation.jacet.document.Document.empty;
import static de.irotation.jacet.document.Document.group;
import static de.irotation.jacet.document.Document.ifBreak;
import static de.irotation.jacet.document.Document.indent;
import static de.irotation.jacet.document.Document.line;
import static de.irotation.jacet.document.Document.softLine;
import static de.irotation.jacet.document.Document.text;
import static de.irotation.jacet.document.Document.willBreak;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import de.irotation.jacet.document.Document;
import de.irotation.jacet.document.Group;
import de.irotation.jacet.parser.JavaParser;

/**
 * Formats type references, generics, and annotations.
 */
final class TypeFormatter implements HandlerProvider {

  private final FormattingDispatch dispatch;

  TypeFormatter(final FormattingDispatch dispatch) {
    this.dispatch = dispatch;
  }

  @Override
  public void registerHandlers(final HandlerRegistry registry) {
    registry.register(JavaParser.TypeTypeContext.class, this::visitTypeType);
    registry.register(JavaParser.TypeTypeOrVoidContext.class, this::visitTypeTypeOrVoid);
    registry.register(JavaParser.ClassOrInterfaceTypeContext.class, this::visitClassOrInterfaceType);
    registry.register(JavaParser.ClassTypeContext.class, this::visitClassType);
    registry.register(JavaParser.PrimitiveTypeContext.class, this::visitPrimitiveType);
    registry.register(JavaParser.TypeParametersContext.class, this::visitTypeParameters);
    registry.register(JavaParser.TypeParameterContext.class, this::visitTypeParameter);
    registry.register(JavaParser.TypeBoundContext.class, this::visitTypeBound);
    registry.register(JavaParser.TypeArgumentsContext.class, this::visitTypeArguments);
    registry.register(JavaParser.TypeArgumentContext.class, this::visitTypeArgument);
    registry.register(JavaParser.TypeListContext.class, this::visitTypeList);
    registry.register(JavaParser.AnnotationContext.class, this::visitAnnotation);
    registry.register(JavaParser.AnnotationFieldValuesContext.class, this::visitAnnotationFieldValues);
    registry.register(JavaParser.AnnotationFieldValueContext.class, this::visitAnnotationFieldValue);
    registry.register(JavaParser.AnnotationValueContext.class, this::visitAnnotationValue);
    registry.register(JavaParser.ElementValueContext.class, this::visitElementValue);
    registry.register(JavaParser.ElementValueArrayInitializerContext.class, this::visitElementValueArrayInitializer);
  }

  /**
   * Formats a type reference. Type annotations may appear before the base type and before each {@code []} (for arrays), so children are
   * iterated in source order to preserve annotation position — e.g. {@code String @Nullable []} (a nullable array) must not collapse to
   * {@code @Nullable String[]} (an array of nullables). Unknown terminals (the grammar currently has only the array brackets) are emitted
   * verbatim rather than dropped or rejected, preserving the "parse errors &rarr; source unchanged" contract across grammar evolution;
   * verbatim emission has no spacing heuristics, so a new multi-char terminal appearing in snapshot diffs is a signal to update the
   * formatter.
   */
  Document visitTypeType(final JavaParser.TypeTypeContext typeContext) {
    final List<Document> parts = new ArrayList<>();
    boolean anythingEmitted = false;
    boolean lastWasAnnotation = false;
    for (int i = 0; i < typeContext.getChildCount(); i++) {
      final ParseTree child = typeContext.getChild(i);
      if (child instanceof final JavaParser.AnnotationContext annotation) {
        if (anythingEmitted) {
          parts.add(text(" "));
        }
        parts.add(dispatch.visit(annotation));
        lastWasAnnotation = true;
        anythingEmitted = true;
      } else if (child instanceof final JavaParser.ClassOrInterfaceTypeContext classOrInterfaceType) {
        if (lastWasAnnotation) {
          parts.add(text(" "));
        }
        parts.add(dispatch.visit(classOrInterfaceType));
        lastWasAnnotation = false;
        anythingEmitted = true;
      } else if (child instanceof final JavaParser.PrimitiveTypeContext primitiveType) {
        if (lastWasAnnotation) {
          parts.add(text(" "));
        }
        parts.add(dispatch.visit(primitiveType));
        lastWasAnnotation = false;
        anythingEmitted = true;
      } else if (child instanceof final TerminalNode terminal) {
        if ("[".equals(terminal.getText()) && lastWasAnnotation) {
          parts.add(text(" "));
        }
        parts.add(Tokens.sourced(terminal));
        lastWasAnnotation = false;
        anythingEmitted = true;
      }
    }
    return concat(parts);
  }

  Document visitTypeTypeOrVoid(final JavaParser.TypeTypeOrVoidContext typeContext) {
    if (typeContext.VOID() != null) {
      return Tokens.sourced(typeContext.VOID());
    }
    return dispatch.visit(typeContext.typeType());
  }

  Document visitClassOrInterfaceType(final JavaParser.ClassOrInterfaceTypeContext typeContext) {
    return dispatch.visit(typeContext.classType());
  }

  Document visitClassType(final JavaParser.ClassTypeContext typeContext) {
    final List<Document> parts = new ArrayList<>();
    for (int i = 0; i < typeContext.getChildCount(); i++) {
      final ParseTree child = typeContext.getChild(i);
      if (child instanceof final TerminalNode terminal) {
        parts.add(Tokens.sourced(terminal));
      } else {
        parts.add(dispatch.visit(child));
      }
    }
    return concat(parts);
  }

  Document visitPrimitiveType(final JavaParser.PrimitiveTypeContext typeContext) {
    return Tokens.sourced(typeContext);
  }

  Document visitTypeParameters(final JavaParser.TypeParametersContext typeContext) {
    final List<Document> parameters = typeContext.typeParameter().stream().map(dispatch::visit).toList();
    return group(
      concat(
        Tokens.sourced(typeContext.LT()),
        indent(concat(softLine(), Tokens.joinSourced(parameters, typeContext.COMMA(), line()))),
        softLine(),
        Tokens.sourced(typeContext.GT())
      )
    );
  }

  Document visitTypeParameter(final JavaParser.TypeParameterContext typeContext) {
    final List<Document> parts = new ArrayList<>();
    for (final JavaParser.AnnotationContext annotation : typeContext.annotation()) {
      parts.add(dispatch.visit(annotation));
      parts.add(text(" "));
    }
    parts.add(dispatch.visit(typeContext.identifier()));
    if (typeContext.typeBound() != null) {
      parts.add(text(" "));
      parts.add(Tokens.sourced(typeContext.EXTENDS()));
      parts.add(text(" "));
      parts.add(dispatch.visit(typeContext.typeBound()));
    }
    return concat(parts);
  }

  Document visitTypeBound(final JavaParser.TypeBoundContext typeContext) {
    final List<Document> types = typeContext.typeType().stream().map(dispatch::visit).toList();
    return Tokens.joinSourced(types, typeContext.BITAND(), text(" "), text(" "));
  }

  Document visitTypeArguments(final JavaParser.TypeArgumentsContext typeContext) {
    final List<Document> arguments = typeContext.typeArgument().stream().map(dispatch::visit).toList();
    return group(
      concat(
        Tokens.sourced(typeContext.LT()),
        indent(concat(softLine(), Tokens.joinSourced(arguments, typeContext.COMMA(), line()))),
        softLine(),
        Tokens.sourced(typeContext.GT())
      )
    );
  }

  Document visitTypeArgument(final JavaParser.TypeArgumentContext typeContext) {
    if (typeContext.QUESTION() != null) {
      if (typeContext.EXTENDS() != null) {
        return concat(
          Tokens.sourced(typeContext.QUESTION()),
          text(" "),
          Tokens.sourced(typeContext.EXTENDS()),
          text(" "),
          dispatch.visit(typeContext.typeType())
        );
      }
      if (typeContext.SUPER() != null) {
        return concat(
          Tokens.sourced(typeContext.QUESTION()),
          text(" "),
          Tokens.sourced(typeContext.SUPER()),
          text(" "),
          dispatch.visit(typeContext.typeType())
        );
      }
      return Tokens.sourced(typeContext.QUESTION());
    }
    return dispatch.visit(typeContext.typeType());
  }

  Document visitTypeList(final JavaParser.TypeListContext typeContext) {
    final List<Document> types = typeContext.typeType().stream().map(dispatch::visit).toList();
    return Tokens.joinSourced(types, typeContext.COMMA(), line());
  }

  /**
   * Formats an annotation. A lone bare-array argument hugs the parens ({@code @CsvSource({...})}) per prettier 2.9.7's
   * annotation-argument-list special case, letting the array group break on its own width/content. Every other shape (multiple args, or a
   * single {@code name = value} pair) wraps in a grouped indent-in-parentheses; since jacet has no automatic break propagation, that parens
   * group is forced to break when the field values will break, so the parens break with them.
   */
  Document visitAnnotation(final JavaParser.AnnotationContext annotationContext) {
    final List<Document> parts = new ArrayList<>();
    parts.add(Tokens.sourced(annotationContext.AT()));
    parts.add(dispatch.visit(annotationContext.qualifiedName()));
    if (annotationContext.annotationFieldValues() != null) {
      final JavaParser.AnnotationFieldValuesContext fieldValues = annotationContext.annotationFieldValues();
      final Document values = dispatch.visit(fieldValues);
      if (isSingleBareArrayArg(fieldValues)) {
        parts.add(Tokens.sourced(fieldValues.LPAREN()));
        parts.add(values);
        parts.add(Tokens.sourced(fieldValues.RPAREN()));
      } else {
        parts.add(
          new Group(
            concat(
              Tokens.sourced(fieldValues.LPAREN()),
              breakIndent(concat(softLine(), values)),
              softLine(),
              Tokens.sourced(fieldValues.RPAREN())
            ),
            willBreak(values)
          )
        );
      }
    }
    return concat(parts);
  }

  /**
   * The parens-hug special case fires only when the sole annotation argument is a bare array initializer (an
   * {@code ElementValueArrayInitializer}) — not a {@code name = {...}} pair and not a plain expression. An annotation value is a bare array
   * iff it is neither an expression nor a nested annotation.
   */
  private static boolean isSingleBareArrayArg(final JavaParser.AnnotationFieldValuesContext fieldValues) {
    final List<JavaParser.AnnotationFieldValueContext> args = fieldValues.annotationFieldValue();
    if (args.size() != 1) {
      return false;
    }
    final JavaParser.AnnotationFieldValueContext arg = args.getFirst();
    if (arg.identifier() != null) {
      return false;
    }
    final JavaParser.AnnotationValueContext value = arg.annotationValue();
    return value != null && value.expression() == null && value.annotation() == null;
  }

  Document visitAnnotationFieldValues(final JavaParser.AnnotationFieldValuesContext annotationContext) {
    final List<Document> values = annotationContext.annotationFieldValue().stream().map(dispatch::visit).toList();
    return Tokens.joinSourced(values, annotationContext.COMMA(), line());
  }

  Document visitAnnotationFieldValue(final JavaParser.AnnotationFieldValueContext annotationContext) {
    if (annotationContext.identifier() != null) {
      return concat(
        dispatch.visit(annotationContext.identifier()),
        text(" "),
        Tokens.sourced(annotationContext.ASSIGN()),
        text(" "),
        dispatch.visit(annotationContext.annotationValue())
      );
    }
    return dispatch.visit(annotationContext.annotationValue());
  }

  Document visitAnnotationValue(final JavaParser.AnnotationValueContext annotationContext) {
    if (annotationContext.annotation() != null) {
      return dispatch.visit(annotationContext.annotation());
    }
    if (annotationContext.expression() != null) {
      return dispatch.visit(annotationContext.expression());
    }
    return this.formatBracedList(annotationContext, annotationContext.annotationValue());
  }

  Document visitElementValue(final JavaParser.ElementValueContext elementContext) {
    return dispatch.visit(elementContext.getChild(0));
  }

  Document visitElementValueArrayInitializer(final JavaParser.ElementValueArrayInitializerContext elementContext) {
    return this.formatBracedList(elementContext, elementContext.elementValue());
  }

  /**
   * Renders {@code { a, b, c }} for both annotation arrays and element-value array initializers. Element trailing comments are emitted
   * after the comma so a {@code // hint} keeps its element association: with {@code CommentAttacher} attaching post-comma line-comments to
   * the preceding element rule (the comma is a terminal and is skipped during attachment), this is the spot that materializes them.
   * Separator commas are sourced from {@code owner}; an input trailing comma (present when there are as many commas as elements) is covered
   * via {@code removed()} and the visible trailing comma is re-synthesised on break.
   *
   * <p>Deliberately kept separate from {@link StatementFormatter#visitArrayInitializer} despite the shared
   * braced-comma-list shape: this variant carries no brace-level comments and always separates elements with a soft {@code line()}, whereas
   * array initializers additionally render brace-level dangling comments and force a hard break after a comma bearing a same-line trailing
   * comment. Folding the two together would require collapsing those two distinct comment models into one parameterised helper more complex
   * than either method.
   */
  private Document formatBracedList(final ParserRuleContext owner, final List<? extends ParserRuleContext> elements) {
    final List<TerminalNode> commas = owner.getTokens(JavaParser.COMMA);
    if (elements.isEmpty()) {
      return concat(Tokens.sourced(owner.getToken(JavaParser.LBRACE, 0)), Tokens.sourced(owner.getToken(JavaParser.RBRACE, 0)));
    }
    final boolean hasTrailingComma = commas.size() == elements.size();
    final List<Document> parts = new ArrayList<>();
    parts.add(line());
    for (int i = 0; i < elements.size(); i++) {
      parts.addAll(dispatch.leading(elements.get(i)));
      parts.add(dispatch.visit(elements.get(i)));
      final boolean isLast = i == elements.size() - 1;
      if (!isLast) {
        parts.add(Tokens.sourced(commas.get(i)));
        parts.add(dispatch.trailing(elements.get(i)));
        parts.add(line());
      } else {
        if (hasTrailingComma) {
          parts.add(Tokens.removed(commas.get(i)));
        }
        parts.add(ifBreak(text(","), empty()));
        parts.add(dispatch.trailing(elements.get(i)));
      }
    }
    return group(
      concat(
        Tokens.sourced(owner.getToken(JavaParser.LBRACE, 0)),
        indent(concat(parts)),
        line(),
        Tokens.sourced(owner.getToken(JavaParser.RBRACE, 0))
      )
    );
  }
}
