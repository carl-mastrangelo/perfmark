package io.perfmark.java7;

final class StringTableDecoder extends AbstractStringTable {

  StringTableDecoder(int maxByteSize) {
    super(maxByteSize);
  }

  StringTableDecoder(int maxByteSize, int maxEntries) {
    super(maxByteSize, maxEntries);
  }
}
