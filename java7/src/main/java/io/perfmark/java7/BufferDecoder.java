package io.perfmark.java7;

import io.perfmark.impl.Generator;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

final class BufferDecoder {

  private static final int CHAR_BYTES = 2;
  private static final int INT_BYTES = 4;
  private static final int LONG_BYTES = 8;

  enum State {
    READ_VERSION,
    READ_HEADER,
    READ_OP,
    READ_OP_NEW_GEN,
    READ_OP_START_TASK_S1N1,
    ;
  }

  enum Op {
    PERF_MARK_HEADER_START0('P' + ('F'<<8), 0),
    PERF_MARK_HEADER_START1('M' + ('K'<<8), 0),
    PERF_MARK_HEADER_VERSION_V1(1 << 8, 4 * INT_BYTES + 2 * LONG_BYTES);
    ;

    private final char code;
    private final int staticBytes;

    Op(int code, int staticBytes) {
      if (code < 0 || code > Character.MAX_VALUE) {
        throw new IllegalArgumentException();
      }
      if (staticBytes < 0) {
        throw new IllegalArgumentException();
      }
      this.code = (char) code;
      this.staticBytes = staticBytes;
    }

    static Map<Character, Op> codes = new HashMap<>();

    static {
      for (Op op : values()) {
        codes.put(op.code(), op);
      }
    }

    int staticBytes() {
      return staticBytes;
    }

    char code() {
      return code;
    }

    int totalBytes(int dynamicBytes) {
      if (dynamicBytes < 0) {
        throw new IllegalArgumentException();
      }
      if (Integer.MAX_VALUE - CHAR_BYTES - staticBytes > dynamicBytes) {
        throw new IllegalArgumentException();
      }
      return CHAR_BYTES + staticBytes + dynamicBytes;
    }
  }

  static final int TABLE0_NEWSTRING_MAXSIZE_BITS = 13;
  static final int TABLE0_NEWSTRING_MAXSIZE = (1 << TABLE0_NEWSTRING_MAXSIZE_BITS) - 1;
  static final int TABLE0_POS_OFFSET = (1 << (TABLE0_NEWSTRING_MAXSIZE_BITS + 1));

  static final int TABLE1_NEWSTRING_MAXSIZE_BITS = 13;
  static final int TABLE1_NEWSTRING_MAXSIZE = (1 << TABLE1_NEWSTRING_MAXSIZE_BITS) - 1;
  static final int TABLE1_POS_OFFSET = (1 << (TABLE1_NEWSTRING_MAXSIZE_BITS + 1));

  static final char OP_NEW_GEN = 1;
  private static final char OP_NEW_GEN_BYTES = 8;

  static final char OP_START_TASK_S1N1 = 2;
  private static final char OP_START_TASK_S1N1_STATIC_BYTES = 8 + 8;
  private static final char OP_START_TASK_S1N1_MIN_BYTES = 2 + 2 + OP_START_TASK_S1N1_STATIC_BYTES;

  static {
    assert TABLE0_NEWSTRING_MAXSIZE == 8191;
    assert TABLE0_POS_OFFSET == 16384;

    assert TABLE1_NEWSTRING_MAXSIZE == 8191;
    assert TABLE1_POS_OFFSET == 16384;
  }

  private final ByteBuffer buf = ByteBuffer.allocate(BufferEncoder.MIN_BUFFER_SIZE);

  private State state = State.READ_VERSION;
  private int bytesWanted = CHAR_BYTES * 3;

  private StringTableDecoder table0;
  private StringTableDecoder table1;
  private long gen = Generator.FAILURE;
  private long initNanoTime;
  private long random;

  private void readVersion() {
    assert state == State.READ_VERSION;
    assert bytesWanted == CHAR_BYTES * 3;
    assert buf.remaining() >= bytesWanted;
    int pos = buf.position();
    checkChar(buf, pos, Op.PERF_MARK_HEADER_START0.code());
    checkChar(buf, pos + CHAR_BYTES, Op.PERF_MARK_HEADER_START1.code());
    checkChar(buf, pos + CHAR_BYTES * 2, Op.PERF_MARK_HEADER_VERSION_V1.code());

    bytesWanted = Op.PERF_MARK_HEADER_VERSION_V1.staticBytes();
    state = State.READ_HEADER;
  }

  private void readHeader() {
    assert state == State.READ_HEADER;
    assert bytesWanted == Op.PERF_MARK_HEADER_VERSION_V1.staticBytes();
    assert buf.remaining() >= bytesWanted;
    int table0Size = buf.getInt();
    int table0ByteSize = buf.getInt();
    int table1Size = buf.getInt();
    int table1ByteSize = buf.getInt();
    if (table0Size > 1 + Character.MAX_VALUE - TABLE0_POS_OFFSET) {
      throw new IllegalArgumentException("Table 0 too big "  + table0Size);
    }
    if (table1Size > 1 + Character.MAX_VALUE - TABLE1_POS_OFFSET) {
      throw new IllegalArgumentException("Table 1 too big "  + table1Size);
    }

    // TODO(carl-mastrangelo): don't mutate the buffer or member variables until everything is verified.
    initNanoTime = buf.getLong();
    random = buf.getLong();
    table0 = new StringTableDecoder(table0ByteSize, table0Size);
    table1 = new StringTableDecoder(table1ByteSize, table1Size);
    bytesWanted = CHAR_BYTES;
    state = State.READ_OP;
  }

  private void readOp() {
    assert state == State.READ_OP;
    assert bytesWanted == CHAR_BYTES;
    assert buf.remaining() >= bytesWanted;
    char op = buf.getChar();
    switch (op) {
      case OP_NEW_GEN:
        bytesWanted = OP_NEW_GEN_BYTES;
        state = State.READ_OP_NEW_GEN;
        break;
      case OP_START_TASK_S1N1:
        bytesWanted = OP_START_TASK_S1N1_MIN_BYTES;
        state = State.READ_OP_START_TASK_S1N1;
        break;
      default:
        throw new IllegalArgumentException("Bad OP " + op);
    }
  }

  private void readOpNewGen() {
    assert state == State.READ_OP_NEW_GEN;
    assert bytesWanted == OP_NEW_GEN_BYTES;
    assert buf.remaining() >= bytesWanted;
    gen = buf.getLong();
    bytesWanted = READ_OP_BYTES;
    state = State.READ_OP;
  }

  private void readOpStartTaskS1n1() {
    assert state == State.READ_OP_START_TASK_S1N1;
    assert bytesWanted >= OP_START_TASK_S1N1_MIN_BYTES;
    assert buf.remaining() >= bytesWanted;

    int pos = buf.position();
    char taskNamePos = buf.getChar(pos + OP_START_TASK_S1N1_STATIC_BYTES);
    char tagNamePos = buf.getChar(pos + 2 + OP_START_TASK_S1N1_STATIC_BYTES);
    int taskNameNewBytes = table0Size(taskNamePos);
    int tagNameNewBytes = table1Size(tagNamePos);
    int dynamicBytesWanted = taskNameNewBytes + tagNameNewBytes;
    if (buf.remaining() < bytesWanted + dynamicBytesWanted) {
      if (bytesWanted == OP_START_TASK_S1N1_MIN_BYTES) {
        bytesWanted += dynamicBytesWanted;
      } else {
        assert bytesWanted == OP_START_TASK_S1N1_MIN_BYTES + dynamicBytesWanted;
      }
      return;
    }
    long nanoTime = buf.getLong();
    long tagId = buf.getLong();
    StandardCharsets.UTF_8.newDecoder();

    //String taskName, String tagName, long tagId, long nanoTime


    bytesWanted = READ_OP_BYTES;
    state = State.READ_OP;
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

  int table0Size(char pos) {
    if (pos >= TABLE0_POS_OFFSET) {
      return 0;
    }
    return pos & TABLE0_NEWSTRING_MAXSIZE;
  }

  int table1Size(char pos) {
    if (pos >= TABLE1_POS_OFFSET) {
      return 0;
    }
    return pos & TABLE1_NEWSTRING_MAXSIZE;
  }

  static void checkChar(ByteBuffer buf, int pos, char expected) {
    if (buf.getChar(pos) != expected) {
      throw new IllegalArgumentException(
          "Buffer doesn't contain " + expected + " at " + pos + ": " + buf.getChar(pos));
    }
  }
}
