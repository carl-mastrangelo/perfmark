package io.perfmark.java7;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

abstract class StringTable implements Iterable<String> {

  private final int maxByteSize;
  private int currentByteSize;

  private final String[] table;
  private int head;
  private int tail;

  StringTable(int maxByteSize) {
    if (maxByteSize < 0) {
      throw new IllegalArgumentException();
    }
    this.maxByteSize = maxByteSize;
    this.table = new String[maxByteSize];
  }

  /**
   * This can't be the same as {@link Collection#add}, because the element may not be in the collection.
   *
   * @param value The value to add
   * @return true if the element is now in the table, or false if the table was cleared.
   */
  public boolean add(String value, int len) {
    Objects.requireNonNull(value);

    assert value.getBytes(StandardCharsets.UTF_8).length == len;

    while (currentByteSize > maxByteSize - len && head != tail) {
      String toDrop = table[tail];
      table[tail] = null;
      int dropLen =0;
      if (++tail == table.length) {
        tail = 0;
      }
      assert currentByteSize >= dropLen;
      currentByteSize -= dropLen;
      onRemove(toDrop);
    }
    if (len <= maxByteSize) {
      currentByteSize += len;
      table[head] = value;
      if (++head == table.length) {
        head = 0;
      }
      return true;
    }

    return false;
  }

  /**
   * This method is for override, and never does anything on its own.
   */
  protected void onRemove(String value) {
    // For Override
  }

  final int size() {
    if (head < tail) {
      return table.length - tail + head;
    }
    return head - tail;
  }

  @Override
  public final Iterator<String> iterator() {
    final class TableItr implements Iterator<String> {
      private final int localHead = head;
      private final int localTail = tail;

      private int idx;

      @Override
      public boolean hasNext() {
        if (head != localHead || tail != localTail) {
          throw new ConcurrentModificationException();
        }
        return idx < size();
      }

      @Override
      public String next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        return null; //get(idx++);
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    }
    return new TableItr();
  }
}
