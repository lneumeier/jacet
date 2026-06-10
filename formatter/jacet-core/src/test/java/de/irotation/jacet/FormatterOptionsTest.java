package de.irotation.jacet;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class FormatterOptionsTest {

  @Test
  void defaultsAreValid() {
    assertDoesNotThrow(FormatterOptions::defaults);
  }

  @Test
  void rejectsNonPositivePrintWidth() {
    assertThrows(IllegalArgumentException.class, () -> new FormatterOptions(0, 2, false, true, EndOfLine.LF, ImportOptions.defaults()));
  }

  @Test
  void rejectsNegativePrintWidth() {
    assertThrows(IllegalArgumentException.class, () -> new FormatterOptions(-10, 2, false, true, EndOfLine.LF, ImportOptions.defaults()));
  }

  @Test
  void rejectsNonPositiveTabWidth() {
    assertThrows(IllegalArgumentException.class, () -> new FormatterOptions(140, 0, false, true, EndOfLine.LF, ImportOptions.defaults()));
  }
}
