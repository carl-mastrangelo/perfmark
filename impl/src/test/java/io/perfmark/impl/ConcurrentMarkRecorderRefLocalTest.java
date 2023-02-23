package io.perfmark.impl;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ConcurrentMarkRecorderRefLocalTest {

  @Test
  public void boo() throws Exception {
    ExecutorService executor = Executors.newCachedThreadPool();
    for (int i = 0; i < 1000; i++) {
      executor.submit(ConcurrentMarkRecorderRefLocal::get);
    }
    executor.shutdown();
    executor.awaitTermination(5, TimeUnit.SECONDS);
    System.gc();
    System.runFinalization();
  }
}
