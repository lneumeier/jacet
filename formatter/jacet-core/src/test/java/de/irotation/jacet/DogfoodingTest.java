package de.irotation.jacet;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Runs the formatter on its own source files. Verifies that formatting does not crash and is idempotent.
 */
class DogfoodingTest {

  private static final Path PROJECT_ROOT = resolveProjectRoot();
  private final JacetFormatter formatter = new JacetFormatter();

  // Walk up from the test class's code source until we find settings.gradle. Resolves regardless of the
  // working directory the test is launched from — the old Path.of("").toAbsolutePath().getParent() could
  // resolve outside the project when Gradle was invoked from the repo root.
  private static Path resolveProjectRoot() {
    final Path start;
    try {
      start = Path.of(DogfoodingTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    } catch (final URISyntaxException e) {
      throw new IllegalStateException("Failed to resolve code source location", e);
    }
    Path current = start;
    while (current != null) {
      if (Files.exists(current.resolve("settings.gradle")) || Files.exists(current.resolve("settings.gradle.kts"))) {
        return current;
      }
      current = current.getParent();
    }
    throw new IllegalStateException("No settings.gradle ancestor found from: " + start);
  }

  static Stream<Path> ownSourceFiles() throws IOException {
    return Files.walk(PROJECT_ROOT)
      .filter(p -> p.toString().endsWith(".java"))
      .filter(p -> !p.toString().contains("build"))
      .sorted();
  }

  // Excludes test resources: the snapshot fixtures include latent-* files that pin known-bad
  // output on purpose, so the strict no-errors test must not walk them.
  static Stream<Path> ownNonResourceSourceFiles() throws IOException {
    return ownSourceFiles().filter(p -> !p.toString().contains("resources"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("ownSourceFiles")
  void formatsWithoutCrashing(final Path file) throws IOException {
    final var source = Files.readString(file);

    final var formatted = assertDoesNotThrow(() -> formatter.format(source), "Formatter crashed on: " + PROJECT_ROOT.relativize(file));

    assertNotNull(formatted, "Formatted result should not be null");
    if (!source.isBlank()) {
      assertFalse(formatted.isBlank(), "Formatted result should not be blank for: " + PROJECT_ROOT.relativize(file));
    }
  }

  // Parse errors and verification failures return the source unchanged, which the crash and
  // idempotence tests cannot distinguish from a successful format — assert them explicitly.
  @ParameterizedTest(name = "{0}")
  @MethodSource("ownNonResourceSourceFiles")
  void formatsOwnSourcesWithoutErrors(final Path file) throws IOException {
    final var source = Files.readString(file);

    final var result = formatter.formatWithResult(source);

    assertEquals(List.of(), result.parseErrors(), "Parse errors in: " + PROJECT_ROOT.relativize(file));
    assertEquals(List.of(), result.verificationErrors(), "Verification errors in: " + PROJECT_ROOT.relativize(file));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("ownSourceFiles")
  void isIdempotent(final Path file) throws IOException {
    final var source = Files.readString(file);

    final var first = formatter.format(source);
    final var second = formatter.format(first);

    assertEquals(first, second, "Formatting is not idempotent for: " + PROJECT_ROOT.relativize(file));
  }
}
