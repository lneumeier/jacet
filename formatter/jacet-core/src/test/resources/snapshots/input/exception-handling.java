package test;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

class ExceptionHandling {

  // Multi-catch
  void multiCatch() {
    try {
      riskyOperation();
    } catch (IOException | SQLException | IllegalArgumentException e) {
      log(e);
    }
  }

  // Multi-catch with very long exception types (should wrap)
  void multiCatchLong() {
    try {
      riskyOperation();
    } catch (java.io.IOException | java.sql.SQLException | java.lang.IllegalArgumentException | java.lang.UnsupportedOperationException e) {
      log(e);
    }
  }

  // Try-with-resources: single resource
  void trySingleResource() throws IOException {
    try (var reader = new BufferedReader(new FileReader("file.txt"))) {
      reader.readLine();
    }
  }

  // Try-with-resources: multiple resources
  void tryMultipleResources() throws IOException, SQLException {
    try (var reader = new BufferedReader(new FileReader("input.txt")); var conn = DriverManager.getConnection("jdbc:test"); var writer = new java.io.PrintWriter("output.txt")) {
      process(reader, conn, writer);
    }
  }

  // Try-with-resources + catch + finally
  void tryResourcesCatchFinally() throws IOException {
    try (var reader = new BufferedReader(new FileReader("data.csv"))) {
      reader.readLine();
    } catch (IOException e) {
      log(e);
      throw e;
    } finally {
      cleanup();
    }
  }

  // Nested try blocks
  void nestedTry() {
    try {
      try {
        innerRisky();
      } catch (RuntimeException e) {
        handleInner(e);
      }
    } catch (Exception e) {
      handleOuter(e);
    }
  }

  // Try-with-resources with existing variable (Java 9+)
  void tryWithExistingVariable(BufferedReader reader) throws IOException {
    try (reader) {
      reader.readLine();
    }
  }

  private void riskyOperation() throws IOException, SQLException {}
  private void innerRisky() {}
  private void handleInner(RuntimeException e) {}
  private void handleOuter(Exception e) {}
  private void process(Object... args) {}
  private void log(Exception e) {}
  private void cleanup() {}
}
