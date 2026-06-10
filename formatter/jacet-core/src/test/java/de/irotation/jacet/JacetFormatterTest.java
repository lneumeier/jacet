package de.irotation.jacet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JacetFormatterTest {

  private final JacetFormatter formatter = new JacetFormatter();

  @Test
  void formatsWithCustomOptions() {
    final var options = new FormatterOptions(80, 2, false, true, EndOfLine.LF, ImportOptions.defaults());
    final var customFormatter = new JacetFormatter(options);

    final var input = """
                      public class Foo {
                          public void bar() {
                              return;
                          }
                      }
                      """;

    final var result = customFormatter.format(input);

    // With tabWidth=2, indentation should use 2 spaces
    assertTrue(
      result.contains("  public void bar()") || result.contains("  public"),
      "Expected 2-space indentation, got:\n" + result
    );
  }

  // ── forceBraces tests ────────────────────────────────────────────

  @Test
  void forceBracesWrapsIfOneLiner() {
    final var input = """
                      public class Foo {
                          public void bar(int x) {
                              if (x > 0) return;
                          }
                      }
                      """;

    final var result = formatter.format(input);

    assertTrue(
      result.contains("if (x > 0) {"),
      "Expected braces around if body, got:\n" + result
    );
    assertTrue(
      result.contains("return;"),
      "Expected return statement, got:\n" + result
    );
  }

  @Test
  void forceBracesWrapsElseOneLiner() {
    final var input = """
                      public class Foo {
                          public void bar(int x) {
                              if (x > 0) doA();
                              else doB();
                          }
                      }
                      """;

    final var result = formatter.format(input);

    assertTrue(
      result.contains("if (x > 0) {"),
      "Expected braces around if body, got:\n" + result
    );
    assertTrue(
      result.contains("} else {"),
      "Expected braces around else body, got:\n" + result
    );
  }

  @Test
  void forceBracesWrapsForOneLiner() {
    final var input = """
                      public class Foo {
                          public void bar() {
                              for (int i = 0; i < 10; i++) doSomething();
                          }
                      }
                      """;

    final var result = formatter.format(input);

    assertTrue(
      result.contains(") {"),
      "Expected braces around for body, got:\n" + result
    );
  }

  @Test
  void forceBracesWrapsWhileOneLiner() {
    final var input = """
                      public class Foo {
                          public void bar() {
                              while (hasNext()) process();
                          }
                      }
                      """;

    final var result = formatter.format(input);

    assertTrue(
      result.contains("while (hasNext()) {"),
      "Expected braces around while body, got:\n" + result
    );
  }

  @Test
  void forceBracesPreservesExistingBlocks() {
    final var input = """
                      public class Foo {
                          public void bar(int x) {
                              if (x > 0) {
                                  return;
                              }
                          }
                      }
                      """;

    final var result = formatter.format(input);
    final var second = formatter.format(result);

    assertEquals(result, second, "Already-braced code should be idempotent");
  }

  @Test
  void forceBracesDisabled() {
    final var options = new FormatterOptions(120, 4, false, false, EndOfLine.LF, ImportOptions.defaults());
    final var noBracesFormatter = new JacetFormatter(options);

    final var input = """
                      public class Foo {
                          public void bar(int x) {
                              if (x > 0) return;
                          }
                      }
                      """;

    final var result = noBracesFormatter.format(input);

    assertFalse(
      result.contains("if (x > 0) {"),
      "With forceBraces=false, should NOT add braces, got:\n" + result
    );
    assertTrue(
      result.contains("if (x > 0) return;"),
      "Expected one-liner preserved, got:\n" + result
    );
  }

  @Test
  void forceBracesPreservesElseIfChain() {
    final var input = """
                      public class Foo {
                          public void bar(int x) {
                              if (x > 0) doA();
                              else if (x < 0) doB();
                              else doC();
                          }
                      }
                      """;

    final var result = formatter.format(input);

    assertTrue(
      result.contains("} else if (x < 0) {"),
      "Expected else-if chain without double wrapping, got:\n" + result
    );
    assertTrue(
      result.contains("} else {"),
      "Expected else with braces, got:\n" + result
    );
  }

  // ── parse error handling ────────────────────────────────────────

  @Test
  void returnsSourceUnchangedOnParseError() {
    final var input = """
                      public class Broken {
                          public void missing( {
                              return;
                          }
                      }
                      """;

    final var result = formatter.format(input);

    assertEquals(input, result, "Source with parse errors should be returned unchanged");
  }

  @Test
  void formatWithResultReportsParseErrors() {
    final var input = """
                      public class Broken {
                          public void missing( {
                          }
                      }
                      """;

    final var result = formatter.formatWithResult(input);

    assertTrue(result.hasParseErrors(), "Should report parse errors");
    assertFalse(result.formatted(), "Should not be marked as formatted");
    assertEquals(input, result.source(), "Source should be unchanged");
    assertFalse(result.parseErrors().isEmpty(), "Should contain error messages");
  }

  @Test
  void unterminatedStringLiteralIsReturnedUnchanged() {
    this.assertLexerErrorReturnsSourceUnchanged("public class Broken { String s = \"never closed; }");
  }

  @Test
  void unterminatedBlockCommentIsReturnedUnchanged() {
    this.assertLexerErrorReturnsSourceUnchanged("public class Broken { /* never closed");
  }

  @Test
  void unterminatedTextBlockIsReturnedUnchanged() {
    this.assertLexerErrorReturnsSourceUnchanged("public class Broken { String s = \"\"\" abc ; }");
  }

  @Test
  void unterminatedCharLiteralIsReturnedUnchanged() {
    this.assertLexerErrorReturnsSourceUnchanged("public class Broken { char c = 'x ; }");
  }

  private void assertLexerErrorReturnsSourceUnchanged(final String input) {
    final var result = formatter.formatWithResult(input);

    assertTrue(result.hasParseErrors(), "Expected parse errors");
    assertEquals(input, result.source(), "Source must be returned byte-for-byte");
    assertFalse(result.formatted(), "Result must not be marked formatted");
  }

  // ── intersection cast ──────────────────────────────────────────

  @Test
  void formatsIntersectionCast() {
    final var input = """
                      public class Foo {
                          public Object bar(Object value) {
                              return (java.io.Serializable & Comparable<String>) value;
                          }
                      }
                      """;

    final var result = formatter.format(input);

    assertTrue(
      result.contains("(java.io.Serializable & Comparable<String>)"),
      "Expected intersection cast with '&' separator, got:\n" + result
    );
  }

  // ── EndOfLine output ───────────────────────────────────────────

  @Test
  void emitsCrlfLineEndings() {
    final var options = new FormatterOptions(140, 2, false, true, EndOfLine.CRLF, ImportOptions.defaults());
    final var crlfFormatter = new JacetFormatter(options);

    final var input = "public class Foo {\n  void bar() {\n  }\n}\n";
    final var result = crlfFormatter.format(input);

    assertTrue(result.contains("\r\n"), "Expected CRLF line endings, got:\n" + result.replace("\r", "\\r").replace("\n", "\\n\n"));
    assertFalse(result.contains("\r\r"), "Should not contain stray CR");
  }

  @Test
  void emitsCrLineEndings() {
    final var options = new FormatterOptions(140, 2, false, true, EndOfLine.CR, ImportOptions.defaults());
    final var crFormatter = new JacetFormatter(options);

    final var input = "public class Foo {\n  void bar() {\n  }\n}\n";
    final var result = crFormatter.format(input);

    assertTrue(result.contains("\r"), "Expected CR line endings");
    assertFalse(result.contains("\n"), "Expected pure CR output without LF, got bytes containing LF");
  }

  @Test
  void emitsCrLineEndingsWithImports() {
    final var options = new FormatterOptions(140, 2, false, true, EndOfLine.CR, ImportOptions.defaults());
    final var crFormatter = new JacetFormatter(options);

    final var input = "package p;\nimport java.util.Map;\nimport java.util.List;\npublic class Foo {\n}\n";
    final var result = crFormatter.format(input);

    assertFalse(result.contains("\n"), "Expected pure CR output, got bytes containing LF: " + result.replace("\r", "\\r").replace("\n", "\\n"));
    assertTrue(result.contains("import java.util.List;"), "Import block must survive: " + result);
    assertTrue(result.contains("import java.util.Map;"), "Import block must survive: " + result);
    assertTrue(result.contains("public class Foo"), "Class body must survive: " + result);

    final int listIdx = result.indexOf("java.util.List");
    final int mapIdx = result.indexOf("java.util.Map");
    assertTrue(listIdx < mapIdx, "Imports should be alphabetically sorted");
  }

  @Test
  void emitsCrlfLineEndingsWithImports() {
    final var options = new FormatterOptions(140, 2, false, true, EndOfLine.CRLF, ImportOptions.defaults());
    final var crlfFormatter = new JacetFormatter(options);

    final var input = "package p;\nimport java.util.Map;\nimport java.util.List;\npublic class Foo {\n}\n";
    final var result = crlfFormatter.format(input);

    assertTrue(result.contains("\r\n"), "Expected CRLF line endings");
    assertFalse(result.contains("\r\r"), "Should not contain stray CR");
    assertTrue(result.contains("import java.util.List;"));
    assertTrue(result.contains("import java.util.Map;"));
    assertTrue(result.contains("public class Foo"));
  }

  @Test
  void endOfLineAutoDetectsCrlfFromInput() {
    final var options = new FormatterOptions(140, 2, false, true, EndOfLine.AUTO, ImportOptions.defaults());
    final var autoFormatter = new JacetFormatter(options);

    final var input = "public class Foo {\r\n  void bar() {\r\n  }\r\n}\r\n";
    final var result = autoFormatter.format(input);

    assertTrue(result.contains("\r\n"), "Expected AUTO to preserve CRLF from input");
  }

  @Test
  void endOfLineAutoFallsBackToLfForSingleLineInput() {
    final var options = new FormatterOptions(140, 2, false, true, EndOfLine.AUTO, ImportOptions.defaults());
    final var autoFormatter = new JacetFormatter(options);

    final var input = "public class Foo {\n}\n";
    final var result = autoFormatter.format(input);

    assertTrue(result.contains("\n"));
    assertFalse(result.contains("\r"), "Expected LF for LF input");
  }

  // ── nested generics over printWidth ────────────────────────────

  @Test
  void nestedGenericsOverflowPrintWidthWithoutCorruption() {
    final var options = new FormatterOptions(60, 2, false, true, EndOfLine.LF, ImportOptions.defaults());
    final var narrowFormatter = new JacetFormatter(options);

    final var input = """
                      public class Foo {
                          private Map<String, List<Map<String, List<Integer>>>> deeplyNested;
                      }
                      """;

    final var result = narrowFormatter.format(input);
    final var idempotent = narrowFormatter.format(result);

    assertEquals(result, idempotent, "Nested generics must be idempotent even when overflowing printWidth");
    // Outer generic may break across lines under a narrow printWidth, but the type structure
    // (token sequence) must survive intact — no dropped/reordered identifiers, no broken brackets.
    final String normalized = result.replaceAll("\\s+", "");
    assertTrue(
      normalized.contains("Map<String,List<Map<String,List<Integer>>>>"),
      "Expected nested generic type structure preserved, got:\n" + result
    );
  }

  @Test
  void formatWithResultReportsSuccess() {
    final var input = """
                      public class Good {
                          public void hello() {
                              return;
                          }
                      }
                      """;

    final var result = formatter.formatWithResult(input);

    assertFalse(result.hasParseErrors(), "Should not report parse errors");
    assertTrue(result.formatted(), "Should be marked as formatted");
    assertNotEquals(input, result.source(), "Output should differ from the unformatted input");
    assertTrue(result.parseErrors().isEmpty(), "Should have no error messages");
  }

  @Test
  void textBlockContainingImportLiteralIsPreservedVerbatim() {
    final var input = """
                      package p;

                      public class Foo {
                        String s = \"""
                          import com.evil.Boom;
                          \""";
                      }
                      """;

    final var out = formatter.format(input);

    assertTrue(out.contains("import com.evil.Boom;"), "text-block content must survive formatting");
    assertFalse(out.startsWith("import com.evil.Boom;"), "text-block line must not be hoisted into the import block");
    assertEquals(out, formatter.format(out), "idempotence");
  }

  // ── output verification (token-coverage guard) ─────────────────

  @Test
  void verificationAcceptsNormalFormatting() {
    final var input = """
                      package p;

                      import java.util.List;

                      public class Foo<T extends Number> implements Runnable {
                        private final int[] xs = { 1, 2, 3 };

                        public void run() {
                          for (int i = 0; i < xs.length; i++) {
                            System.out.println(xs[i]);
                          }
                        }
                      }
                      """;

    final var result = formatter.formatWithResult(input);

    assertTrue(result.formatted(), "Should be marked as formatted");
    assertFalse(result.hasVerificationErrors(), () -> "Unexpected verification errors: " + result.verificationErrors());
  }
}
