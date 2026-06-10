/**
 * This is a top-level class javadoc. It has multiple lines.
 */
class CommentCases {

  // comments: field with single-line leading comment

  // Single-line comment above a field
  private int value;

  // comments: field with block leading comment

  /* Block comment */
  private String name;

  // comments: method with javadoc, inline and trailing comments

  /**
   * Javadoc for a method.
   *
   * @param x the input value
   * @return the computed result
   */
  public int compute(int x) {
    // inline comment
    return x * 2; // trailing comment
  }

  // comments-in-args-and-arrays: comments inside call arguments

  public static Stream<Arguments> argsAndArrays() {
    return Stream.of(
      // first case — comments survive
      Arguments.of("a", "b"),
      // second case — multiple lines
      Arguments.of("c", "d"),
      Arguments.of("e", "f") // trailing on last
    );
  }

  // comments-in-args-and-arrays: comments inside array initializer

  public static final String[] LABELS = {
    // grouping headers below
    "alpha",
    "beta",
    // second group
    "gamma",
    "delta"
  };

  // comment-box-style: box-bordered comment

  /*------------+
   | set primary |
   +-------------*/
  void boxSetPrimary() {}

  // comment-box-style: bordered javadoc

  /*==================*
   * bordered javadoc *
   *==================*/
  void boxBordered() {}

  // comment-box-style: normal block comment with stars

  /*
   * Normal block comment
   * with stars
   */
  void boxNormal() {}

  // comment-box-style: single-line block comment

  /* single-line block comment */
  void boxSingleLine() {}

  // param-list-comments: leading comments between parameters

  void withLeadingComment(
    final String userId,
    // origin data
    final long version,
    // updated data
    final String status
  ) {}

  // param-list-comments: trailing comment on last parameter

  void withTrailingComment(final String a, final String b // trailing on last param
  ) {}

  // blank-line-between-leading-comments: blank line splits leading comment blocks on a method

  // First comment block, line 1
  // First comment block, line 2

  // Second comment block after blank line
  // Second comment block, line 2
  void blankLineLeadingMethod() {}

  // blank-line-between-leading-comments: blank line splits leading comment blocks on a field

  // Group A
  // also Group A

  // Group B
  String blankLineLeadingField;

  // blank-lines-in-body: blank lines between fields, constructor and methods

  private static final String CONSTANT = "x";

  private final String firstField;
  private final String secondField;

  private String mutableField;

  public void blankLinesInBody(final String firstField, final String secondField) {
    this.firstField = firstField;
    this.secondField = secondField;
  }

  public String firstField() {
    return firstField;
  }

  public String secondField() {
    return secondField;
  }

  // block-end-trailing-leading-comment: comment at end of block

  void blockEndComment() {
    System.out.println("hello");

    // TODO: implement the rest
  }

  // array-initializer-trailing-comments: trailing comments on array elements

  String[] arrayInitializerTrailingFields = new String[] {
    "id", // set by framework
    "version", // set by framework
    "name",
  };

  // binop-inline-comments: inline comments inside a binary-operator chain

  boolean hasValidFormat() {
    return (
      this.bic.isEmpty() ||
      (this.hasCorrectLength() &&
        this.checkBlock1(bic) && // 1. Block
        this.getLaenderCode().isPresent() && // 2. Block
        this.checkBlock3(bic) && // 3. Block
        this.checkBlock4(bic))
    ); // 4. Block
  }

  // empty-block-comments: a same-line block comment hugs the braces; line comments and own-line comments break

  void emptyBlockComments(int x) {
    switch (x) {
      case 1 -> { /* already formatted */ }
      default -> emptyBlockComments(0);
    }
    if (x > 0) { /* no-op */ }
    Runnable r = () -> { /* nothing */ };
  }

  void emptyBody() { /* nothing to initialize */ }

  void emptyBlockSameLineLineComment(boolean flag) {
    if (flag) { // nothing to do
    }
  }

  void emptyBlockOwnLineBlockComment(boolean flag) {
    if (flag) {
      /* deliberately empty */
    }
  }
}
