package io.perfmark.java7;

import io.perfmark.impl.Generator;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

final class BufferDecoder {

  private static final int READ_VERSION = 1;
  private static final int READ_HEADER = 2;
  private static final int READ_OP = 3;
  private static final int READ_OP_NEW_GEN = 4;
  private static final int READ_OP_START_TASK_S1N1 = 5;

  static final char OP_PERF_MARK_HEADER_START0 = 'P' + ('F'<<8);
  static final char OP_PERF_MARK_HEADER_START1 = 'M' + ('K'<<8);
  static final char OP_PERF_MARK_HEADER_VERSION_V1 = (1 << 8);

  private static final int READ_VERSION_BYTES = 6;
  private static final int READ_HEADER_BYTES = 4 + 4 + 4 + 4 + 8 + 8;
  private static final int READ_OP_BYTES = 2;

  static final char OP_NEW_GEN = 1;
  private static final char OP_NEW_GEN_BYTES = 8;

  private static final char OP_START_TASK_S1N1 = 2;
  private static final char OP_START_TASK_S1N1_STATIC_BYTES = 2 + 2 + 8 + 8;

  private final ByteBuffer buf = ByteBuffer.allocate(BufferEncoder.MIN_BUFFER_SIZE);

  private int state = READ_VERSION;
  private int bytesWanted = READ_VERSION_BYTES;

  private StringTableDecoder table0;
  private StringTableDecoder table1;
  private long gen = Generator.FAILURE;
  private long initNanoTime;
  private long random;

  private void readVersion() {
    assert state == READ_VERSION;
    assert bytesWanted == READ_VERSION_BYTES;
    assert buf.remaining() >= bytesWanted;
    checkBufChar(OP_PERF_MARK_HEADER_START0);
    checkBufChar(OP_PERF_MARK_HEADER_START1);
    checkBufChar(OP_PERF_MARK_HEADER_VERSION_V1);
    bytesWanted = READ_HEADER_BYTES;
    state = READ_HEADER;
  }

  private void readHeader() {
    assert state == READ_HEADER;
    assert bytesWanted == READ_HEADER_BYTES;
    assert buf.remaining() >= bytesWanted;
    int table0Size = buf.getInt();
    int table0ByteSize = buf.getInt();
    int table1Size = buf.getInt();
    int table1ByteSize = buf.getInt();
    // TODO(carl-mastrangelo): don't mutate the buffer or member variables until everything is verified.
    initNanoTime = buf.getLong();
    random = buf.getLong();
    table0 = new StringTableDecoder(table0ByteSize, table0Size);
    table1 = new StringTableDecoder(table1ByteSize, table1Size);
    bytesWanted = READ_OP_BYTES;
    state = READ_OP;
  }

  private void readOp() {
    assert state == READ_OP;
    assert bytesWanted == READ_HEADER_BYTES;
    assert buf.remaining() >= bytesWanted;
    char op = buf.getChar();
    switch (op) {
      case OP_NEW_GEN:
        bytesWanted = OP_NEW_GEN_BYTES;
        state = READ_OP_NEW_GEN;
        break;
      case OP_START_TASK_S1N1:
        bytesWanted = OP_START_TASK_S1N1_STATIC_BYTES;
        state = READ_OP_START_TASK_S1N1;
        break;
      default:
        throw new IllegalArgumentException("Bad OP " + op);
    }
  }

  private void readOpNewGen() {
    assert state == READ_OP_NEW_GEN;
    assert bytesWanted == OP_NEW_GEN_BYTES;
    assert buf.remaining() >= bytesWanted;
    gen = buf.getLong();
    bytesWanted = READ_OP_BYTES;
    state = READ_OP;
  }

  private void readOpStartTaskS1n1() {
    assert state == READ_OP_START_TASK_S1N1;
    assert bytesWanted >= OP_START_TASK_S1N1_STATIC_BYTES;
    assert buf.remaining() >= bytesWanted;
    int pos = buf.position();
    int taskNamePos = buf.getChar(pos);
    int tagNamePos = buf.getChar(pos + 2);

    int dynamicBytesWanted = 0;
    if (taskNamePos < 16384) {
      dynamicBytesWanted += taskNamePos & 0x;
    }

    if (buf.remaining() < bytesWanted + dynamicBytesWanted) {
      return;
    }


    gen = buf.getLong();
    bytesWanted = READ_OP_BYTES;
    state = READ_OP;
  }


  void onData() {
    while (buf.remaining() >= bytesWanted) {
      switch (state) {
        case READ_VERSION:
          readVersion();
          break;
        case READ_HEADER:
          readHeader();
          break;
        case READ_OP:
          readOp();
          break;
        case READ_OP_NEW_GEN:
          readOpNewGen();
          break;
        case READ_OP_START_TASK_S1N1:
          readOpStartTaskS1n1();
          break;
        default:
          throw new AssertionError();
      }
    }
  }

  void checkBufChar(char expected) {
    int pos = buf.position();
    char c = buf.getChar();
    if (c != expected) {
      throw new IllegalArgumentException(
          "Buffer doesn't contain " + expected + " at " + pos + ": " + c);
    }
  }
}
