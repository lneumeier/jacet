package com.example;

// annotation-arguments: simple single-value annotations
// Simple single-value annotations
@SuppressWarnings("unchecked")
@Deprecated(since = "1.0", forRemoval = true)
class SimpleAnnotations {}

// annotation-arguments: long annotation that should break across lines
// Long annotation that should break across lines
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.CONSTRUCTOR, ElementType.LOCAL_VARIABLE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@interface LongArrayAnnotation {
  String value() default "";
}

// annotation-arguments: multiple named parameters with long values
// Multiple named parameters with long values
@interface Config {
  String name();
  String description();
  int priority();
  String[] tags();
  Class<?> handler();
}

@Config(name = "myService", description = "A very long description that explains what this service does in great detail", priority = 42, tags = {"api", "internal", "experimental", "deprecated"}, handler = Void.class)
class MultiParamAnnotation {}

// annotation-arguments: nested annotations
// Nested annotations
@interface ArgInner {
  String value() default "";
}

@interface ArgOuter {
  ArgInner value();
  ArgInner[] extras() default {};
}

@ArgOuter(value = @ArgInner("primary"), extras = {@ArgInner("extra1"), @ArgInner("extra2"), @ArgInner("extra3")})
class NestedAnnotation {}

// annotation-arguments: deeply nested annotations
// Deeply nested annotations
@interface Level1 {
  Level2 value();
}

@interface Level2 {
  Level3 value();
}

@interface Level3 {
  String value();
}

@Level1(@Level2(@Level3("deep")))
class DeepNesting {}

// annotation-arguments: annotation with complex expression values
// Annotation with complex expression values
@SuppressWarnings({"unchecked", "rawtypes", "deprecation", "serial", "finally", "fallthrough"})
class ManyWarnings {}

// annotation-arguments: annotation on various targets
// Annotation on various targets
class AnnotationTargets {
  @Deprecated(since = "2.0", forRemoval = true)
  private String oldField;

  @SuppressWarnings({"unchecked", "rawtypes"})
  public <T> T cast(@Deprecated final Object input, @SuppressWarnings("unused") final int flags) {
    return (T) input;
  }

  @SafeVarargs
  @SuppressWarnings("unchecked")
  final <T> void varargMethod(@Deprecated T... args) {}
}

// annotation-arguments: annotation with conditional/ternary expression as value (where supported)
// Annotation with conditional/ternary expression as value (where supported)
@interface IntValue {
  int value();
}

@IntValue(1 + 2 * 3)
class ExpressionValue {}

// annotation-arguments: empty annotation arguments
// Empty annotation arguments
@SuppressWarnings({})
class EmptyArray {}

// annotation-arguments: single-element array (could omit braces)
// Single-element array (could omit braces)
@SuppressWarnings({"unchecked"})
class SingleElementArray {}

// annotation-arguments: annotation combining everything
// Annotation combining everything
@interface Complex {
  String name();
  int[] values();
  Class<?>[] types();
  Retention retention();
  Target target();
}

@Complex(name = "test", values = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, types = {String.class, Integer.class, Long.class, Double.class, Float.class}, retention = @Retention(RetentionPolicy.RUNTIME), target = @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER}))
class KitchenSink {}

// annotation-array-break: arrays breaking across lines with trailing line comments
class AnnotationArrayBreak {
  @ParameterizedTest
  @CsvSource({
    "-2, -2", // in the past
    "2, 0", // today
    ", -1", // no date
  })
  void csvWithComments(String a, String b) {}

  @ParameterizedTest
  @ValueSource(strings = {
    "1E02120300000000202051", // digits instead of letters
    "D", // too few characters
  })
  void valueSourceWithComments(String code) {}

  @CsvSource({ "a,1", "b,2" })
  void shortInline(String s, int n) {}
}

// annotation-array-trailing-comma: trailing commas in nested annotation arrays
@ComponentScan(
  basePackages = {
    "com.example.foo.bar.baz.veryLongPackageNameToForceMultilineLayout",
    "com.example.qux.quux.corge.anotherLongPackageNameForLayoutBreaking",
  },
  excludeFilters = {
    @ComponentScan.Filter(
      type = FilterType.REGEX,
      pattern = {
        "com.example.foo.first",
        "com.example.foo.second",
      }
    )
  }
)
class AnnotationArrayTrailingComma {}

// annotation-array-trailing-line-comments: trailing line comments inside annotation arrays
class AnnotationArrayTrailingLineComments {

  @CsvSource(
    {
      "name, name", // direct field
      "address.street, street", // nested field
      "address.city.name, name", // deeply nested field
    }
  )
  void m1() {}

  @CsvSource({ "a, a", "b, b" })
  void m2() {}

  @ComponentScan(
    basePackages = {
      "com.example.foo", // foo group
      "com.example.bar", // bar group
    }
  )
  void m3() {}
}

// annotation-types: annotation type with retention/target and various members
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
@interface MyAnnotation {

  String value() default "";

  int priority() default 0;

  String[] tags() default {};

  Class<?> handler() default Void.class;
}

// annotation-types: empty marker annotation type
@interface SimpleMarker {
}

// annotation-types: annotation type with constants
@interface WithConstants {

  int MAX_RETRIES = 3;

  String DEFAULT_NAME = "unnamed";

  String name();
}

// annotation-types: annotation type with nested annotation type
@interface WithNestedAnnotation {

  @interface Inner {

    String value();
  }

  Inner inner() default @Inner("default");
}

// annotation-types: annotation type with array defaults
@interface WithArrayDefaults {

  String[] names() default {"foo", "bar", "baz"};

  int[] values() default {1, 2, 3};
}

// annotations: type-use vs declaration annotations on a class and its members
@SuppressWarnings("unchecked")
@Deprecated
class AnnotatedClass {

  @Override
  public String toString() {
    return "AnnotatedClass";
  }

  @SuppressWarnings("unused")
  private void helperMethod() {
  }

  // Type-use annotations after non-annotation modifiers must stay inline,
  // not get pushed onto a new line like declaration annotations.
  private final @Nullable String nullableField = null;
  public static @Deprecated String staticDeprecatedField = "x";

  public @Nullable String interspersedReturn(@NonNull String input) {
    return null;
  }

  // Array-level type annotations: "String @Nullable []" is a nullable array of
  // Strings — must NOT be collapsed to "@Nullable String[]" (array of nullables).
  private String @Nullable [] nullableArray;
  private @Nullable String[] arrayOfNullables;
  private @Nullable String @NonNull [] bothAnnotated;
}

// cast-annotation: annotations inside cast expressions within methods
class AnnotationCases {
  Object single(final Object value) {
    return (@Nullable BigDecimal) value;
  }

  Object multiple(final Object value) {
    return (@Nullable @Deprecated String) value;
  }

  Object asArgument() {
    return foo((@Nullable BigDecimal) null);
  }

  Object intersection(final Object value) {
    return (@Nullable Foo & Bar) value;
  }

  Object plain(final Object value) {
    return (BigDecimal) value;
  }
}
