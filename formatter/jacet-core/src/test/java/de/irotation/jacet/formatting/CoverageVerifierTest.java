package de.irotation.jacet.formatting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.junit.jupiter.api.Test;

import de.irotation.jacet.document.Document;
import de.irotation.jacet.document.Text;
import de.irotation.jacet.parser.JavaLexer;

class CoverageVerifierTest {

  private static CommonTokenStream lex(final String source) {
    final JavaLexer lexer = new JavaLexer(CharStreams.fromString(source));
    final CommonTokenStream tokens = new CommonTokenStream(lexer);
    tokens.fill();
    return tokens;
  }

  /** A fully sourced document: every default-channel code token emitted from its own index. */
  private static List<Document> sourcedParts(final TokenStream tokens) {
    final List<Document> parts = new ArrayList<>();
    for (int i = 0; i < tokens.size(); i++) {
      final Token token = tokens.get(i);
      if (token.getChannel() == Token.DEFAULT_CHANNEL && token.getType() != Token.EOF) {
        parts.add(new Text(token.getText(), i, i));
      }
    }
    return parts;
  }

  private static int indexOfText(final TokenStream tokens, final String text) {
    for (int i = 0; i < tokens.size(); i++) {
      if (tokens.get(i).getChannel() == Token.DEFAULT_CHANNEL && tokens.get(i).getText().equals(text)) {
        return i;
      }
    }
    throw new IllegalStateException("no token " + text);
  }

  @Test
  void acceptsFullySourcedDocument() {
    final CommonTokenStream tokens = lex("class A { int x = 1; }");
    final List<String> errors = CoverageVerifier.verify(tokens, Document.concat(sourcedParts(tokens)), new CommentHelper(tokens));
    assertTrue(errors.isEmpty(), () -> "expected no errors but got " + errors);
  }

  @Test
  void detectsLostToken() {
    final CommonTokenStream tokens = lex("class A { int x = 1; }");
    final List<Document> parts = sourcedParts(tokens);
    final int identifierIndex = indexOfText(tokens, "x");
    final List<Document> withGap = new ArrayList<>();
    for (final Document part : parts) {
      if (!(part instanceof final Text t && t.firstToken() == identifierIndex)) {
        withGap.add(part);
      }
    }

    final List<String> errors = CoverageVerifier.verify(tokens, Document.concat(withGap), new CommentHelper(tokens));
    assertEquals(1, errors.size(), errors::toString);
    assertTrue(errors.getFirst().startsWith("code token lost: #" + identifierIndex), errors::getFirst);
    assertTrue(errors.getFirst().contains("\"x\""), errors::getFirst);
  }

  @Test
  void detectsDoubleEmission() {
    final CommonTokenStream tokens = lex("class A { int x = 1; }");
    final List<Document> parts = new ArrayList<>(sourcedParts(tokens));
    final int identifierIndex = indexOfText(tokens, "x");
    parts.add(new Text("x", identifierIndex, identifierIndex));

    final List<String> errors = CoverageVerifier.verify(tokens, Document.concat(parts), new CommentHelper(tokens));
    assertEquals(1, errors.size(), errors::toString);
    assertTrue(errors.getFirst().startsWith("code token emitted twice: #" + identifierIndex), errors::getFirst);
  }

  @Test
  void detectsLostSeparatorComma() {
    final CommonTokenStream tokens = lex("class A { int[] x = { 1, 2 }; }");
    final List<Document> parts = sourcedParts(tokens);
    final int commaIndex = indexOfText(tokens, ",");
    final List<Document> withGap = new ArrayList<>();
    for (final Document part : parts) {
      if (!(part instanceof final Text t && t.firstToken() == commaIndex)) {
        withGap.add(part);
      }
    }

    final List<String> errors = CoverageVerifier.verify(tokens, Document.concat(withGap), new CommentHelper(tokens));
    assertEquals(1, errors.size(), errors::toString);
    assertTrue(errors.getFirst().contains("\",\""), errors::getFirst);
  }

  @Test
  void removedCoversTrailingComma() {
    final CommonTokenStream tokens = lex("class A { int[] x = { 1, 2, }; }");
    final List<Document> parts = new ArrayList<>();
    boolean firstComma = true;
    for (int i = 0; i < tokens.size(); i++) {
      final Token token = tokens.get(i);
      if (token.getChannel() != Token.DEFAULT_CHANNEL || token.getType() == Token.EOF) {
        continue;
      }
      if (",".equals(token.getText()) && !firstComma) {
        parts.add(new Text("", i, i));
      } else {
        if (",".equals(token.getText())) {
          firstComma = false;
        }
        parts.add(new Text(token.getText(), i, i));
      }
    }

    final List<String> errors = CoverageVerifier.verify(tokens, Document.concat(parts), new CommentHelper(tokens));
    assertTrue(errors.isEmpty(), () -> "expected no errors but got " + errors);
  }

  @Test
  void detectsLostComment() {
    final CommonTokenStream tokens = lex("// note\nclass A {}");
    final List<String> errors = CoverageVerifier.verify(tokens, Document.concat(sourcedParts(tokens)), new CommentHelper(tokens));
    assertEquals(1, errors.size(), errors::toString);
    assertTrue(errors.getFirst().startsWith("comment lost:"), errors::getFirst);
    assertTrue(errors.getFirst().contains("// note"), errors::getFirst);
  }

  @Test
  void acceptsPrintedComment() {
    final CommonTokenStream tokens = lex("// note\nclass A {}");
    final CommentHelper commentHelper = new CommentHelper(tokens);
    commentHelper.leadingComments(indexOfText(tokens, "class"));

    final List<String> errors = CoverageVerifier.verify(tokens, Document.concat(sourcedParts(tokens)), commentHelper);
    assertFalse(errors.stream().anyMatch(e -> e.startsWith("comment lost:")), errors::toString);
  }

  @Test
  void coverageUnionAllowsAlternativeBranches() {
    final CommonTokenStream tokens = lex("class A { int x = 1; }");
    final List<Document> parts = sourcedParts(tokens);
    final Document conditional = Document.conditionalLayout(Document.concat(parts), Document.concat(parts));

    final List<String> errors = CoverageVerifier.verify(tokens, conditional, new CommentHelper(tokens));
    assertTrue(errors.isEmpty(), () -> "branch duplication must not read as loss or double-emit: " + errors);
  }
}
