// record-header-break: top-level record headers that wrap
class Outer {
  private record NestedTreeParameter(
    @RecordNumber String recordNumber,
    @Note String note,
    boolean showAggregatedItemValues
  ) implements RecordPartId {
    @Override
    public String getRecordNumber() {
      return recordNumber;
    }
  }

  private record ShortRecord(String a, int b) implements Marker {}

  private record NoInterface(
    @RecordNumber String recordNumber,
    @Note String note,
    boolean showAggregatedItemValues,
    String extraComponent
  ) {}
}

class RecordPatternCases {

  // record-pattern-breaks-when-too-wide: record pattern in switch breaks when too wide
  String describeBreaks(final Object value) {
    return switch (value) {
      case PairOfLongIdentifierNames(
        final SomeVeryLongIdentifierType firstComponentNameAndMore,
        final SomeVeryLongIdentifierType secondComponentNameAndMore
      ) -> firstComponentNameAndMore.toString();
      default -> "";
    };
  }

  // record-pattern-fits-one-line: record patterns that fit on one line
  long sizeFits(final Object value) {
    return switch (value) {
      case Pair(final Boolean value1, final Boolean value2) -> 1L;
      case Triple(final Integer a, final Integer b, final Integer c) -> 2L;
      default -> 0L;
    };
  }

  // record-pattern-with-chain-body: long chain as switch arm body wraps
  // Long chain as switch arm body — the full line > 140 chars, chain should wrap
  long computeChainBody(Object obj) {
    return switch (obj) {
      case BigDecimal bd -> bd.subtract(baseline).movePointRight(precisionValue).setScale(0, RoundingMode.HALF_UP).longValueExact() +
        offset;
      default -> 0L;
    };
  }
}

class CompactConstructorComments {

  // compact-constructor-comments: javadoc and line comments on a compact constructor survive
  record Validated(int value) {

    /**
     * Rejects negative values.
     */
    // implementation note above the constructor
    public Validated {
      // trailing comment
      if (value < 0) {
        throw new IllegalArgumentException("negative: " + value);
      }
    }
  }
}
