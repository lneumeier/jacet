package de.irotation.jacet.formatting;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import de.irotation.jacet.parser.JavaLexer;

/**
 * Pre/post-processing for {@code @formatter:off} / {@code @formatter:on} directives.
 *
 * <p>Regions between an {@code @formatter:off} marker and the matching {@code @formatter:on}
 * (or EOF) are extracted before formatting, replaced with a unique placeholder line comment, and
 * spliced back verbatim afterwards — so the formatter never touches code inside such regions.
 */
public final class FormatterDirectives {

  private static final String PLACEHOLDER_PREFIX = "//jacet:off-region:";

  private FormatterDirectives() {}

  /**
   * Extract {@code @formatter:off}…{@code @formatter:on} regions and replace each with a
   * placeholder line. Returns the rewritten source, the captured region texts, and the placeholder
   * prefix used. The prefix is extended until it does not occur anywhere in the source, so a comment
   * or string literal that happens to contain the literal placeholder text can never be mistaken for
   * a real placeholder when the regions are spliced back.
   */
  public static PreProcessed preProcess(final String source) {
    final List<int[]> regions = findRegions(source);
    if (regions.isEmpty()) {
      return new PreProcessed(source, List.of(), PLACEHOLDER_PREFIX);
    }

    String prefix = PLACEHOLDER_PREFIX;
    while (source.contains(prefix)) {
      prefix += "x";
    }

    final List<String> savedRegions = new ArrayList<>(regions.size());
    final StringBuilder rewritten = new StringBuilder(source.length());
    int cursor = 0;
    for (int i = 0; i < regions.size(); i++) {
      final int[] region = regions.get(i);
      final int start = region[0];
      final int end = region[1];
      rewritten.append(source, cursor, start);
      savedRegions.add(source.substring(start, end));
      rewritten.append(prefix).append(i).append('\n');
      cursor = end;
    }
    rewritten.append(source, cursor, source.length());
    return new PreProcessed(rewritten.toString(), savedRegions, prefix);
  }

  /**
   * Replace each placeholder line in the formatted output with the corresponding original region. The
   * whole placeholder line (including its trailing newline) is replaced, because the saved region already
   * carries its original indentation and trailing newline. A placeholder only matches as a complete line
   * (leading whitespace from re-indentation aside), so {@code …:0} never matches inside the {@code …:01}
   * placeholder line.
   */
  public static String postProcess(final String formatted, final PreProcessed preProcessed) {
    final List<String> savedRegions = preProcessed.regions();
    if (savedRegions.isEmpty()) {
      return formatted;
    }
    String result = formatted;
    for (int i = 0; i < savedRegions.size(); i++) {
      final String placeholder = preProcessed.placeholderPrefix() + i;
      final int idx = indexOfPlaceholderLine(result, placeholder);
      if (idx < 0) {
        continue;
      }
      final int lineStart = lineStartOffset(result, idx);
      int lineEnd = idx + placeholder.length();
      if (lineEnd < result.length() && result.charAt(lineEnd) == '\r') {
        lineEnd++;
      }
      if (lineEnd < result.length() && result.charAt(lineEnd) == '\n') {
        lineEnd++;
      }
      result = result.substring(0, lineStart) + savedRegions.get(i) + result.substring(lineEnd);
    }
    return result;
  }

  /**
   * Index of the first occurrence of {@code placeholder} that forms a complete line of {@code text}:
   * nothing but whitespace before it on its line and a line break (or end of text) directly after it.
   */
  private static int indexOfPlaceholderLine(final String text, final String placeholder) {
    int from = 0;
    while (true) {
      final int idx = text.indexOf(placeholder, from);
      if (idx < 0) {
        return -1;
      }
      final int end = idx + placeholder.length();
      final boolean lineEndsHere = end == text.length() || text.charAt(end) == '\n' || text.charAt(end) == '\r';
      if (lineEndsHere && text.substring(lineStartOffset(text, idx), idx).isBlank()) {
        return idx;
      }
      from = idx + 1;
    }
  }

  /**
   * Scans comment tokens for {@code @formatter:off}/{@code @formatter:on} markers and returns the source
   * offset ranges they enclose. A single comment containing <em>both</em> markers is treated as
   * documentation (e.g. prose mentioning both) and ignored — the directives cancel out.
   */
  private static List<int[]> findRegions(final String source) {
    final JavaLexer lexer = new JavaLexer(CharStreams.fromString(source));
    final CommonTokenStream tokens = new CommonTokenStream(lexer);
    tokens.fill();
    final List<int[]> regions = new ArrayList<>();
    int regionStart = -1;
    for (final Token token : tokens.getTokens()) {
      final int type = token.getType();
      if (type != JavaLexer.LINE_COMMENT && type != JavaLexer.COMMENT) {
        continue;
      }
      final String text = token.getText();
      final boolean hasOff = text.contains("@formatter:off");
      final boolean hasOn = text.contains("@formatter:on");
      if (hasOff && hasOn) {
        continue;
      }
      if (regionStart < 0 && hasOff) {
        regionStart = lineStartOffset(source, token.getStartIndex());
      } else if (regionStart >= 0 && hasOn) {
        final int end = consumeBlankLinesAfter(source, lineEndOffsetInclusive(source, token.getStopIndex()));
        regions.add(new int[] { regionStart, end });
        regionStart = -1;
      }
    }
    if (regionStart >= 0) {
      regions.add(new int[] { regionStart, source.length() });
    }
    return regions;
  }

  private static int lineStartOffset(final String source, final int offset) {
    int i = offset;
    while (i > 0 && source.charAt(i - 1) != '\n') {
      i--;
    }
    return i;
  }

  private static int lineEndOffsetInclusive(final String source, final int offset) {
    int i = offset;
    while (i < source.length() && source.charAt(i) != '\n') {
      i++;
    }
    if (i < source.length()) {
      i++;
    }
    return i;
  }

  /**
   * Extend a position past any trailing blank lines so the saved region absorbs them. Without this, the blank line following
   * {@code @formatter:on} ends up lost: the placeholder ends up as a leading comment of the following member, and the formatter only emits
   * a single blank line before that member (using the blank line that already existed before the off-marker).
   */
  private static int consumeBlankLinesAfter(final String source, final int offset) {
    int i = offset;
    while (i < source.length()) {
      int j = i;
      while (j < source.length() && (source.charAt(j) == ' ' || source.charAt(j) == '\t')) {
        j++;
      }
      if (j < source.length() && source.charAt(j) == '\n') {
        i = j + 1;
      } else {
        break;
      }
    }
    return i;
  }

  /**
   * Result of {@link #preProcess(String)}: the rewritten source, the regions to restore, and the
   * collision-free placeholder prefix used for this source.
   */
  public record PreProcessed(String source, List<String> regions, String placeholderPrefix) {}
}
