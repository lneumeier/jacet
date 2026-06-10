package test;

sealed interface Shape permits Circle, Rectangle {}

record Circle(double radius) implements Shape {}

record Rectangle(double width, double height) implements Shape {}

class PatternMatching {

  // instanceof pattern matching
  String describe(Object obj) {
    if (obj instanceof String s) {
      return "String: " + s;
    }
    if (obj instanceof Integer i && i > 0) {
      return "Positive: " + i;
    }
    return "Unknown";
  }

  // Switch with case patterns
  double area(Shape shape) {
    return switch (shape) {
      case Circle c -> Math.PI * c.radius() * c.radius();
      case Rectangle r -> r.width() * r.height();
    };
  }

  // Switch with guard
  String classify(Object obj) {
    return switch (obj) {
      case String s when s.length() > 10 -> "long string";
      case String s -> "short string";
      case Integer i when i > 0 -> "positive";
      case Integer i -> "non-positive";
      default -> "other";
    };
  }

  // Record pattern matching
  String describeShape(Shape shape) {
    return switch (shape) {
      case Circle(var r) -> "Circle with radius " + r;
      case Rectangle(var w, var h) -> "Rectangle " + w + "x" + h;
    };
  }

  // var-record-patterns: var components also bind in instanceof, nested patterns, and with modifiers
  String describeNested(Object obj) {
    if (obj instanceof Circle(var r)) {
      return "circle " + r;
    }
    return switch (obj) {
      case Point(var x, Line(var a, var b)) -> "nested " + x + a + b;
      case Circle(final var radius) -> "boxed " + radius;
      default -> "other";
    };
  }
}
