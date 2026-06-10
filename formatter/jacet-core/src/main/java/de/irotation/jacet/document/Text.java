package de.irotation.jacet.document;

/**
 * Literal text, printed as-is.
 *
 * <p>{@code firstToken}/{@code lastToken} carry the text's <em>provenance</em>: the inclusive span of
 * input token-stream indices whose source bytes this text reproduces. They underpin the token-coverage
 * guarantee — every code token of the input must be emitted from exactly one sourced {@code Text}. A span
 * of {@code -1}/{@code -1} marks <em>synthetic</em> text: layout, spacing, or additions (braces inserted by
 * {@code forceBraces}, the visible trailing comma) that correspond to no input token and are excluded from
 * coverage tracking. The plain {@code int} fields keep the {@code document} package parser-free.
 *
 * @param value      the literal characters to print
 * @param firstToken first covered input-token index, or {@code -1} when synthetic
 * @param lastToken  last covered input-token index (inclusive), or {@code -1} when synthetic
 */
public record Text(String value, int firstToken, int lastToken) implements Document {

  /** Synthetic text — printed verbatim but covering no input token. */
  public Text(final String value) {
    this(value, -1, -1);
  }
}
