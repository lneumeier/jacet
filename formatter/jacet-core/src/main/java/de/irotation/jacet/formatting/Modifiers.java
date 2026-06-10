package de.irotation.jacet.formatting;

import static de.irotation.jacet.document.Document.hardLine;
import static de.irotation.jacet.document.Document.text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.antlr.v4.runtime.ParserRuleContext;

import de.irotation.jacet.document.Document;
import de.irotation.jacet.parser.JavaParser;

final class Modifiers {

  static final Predicate<JavaParser.ModifierContext> IS_ANNOTATION = mod ->
    mod.classOrInterfaceModifier() != null && mod.classOrInterfaceModifier().annotation() != null;

  static final Predicate<JavaParser.ClassOrInterfaceModifierContext> IS_CLASS_ANNOTATION = mod -> mod.annotation() != null;

  static final Predicate<JavaParser.InterfaceMethodModifierContext> IS_INTERFACE_METHOD_ANNOTATION = mod -> mod.annotation() != null;

  private Modifiers() {}

  /**
   * Formats a modifier list. Annotations at the <em>start</em> of the list are statement/declaration annotations and go on their own line;
   * annotations after a non-annotation modifier (e.g. {@code private final @Nullable Type x}) are type annotations on the following type
   * and stay inline, so a declaration is never split mid-type. Leading comments are fetched per modifier here because
   * {@code CommentAttacher} routes a between-modifier line to the <em>next</em> child rule — a note between {@code @Annotation} and
   * {@code private} attaches to {@code private} — and no other handler fetches modifier leading, so without this the note would be silently
   * dropped.
   */
  static <T extends ParserRuleContext> List<Document> format(
    final FormattingDispatch dispatch,
    final Iterable<T> modifiers,
    final Predicate<T> isAnnotation
  ) {
    final List<Document> parts = new ArrayList<>();
    boolean seenNonAnnotation = false;
    for (final T mod : modifiers) {
      parts.addAll(dispatch.leading(mod));
      parts.add(dispatch.visit(mod));
      parts.add(dispatch.trailing(mod));
      final boolean isAnn = isAnnotation.test(mod);
      parts.add(isAnn && !seenNonAnnotation ? hardLine() : text(" "));
      if (!isAnn) {
        seenNonAnnotation = true;
      }
    }
    return parts;
  }

  static List<Document> formatVariable(final FormattingDispatch dispatch, final Iterable<JavaParser.VariableModifierContext> modifiers) {
    return formatVariable(dispatch, modifiers, false);
  }

  static List<Document> formatVariable(
    final FormattingDispatch dispatch,
    final Iterable<JavaParser.VariableModifierContext> modifiers,
    final boolean leadingAnnotationsOnOwnLine
  ) {
    final List<Document> parts = new ArrayList<>();
    boolean seenNonAnnotation = false;
    for (final JavaParser.VariableModifierContext mod : modifiers) {
      parts.addAll(dispatch.leading(mod));
      parts.add(dispatch.visit(mod));
      parts.add(dispatch.trailing(mod));
      final boolean isAnn = mod.annotation() != null;
      final boolean ownLine = leadingAnnotationsOnOwnLine && isAnn && !seenNonAnnotation;
      parts.add(ownLine ? hardLine() : text(" "));
      if (!isAnn) {
        seenNonAnnotation = true;
      }
    }
    return parts;
  }
}
