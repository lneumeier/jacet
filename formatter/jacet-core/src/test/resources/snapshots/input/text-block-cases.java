package test;

class TextBlockCases {

  // escaped-quote-trio-stays-inside: \""" inside a text block must not terminate it early
  String nestedTextBlockSource() {
    return """
      String s = \"""
        nested content
        \""";
      """;
  }

  // escaped-backslash-before-close: \\ directly before the real closing delimiter
  String endsWithEscapedBackslash() {
    return """
      path C:\\""";
  }

  // text-block-only-escapes: \s keeps a trailing space, \<newline> joins lines
  String textBlockOnlyEscapes() {
    return """
      trailing space kept\s
      joined \
      line""";
  }

  // plain-text-block-verbatim: a text block without escapes stays verbatim
  String plain() {
    return """
      hello
      world
      """;
  }
}
