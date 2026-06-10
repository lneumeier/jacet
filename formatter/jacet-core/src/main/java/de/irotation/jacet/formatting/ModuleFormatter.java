package de.irotation.jacet.formatting;

import static de.irotation.jacet.document.Document.concat;
import static de.irotation.jacet.document.Document.hardLine;
import static de.irotation.jacet.document.Document.indent;
import static de.irotation.jacet.document.Document.join;
import static de.irotation.jacet.document.Document.text;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.tree.TerminalNode;

import de.irotation.jacet.document.Document;
import de.irotation.jacet.parser.JavaParser;

/**
 * Formats Java module declarations (module-info.java).
 */
final class ModuleFormatter implements HandlerProvider {

  private final FormattingDispatch dispatch;

  ModuleFormatter(final FormattingDispatch dispatch) {
    this.dispatch = dispatch;
  }

  @Override
  public void registerHandlers(final HandlerRegistry registry) {
    registry.register(JavaParser.ModularCompilationUnitContext.class, this::visitModularCompilationUnit);
    registry.register(JavaParser.ModuleDeclarationContext.class, this::visitModuleDeclaration);
    registry.register(JavaParser.ModuleDirectiveContext.class, this::visitModuleDirective);
    registry.register(JavaParser.RequiresModifierContext.class, this::visitRequiresModifier);
    registry.register(JavaParser.PackageNameContext.class, this::visitPackageName);
  }

  Document visitModularCompilationUnit(final JavaParser.ModularCompilationUnitContext moduleContext) {
    final List<Document> parts = new ArrayList<>();
    for (final JavaParser.ImportDeclarationContext imp : moduleContext.importDeclaration()) {
      parts.add(dispatch.visit(imp));
      parts.add(hardLine());
    }
    if (!moduleContext.importDeclaration().isEmpty()) {
      parts.add(hardLine());
    }
    parts.add(this.visitModuleDeclaration(moduleContext.moduleDeclaration()));
    parts.add(hardLine());
    return concat(parts);
  }

  Document visitModuleDeclaration(final JavaParser.ModuleDeclarationContext moduleContext) {
    final List<Document> parts = new ArrayList<>();
    for (final JavaParser.AnnotationContext annotation : moduleContext.annotation()) {
      parts.add(dispatch.visit(annotation));
      parts.add(hardLine());
    }
    if (moduleContext.OPEN() != null) {
      parts.add(Tokens.sourced(moduleContext.OPEN()));
      parts.add(text(" "));
    }
    parts.add(Tokens.sourced(moduleContext.MODULE()));
    parts.add(text(" "));
    parts.add(dispatch.visit(moduleContext.qualifiedName()));
    parts.add(text(" "));
    parts.add(Tokens.sourced(moduleContext.LBRACE()));

    if (!moduleContext.moduleDirective().isEmpty()) {
      final List<Document> directives = new ArrayList<>();
      for (final JavaParser.ModuleDirectiveContext directive : moduleContext.moduleDirective()) {
        directives.add(this.visitModuleDirective(directive));
      }
      parts.add(indent(concat(hardLine(), join(hardLine(), directives))));
    }

    parts.add(hardLine());
    parts.add(Tokens.sourced(moduleContext.RBRACE()));
    return concat(parts);
  }

  Document visitModuleDirective(final JavaParser.ModuleDirectiveContext directiveContext) {
    final List<Document> parts = new ArrayList<>();
    if (directiveContext.REQUIRES() != null) {
      parts.add(Tokens.sourced(directiveContext.REQUIRES()));
      parts.add(text(" "));
      for (final JavaParser.RequiresModifierContext mod : directiveContext.requiresModifier()) {
        parts.add(this.visitRequiresModifier(mod));
        parts.add(text(" "));
      }
      parts.add(dispatch.visit(directiveContext.qualifiedName(0)));
    } else if (directiveContext.EXPORTS() != null) {
      parts.add(Tokens.sourced(directiveContext.EXPORTS()));
      parts.add(text(" "));
      parts.add(dispatch.visit(directiveContext.qualifiedName(0)));
      if (directiveContext.TO() != null) {
        parts.add(text(" "));
        parts.add(Tokens.sourced(directiveContext.TO()));
        parts.add(text(" "));
        parts.add(this.joinQualifiedNames(directiveContext.qualifiedName(), 1, directiveContext.COMMA()));
      }
    } else if (directiveContext.OPENS() != null) {
      parts.add(Tokens.sourced(directiveContext.OPENS()));
      parts.add(text(" "));
      parts.add(dispatch.visit(directiveContext.qualifiedName(0)));
      if (directiveContext.TO() != null) {
        parts.add(text(" "));
        parts.add(Tokens.sourced(directiveContext.TO()));
        parts.add(text(" "));
        parts.add(this.joinQualifiedNames(directiveContext.qualifiedName(), 1, directiveContext.COMMA()));
      }
    } else if (directiveContext.USES() != null) {
      parts.add(Tokens.sourced(directiveContext.USES()));
      parts.add(text(" "));
      parts.add(dispatch.visit(directiveContext.qualifiedName(0)));
    } else if (directiveContext.PROVIDES() != null) {
      parts.add(Tokens.sourced(directiveContext.PROVIDES()));
      parts.add(text(" "));
      parts.add(dispatch.visit(directiveContext.qualifiedName(0)));
      parts.add(text(" "));
      parts.add(Tokens.sourced(directiveContext.WITH()));
      parts.add(text(" "));
      parts.add(this.joinQualifiedNames(directiveContext.qualifiedName(), 1, directiveContext.COMMA()));
    }
    parts.add(Tokens.sourced(directiveContext.SEMI()));
    return concat(parts);
  }

  Document visitRequiresModifier(final JavaParser.RequiresModifierContext modifierContext) {
    return Tokens.sourced(modifierContext);
  }

  Document visitPackageName(final JavaParser.PackageNameContext packageContext) {
    return Tokens.sourced(packageContext);
  }

  private Document joinQualifiedNames(
    final List<JavaParser.QualifiedNameContext> names,
    final int fromIndex,
    final List<TerminalNode> commas
  ) {
    final List<Document> docs = new ArrayList<>();
    for (int i = fromIndex; i < names.size(); i++) {
      docs.add(dispatch.visit(names.get(i)));
    }
    return Tokens.joinSourced(docs, commas, text(" "));
  }
}
