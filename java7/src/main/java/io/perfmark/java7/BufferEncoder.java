package io.perfmark.java7;

import static io.perfmark.java7.BufferDecoder.OP_NEW_GEN;
import static io.perfmark.java7.BufferDecoder.OP_PERF_MARK_HEADER_START0;
import static io.perfmark.java7.BufferDecoder.OP_PERF_MARK_HEADER_START1;
import static io.perfmark.java7.BufferDecoder.OP_PERF_MARK_HEADER_VERSION_V1;
import static io.perfmark.java7.BufferDecoder.OP_START_TASK_S1N1;
import static io.perfmark.java7.BufferDecoder.TABLE0_NEWSTRING_MAXSIZE;
import static io.perfmark.java7.BufferDecoder.TABLE0_POS_OFFSET;
import static io.perfmark.java7.BufferDecoder.TABLE1_NEWSTRING_MAXSIZE;
import static io.perfmark.java7.BufferDecoder.TABLE1_POS_OFFSET;

import io.perfmark.impl.Generator;
import io.perfmark.impl.Mark;
import io.perfmark.impl.MarkHolder;
import io.perfmark.impl.Storage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

final class BufferEncoder extends MarkHolder {

  public static final int MIN_BUFFER_SIZE = 131072;

  private static final ByteBuffer DEFAULT_BUF = ByteBuffer.allocate(0);

  private static final long INIT_RANDOM = ThreadLocalRandom.current().nextLong();






  private static final int TABLE0_SIZE = Character.MAX_VALUE + 1 - TABLE0_POS_OFFSET;
  private static final int TABLE0_BYTE_SIZE = TABLE0_SIZE * (ChannelEncoder.STRING_OVERHEAD + 128);

  private static final int TABLE1_SIZE = 1024;
  private static final int TABLE1_BYTE_SIZE = TABLE1_SIZE * (ChannelEncoder.STRING_OVERHEAD + 128);

  static {
    assert TABLE0_SIZE == 49152;
  }

  private final StringTableEncoder table0;
  private final StringTableEncoder table1;
  private final Flusher flusher;
  private ByteBuffer buffer = DEFAULT_BUF;
  private Throwable failure;

  private long currentGen = Generator.FAILURE;

  BufferEncoder(Flusher flusher) {
    this.flusher = Objects.requireNonNull(flusher, "flusher");
    this.table0 = new StringTableEncoder(TABLE0_SIZE, TABLE0_BYTE_SIZE);
    this.table1 = new StringTableEncoder(TABLE1_SIZE, TABLE1_BYTE_SIZE);
  }

  interface Flusher {
    ByteBuffer apply(ByteBuffer src);
  }

  void startEncoder() {
    if (!maybeFlush(2 + 2 + 2 + 4 + 4 + 4 + 4 + 8 + 8)) {
      return;
    }
    buffer.putChar(OP_PERF_MARK_HEADER_START0)
        .putChar(OP_PERF_MARK_HEADER_START1)
        .putChar(OP_PERF_MARK_HEADER_VERSION_V1);
    buffer.putInt(TABLE0_SIZE).putInt(TABLE0_BYTE_SIZE);
    buffer.putInt(TABLE1_SIZE).putInt(TABLE1_BYTE_SIZE);
    buffer.putLong(Storage.getInitNanoTime());
    // FIXME: add the currentTimeMillis, and init random
    buffer.putLong(INIT_RANDOM);
  }


  @SuppressWarnings({"unused", "CheckReturnValue"})
  void finishEncoder() {
    forceFlush();
  }

  private static String deNull(String s) {
    if (s == null) {
      return "";
    }
    return s;
  }

  @Override
  public void start(long gen, String taskName, String tagName, long tagId, long nanoTime) {
    taskName = deNull(taskName);
    tagName = deNull(taskName);
    int taskNamePos = table0.get(taskName);
    int taskNameByteCount;
    byte[] taskNameBytes;
    if (taskNamePos < 0) {
      taskNameBytes = taskName.getBytes(StandardCharsets.UTF_8);
      taskNamePos = Math.min(taskNameBytes.length, TABLE0_NEWSTRING_MAXSIZE);
      taskNameByteCount = taskNamePos;
    } else {
      taskNamePos += TABLE0_POS_OFFSET;
      taskNameByteCount = 0;
      taskNameBytes = null;
    }

    int tagNamePos = table1.get(tagName);
    int tagNameByteCount;
    byte[] tagNameBytes;
    if (tagNamePos < 0) {
      tagNameBytes = tagName.getBytes(StandardCharsets.UTF_8);
      tagNamePos = Math.min(tagNameBytes.length, TABLE1_NEWSTRING_MAXSIZE);
      tagNameByteCount = tagNamePos;
    } else {
      tagNamePos += TABLE1_POS_OFFSET;
      tagNameBytes = null;
      tagNameByteCount = 0;
    }

    long neededSize = 2L + 10 + 2 + taskNameByteCount + 2 + tagNameByteCount + 8 + 8;
    if (!maybeFlush(neededSize)) {
      return;
    }
    maybePutGen(gen);
    buffer.putChar(OP_START_TASK_S1N1);
    assert taskNamePos <= Character.MAX_VALUE;
    buffer.putChar((char) taskNamePos);
    if (taskNameBytes != null) {
      buffer.put(taskNameBytes, 0, taskNameByteCount);
      table0.add(taskName, taskNameByteCount);
    }

    assert tagNamePos <= Character.MAX_VALUE;
    buffer.putChar((char) tagNamePos);
    if (tagNameBytes != null) {
      buffer.put(tagNameBytes, 0, tagNameByteCount);
      table1.add(tagName, tagNameByteCount);
    }
    buffer.putLong(tagId);
    buffer.putLong(nanoTime);
  }

  @Override
  public void start(long gen, String taskName, long nanoTime) {
  }

  @Override
  public void start(long gen, String taskName, String subTaskName, long nanoTime) {
  }

  @Override
  public void link(long gen, long linkId) {
  }

  @Override
  public void stop(long gen, long nanoTime) {
  }

  @Override
  public void stop(long gen, String taskName, String tagName, long tagId, long nanoTime) {
  }

  @Override
  public void stop(long gen, String taskName, long nanoTime) {
  }

  @Override
  public void stop(long gen, String taskName, String subTaskName, long nanoTime) {
  }

  @Override
  public void event(long gen, String eventName, String tagName, long tagId, long nanoTime) {
  }

  @Override
  public void event(long gen, String eventName, long nanoTime) {
  }

  @Override
  public void event(long gen, String eventName, String subEventName, long nanoTime) {
  }

  @Override
  public void attachTag(long gen, String tagName, long tagId) {
  }

  @Override
  public void attachKeyedTag(long gen, String name, String value) {
  }

  @Override
  public void attachKeyedTag(long gen, String name, long value0) {
  }

  @Override
  public void attachKeyedTag(long gen, String name, long value0, long value1) {
  }

  @Override
  public void resetForTest() {
  }

  @Override
  public List<Mark> read(boolean concurrentWrites) {
    return Collections.emptyList();
  }

  private boolean maybeFlush(long neededSize) {
    assert neededSize <= MIN_BUFFER_SIZE;
    if (failure != null) {
      return false;
    }
    if (buffer.remaining() < neededSize) {
      return forceFlush();
    }
    return true;
  }

  private boolean forceFlush() {
    try {
      ByteBuffer newBuffer = flusher.apply(buffer);
      Objects.requireNonNull(newBuffer, "newBuffer");
      if (newBuffer.remaining() < MIN_BUFFER_SIZE) {
        throw new IllegalArgumentException("Buffer Too Small");
      }
      if (newBuffer.order() != ByteOrder.BIG_ENDIAN) {
        newBuffer.order(ByteOrder.BIG_ENDIAN);
      }
      buffer = newBuffer;
    } catch (Throwable t) {
      failure = t;
      buffer = DEFAULT_BUF;
      return false;
    }
    return true;
  }

  private void maybePutGen(long gen) {
    if (gen != currentGen) {
      currentGen = gen;
      buffer.putChar(OP_NEW_GEN);
      buffer.putLong(gen);
    }
  }
}
