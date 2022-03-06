package io.perfmark.java7;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

abstract class AbstractStringTable implements ChannelEncoder.StringTable, Iterable<String> {

  private final int maxByteSize;
  private int currentByteSize;
  private int head;
  private int size;

  private final String[] stringTable;
  private final int[] lengthTable;

  AbstractStringTable(int maxByteSize) {
    if (maxByteSize < 0) {
      throw new IllegalArgumentException();
    }
    this.maxByteSize = maxByteSize;
    int entryCount = maxByteSize / ChannelEncoder.STRING_OVERHEAD;
    this.stringTable = new String[entryCount];
    this.lengthTable = new int[entryCount];
  }

  @Override
  public final void add(final String value, final int encodedLength) {
    Objects.requireNonNull(value);
    if (encodedLength > Integer.MAX_VALUE - ChannelEncoder.STRING_OVERHEAD) {
      throw new IllegalArgumentException("String too big");
    } else if (encodedLength < 0) {
      throw new IllegalArgumentException("String too small");
    }
    final int entryLength = encodedLength + ChannelEncoder.STRING_OVERHEAD;

    while (currentByteSize > maxByteSize - entryLength && size > 0) {
      int tail = tail();
      String toRemove = stringTable[tail];
      stringTable[tail] = null;
      int dropLen = lengthTable[tail];
      // We could remove the length, but meh.
      assert currentByteSize >= dropLen;
      currentByteSize -= dropLen;
      size--;
      // Update our state before calling into subclasses.
      valueRemove(toRemove);
    }
    if (entryLength <= maxByteSize) {
      assert stringTable[head] == null;
      assert size < stringTable.length;
      currentByteSize += entryLength;
      stringTable[head] = value;
      lengthTable[head] = entryLength;
      int oldHead = head;
      if (++head == stringTable.length) {
        head = 0;
      }
      size++;
      valueAdded(value, oldHead);
    }
  }

  final String get(int position) {
    if (position >= size()) {
      throw new IndexOutOfBoundsException();
    }
    int index = head - 1 - position;
    if (index < 0) {
      index += stringTable.length;
    }
    assert stringTable[index] != null;
    return stringTable[index];
  }

  protected void valueRemove(String value) {
    // Don't call this from a child class.
  }

  protected void valueAdded(String value, int index) {
    // Don't call this from a child class.
  }

  protected final int indexToPosition(int index) {
    assert index >= 0;
    assert index < stringTable.length;
    assert size >= 0;

    int position = head - index  - 1;
    if (position < 0) {
      position += stringTable.length;
    }
    return position;
  }

  @Override
  public final Iterator<String> iterator() {
    final class TableItr implements Iterator<String> {
      private final int localHead = head;
      private final int localSize = size;

      private int position;

      @Override
      public boolean hasNext() {
        if (head != localHead || size != localSize) {
          throw new ConcurrentModificationException();
        }
        return position < size();
      }

      @Override
      public String next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        return get(position++);
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    }
    return new TableItr();
  }

  final int size() {
    return size;
  }

  final int byteSize() {
    return currentByteSize;
  }

  private int tail() {
    assert size > 0;
    int tail = head - size;
    if (tail < 0) {
      tail += stringTable.length;
    }
    return tail;
  }
}
