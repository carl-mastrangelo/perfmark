package io.perfmark.java7;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

final class StringTableEncoder extends AbstractStringTable {

  static final int ABSENT = -1;

  private final Map<String, Integer> positions = new HashMap<>();

  StringTableEncoder(int maxByteSize) {
    super(maxByteSize);
  }

  @Override
  protected void valueAdded(String value, int index) {
    // If duplicate values are put in, the later ones become inaccessible.  Ideally, the caller should check if
    // get() returned an index, but nothing forces it.
    positions.put(value, index);
  }

  @Override
  protected void valueRemove(String value) {
    positions.remove(value);
  }

  /**
   * Returns the position of the given string in the table, or {@code -1} if absent.
   * If the value is present in the table multiple times, the one with a smaller index is returned.
   */
  int get(String value) {
    Objects.requireNonNull(value);
    Integer index = positions.get(value);
    if (index != null) {
      return indexToPosition(index);
    }
    return ABSENT;
  }
}
