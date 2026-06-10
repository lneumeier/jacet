package de.irotation.jacet.formatting;

import static de.irotation.jacet.document.Document.concat;
import static de.irotation.jacet.document.Document.hardLine;
import static de.irotation.jacet.document.Document.text;

import java.util.ArrayList;
import java.util.List;

import de.irotation.jacet.document.Document;
import de.irotation.jacet.parser.JavaParser;

/**
 * Formats compilation unit structure: package declaration, imports, and top-level type declarations.
 */
final class CompilationUnitFormatter implements HandlerProvider {

  private final FormattingDispatch dispatch;

  CompilationUnitFormatter(final FormattingDispatch dispatch) {
    this.dispatch = dispatch;
  }

  Document visitCompilationUnit(final JavaParser.CompilationUnitContext compilationUnitContext) {
    if (compilationUnitContext.modularCompilationUnit() != null) {
      return dispatch.visit(compilationUnitContext.modularCompilationUnit());
    }

    final List<Document> parts = new ArrayList<>();

    final boolean hasImports = !compilationUnitContext.importDeclaration().isEmpty();
    final boolean hasTypes = !compilationUnitContext.typeDeclaration().isEmpty();

    if (compilationUnitContext.packageDeclaration() != null) {
      parts.add(dispatch.visit(compilationUnitContext.packageDeclaration()));
      parts.add(hardLine());
      if (hasImports || hasTypes) {
        parts.add(hardLine());
      }
    }

    if (hasImports) {
      for (final JavaParser.ImportDeclarationContext imp : compilationUnitContext.importDeclaration()) {
        parts.add(dispatch.visit(imp));
        parts.add(hardLine());
      }
      if (hasTypes) {
        parts.add(hardLine());
      }
    }

    for (int i = 0; i < compilationUnitContext.typeDeclaration().size(); i++) {
      if (i > 0) {
        parts.add(hardLine());
        parts.add(hardLine());
      }
      parts.add(dispatch.visit(compilationUnitContext.typeDeclaration(i)));
    }

    if (hasTypes) {
      parts.add(hardLine());
    }
    return concat(parts);
  }

  Document visitPackageDeclaration(final JavaParser.PackageDeclarationContext packageContext) {
    final List<Document> parts = new ArrayList<>(dispatch.leading(packageContext));
    for (final JavaParser.AnnotationContext annotation : packageContext.annotation()) {
      parts.add(dispatch.visit(annotation));
      parts.add(hardLine());
    }
    parts.add(Tokens.sourced(packageContext.PACKAGE()));
    parts.add(text(" "));
    parts.add(dispatch.visit(packageContext.qualifiedName()));
    parts.add(Tokens.sourced(packageContext.SEMI()));
    return concat(parts);
  }

  @Override
  public void registerHandlers(final HandlerRegistry registry) {
    registry.register(JavaParser.CompilationUnitContext.class, this::visitCompilationUnit);
    registry.register(JavaParser.PackageDeclarationContext.class, this::visitPackageDeclaration);
    registry.register(JavaParser.ImportDeclarationContext.class, this::visitImportDeclaration);
  }

  /**
   * Formats an import declaration, prepending any leading comments. A comment preceding an import — e.g.
   * a file header before the first import when there is no package — attaches here as a leading comment;
   * rendering it preserves it, since the import sorter only rewrites the import lines themselves and
   * leaves preceding lines untouched.
   */
  Document visitImportDeclaration(final JavaParser.ImportDeclarationContext importContext) {
    final List<Document> importParts = new ArrayList<>();
    importParts.add(Tokens.sourced(importContext.IMPORT()));
    importParts.add(text(" "));
    if (importContext.STATIC() != null) {
      importParts.add(Tokens.sourced(importContext.STATIC()));
      importParts.add(text(" "));
    }
    importParts.add(Tokens.sourced(importContext.qualifiedName()));
    if (importContext.MUL() != null) {
      importParts.add(Tokens.sourced(importContext.DOT()));
      importParts.add(Tokens.sourced(importContext.MUL()));
    }
    importParts.add(Tokens.sourced(importContext.SEMI()));

    final List<Document> leading = dispatch.leading(importContext);
    if (leading.isEmpty()) {
      return concat(importParts);
    }
    final List<Document> parts = new ArrayList<>(leading);
    parts.add(concat(importParts));
    return concat(parts);
  }
}
