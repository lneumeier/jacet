package de.irotation.jacet;

import static de.irotation.jacet.document.Document.concat;
import static de.irotation.jacet.document.Document.empty;
import static de.irotation.jacet.document.Document.group;
import static de.irotation.jacet.document.Document.hardLine;
import static de.irotation.jacet.document.Document.ifBreak;
import static de.irotation.jacet.document.Document.indent;
import static de.irotation.jacet.document.Document.join;
import static de.irotation.jacet.document.Document.line;
import static de.irotation.jacet.document.Document.lineSuffix;
import static de.irotation.jacet.document.Document.softLine;
import static de.irotation.jacet.document.Document.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class DocumentPrinterTest {

  private final DocumentPrinter printer = new DocumentPrinter(40, 4, false, "\n");

  @Test
  void textIsPrintedAsIs() {
    assertEquals("hello", printer.print(text("hello")));
  }

  @Test
  void concatJoinsDocuments() {
    final var doc = concat(text("hello"), text(" "), text("world"));
    assertEquals("hello world", printer.print(doc));
  }

  @Test
  void groupFitsOnOneLine() {
    final var doc = group(concat(text("a"), line(), text("b")));
    assertEquals("a b", printer.print(doc));
  }

  @Test
  void groupBreaksWhenTooWide() {
    // printWidth is 40, this group is wider
    final var doc = group(concat(text("aaaaaaaaaaaaaaaa"), line(), text("bbbbbbbbbbbbbbbb"), line(), text("cccccccccccccccc")));
    assertEquals("aaaaaaaaaaaaaaaa\nbbbbbbbbbbbbbbbb\ncccccccccccccccc", printer.print(doc));
  }

  @Test
  void indentAddsSpaces() {
    // Standard Prettier pattern: line break inside indent
    final var doc = group(concat(text("if (true)"), indent(concat(line(), text("aaaaaaaaaaaaaaaa"), line(), text("bbbbbbbbbbbbbbbb")))));
    // Should break because too wide for one line (9+1+16+1+16 = 43 > 40)
    final var result = printer.print(doc);
    assertTrue(result.contains("    aaaaaaaaaaaaaaaa"), "Expected indented content, got: " + result);
    assertTrue(result.contains("    bbbbbbbbbbbbbbbb"), "Expected indented content, got: " + result);
  }

  @Test
  void softLineDisappearsInFlatMode() {
    final var doc = group(concat(text("a"), softLine(), text("b")));
    assertEquals("ab", printer.print(doc));
  }

  @Test
  void softLineBreaksInBreakMode() {
    final var doc = group(concat(text("aaaaaaaaaaaaaaaaaaaaaa"), softLine(), text("bbbbbbbbbbbbbbbbbbbb")));
    assertEquals("aaaaaaaaaaaaaaaaaaaaaa\nbbbbbbbbbbbbbbbbbbbb", printer.print(doc));
  }

  @Test
  void hardLineAlwaysBreaks() {
    final var doc = concat(text("a"), hardLine(), text("b"));
    assertEquals("a\nb", printer.print(doc));
  }

  @Test
  void ifBreakUsesBreakContentsInBreakMode() {
    final var doc = group(
      concat(text("aaaaaaaaaaaaaaaaaaaaaa"), line(), ifBreak(text("[BREAK]"), text("[FLAT]")), line(), text("cccccccccccccccccccc"))
    );
    final var result = printer.print(doc);
    assertTrue(result.contains("[BREAK]"));
  }

  @Test
  void ifBreakUsesFlatContentsInFlatMode() {
    final var doc = group(concat(text("a"), line(), ifBreak(text("[BREAK]"), text("[FLAT]"))));
    assertEquals("a [FLAT]", printer.print(doc));
  }

  @Test
  void joinWithSeparator() {
    final var doc = join(concat(text(","), line()), List.of(text("a"), text("b"), text("c")));
    final var result = printer.print(group(doc));
    assertEquals("a, b, c", result);
  }

  @Test
  void nestedGroupsIndependentlyDecide() {
    // Outer group breaks, inner group stays flat
    final var inner = group(concat(text("x"), line(), text("y")));
    final var doc = group(concat(text("aaaaaaaaaaaaaaaaaaaaa"), line(), inner, line(), text("bbbbbbbbbbbbbbbbbbb")));
    final var result = printer.print(doc);
    assertTrue(result.contains("x y")); // inner group stays flat
    assertTrue(result.contains("\n")); // outer breaks
  }

  @Test
  void lineSuffixPrintsAtEndOfLine() {
    final var doc = concat(text("code"), lineSuffix(text(" // comment")), hardLine(), text("next"));
    final var result = printer.print(doc);
    assertEquals("code // comment\nnext", result);
  }

  @Test
  void usesTabsWhenConfigured() {
    final var tabPrinter = new DocumentPrinter(40, 4, true, "\n");
    final var doc = group(concat(text("aaaaaaaaaaaaaaaaaaaaaaaaa"), indent(concat(line(), text("indented")))));
    // "aaaaaaaaaaaaaaaaaaaaaaaaa indented" = 24+1+8 = 33 < 40, so need to make it not fit
    // Actually 24 + " " + "indented" = 33 < 40, still fits. Make wider:
    final var wideDoc = group(concat(text("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"), indent(concat(line(), text("indented")))));
    final var result = tabPrinter.print(wideDoc);
    assertTrue(result.contains("\t"), "Expected tab indentation, got: " + result);
  }

  @Test
  void respectsCrlfLineEnding() {
    final var crlfPrinter = new DocumentPrinter(40, 4, false, "\r\n");
    final var doc = concat(text("a"), hardLine(), text("b"));
    assertEquals("a\r\nb", crlfPrinter.print(doc));
  }

  @Test
  void emptyDocProducesEmptyOutput() {
    assertEquals("", printer.print(empty()));
  }

  @Test
  void methodSignatureExample() {
    // Realistic example: method signature formatting
    final var params = join(concat(text(","), line()), List.of(text("String name"), text("int age"), text("boolean active")));
    final var doc = group(concat(text("public void doSomething"), text("("), indent(concat(softLine(), params)), softLine(), text(")")));
    // Should fit on one line with width 40? Let's check
    final var result = printer.print(doc);
    // "public void doSomething(String name, int age, boolean active)" = 62 chars > 40
    // So it should break
    assertTrue(result.contains("\n"));
    assertTrue(result.contains("    String name"));
  }
}
