package de.irotation.jacet;

/**
 * Line-ending policy for formatter output. {@link #LF}, {@link #CRLF}, and {@link #CR} are explicit choices; {@link #AUTO} picks the ending
 * from the input source at format time.
 */
public enum EndOfLine {
  LF,
  CRLF,
  CR,
  AUTO;

  /**
   * Detect the line ending used in the given source text. Returns LF/CRLF/CR based on the first line break found — mixed-EOL sources get
   * normalized to whichever ending appears first (matches Prettier's "auto" semantics).
   */
  private static EndOfLine detect(final String source) {
    for (int i = 0; i < source.length(); i++) {
      final char c = source.charAt(i);
      if (c == '\r') {
        return i + 1 < source.length() && source.charAt(i + 1) == '\n' ? CRLF : CR;
      }
      if (c == '\n') {
        return LF;
      }
    }
    return LF;
  }

  /**
   * Resolve to a concrete line-ending string. AUTO detects from source; the others return their fixed value.
   */
  public String resolve(final String source) {
    final EndOfLine concrete = this == AUTO ? detect(source) : this;
    return switch (concrete) {
      case LF -> "\n";
      case CRLF -> "\r\n";
      case CR -> "\r";
      case AUTO -> throw new AssertionError("detect() never returns AUTO");
    };
  }
}
