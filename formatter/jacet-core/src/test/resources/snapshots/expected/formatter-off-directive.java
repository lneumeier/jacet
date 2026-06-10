package com.example;

public class FormatterOffDemo {

  public boolean isNullRow() {
    // @formatter:off
    return AllZeroVerifier.isZero(this.getMetricBaselines(), this.getCounts(), this.getQueuedPausedSkipped())
        && AllZeroVerifier.isZero(this.getCellTotals(), this.getCellTotalsOld());
    // @formatter:on
  }

  // @formatter:off
  private final int[] crcArray = {
      /* row1 */ 0x0000, 0xC0C1, 0xC181, 0x0140, 0xC301, 0x03C0, 0x0280, 0xC241,
      /* row2 */ 0xC601, 0x06C0, 0x0780, 0xC741, 0x0500, 0xC5C1, 0xC481, 0x0440,
  };
  // @formatter:on

  public void normalCode() {
    final int x = 1 + 2 + 3;
    System.out.println(x);
  }
}
