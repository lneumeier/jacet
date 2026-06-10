package test;

import java.util.List;
import java.util.function.BiFunction;

// Array creation
class ArrayExamples {
  void arrays() {
    int[] a = new int[10];
    int[][] b = new int[3][4];
    String[] c = new String[] { "hello", "world" };
    int[] d = new int[] { 1, 2, 3 };
    Object[][] e = new Object[5][];
  }
}

// Compact constructor in record
record Range(int lo, int hi) {
  Range {
    if (lo > hi) {
      throw new IllegalArgumentException("lo > hi");
    }
  }

  public boolean contains(int value) {
    return value >= lo && value <= hi;
  }
}

// Local type declaration inside method
class LocalTypes {
  void example() {
    class Helper {
      String format(int value) {
        return String.valueOf(value);
      }
    }

    final Helper helper = new Helper();
    System.out.println(helper.format(42));
  }
}

// Interface constants and generic interface methods
interface Constants {
  int MAX_SIZE = 100;
  String DEFAULT_NAME = "unnamed";
  double PI_APPROX = 3.14;

  <T> T convert(T input);

  <T extends Comparable<T>> int compare(T a, T b);
}

// Inner creator
class Outer {
  class Inner {
    Inner(String name) {}
  }

  void create() {
    Outer outer = new Outer();
    Inner inner = outer.new Inner("test");
  }
}

// Explicit generic invocation and super suffix
class Base {
  <T> T convert(T value) {
    return value;
  }
}

class Child extends Base {
  @Override
  <T> T convert(T value) {
    return super.<T>convert(value);
  }

  void callSuper() {
    super.convert("hello");
  }
}

// Receiver parameter
class ReceiverExample {
  class Inner {
    void method(ReceiverExample ReceiverExample.this) {
      System.out.println("receiver");
    }
  }
}

// Lambda with var (LVTI)
class LambdaVar {
  void example() {
    BiFunction<String, String, String> concat = (var a, var b) -> a + b;
    List.of(1, 2, 3)
      .stream()
      .map((var x) -> x * 2)
      .toList();
  }
}
