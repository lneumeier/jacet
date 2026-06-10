package com.example;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipOutputStream;

class TryWithResources {

  void single() throws IOException {
    try (var stream = new ByteArrayOutputStream()) {
      stream.write(0);
    }
  }

  void multipleShort() throws IOException {
    try (var a = new ByteArrayOutputStream(); var b = new ByteArrayOutputStream()) {
      a.write(0);
      b.write(0);
    }
  }

  void multipleLong() throws IOException {
    try (final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream)) {
      zipOutputStream.write(0);
    }
  }
}
