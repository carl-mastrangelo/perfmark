package io.perfmark.java7;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

final class StringTableEncoder implements ChannelEncoder.StringTable {

  static final int ABSENT = -1;

  private final int maxByteSize;
  private int currentByteSize;
  private int head;
  private int tail;

  private final String[] stringTable;
  private final int[] lengthTable;
  private final Map<String, Integer> positions = new HashMap<>();

  StringTableEncoder(int maxByteSize) {
    if (maxByteSize < 0) {
      throw new IllegalArgumentException();
    }
    this.maxByteSize = maxByteSize;
    // the plus 1 eases the case where the table is full
    int entryCount = maxByteSize / ChannelEncoder.STRING_OVERHEAD + 1;
    this.stringTable = new String[entryCount];
    this.lengthTable = new int[entryCount];
  }

  @Override
  public void add(String value, int encodedLength) {
    Objects.requireNonNull(value);
    if (positions.containsKey(value)) {
      return;
    }
    encodedLength += ChannelEncoder.STRING_OVERHEAD;
    while (currentByteSize > maxByteSize - encodedLength && head != tail) {
      String toRemove = stringTable[tail];
      stringTable[tail] = null;
      int dropLen = lengthTable[tail];
      // We could remove the length, but meh.
      if (++tail == stringTable.length) {
        tail = 0;
      }
      assert currentByteSize >= dropLen;
      currentByteSize -= dropLen;
      positions.remove(toRemove);
    }
    if (encodedLength <= maxByteSize) {
      assert stringTable[head] == null;
      currentByteSize += encodedLength;
      stringTable[head] = value;
      lengthTable[head] = encodedLength;
      positions.put(value, head);
      if (++head == stringTable.length) {
        head = 0;
      }
    }
  }

  int get(String value) {
    Objects.requireNonNull(value);
    Integer idx = positions.get(value);
    if (idx != null) {
      if (head <= idx) {
        return head + lengthTable.length - 1 - idx;
      } else {
        return head - 1 - idx;
      }
    }
    return ABSENT;
  }

  int size() {
    if (head < tail) {
      return stringTable.length - tail + head;
    }
    return head - tail;
  }

  int byteSize() {
    return currentByteSize;
  }
}
