package de.irotation.jacet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ImportSorterTest {

  private static final String EOL = "\n";
  private static final ImportOptions DEFAULT_OPTIONS = ImportOptions.defaults();

  @Test
  void sortsRegularImportsByGroup() {
    final var imports = List.of(
      new ImportSorter.ImportStatement("com.google.common.collect.ImmutableList", false),
      new ImportSorter.ImportStatement("java.util.List", false),
      new ImportSorter.ImportStatement("org.springframework.stereotype.Service", false),
      new ImportSorter.ImportStatement("javax.annotation.Nonnull", false),
      new ImportSorter.ImportStatement("jakarta.persistence.Entity", false)
    );

    final var result = ImportSorter.sort(imports, DEFAULT_OPTIONS, EOL);

    final var lines = result.split("\n");
    assertEquals("import java.util.List;", lines[0]);
    // javax group
    assertTrue(result.indexOf("javax") < result.indexOf("jakarta"));
    assertTrue(result.indexOf("jakarta") < result.indexOf("org"));
    assertTrue(result.indexOf("org") < result.indexOf("com"));
  }

  @Test
  void addsBlankLinesBetweenGroups() {
    final var imports = List.of(
      new ImportSorter.ImportStatement("java.util.List", false),
      new ImportSorter.ImportStatement("org.junit.jupiter.api.Test", false)
    );

    final var result = ImportSorter.sort(imports, DEFAULT_OPTIONS, EOL);

    // Should have a blank line between java and org groups
    assertTrue(result.contains(";\n\nimport org"));
  }

  @Test
  void staticImportsAtBottom() {
    final var options = new ImportOptions(ImportOptions.StaticPosition.BOTTOM, DEFAULT_OPTIONS.groups(), false);

    final var imports = List.of(
      new ImportSorter.ImportStatement("java.util.List", false),
      new ImportSorter.ImportStatement("org.junit.jupiter.api.Assertions.assertEquals", true)
    );

    final var result = ImportSorter.sort(imports, options, EOL);

    assertTrue(result.indexOf("java.util.List") < result.indexOf("static"));
  }

  @Test
  void staticImportsAtTop() {
    final var options = new ImportOptions(ImportOptions.StaticPosition.TOP, DEFAULT_OPTIONS.groups(), false);

    final var imports = List.of(
      new ImportSorter.ImportStatement("java.util.List", false),
      new ImportSorter.ImportStatement("org.junit.jupiter.api.Assertions.assertEquals", true)
    );

    final var result = ImportSorter.sort(imports, options, EOL);

    assertTrue(result.indexOf("static") < result.indexOf("java.util.List"));
  }

  @Test
  void staticImportsMixed() {
    final var options = new ImportOptions(ImportOptions.StaticPosition.MIXED, DEFAULT_OPTIONS.groups(), false);

    final var imports = List.of(
      new ImportSorter.ImportStatement("java.util.List", false),
      new ImportSorter.ImportStatement("java.util.Map", false),
      new ImportSorter.ImportStatement("org.junit.jupiter.api.Assertions.assertEquals", true)
    );

    final var result = ImportSorter.sort(imports, options, EOL);

    // In MIXED mode, all imports are interleaved alphabetically by group
    // Static imports still have "import static" prefix but are not in a separate block
    assertTrue(result.contains("java.util.List"));
    assertTrue(result.contains("org.junit.jupiter.api.Assertions.assertEquals"));
    // java group should come before org group
    assertTrue(result.indexOf("java.util") < result.indexOf("org.junit"));
  }

  @Test
  void alphabeticWithinGroup() {
    final var imports = List.of(
      new ImportSorter.ImportStatement("java.util.Map", false),
      new ImportSorter.ImportStatement("java.io.IOException", false),
      new ImportSorter.ImportStatement("java.util.List", false)
    );

    final var result = ImportSorter.sort(imports, DEFAULT_OPTIONS, EOL);

    assertTrue(result.indexOf("IOException") < result.indexOf("List"));
    assertTrue(result.indexOf("List") < result.indexOf("Map"));
  }

  @Test
  void unknownGroupsGoToOther() {
    final var imports = List.of(
      new ImportSorter.ImportStatement("java.util.List", false),
      new ImportSorter.ImportStatement("io.vavr.control.Try", false)
    );

    final var result = ImportSorter.sort(imports, DEFAULT_OPTIONS, EOL);

    // io.vavr matches no configured group and should come after them
    assertTrue(result.indexOf("java.util.List") < result.indexOf("io.vavr"));
  }

  @Test
  void deGroupSortsBeforeOther() {
    final var imports = List.of(
      new ImportSorter.ImportStatement("io.vavr.control.Try", false),
      new ImportSorter.ImportStatement("de.irotation.myapp.MyClass", false)
    );

    final var result = ImportSorter.sort(imports, DEFAULT_OPTIONS, EOL);

    // unmatched imports trail every configured group
    assertTrue(result.indexOf("de.irotation") < result.indexOf("io.vavr"));
    assertTrue(result.contains(";\n\nimport io.vavr"));
  }

  @Test
  void lombokGroupSortsAfterDeBeforeOther() {
    final var imports = List.of(
      new ImportSorter.ImportStatement("io.vavr.control.Try", false),
      new ImportSorter.ImportStatement("lombok.Getter", false),
      new ImportSorter.ImportStatement("de.irotation.myapp.MyClass", false)
    );

    final var result = ImportSorter.sort(imports, DEFAULT_OPTIONS, EOL);

    // lombok is the last configured group; unmatched imports trail it
    assertTrue(result.indexOf("de.irotation") < result.indexOf("lombok.Getter"));
    assertTrue(result.indexOf("lombok.Getter") < result.indexOf("io.vavr"));
    assertTrue(result.contains("lombok.Getter;\n\nimport io.vavr"));
  }

  @Test
  void wildcardImportsPreserved() {
    final var imports = List.of(
      new ImportSorter.ImportStatement("java.util.*", false)
    );

    final var result = ImportSorter.sort(imports, DEFAULT_OPTIONS, EOL);

    assertTrue(result.contains("import java.util.*;"));
  }

  @Test
  void parseImportsFromSource() {
    final var source = """
                       package com.example;
                       
                       import java.util.List;
                       import static org.junit.jupiter.api.Assertions.assertEquals;
                       import java.util.Map;
                       
                       public class Foo {}
                       """;

    final var imports = ImportSorter.parse(source);

    assertEquals(3, imports.size());
    assertTrue(imports.stream().anyMatch(i -> "java.util.List".equals(i.qualifiedName()) && !i.isStatic()));
    assertTrue(imports.stream().anyMatch(ImportSorter.ImportStatement::isStatic));
  }

  @Test
  void emptyImportsReturnEmptyString() {
    assertEquals("", ImportSorter.sort(List.of(), DEFAULT_OPTIONS, EOL));
  }

  @Test
  void parseStopsAtTypeDeclarationWhenNoImportsPresent() {
    final var source = """
                       package p;
                       
                       public class Foo {
                         String s = \"""
                           import com.evil.Boom;
                           \""";
                       }
                       """;

    final var region = ImportSorter.parseRegion(source);

    assertTrue(region.imports().isEmpty(), "text-block content must not be adopted as an import");
    assertEquals(-1, region.startLine());
  }

  @Test
  void parseIgnoresImportLookalikeInTextBlockAfterRealImports() {
    final var source = """
                       package p;
                       
                       import java.util.List;
                       
                       public class Foo {
                         String s = \"""
                           import com.evil.Boom;
                           \""";
                       }
                       """;

    final var region = ImportSorter.parseRegion(source);

    assertEquals(1, region.imports().size());
    assertEquals("java.util.List", region.imports().getFirst().qualifiedName());
  }

  @Test
  void parseSkipsPackageAnnotationsAndBlockComments() {
    final var source = """
                       /*
                        * Copyright blurb.
                        */
                       @Deprecated
                       package p;
                       
                       import java.util.List;
                       
                       public class Foo {}
                       """;

    final var region = ImportSorter.parseRegion(source);

    assertEquals(1, region.imports().size());
    assertEquals("java.util.List", region.imports().getFirst().qualifiedName());
  }

  @Test
  void customGroupOrder() {
    final var options = new ImportOptions(
      ImportOptions.StaticPosition.BOTTOM,
      List.of(
        new ImportOptions.Group("org"),
        new ImportOptions.Group("com"),
        new ImportOptions.Group("java")
      ),
      false
    );

    final var imports = List.of(
      new ImportSorter.ImportStatement("java.util.List", false),
      new ImportSorter.ImportStatement("org.springframework.stereotype.Service", false),
      new ImportSorter.ImportStatement("com.google.common.collect.ImmutableList", false)
    );

    final var result = ImportSorter.sort(imports, options, EOL);

    // org should come first
    assertTrue(result.indexOf("org") < result.indexOf("com"));
    assertTrue(result.indexOf("com") < result.indexOf("java"));
  }
}
