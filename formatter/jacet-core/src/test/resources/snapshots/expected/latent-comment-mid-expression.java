class LatentCommentMidExpression {
  // block-comment-before-close-paren: a block comment between the condition and the `)` stays inline

  int blockCommentInCondition(int index) {
    if (index >= 0 /* && index < data.size() */) {
      return index;
    }
    return -1;
  }

  // chain-head-trailing-line-comment: a line comment after the chain receiver breaks before the dot

  boolean chainHead() {
    return "true" // keep me on the receiver line
      .equalsIgnoreCase(getValue());
  }

  // binary-operand-trailing-line-comment: NLS-style markers stay on each operand's line

  void binaryOperandComments() {
    System.err.println(
      "a: " + //$NON-NLS-1$
        first() +
        " b: " + //$NON-NLS-1$
        second()
    );
  }

  // chain-head-trailing-block-comment: a block comment after the chain receiver stays inline before the dot

  boolean chainHeadBlock() {
    return "true" /* inline */.equalsIgnoreCase(getValue());
  }

  // binary-operand-trailing-block-comment: a block comment after an operand stays inline before the operator

  void binaryOperandBlockComments() {
    System.err.println("a: " /* note */ + first() + " b: " /* note */ + second());
  }

  // comment-between-else-and-if: a comment after `else` breaks the else-branch onto its own line

  java.util.List<String> elseIfComment(boolean preproc, java.util.List<String> table) {
    if (preproc) {
      return java.util.List.of();
    } else // if preproc, only when there is at least 1 letter
    if (table.isEmpty()) {
      return java.util.List.of();
    }
    return table;
  }

  String getValue() {
    return "x";
  }

  String first() {
    return "1";
  }

  String second() {
    return "2";
  }
}
