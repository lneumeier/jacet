package de.irotation.jacet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class UnusedImportScannerTest {

  private static Set<String> unusedNames(final String source) {
    return UnusedImportScanner.findUnused(source)
      .stream()
      .map(ImportSorter.ImportStatement::qualifiedName)
      .collect(Collectors.toSet());
  }

  @Test
  void reportsUnusedTypeImport() {
    final var source = """
      import java.util.List;
      import java.util.Map;

      class Foo {
        List<String> names;
      }
      """;

    assertEquals(Set.of("java.util.Map"), unusedNames(source));
  }

  @Test
  void reportsUnusedStaticImportByMemberName() {
    final var source = """
      import static java.util.Objects.requireNonNull;
      import static java.util.Objects.requireNonNullElse;

      class Foo {
        Object bar(Object o) {
          return requireNonNull(o);
        }
      }
      """;

    final var unused = UnusedImportScanner.findUnused(source);
    assertEquals(Set.of(new ImportSorter.ImportStatement("java.util.Objects.requireNonNullElse", true)), unused);
  }

  @Test
  void neverReportsWildcardImports() {
    final var source = """
      import java.util.*;
      import static java.util.Objects.*;

      class Foo {}
      """;

    assertTrue(UnusedImportScanner.findUnused(source).isEmpty());
  }

  @Test
  void javadocTagReferencesKeepImports() {
    final var source = """
      import java.util.List;
      import java.util.Map;
      import java.util.Set;
      import java.io.IOException;

      /**
       * Uses {@link List} and {@link java.util.Map#of(Set, Set)}.
       *
       * @throws IOException never
       */
      class Foo {}
      """;

    assertTrue(UnusedImportScanner.findUnused(source).isEmpty());
  }

  @Test
  void commentProseDoesNotKeepImports() {
    final var source = """
      import java.util.List;
      import java.util.Map;

      // a List of things
      /* a Map of things */
      class Foo {}
      """;

    assertEquals(Set.of("java.util.List", "java.util.Map"), unusedNames(source));
  }

  @Test
  void javadocProseWithoutTagDoesNotKeepImports() {
    final var source = """
      import java.util.List;

      /** Works on any List instance. */
      class Foo {}
      """;

    assertEquals(Set.of("java.util.List"), unusedNames(source));
  }

  @Test
  void usageInsideFormatterOffRegionKeepsImport() {
    final var source = """
      import java.util.List;

      class Foo {
        // @formatter:off
        List<String> names;
        // @formatter:on
      }
      """;

    assertTrue(UnusedImportScanner.findUnused(source).isEmpty());
  }

  @Test
  void fullyQualifiedUsageKeepsImport() {
    final var source = """
      import java.util.List;

      class Foo {
        java.util.List<String> names;
      }
      """;

    assertTrue(UnusedImportScanner.findUnused(source).isEmpty());
  }

  @Test
  void occurrenceInOtherImportDeclarationIsNotUsage() {
    final var source = """
      import com.example.List;
      import com.example.List.Builder;

      class Foo {
        Builder builder;
      }
      """;

    assertEquals(Set.of("com.example.List"), unusedNames(source));
  }

  @Test
  void importLookalikeInTextBlockIsNotADeclaration() {
    final var source = """
      import java.util.List;

      class Foo {
        String s = \"""
          import com.example.Phantom;
          \""";
        List<String> names;
      }
      """;

    assertTrue(UnusedImportScanner.findUnused(source).isEmpty());
  }

  @Test
  void contextualKeywordMemberUsageKeepsStaticImport() {
    final var source = """
      import static com.example.Records.record;

      class Foo {
        Object bar() {
          return record();
        }
      }
      """;

    assertTrue(UnusedImportScanner.findUnused(source).isEmpty());
  }

  @Test
  void moduleDirectiveReferenceKeepsImport() {
    final var source = """
      import com.example.impl.ServiceImpl;
      import com.example.api.UnusedThing;

      module com.example {
        provides com.example.api.Service with ServiceImpl;
      }
      """;

    assertEquals(Set.of("com.example.api.UnusedThing"), unusedNames(source));
  }

  @Test
  void producesRecordsEqualToImportSorterParseRegion() {
    final var source = """
      package p;

      import java.util.List;
      import java.util.Map.Entry;
      import static java.util.Objects.requireNonNull;

      class Foo {}
      """;

    assertEquals(Set.copyOf(ImportSorter.parseRegion(source).imports()), UnusedImportScanner.findUnused(source));
  }

  @Test
  void annotationUsageKeepsImport() {
    final var source = """
      import java.lang.annotation.Documented;

      @Documented
      @interface Foo {}
      """;

    assertTrue(UnusedImportScanner.findUnused(source).isEmpty());
  }
}
