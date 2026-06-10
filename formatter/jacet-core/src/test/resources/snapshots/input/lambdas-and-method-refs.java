public class LambdaDemo {
  public void example() {
    Runnable r = () -> doSomething();

    Runnable r2 = () -> {
      doFirst();
      doSecond();
    };

    list.stream().filter(x -> x > 0).map(x -> x * 2).toList();

    list.forEach(System.out::println);
  }
}
