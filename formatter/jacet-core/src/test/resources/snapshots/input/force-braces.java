public class ForceBraces {
  public void example(int x, boolean flag) {
    if (x > 0) {
      return;
    }

    if (x > 0) {
      doA();
    } else {
      doB();
    }

    if (x > 0) {
      doA();
    } else if (x < 0) {
      doB();
    } else {
      doC();
    }

    for (int i = 0; i < 10; i++) {
      process(i);
    }

    while (flag) {
      flag = update();
    }
  }
}
