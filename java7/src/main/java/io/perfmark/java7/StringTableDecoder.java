package io.perfmark.java7;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

final class StringTableDecoder implements ChannelEncoder.StringTable {
  private final int maxByteSize;
  private int currentByteSize;
  // head points to the next location to write to.  If head == tail, the table is empty.
  private int head;
  // tail points to the last element, i.e. the element to be dropped soonest.
  // If head == tail, the table is empty.
  private int tail;

  private final String[] stringTable;
  private final int[] lengthTable;

  StringTableDecoder(int maxByteSize) {
    if (maxByteSize < 0) {
      throw new IllegalArgumentException();
    }
    this.maxByteSize = maxByteSize;
    int entryCount = maxByteSize / ChannelEncoder.STRING_OVERHEAD + 1;
    this.stringTable = new String[entryCount];
    this.lengthTable = new int[entryCount];
  }

  @Override
  public void add(String value, int encodedLength) {
    Objects.requireNonNull(value);
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
    }

    if (encodedLength <= maxByteSize) {
      assert stringTable[head] == null;
      currentByteSize += encodedLength;
      stringTable[head] = value;
      lengthTable[head] = encodedLength;
      if (++head == stringTable.length) {
        head = 0;
      }
    }
  }

  String get(int index) {
    if (index >= size()) {
      throw new IndexOutOfBoundsException();
    }
    int i = head - 1 - index;
    if (i < 0) {
      i += stringTable.length;
    }
    assert stringTable[i] != null;
    return stringTable[i];
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
