package com.example;

public class PlaceholderCollision {

  public void mentionsPlaceholderText() {
    // literal //jacet:off-region:0 in a comment must not be mistaken for the real placeholder
    final String marker = "//jacet:off-region:0";
    System.out.println(marker);
  }

  // @formatter:off
  private final int[]   keepAsIs = { 1,2,
      3 };
  // @formatter:on
}
