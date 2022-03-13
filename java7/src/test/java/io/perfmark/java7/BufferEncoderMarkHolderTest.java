package io.perfmark.java7;

import io.perfmark.impl.MarkHolder;
import io.perfmark.testing.MarkHolderTest;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BufferEncoderMarkHolderTest extends MarkHolderTest {
  private final List<ByteBuffer> output = new ArrayList<>();



  @Override
  protected MarkHolder getMarkHolder() {
    return new BufferEncoder(src -> {
      if (src.capacity() != 0) {
        src.flip();
        ByteBuffer dst = ByteBuffer.allocate(src.remaining());
        dst.put(src);
        dst.flip();
        src.flip();
        output.add(dst);
      }
      if (src.capacity() < BufferEncoder.MIN_BUFFER_SIZE) {
        src = ByteBuffer.allocate(BufferEncoder.MIN_BUFFER_SIZE);
      }
      return src;
    });
  }

}
