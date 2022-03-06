package io.perfmark.java7;

import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public class ChannelEncoder implements Closeable {

  static final int STRING_OVERHEAD = 32;

  private static final int INDEXED_STRING = 0x80000000;
  private static final int NEW_INDEXED_STRING = 0x04000000;

  private final StringTableEncoder taskTable = new StringTableEncoder(32 << 20);

  private final WritableByteChannel chan;
  private ByteBuffer buffer;
  private final CharsetEncoder encoder =
      StandardCharsets.UTF_8.newEncoder()
          .onMalformedInput(CodingErrorAction.REPLACE)
          .onUnmappableCharacter(CodingErrorAction.REPLACE);

  public ChannelEncoder() throws IOException  {
    chan = new FileOutputStream("/tmp/strout").getChannel();
    buffer = ByteBuffer.allocateDirect(1048576);
    buffer.order(ByteOrder.BIG_ENDIAN);
  }


  public static void main(String [] args) throws IOException {
    try (ChannelEncoder enc = new ChannelEncoder()) {
      long start = System.nanoTime();
      for (int i = 0; i < 1000000000; i++) {
        enc.writeStartTask(System.currentTimeMillis(), "running", "something" + (i & 0xF), i);
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
          buffer = ByteBuffer.allocateDirect(buffer.capacity() + (buffer.capacity() >> 1));
        } else {
          quietFlush();
        }
      }
    }
  }

  private void writeStartTask0(long timeStamp, String taskName, String tagId0, long tagId1) {
    buffer.putShort((short) 1);
    buffer.putLong(timeStamp);
    int taskNameSize = putString(taskTable, taskName);
    int tagSize = putString(taskTable, tagId0);
    buffer.putLong(tagId1);
    if (taskNameSize >= 0) {
      taskTable.add(taskName, taskNameSize);
    }
    if (tagSize >= 0) {
      taskTable.add(tagId0, tagSize);
    }
  }

  // returns the new index string size, or else -1;
  private int putString(StringTableEncoder table, String value) {
    int pos = table.get(value);
    int size = -1;
    if (pos < 0) {
      int bufPos = buffer.position();
      buffer.putInt(0);
      CharBuffer cbuf = CharBuffer.wrap(value);
      CoderResult res = encoder.encode(cbuf, buffer, true);
      if (res.isError()) {
        // the encoder is setup to be forgiving, so this shouldn't happen.
        throw new RuntimeException(res.toString());
      }
      if (res.isOverflow()) {
        throw new BufferOverflowException();
      }
      int newBufPos = buffer.position();
      size = newBufPos - bufPos - 4;
      // TODO(carl-mastrangelo): check size doesn't overflow
      buffer.putInt(bufPos, NEW_INDEXED_STRING | size);
    } else {
      // TODO(carl-mastrangelo): check pos doesn't overflow
      buffer.putInt(INDEXED_STRING | pos);
    }
    return size;
  }

  interface StringTable {
    void add(String value, int encodedLength);
  }
}
