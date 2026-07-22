package com.example;

import static java.util.Collections.*;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/** Keeps {@link Map} through a javadoc reference; a Set mention in prose does not count. */
public class RemoveUnusedImports {

  private final List<String> items = List.of();

  /**
   * @throws IOException never
   */
  void useStatic() throws IOException {
    requireNonNull(items);
  }
}
