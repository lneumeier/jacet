package de.irotation.jacet.formatting;

import static de.irotation.jacet.document.Document.concat;
import static de.irotation.jacet.document.Document.hardLine;
import static de.irotation.jacet.document.Document.indent;
import static de.irotation.jacet.document.Document.text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;

import de.irotation.jacet.document.Document;
import de.irotation.jacet.parser.JavaParser;

/**
 * Formats class, interface, and record bodies — member and enum-body declarations, preserving the source blank lines between members.
 */
final class ClassBodyFormatter implements HandlerProvider {

  private final FormattingDispatch dispatch;

  ClassBodyFormatter(final FormattingDispatch dispatch) {
    this.dispatch = dispatch;
  }

  @Override
  public void registerHandlers(final HandlerRegistry registry) {
    registry.register(JavaParser.RecordBodyContext.class, this::visitRecordBody);
    registry.register(JavaParser.ClassBodyContext.class, this::visitClassBody);
    registry.register(JavaParser.InterfaceBodyContext.class, this::visitInterfaceBody);
    registry.register(JavaParser.ClassBodyDeclarationContext.class, this::visitClassBodyDeclaration);
    registry.register(JavaParser.MemberDeclarationContext.class, this::visitMemberDeclaration);
    registry.register(JavaParser.InterfaceBodyDeclarationContext.class, this::visitInterfaceBodyDeclaration);
    registry.register(JavaParser.InterfaceMemberDeclarationContext.class, this::visitInterfaceMemberDeclaration);
    registry.register(JavaParser.EnumBodyDeclarationsContext.class, this::visitEnumBodyDeclarations);
  }

  Document visitClassBody(final JavaParser.ClassBodyContext bodyContext) {
    return this.visitBodyWithDeclarations(bodyContext, bodyContext.classBodyDeclaration());
  }

  Document visitInterfaceBody(final JavaParser.InterfaceBodyContext bodyContext) {
    return this.visitBodyWithDeclarations(bodyContext, bodyContext.interfaceBodyDeclaration());
  }

  /**
   * Formats a record body, rendering its class-body declarations and any compact constructor in source order (sorted by start token
   * index).
   */
  Document visitRecordBody(final JavaParser.RecordBodyContext recordContext) {
    final List<ParserRuleContext> members = new ArrayList<>(recordContext.classBodyDeclaration());
    members.addAll(recordContext.compactConstructorDeclaration());
    members.sort(Comparator.comparingInt(member -> member.getStart().getTokenIndex()));
    return this.visitBodyWithDeclarations(recordContext, members);
  }

  Document visitClassBodyDeclaration(final JavaParser.ClassBodyDeclarationContext declarationContext) {
    if (declarationContext.SEMI() != null && declarationContext.memberDeclaration() == null) {
      return Tokens.sourced(declarationContext.SEMI());
    }
    if (declarationContext.block() != null && declarationContext.memberDeclaration() == null) {
      final List<Document> parts = new ArrayList<>();
      if (declarationContext.STATIC() != null) {
        parts.add(Tokens.sourced(declarationContext.STATIC()));
        parts.add(text(" "));
      }
      parts.add(dispatch.visit(declarationContext.block()));
      return concat(parts);
    }

    final List<Document> parts = new ArrayList<>(dispatch.leading(declarationContext));
    parts.addAll(Modifiers.format(dispatch, declarationContext.modifier(), Modifiers.IS_ANNOTATION));
    if (declarationContext.memberDeclaration() != null) {
      parts.add(dispatch.visit(declarationContext.memberDeclaration()));
    }
    parts.add(dispatch.trailing(declarationContext));
    return concat(parts);
  }

  /**
   * Formats a member declaration, emitting leading comments here before delegating. {@code CommentAttacher} routes a line-comment sitting
   * between the last modifier and the member (e.g. a {@code // Note: …} above a field) to this node's leading, and the inner declaration
   * handlers don't fetch leading for it.
   */
  Document visitMemberDeclaration(final JavaParser.MemberDeclarationContext declarationContext) {
    final List<Document> leading = dispatch.leading(declarationContext);
    final Document inner = dispatch.visit(declarationContext.getChild(0));
    return leading.isEmpty() ? inner : concat(concat(leading), inner);
  }

  Document visitInterfaceBodyDeclaration(final JavaParser.InterfaceBodyDeclarationContext declarationContext) {
    if (declarationContext.SEMI() != null && declarationContext.interfaceMemberDeclaration() == null) {
      return Tokens.sourced(declarationContext.SEMI());
    }
    final List<Document> parts = new ArrayList<>(dispatch.leading(declarationContext));
    parts.addAll(Modifiers.format(dispatch, declarationContext.modifier(), Modifiers.IS_ANNOTATION));
    if (declarationContext.interfaceMemberDeclaration() != null) {
      parts.add(dispatch.visit(declarationContext.interfaceMemberDeclaration()));
    }
    parts.add(dispatch.trailing(declarationContext));
    return concat(parts);
  }

  Document visitInterfaceMemberDeclaration(final JavaParser.InterfaceMemberDeclarationContext declarationContext) {
    final List<Document> leading = dispatch.leading(declarationContext);
    final Document inner = dispatch.visit(declarationContext.getChild(0));
    return leading.isEmpty() ? inner : concat(concat(leading), inner);
  }

  /**
   * Formats the declarations following an enum's constants. The first declaration always gets a blank line separating it from the
   * constants; subsequent declarations preserve source layout, so adjacent fields stay adjacent.
   */
  Document visitEnumBodyDeclarations(final JavaParser.EnumBodyDeclarationsContext enumContext) {
    final List<Document> parts = new ArrayList<>();
    parts.add(Tokens.sourced(enumContext.SEMI()));
    parts.add(dispatch.trailingComment(enumContext.getStart().getTokenIndex()));
    final List<JavaParser.ClassBodyDeclarationContext> declarations = enumContext.classBodyDeclaration();
    for (int i = 0; i < declarations.size(); i++) {
      parts.add(hardLine());
      if (i == 0 || dispatch.hasBlankLineBefore(declarations.get(i))) {
        parts.add(hardLine());
      }
      parts.add(dispatch.visit(declarations.get(i)));
    }
    return concat(parts);
  }

  /**
   * Renders a type body's declarations, preserving blank lines between them. Dangling stand-alone comments past the last declaration are
   * rendered at body indent before the closing brace.
   */
  Document visitBodyWithDeclarations(final ParserRuleContext bodyContext, final List<? extends ParserRuleContext> declarations) {
    final TerminalNode lbrace = bodyContext.getToken(JavaParser.LBRACE, 0);
    final TerminalNode rbrace = bodyContext.getToken(JavaParser.RBRACE, 0);
    if (declarations.isEmpty()) {
      return concat(Tokens.sourced(lbrace), Tokens.sourced(rbrace));
    }

    final List<Document> parts = new ArrayList<>();
    parts.add(hardLine());
    if (dispatch.hasBlankLineBefore(declarations.getFirst())) {
      parts.add(hardLine());
    }
    for (int i = 0; i < declarations.size(); i++) {
      if (i > 0) {
        parts.add(hardLine());
        if (dispatch.hasBlankLineBefore(declarations.get(i))) {
          parts.add(hardLine());
        }
      }
      parts.add(dispatch.visit(declarations.get(i)));
    }
    final List<Document> dangling = dispatch.dangling(bodyContext);
    if (!dangling.isEmpty()) {
      parts.add(hardLine());
      parts.addAll(dangling);
    }

    return concat(Tokens.sourced(lbrace), indent(concat(parts)), hardLine(), Tokens.sourced(rbrace));
  }
}
