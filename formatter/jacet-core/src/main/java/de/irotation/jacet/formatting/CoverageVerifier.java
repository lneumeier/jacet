package de.irotation.jacet.formatting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;

import de.irotation.jacet.document.BreakIndent;
import de.irotation.jacet.document.Concat;
import de.irotation.jacet.document.ConditionalLayout;
import de.irotation.jacet.document.Document;
import de.irotation.jacet.document.Group;
import de.irotation.jacet.document.HardLine;
import de.irotation.jacet.document.IfBreak;
import de.irotation.jacet.document.Indent;
import de.irotation.jacet.document.IndentIfBreak;
import de.irotation.jacet.document.Line;
import de.irotation.jacet.document.LineSuffix;
import de.irotation.jacet.document.SoftLine;
import de.irotation.jacet.document.Text;
import de.irotation.jacet.parser.JavaLexer;

/**
 * Token-coverage guarantee: proves that the {@link Document} the visitor built reproduces every input token exactly once, so no code token
 * or comment is ever silently dropped.
 *
 * <p>The IR <em>is</em> the emission protocol — each sourced {@link Text} carries the input-token span whose
 * bytes it prints (see {@code Tokens}). The check is therefore a precise set comparison rather than a heuristic re-lex:
 *
 * <ul>
 *   <li><b>Code tokens</b> — {@code expected} = every default-channel token (minus {@code EOF}); {@code got} =
 *       the union of all sourced/removed spans in the tree. {@code expected ⊆ got} catches loss; a second,
 *       single-render-path walk that marks each token at most once catches double-emission (Biome-style). The
 *       two alternative branches of {@link IfBreak}/{@link ConditionalLayout} both contribute to the coverage
 *       union (so an asymmetric branch can't read as loss) but only the primary branch is walked for overlap
 *       (so a chain's two layouts can't read as a double-emission).</li>
 *   <li><b>Comments</b> — Prettier-style {@code ensureAllCommentsPrinted}: every hidden-channel comment token
 *       must appear in {@code CommentHelper}'s printed set.</li>
 * </ul>
 *
 * <p>Returns the list of violations (empty when sound); the caller decides what to do — {@code JacetFormatter}
 * falls back to the original source, mirroring the parse-error contract.
 */
public final class CoverageVerifier {

  private CoverageVerifier() {}

  public static List<String> verify(final TokenStream tokens, final Document document, final CommentHelper commentHelper) {
    final List<String> errors = new ArrayList<>();
    verifyCodeTokens(tokens, document, errors);
    verifyComments(tokens, commentHelper, errors);
    return errors;
  }

  private static void verifyCodeTokens(final TokenStream tokens, final Document document, final Collection<String> errors) {
    final int size = tokens.size();
    final boolean[] covered = new boolean[size];
    walk(document, false, (first, last) -> markCovered(tokens, covered, first, last));

    final boolean[] seen = new boolean[size];
    final Collection<Integer> reportedOverlap = new HashSet<>();
    walk(document, true, (first, last) -> markOverlap(tokens, seen, reportedOverlap, errors, first, last));

    for (int i = 0; i < size; i++) {
      final Token token = tokens.get(i);
      if (token.getChannel() != Token.DEFAULT_CHANNEL || token.getType() == Token.EOF) {
        continue;
      }
      if (!covered[i]) {
        errors.add("code token lost: #" + i + " " + describe(token));
      }
    }
  }

  private static void markCovered(final TokenStream tokens, final boolean[] covered, final int first, final int last) {
    for (int j = first; j <= last && j < tokens.size(); j++) {
      if (j >= 0 && tokens.get(j).getChannel() == Token.DEFAULT_CHANNEL) {
        covered[j] = true;
      }
    }
  }

  private static void markOverlap(
    final TokenStream tokens,
    final boolean[] seen,
    final Collection<Integer> reported,
    final Collection<String> errors,
    final int first,
    final int last
  ) {
    for (int j = first; j <= last && j < tokens.size(); j++) {
      if (j < 0 || tokens.get(j).getChannel() != Token.DEFAULT_CHANNEL) {
        continue;
      }
      if (seen[j] && reported.add(j)) {
        errors.add("code token emitted twice: #" + j + " " + describe(tokens.get(j)));
      }
      seen[j] = true;
    }
  }

  private static void verifyComments(final TokenStream tokens, final CommentHelper commentHelper, final Collection<String> errors) {
    final Set<Integer> printed = commentHelper.printedCommentIndices();
    for (int i = 0; i < tokens.size(); i++) {
      final Token token = tokens.get(i);
      if (token.getType() != JavaLexer.COMMENT && token.getType() != JavaLexer.LINE_COMMENT) {
        continue;
      }
      if (!printed.contains(i)) {
        errors.add("comment lost: \"" + token.getText().strip() + "\" @line " + token.getLine());
      }
    }
  }

  private static String describe(final Token token) {
    return "\"" + token.getText() + "\" @" + token.getLine() + ":" + token.getCharPositionInLine();
  }

  /**
   * Walk the visitor-built IR, reporting each sourced {@link Text}'s covered span to {@code sink}. With {@code singlePath} false both
   * branches of {@link IfBreak}/{@link ConditionalLayout} are visited (coverage union); with it true only the break/primary branch is
   * visited (overlap counting along one render path). {@link IndentIfBreak#group()} is not followed — the referenced group is rendered
   * standalone elsewhere in the tree, so following it here would double-count.
   */
  private static void walk(final Document doc, final boolean singlePath, final SpanSink sink) {
    switch (doc) {
      case final Text t -> {
        if (t.firstToken() >= 0) {
          sink.accept(t.firstToken(), t.lastToken());
        }
      }
      case final Concat c -> {
        for (final Document p : c.parts()) {
          walk(p, singlePath, sink);
        }
      }
      case final Group g -> walk(g.contents(), singlePath, sink);
      case final Indent i -> walk(i.contents(), singlePath, sink);
      case final BreakIndent bi -> walk(bi.contents(), singlePath, sink);
      case final IndentIfBreak iib -> walk(iib.contents(), singlePath, sink);
      case final LineSuffix ls -> walk(ls.contents(), singlePath, sink);
      case final IfBreak ib -> {
        walk(ib.breakContents(), singlePath, sink);
        if (!singlePath) {
          walk(ib.flatContents(), false, sink);
        }
      }
      case final ConditionalLayout cl -> {
        walk(cl.primary(), singlePath, sink);
        if (!singlePath) {
          walk(cl.fallback(), false, sink);
        }
      }
      case Line _, SoftLine _, HardLine _ -> {}
    }
  }

  @FunctionalInterface
  private interface SpanSink {
    void accept(int first, int last);
  }
}
