package io.perfmark.java7;

import static io.perfmark.java7.ChannelEncoder.STRING_OVERHEAD;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@SuppressWarnings("ResultOfMethodCallIgnored")
@RunWith(JUnit4.class)
public class StringTableTest {


  @Test
  public void ad3dStrings() {
    Random srand = new Random();
    long seed = srand.nextLong();
    System.out.println("Seed "+ seed);
    trial(4000, new Random(-3788030927773439961L));
  }

  private void trial(int iterations, Random r) {
    List<String> reuse = new ArrayList<>();
    int maxSize = r.nextInt(iterations / 10);
    StringTableEncoder enc = new StringTableEncoder(maxSize);
    StringTableDecoder dec = new StringTableDecoder(maxSize);
    List<String> encSequence = new ArrayList<>();
    List<String> decSequence = new ArrayList<>();
    int found = 0;
    int neu = 0 ;
    for (int i = 0; i < iterations; i++) {
      String s = switch (r.nextInt(4)) {
        case 0 -> {
          while (!reuse.isEmpty() && r.nextBoolean()){
            reuse.remove(r.nextInt(reuse.size()));
          }
          reuse.add(UUID.randomUUID().toString());
          yield reuse.get(reuse.size() - 1);
        }
        case 1 -> {
          if (reuse.isEmpty()) {
            reuse.add(UUID.randomUUID().toString());
          }
          yield reuse.get(r.nextInt(reuse.size()));
        }
        case 2 -> {
          int size = r.nextInt(maxSize * 2);
          char[] data = new char[size];
          for (int k = 0; k < size; k++) {
            data[k] = (char) r.nextInt(Character.MAX_VALUE + 1);
          }
          yield new String(data);
        }
        case 3 -> {
          int size = r.nextInt(2);
          char[] data = new char[size];
          for (int k = 0; k < size; k++) {
            data[k] = (char) r.nextInt(Character.MAX_VALUE + 1);
          }
          yield new String(data);
        }
        default -> throw new AssertionError();
      };
      encSequence.add(s);
      int idx = enc.get(s);
      if (idx == -1) {
        enc.add(s, s.length());
        dec.add(s, s.length());
        decSequence.add(s);
        neu += 1;
      } else {
        found += 1;
        decSequence.add(dec.get(idx));
      }
    }

    assertEquals(encSequence, decSequence);
  }

  @Test
  public void addStrings() {
    var table = new StringTableDecoder(STRING_OVERHEAD * 4);
    assertEquals(0, table.size());
    assertEquals(0, table.byteSize());

    table.add("hi", 2);
    assertEquals(1, table.size());
    assertEquals(2 + STRING_OVERHEAD, table.byteSize());
    assertEquals("hi", table.get(0));

    table.add("there", 5);
    assertEquals(2, table.size());
    assertEquals(2 + 5 + 2 * STRING_OVERHEAD, table.byteSize());
    assertEquals("there", table.get(0));
    assertEquals("hi", table.get(1));

    table.add("mom", 3);
    assertEquals(3, table.size());
    assertEquals(2 + 5 + 3 + 3 * STRING_OVERHEAD, table.byteSize());
    assertEquals("mom", table.get(0));
    assertEquals("there", table.get(1));
    assertEquals("hi", table.get(2));

    table.add("weee", 4);
    assertEquals(3, table.size());
    assertEquals(5 + 3 + 4 + 3 * STRING_OVERHEAD, table.byteSize());
    assertEquals("weee", table.get(0));
    assertEquals("mom", table.get(1));
    assertEquals("there", table.get(2));
  }

  @Test
  public void addStrings_empty() {
    var table = new StringTableDecoder(STRING_OVERHEAD - 1);
    assertEquals(0, table.size());
    assertEquals(0, table.byteSize());
    table.add("", 0);

    assertEquals(0, table.size());
    assertEquals(0, table.byteSize());

  }

  @Test
  public void addStrings_zero() {
    var table = new StringTableDecoder(0);
    assertEquals(0, table.size());
    assertEquals(0, table.byteSize());
    table.add("", 0);

    assertEquals(0, table.size());
    assertEquals(0, table.byteSize());
  }
}