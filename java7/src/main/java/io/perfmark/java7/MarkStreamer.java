package io.perfmark.java7;

import io.perfmark.impl.MarkRecorder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public final class MarkStreamer extends MarkRecorder {

  private final BufferProvider provider;
  private final long initNanos;
  private final StringTableEncoder table0;
  private final StringTableEncoder table1;

  interface BufferProvider {
    ByteBuffer allocate(int size);
    void flush(ByteBuffer bufferToRelease);
  }

  MarkStreamer(long initNanos, BufferProvider provider, StringTableEncoder table0, StringTableEncoder table1) {
    this.table0 = table0;
    this.table1 = table1;
    this.initNanos = initNanos;
    this.provider = provider;
  }

  private static final int STRING_INDEX_OFFSET = (1<<16) - (1<<14);
  private static final int STRING_MAX_LENGTH = (1<<13) - 1;
  private static final int STRING_NOINDEX_OFFSET = STRING_INDEX_OFFSET + STRING_MAX_LENGTH + 1;

  private static final char START_TASK_OP = 2;
  private static final char STOP_TASK_OP = 3;
  private static final char ATTACH_STRING_TAG_OP = 6;
  private static final char ATTACH_LONG_TAG_OP = 7;
  private static final char ATTACH_LONG_LONG_TAG_OP = 8;
  private static final char LINK_OP = 9;
  private static final int START_TASK_STATIC = 2 + 8 + 2 + 2;
  private static final int STOP_TASK_STATIC = 2 + 8 + 2 + 2;
  private static final int ATTACH_STRING_TAG_STATIC = 2 + 2 + 2;
  private static final int ATTACH_LONG_TAG_STATIC = 2 + 2 + 8;
  private static final int ATTACH_LONG_LONG_TAG_STATIC = 2 + 2 + 8 + 8;
  private static final int LINK_STATIC = 2 + 8 + 8;

  private static final int STRING_EMPTY_NOINDEX = STRING_NOINDEX_OFFSET;

  @Override
  public void start(long gen, String taskName, String tagName, long tagId, long nanoTime) {
    ByteBuffer buf = null;
    buf = startTask(buf, taskName, null, nanoTime);
    buf = writeTag(buf, null, tagName);
    buf = writeTag(buf, null, tagId);
    provider.flush(buf);
  }

  @Override
  public void start(long gen, String taskName, long nanoTime) {
    ByteBuffer buf = null;
    startTask(buf, taskName, null, nanoTime);
    provider.flush(buf);
  }

  @Override
  public void start(long gen, String taskName, String subTaskName, long nanoTime) {
    ByteBuffer buf = null;
    buf = startTask(buf, taskName, subTaskName, nanoTime);
    provider.flush(buf);
  }

  @Override
  public void link(long gen, long linkId) {
    ByteBuffer buf = null;
    buf = alloc(buf, LINK_STATIC);
    buf.putChar(LINK_OP);
    buf.putLong(linkId);
    buf.putLong(0);
    provider.flush(buf);
  }

  @Override
  public void stop(long gen, long nanoTime) {
    ByteBuffer buf = null;
    buf = stopTask(buf, null, null, nanoTime);
    provider.flush(buf);
  }

  @Override
  public void stop(long gen, String taskName, String tagName, long tagId, long nanoTime) {
    ByteBuffer buf = null;
    buf = writeTag(buf, null, tagName);
    buf = writeTag(buf, null, tagId);
    buf = stopTask(buf, null, null, nanoTime);
    provider.flush(buf);
  }

  @Override
  public void stop(long gen, String taskName, long nanoTime) {
    ByteBuffer buf = null;
    buf = stopTask(buf, taskName, null, nanoTime);
    provider.flush(buf);
  }

  @Override
  public void stop(long gen, String taskName, String subTaskName, long nanoTime) {
    ByteBuffer buf = null;
    buf = stopTask(buf, taskName, subTaskName, nanoTime);
    provider.flush(buf);
  }

  @Override
  public void event(long gen, String eventName, String tagName, long tagId, long nanoTime) {
    ByteBuffer buf = null;
    buf = startTask(buf, eventName, null, nanoTime);
    buf = writeTag(buf, null, tagName);
    buf = writeTag(buf, null, tagId);
    buf = stopTask(buf, null, null, nanoTime);
    provider.flush(buf);
  }

  @Override
  public void event(long gen, String eventName, long nanoTime) {
    ByteBuffer buf = startTask(null, eventName, null, nanoTime);
    buf = stopTask(buf, null, null, nanoTime);
    provider.flush(buf);
  }

  @Override
  public void event(long gen, String eventName, String subEventName, long nanoTime) {
    ByteBuffer buf = startTask(null, eventName, subEventName, nanoTime);
    buf = stopTask(buf, null, null, nanoTime);
    provider.flush(buf);
  }

  @Override
  public void attachTag(long gen, String tagName, long tagId) {
    ByteBuffer buf = null;
    buf = writeTag(buf, null, tagName);
    buf = writeTag(buf, null, tagId);
    provider.flush(buf);
  }

  @Override
  public void attachKeyedTag(long gen, String name, String value) {
    ByteBuffer buf = null;
    buf = writeTag(buf, name, value);
    provider.flush(buf);
  }

  @Override
  public void attachKeyedTag(long gen, String name, long value0) {
    ByteBuffer buf = null;
    buf = writeTag(buf, name, value0);
    provider.flush(buf);
  }

  @Override
  public void attachKeyedTag(long gen, String name, long value0, long value1) {
    ByteBuffer buf = null;
    buf = alloc(buf, ATTACH_LONG_LONG_TAG_STATIC);
    buf.putChar(ATTACH_LONG_LONG_TAG_OP);
    byte[][] values = new byte[1][];
    int[] lengths = new int[1];
    int idx = 0;
    idx = pushString(idx, values, lengths, buf, name, table0);
    buf.putLong(value0);
    buf.putLong(value1);
    buf = writeStrings(idx, values, lengths, buf);
    provider.flush(buf);
  }

  private ByteBuffer startTask(ByteBuffer buf, String taskName, String subTaskName, long nanoTime) {
    buf = alloc(buf, START_TASK_STATIC);
    buf.putChar(START_TASK_OP);
    buf.putLong(nanoTime - initNanos);
    byte[][] values = new byte[2][];
    int[] lengths = new int[2];
    int idx = 0;
    idx = pushString(idx, values, lengths, buf, taskName, table0);
    idx = pushString(idx, values, lengths, buf, subTaskName, table0);
    return writeStrings(idx, values, lengths, buf);
  }

  private ByteBuffer stopTask(ByteBuffer buf, String taskName, String subTaskName, long nanoTime) {
    buf = alloc(buf, STOP_TASK_STATIC);
    buf.putChar(STOP_TASK_OP);
    buf.putLong(nanoTime - initNanos);
    byte[][] values = new byte[2][];
    int[] lengths = new int[2];
    int idx = 0;
    idx = pushString(idx, values, lengths, buf, taskName, table0);
    idx = pushString(idx, values, lengths, buf, subTaskName, table0);
    return writeStrings(idx, values, lengths, buf);
  }

  private static int pushString(
      int idx, byte[][] values, int[]lengths, ByteBuffer buf, String value, StringTableEncoder table) {
    assert buf.remaining() >= 2;
    if (value == null || value.equals("")) {
      buf.putChar(toChar(STRING_EMPTY_NOINDEX));
      return idx;
    }
    int pos = table.get(value);
    if (pos != StringTableEncoder.ABSENT) {
      buf.putChar(toChar(pos));
      return idx;
    }
    byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
    int valueLength = Math.min(valueBytes.length, STRING_MAX_LENGTH);
    table.add(value, valueLength);
    buf.putChar(toChar(valueLength));
    values[idx] = valueBytes;
    lengths[idx] = valueLength;
    return idx + 1;
  }

  private ByteBuffer writeStrings(int idx, byte[][] values, int[]lengths, ByteBuffer buf) {
    int wantedDynamicBytes = 0;
    for (int i = 0; i < idx; i++) {
      wantedDynamicBytes += lengths[i];
    }
    buf = alloc(buf, wantedDynamicBytes);
    for (int i = 0; i < idx; i++) {
      buf.put(values[i], 0, lengths[i]);
    }
    return buf;
  }

  private ByteBuffer writeTag(ByteBuffer buf, String tagName, String tagValue) {
    buf = alloc(buf, ATTACH_STRING_TAG_STATIC);
    buf.putChar(ATTACH_STRING_TAG_OP);
    byte[][] values = new byte[2][];
    int[] lengths = new int[2];
    int idx = 0;
    idx = pushString(idx, values, lengths, buf, tagName, table0);
    idx = pushString(idx, values, lengths, buf, tagValue, table1);
    return writeStrings(idx, values, lengths, buf);
  }

  private ByteBuffer writeTag(ByteBuffer buf, String tagName, long tagValue) {
    buf = alloc(buf, ATTACH_LONG_TAG_STATIC);
    buf.putChar(ATTACH_LONG_TAG_OP);
    byte[][] values = new byte[1][];
    int[] lengths = new int[1];
    int idx = 0;
    idx = pushString(idx, values, lengths, buf, tagName, table0);
    buf.putLong(tagValue);
    return writeStrings(idx, values, lengths, buf);
  }

  private ByteBuffer alloc(ByteBuffer previous, int size) {
    if (previous != null) {
      if (previous.remaining() >= size) {
        return previous;
      } else {
        provider.flush(previous);
      }
    }
    ByteBuffer ret = provider.allocate(size);
    if (ret.order() != ByteOrder.LITTLE_ENDIAN) {
      ret.order(ByteOrder.LITTLE_ENDIAN);
    }
    assert ret.remaining() >= size;
    return ret;
  }

  private static char toChar(int value) {
    assert value >= 0;
    assert value <= Character.MAX_VALUE;
    return (char) value;
  }
}
