public class ControlFlow {
  public void example(int x, boolean flag) {
    if (x > 0) {
      doPositive();
    } else if (x < 0) {
      doNegative();
    } else {
      doZero();
    }

    for (int i = 0; i < x; i++) {
      process(i);
    }

    for (var item : getItems()) {
      handle(item);
    }

    while (flag) {
      flag = check();
    }

    do {
      attempt();
    } while (!done());

    try {
      riskyOperation();
    } catch (RuntimeException e) {
      handleRuntime(e);
    } catch (Exception e) {
      handleException(e);
    } finally {
      cleanup();
    }
  }
}
