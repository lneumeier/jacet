package de.irotation.jacet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class SnapshotTest {

  private static final Path SNAPSHOT_DIR = Path.of("src/test/resources/snapshots");
  private static final Path INPUT_DIR = SNAPSHOT_DIR.resolve("input");
  private static final Path EXPECTED_DIR = SNAPSHOT_DIR.resolve("expected");

  private final JacetFormatter formatter = new JacetFormatter();

  static Stream<String> snapshotFiles() throws IOException {
    return Files.list(INPUT_DIR)
      .filter(p -> p.toString().endsWith(".java"))
      .map(p -> p.getFileName().toString())
      .sorted();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("snapshotFiles")
  void matchesExpectedOutput(final String fileName) throws IOException {
    final var input = Files.readString(INPUT_DIR.resolve(fileName));
    final var actual = formatter.format(input);

    final var expectedFile = EXPECTED_DIR.resolve(fileName);
    if (!Files.exists(expectedFile)) {
      Files.writeString(expectedFile, actual);
      fail("Expected file did not exist — generated at " + expectedFile + "\nReview and re-run. Formatted output:\n" + actual);
    }

    final var expected = Files.readString(expectedFile);
    assertEquals(expected, actual, "Snapshot mismatch for " + fileName + "\nActual:\n" + actual);
  }

  @ParameterizedTest(name = "idempotent: {0}")
  @MethodSource("snapshotFiles")
  void isIdempotent(final String fileName) throws IOException {
    final var input = Files.readString(INPUT_DIR.resolve(fileName));
    final var first = formatter.format(input);
    final var second = formatter.format(first);

    assertEquals(first, second, "Formatting is not idempotent for " + fileName);
  }
}
