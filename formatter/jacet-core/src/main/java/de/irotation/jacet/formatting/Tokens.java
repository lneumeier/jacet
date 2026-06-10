package de.irotation.jacet.formatting;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.TerminalNode;

import de.irotation.jacet.document.Document;
import de.irotation.jacet.document.Text;

/**
 * Emission markers that source text from the input token stream so it carries provenance for the token-coverage guarantee (see
 * {@code CoverageVerifier}). This is the only place in the formatting layer that mints {@link Text} with a covered span; plain
 * {@link Document#text} stays synthetic. Modelled on Biome's {@code format} / {@code format_removed}:
 *
 * <ul>
 *   <li>{@link #sourced} — the normal case: reproduce a token's (or a whole context's) source bytes and
 *       mark its index span covered. {@link ParserRuleContext#getText()} concatenates exactly the tokens of
 *       {@link ParserRuleContext#getSourceInterval()}, so a qualified name {@code a.b.c} sources as one span.
 *   <li>{@link #removed} — empty text with real provenance: prints nothing but marks the token index covered.
 *       Used for the redundant trailing comma of a flat-printed {@code {}}-list, which is normalised away.
 * </ul>
 */
public final class Tokens {

  private Tokens() {}

  /** Emit a terminal's source text and mark its token index covered. */
  public static Document sourced(final TerminalNode node) {
    return sourced(node.getSymbol());
  }

  /** Emit a token's source text and mark its index covered (for labelled tokens with no terminal accessor). */
  public static Document sourced(final Token token) {
    final int index = token.getTokenIndex();
    return new Text(token.getText(), index, index);
  }

  /** Emit a context's full source text (token bytes, no separators) and mark its index span covered. */
  public static Document sourced(final ParserRuleContext context) {
    final Interval interval = context.getSourceInterval();
    return new Text(context.getText(), interval.a, interval.b);
  }

  /** Mark a terminal's token index covered without emitting it — the normalised-away trailing comma. */
  public static Document removed(final TerminalNode node) {
    final int index = node.getSymbol().getTokenIndex();
    return new Text("", index, index);
  }

  /**
   * Join {@code parts} with their real separator tokens between consecutive elements, sourcing each separator so it is tracked rather than
   * synthesised. {@code separators} must hold the {@code parts.size() - 1} tokens that sit between elements (any trailing separator must be
   * handled by the caller via {@link #removed}). {@code afterSeparator} is the synthetic layout placed after each separator (typically a
   * {@code line()}).
   */
  public static Document joinSourced(final List<Document> parts, final List<TerminalNode> separators, final Document afterSeparator) {
    return joinSourced(parts, separators, Document.empty(), afterSeparator);
  }

  /**
   * Like {@link #joinSourced(List, List, Document)} but also places {@code beforeSeparator} immediately before each sourced separator — for
   * operators that carry layout on both sides ({@code a & b}, {@code a | b}).
   */
  public static Document joinSourced(
    final List<Document> parts,
    final List<TerminalNode> separators,
    final Document beforeSeparator,
    final Document afterSeparator
  ) {
    if (parts.isEmpty()) {
      return Document.empty();
    }
    final List<Document> result = new ArrayList<>();
    result.add(parts.getFirst());
    for (int i = 1; i < parts.size(); i++) {
      if (!Document.EMPTY.equals(beforeSeparator)) {
        result.add(beforeSeparator);
      }
      result.add(sourced(separators.get(i - 1)));
      result.add(afterSeparator);
      result.add(parts.get(i));
    }
    return Document.concat(result);
  }
}
