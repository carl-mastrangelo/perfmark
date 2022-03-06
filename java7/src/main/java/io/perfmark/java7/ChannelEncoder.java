package io.perfmark.java7;

import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

public class ChannelEncoder {

  static final int STRING_OVERHEAD = 32;


  WritableByteChannel chan;


  ByteBuffer buffer;

  interface StringTable {
    void add(String value, int encodedLength);
  }
}
