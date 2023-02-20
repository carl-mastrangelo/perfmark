package io.perfmark.java7;

import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.WritableByteChannel;
import java.util.Objects;

public final class ChannelEncoder implements Closeable {

  static final int STRING_OVERHEAD = 32;

  private static final int INDEXED_STRING = 0x8000_0000;
  private static final int NEW_INDEXED_STRING = 0x0400_0000;

  private final StringTableEncoder taskTable = new StringTableEncoder(32 << 20, (1<<16) - (1<<14));

  private final WritableByteChannel chan;
  private ByteBuffer buffer;

  public ChannelEncoder() throws IOException  {
    chan = new FileOutputStream("/tmp/strout").getChannel();
    buffer = ByteBuffer.allocateDirect(1);
    buffer.order(ByteOrder.BIG_ENDIAN);
  }

  public static void main(String [] args) throws IOException {
    try (ChannelEncoder enc = new ChannelEncoder()) {
      long start = System.nanoTime();
      for (int i = 0; i < 100000000; i++) {
        enc.writeStartTask(System.currentTimeMillis(), "running", "something00000" + i, i);
      }
      long stop = System.nanoTime();
      System.out.println(stop - start);
    }
  }

  @Override
  public void close() throws IOException {
    try (Closeable c = chan) {
      flush();
    }
  }

  private void flush() throws IOException {
    buffer.flip();
    while (buffer.remaining() != 0) {
      if (chan.write(buffer) == 0) {
        throw new IOException("nothing written");
      }
    }
    buffer.clear();
  }

  private void quietFlush() {
    buffer.flip();
    try {
      while (buffer.remaining() != 0) {
        if (chan.write(buffer) == 0) {
          throw new IOException("nothing written");
        }
      }
    } catch (IOException e) {
      // we disregard it
      // this should disable completely too at some point.
    }

    buffer.clear();
  }

  public void writeStartTask(long timeStamp, String taskName, String tagId0, long tagId1) {
    int fixedSize = 1 + 8 + 4 + 4 + 8;
    if (taskName == null) {
      taskName = "";
    }
    if (tagId0 == null) {
      tagId0 = "";
    }
    int estimated = taskName.length() + (taskName.length() >> 3) + tagId0.length() + (tagId0.length() >> 3);
    if (buffer.remaining() < fixedSize + estimated) {
      quietFlush();
    }

    while (true) {
      int readable = buffer.capacity() - buffer.remaining();
      try {
        writeStartTask0(timeStamp, taskName, tagId0, tagId1);
        return;
      } catch (BufferOverflowException e) {
        if (readable == 0) {
          buffer = ByteBuffer.allocateDirect(buffer.capacity() + (buffer.capacity() >> 1) + 1);
        } else {
          quietFlush();
        }
      }
    }
  }



  private void writeStartTask0(long timeStamp, String taskName, String tagId0, long tagId1) {
    buffer.putShort((short) 1);
    buffer.putLong(timeStamp);
    int taskNameSize = 0;// putString(taskTable, taskName);
    int tagSize = 0;//putString(taskTable, tagId0);
    buffer.putLong(tagId1);
    if (taskNameSize >= 0) {
      taskTable.add(taskName, taskNameSize);
    }
    if (tagSize >= 0) {
      taskTable.add(tagId0, tagSize);
    }
  }

  private static final int SHORT_STRING_NEW_BITS = 14;
  private static final int SHORT_STRING_NEW_SIZE_MAX = (1 << SHORT_STRING_NEW_BITS) - 1;
  private static final int SHORT_STRING_BITS = 16;
  private static final int SHORT_STRING_INDEX_MAX = (1 << SHORT_STRING_BITS) - (SHORT_STRING_NEW_SIZE_MAX + 1);

  // returns the new index string size, or else -1;
  /*
  @SuppressWarnings("UnusedMethod")
  private int putShortString(StringTableEncoder table, String value) {
    assert table.maxSize() <= SHORT_STRING_INDEX_MAX;

    int pos = table.get(value);
    int size = -1;
    if (pos < 0) {
      byte[] b = value.getBytes(StandardCharsets.UTF_8);
      size = b.length;
      if (size > SHORT_STRING_NEW_SIZE_MAX) {

      }
      buffer.putInt(NEW_INDEXED_STRING | size);
      buffer.put(b, 0, b.length);
      // TODO(carl-mastrangelo): check size doesn't overflow
    } else {
      // TODO(carl-mastrangelo): check pos doesn't overflow
      buffer.putInt(INDEXED_STRING | pos);
    }
    return size;
  }

   */

  interface StringTable {
    void add(String value, int encodedLength);
  }
}
