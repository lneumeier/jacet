package de.irotation.jacet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.atn.PredictionMode;

import de.irotation.jacet.document.Document;
import de.irotation.jacet.formatting.CoverageVerifier;
import de.irotation.jacet.formatting.DocumentVisitor;
import de.irotation.jacet.formatting.DocumentVisitorFactory;
import de.irotation.jacet.formatting.FormatterDirectives;
import de.irotation.jacet.parser.JavaLexer;
import de.irotation.jacet.parser.JavaParser;

/**
 * Main entry point for the Java Prettier formatter.
 *
 * <p>Typical usage:
 * <pre>{@code
 * final String formatted = new JacetFormatter().format(source);
 * }</pre>
 *
 * <p>Instances are safe to reuse and call from multiple threads; all state lives in the immutable {@link FormatterOptions} record.
 */
public final class JacetFormatter {

  private final FormatterOptions options;

  /**
   * Create a formatter with the given configuration.
   */
  public JacetFormatter(final FormatterOptions options) {
    this.options = options;
  }

  /**
   * Create a formatter with {@link FormatterOptions#defaults()}.
   */
  public JacetFormatter() {
    this(FormatterOptions.defaults());
  }

  /**
   * Character offset at which the {@code lineNumber}-th line begins (0-based: line 0 starts at offset 0), advancing past one {@code eol}
   * separator per line. A {@code lineNumber} beyond the last line clamps to {@code source.length()} rather than failing —
   * {@link #formatImports} relies on the clamp to address the position just past the final line when the import region runs to end of
   * file.
   */
  private static int nthLineOffset(final String source, final int lineNumber, final String eol) {
    int offset = 0;
    for (int i = 0; i < lineNumber && offset < source.length(); i++) {
      final int next = source.indexOf(eol, offset);
      offset = next < 0 ? source.length() : next + eol.length();
    }
    return offset;
  }

  /**
   * Format a single Java source string. Returns the source unchanged if parsing fails.
   *
   * @param source the Java source code
   * @return the formatted source code, or unchanged source on parse errors
   */
  public String format(final String source) {
    return this.formatWithResult(source).source();
  }

  /**
   * Format a single Java source string, returning detailed results including any parse errors encountered.
   *
   * <p>The pipeline runs in phases:
   * <ol>
   *   <li>Extract {@code @formatter:off}/{@code @formatter:on} regions, replacing them with placeholders.</li>
   *   <li>Format the code (ANTLR4 CST → {@link Document} IR → {@link DocumentPrinter}). Full LL prediction
   *       lets the parser disambiguate arrow-style from colon-style switches. On any parse error the
   *       original source is returned unchanged, to avoid unpredictable output.</li>
   *   <li>Sort imports — after formatting, so blank lines between groups are preserved.</li>
   *   <li>Restore the {@code @formatter:off} regions.</li>
   * </ol>
   *
   * @param source the Java source code
   * @return result containing formatted source and any parse errors
   */
  public FormatResult formatWithResult(final String source) {
    if (source.isBlank()) {
      return new FormatResult(source, true, List.of(), List.of());
    }

    final String eol = options.endOfLine().resolve(source);

    final FormatterDirectives.PreProcessed preProcessed = FormatterDirectives.preProcess(source);
    final String workingSource = preProcessed.source();

    final JavaLexer lexer = new JavaLexer(CharStreams.fromString(workingSource));
    final CommonTokenStream tokenStream = new CommonTokenStream(lexer);
    final JavaParser parser = new JavaParser(tokenStream);

    final ErrorCollector errorCollector = new ErrorCollector();
    parser.removeErrorListeners();
    lexer.removeErrorListeners();
    parser.addErrorListener(errorCollector);
    lexer.addErrorListener(errorCollector);

    parser.getInterpreter().setPredictionMode(PredictionMode.LL);

    final JavaParser.CompilationUnitContext tree = parser.compilationUnit();

    if (errorCollector.hasErrors()) {
      return new FormatResult(source, false, errorCollector.errors(), List.of());
    }

    final DocumentVisitor documentVisitor = DocumentVisitorFactory.create(tokenStream, tree, options.forceBraces(), options.printWidth());
    final Document document = documentVisitor.visit(tree);

    final List<String> verificationErrors = CoverageVerifier.verify(tokenStream, document, documentVisitor.commentHelper());
    if (!verificationErrors.isEmpty()) {
      return new FormatResult(source, false, List.of(), verificationErrors);
    }

    final DocumentPrinter printer = new DocumentPrinter(options.printWidth(), options.tabWidth(), options.useTabs(), eol);
    final String formatted = printer.print(document);

    final String sorted = this.formatImports(formatted, source, eol);

    final String result = FormatterDirectives.postProcess(sorted, preProcessed);
    return new FormatResult(result, true, List.of(), List.of());
  }

  /**
   * Replaces the formatted source's import region with the sorted imports, absorbing any blank lines that
   * trailed the original region so import-group spacing isn't doubled. With {@link ImportOptions#removeUnused()}
   * enabled, imports detected by {@link UnusedImportScanner} on {@code original} are dropped before sorting
   * (matching works because both sides normalize qualified names to dot-joined segments); the scan runs only
   * when an import region exists, so import-free files never pay for the extra lex pass. When removal empties a
   * region that runs to end of file, the blank lines that preceded it are trimmed too — a dangling blank line
   * before EOF would otherwise break {@code format(format(x)) == format(x)}.
   */
  private String formatImports(final String source, final String original, final String eol) {
    final ImportSorter.ImportRegion region = ImportSorter.parseRegion(source);
    if (region.imports().isEmpty()) {
      return source;
    }

    final Set<ImportSorter.ImportStatement> unused = options.imports().removeUnused()
      ? UnusedImportScanner.findUnused(original)
      : Set.of();
    final List<ImportSorter.ImportStatement> kept = region.imports().stream().filter(i -> !unused.contains(i)).toList();
    final String sortedImports = ImportSorter.sort(kept, options.imports(), eol);

    final int beforeEnd = nthLineOffset(source, region.startLine(), eol);
    int afterStart = nthLineOffset(source, region.endLine() + 1, eol);

    while (afterStart < source.length()) {
      final int nextLineEnd = source.indexOf(eol, afterStart);
      final String line = nextLineEnd < 0 ? source.substring(afterStart) : source.substring(afterStart, nextLineEnd);
      if (!line.isBlank()) {
        break;
      }
      afterStart = nextLineEnd < 0 ? source.length() : nextLineEnd + eol.length();
    }

    final String trailing = source.substring(afterStart);
    if (sortedImports.isEmpty() && trailing.isEmpty()) {
      int end = beforeEnd;
      while (end >= eol.length() && source.startsWith(eol, end - eol.length())) {
        end -= eol.length();
      }
      return end == 0 ? "" : source.substring(0, end) + eol;
    }
    final String separator = trailing.isEmpty() || sortedImports.isEmpty() ? "" : eol;
    return source.substring(0, beforeEnd) + sortedImports + separator + trailing;
  }

  private static final class ErrorCollector extends BaseErrorListener {
    private final Collection<String> errors = new ArrayList<>();

    @Override
    public void syntaxError(
      final Recognizer<?, ?> recognizer,
      final Object offendingSymbol,
      final int line,
      final int charPositionInLine,
      final String msg,
      final RecognitionException e
    ) {
      errors.add("line " + line + ":" + charPositionInLine + " " + msg);
    }

    boolean hasErrors() {
      return !errors.isEmpty();
    }

    List<String> errors() {
      return List.copyOf(errors);
    }
  }
}
