// empty-enum-body: empty and compact enum bodies
class Outer {
  enum AlreadyCompact {}

  enum WithNewline {}

  enum WithImplements implements Comparable<WithImplements> {}

  enum NonEmpty {
    A,
    B,
  }
}

// enum-and-record: enum with method beside a record
enum Color {
  RED,
  GREEN,
  BLUE;

  public String lower() {
    return name().toLowerCase();
  }
}

record Point(int x, int y) {
  public double distance() {
    return Math.sqrt(x * x + y * y);
  }
}

// enum-blank-lines-between-constants: blank lines and group comments between constants
enum Group {
  // Group A
  ALPHA,
  BETA,

  // Group B
  GAMMA,
  DELTA,

  EPSILON,
}

// enum-leading-comment-block: leading comment block before constants
enum NotificationType {
  // When changing this, the FE must be updated too!

  // External
  EXTERNAL_RETRIEVE,
  EXTERNAL_TO_TARGET,

  //Group
  INTERNAL_PROCESSING_STATUS,
}

// enum-trailing-comments: trailing comments on constants
enum Status {
  OPEN, // 0
  CLOSED; // 1

  String label() {
    return name();
  }
}
