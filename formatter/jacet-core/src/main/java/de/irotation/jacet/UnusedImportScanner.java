package de.irotation.jacet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import de.irotation.jacet.parser.JavaLexer;

/**
 * Detects import statements whose imported name is never referenced, using token-level heuristics in the spirit of
 * google-java-format's {@code RemoveUnusedImports} — no symbol resolution.
 *
 * <p>An import counts as <em>used</em> when its simple name (the last dotted segment; for static imports the member name) occurs as an
 * identifier-like token anywhere outside import declarations, or inside a javadoc reference tag ({@code {@link}}, {@code {@linkplain}},
 * {@code {@value}}, {@code @see}, {@code @throws}, {@code @exception}). Plain {@code //} and {@code /*} comment prose never keeps an
 * import alive. Wildcard imports ({@code .*}, static or not) are never reported.
 *
 * <p>All heuristic error goes in the safe direction — in doubt an import is kept, never removed: fully-qualified-only usage still
 * produces the simple name as a token, overloaded static members match by name, and identifier-like contextual keyword tokens
 * ({@code record}, {@code when}, {@code yield}, …) count as usage exactly like the parser's {@code identifier} rule accepts them.
 * Shadowing, same-simple-name collisions, and names appearing only in string literals are out of scope for the same reason.
 *
 * <p>The scanner lexes the <em>original</em> source, not the working source preprocessed by
 * {@link de.irotation.jacet.formatting.FormatterDirectives}: directive-protected off-regions are replaced by placeholders before parsing,
 * so their identifiers are invisible to the parse-time token stream — lexing the original source is the only way a usage inside such a
 * region can keep its import.
 */
final class UnusedImportScanner {

  /** Token types the parser's {@code identifier} rule accepts: {@code IDENTIFIER} plus the contextual keywords. */
  private static final Set<Integer> IDENTIFIER_LIKE = Set.of(
    JavaLexer.IDENTIFIER,
    JavaLexer.MODULE,
    JavaLexer.OPEN,
    JavaLexer.REQUIRES,
    JavaLexer.EXPORTS,
    JavaLexer.OPENS,
    JavaLexer.TO,
    JavaLexer.USES,
    JavaLexer.PROVIDES,
    JavaLexer.WHEN,
    JavaLexer.WITH,
    JavaLexer.TRANSITIVE,
    JavaLexer.YIELD,
    JavaLexer.SEALED,
    JavaLexer.PERMITS,
    JavaLexer.RECORD,
    JavaLexer.VAR
  );

  /**
   * Matches the reference portion of javadoc tags: the braces-enclosed argument of inline {@code {@link}}/{@code {@linkplain}}/
   * {@code {@value}}, or the rest of the line after block tags {@code @see}/{@code @throws}/{@code @exception}. Every identifier word
   * inside a capture counts as usage, so qualified references ({@code pkg.Class#member(ArgType)}) keep all their named types. Link labels
   * and prose after {@code @see} over-approximate, which only ever keeps imports.
   */
  private static final Pattern JAVADOC_REFERENCE = Pattern.compile(
    "\\{@(?:link|linkplain|value)\\s+([^}]*)}|@(?:see|throws|exception)\\s+([^\\r\\n{@]*)"
  );

  private static final Pattern IDENTIFIER_WORD = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");

  private UnusedImportScanner() {}

  /**
   * Scan the original (unformatted, un-preprocessed) source and return the import statements that are safe to drop. The returned records
   * match the ones {@link ImportSorter#parseRegion(String)} produces for the formatted output, because both normalize the qualified name
   * to dot-joined segments.
   */
  static Set<ImportSorter.ImportStatement> findUnused(final String source) {
    final JavaLexer lexer = new JavaLexer(CharStreams.fromString(source));
    lexer.removeErrorListeners();
    final CommonTokenStream tokenStream = new CommonTokenStream(lexer);
    tokenStream.fill();

    final Set<ImportSorter.ImportStatement> declared = new LinkedHashSet<>();
    final Set<String> usedNames = new HashSet<>();
    collect(tokenStream.getTokens(), declared, usedNames);

    final Set<ImportSorter.ImportStatement> unused = new LinkedHashSet<>();
    for (final ImportSorter.ImportStatement statement : declared) {
      if (!statement.qualifiedName().endsWith(".*") && !usedNames.contains(simpleName(statement.qualifiedName()))) {
        unused.add(statement);
      }
    }
    return unused;
  }

  private static void collect(final List<Token> tokens, final Set<ImportSorter.ImportStatement> declared, final Set<String> usedNames) {
    int i = 0;
    while (i < tokens.size()) {
      final Token token = tokens.get(i);
      if (token.getChannel() == Token.DEFAULT_CHANNEL && token.getType() == JavaLexer.IMPORT) {
        i = consumeImport(tokens, i + 1, declared, usedNames);
      } else {
        if (token.getChannel() == Token.DEFAULT_CHANNEL && IDENTIFIER_LIKE.contains(token.getType())) {
          usedNames.add(token.getText());
        } else if (token.getType() == JavaLexer.COMMENT && token.getText().startsWith("/**")) {
          addJavadocReferences(token.getText(), usedNames);
        }
        i++;
      }
    }
  }

  /**
   * Consume one import declaration starting after its {@code import} keyword and record it in {@code declared}; returns the index to
   * continue scanning from. The name segments of a well-formed declaration are deliberately <em>not</em> added to {@code usedNames} — a
   * declaration is not a usage. Any unexpected token aborts the declaration: the collected segments are then flushed into
   * {@code usedNames} as ordinary identifiers and the offending token is re-processed by the caller, so malformed content (only possible
   * inside directive-protected off-regions — real preambles have parsed successfully before this scanner runs) can never shrink the
   * used-name set, which is the unsafe direction.
   */
  private static int consumeImport(
    final List<Token> tokens,
    final int start,
    final Set<ImportSorter.ImportStatement> declared,
    final Set<String> usedNames
  ) {
    final List<String> segments = new ArrayList<>();
    boolean isStatic = false;
    boolean wildcard = false;
    for (int i = start; i < tokens.size(); i++) {
      final Token token = tokens.get(i);
      if (token.getChannel() != Token.DEFAULT_CHANNEL) {
        continue;
      }
      if (token.getType() == JavaLexer.STATIC && segments.isEmpty()) {
        isStatic = true;
      } else if (IDENTIFIER_LIKE.contains(token.getType())) {
        segments.add(token.getText());
      } else if (token.getType() == JavaLexer.MUL) {
        wildcard = true;
      } else if (token.getType() == JavaLexer.SEMI) {
        declared.add(new ImportSorter.ImportStatement(String.join(".", segments) + (wildcard ? ".*" : ""), isStatic));
        return i + 1;
      } else if (token.getType() != JavaLexer.DOT) {
        usedNames.addAll(segments);
        return i;
      }
    }
    usedNames.addAll(segments);
    return tokens.size();
  }

  private static void addJavadocReferences(final String javadoc, final Set<String> usedNames) {
    final Matcher references = JAVADOC_REFERENCE.matcher(javadoc);
    while (references.find()) {
      final String reference = references.group(1) != null ? references.group(1) : references.group(2);
      final Matcher words = IDENTIFIER_WORD.matcher(reference);
      while (words.find()) {
        usedNames.add(words.group());
      }
    }
  }

  private static String simpleName(final String qualifiedName) {
    return qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
  }
}
