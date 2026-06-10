package de.irotation.jacet.formatting;

import org.antlr.v4.runtime.Token;

/**
 * A single comment token from ANTLR's hidden channel, with newline-context flags relative to surrounding default-channel tokens.
 *
 * <p>{@code precedingNewline} == comment lives on its own line (newline between previous default token and this comment).
 * {@code trailingNewline} == comment ends a line (newline between this comment and next default token). {@code precedingBlankLine} == there
 * is a blank line (≥2 newlines) immediately before this comment — used to preserve paragraph breaks between comment groups attached to the
 * same node. {@code trailingBlankLine} == there is a blank line immediately after this comment, before the next default token — used to
 * preserve a gap between leading comments and the statement they precede.
 */
record Comment(Token token, boolean precedingNewline, boolean trailingNewline, boolean precedingBlankLine, boolean trailingBlankLine) {

  String text() {
    return token.getText();
  }

  boolean isLineComment() {
    return this.text().startsWith("//");
  }

  boolean isBlockComment() {
    return this.text().startsWith("/*");
  }

  int tokenIndex() {
    return token.getTokenIndex();
  }
}
