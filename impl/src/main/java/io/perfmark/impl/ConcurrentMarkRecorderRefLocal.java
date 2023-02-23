package io.perfmark.impl;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ConcurrentMarkRecorderRefLocal {
  private static final ConcurrentMap<ThreadRef, MarkRecorderRef> refs =
      new ConcurrentHashMap<ThreadRef, MarkRecorderRef>();
  private static final ReferenceQueue<Thread> queue = new ReferenceQueue<Thread>();

  public static MarkRecorderRef get() {
    MarkRecorderRef mrr = refs.get(ThreadRef.IDENTITY);
    if (mrr != null) {
      return mrr;
    }
    return initialValue();
  }

  private static MarkRecorderRef initialValue() {
    drainQueue();
    Thread thread = Thread.currentThread();
    ThreadRef threadRef = new ThreadRef(thread);
    MarkRecorderRef mrr = MarkRecorderRef.newRef(thread, threadRef);
    MarkRecorderRef previousMrr = refs.putIfAbsent(threadRef, mrr);
    assert previousMrr == null;
    return mrr;
  }

  private static void drainQueue() {
    Reference<? extends Thread> ref = queue.poll();
    if (ref == null) {
      return;
    }
    drainQueue(ref);
  }

  private static void drainQueue(Reference<? extends Thread> ref) {
    do {
      refs.remove((ThreadRef) ref);
    } while ((ref = queue.poll()) != null);
  }

  private static final class ThreadRef extends WeakReference<Thread> {
    private static final ThreadRef IDENTITY = new ThreadRef();

    private final int hashCode;

    ThreadRef(Thread thread) {
      super(thread, queue);
      this.hashCode = System.identityHashCode(thread);
    }

    private ThreadRef() {
      super(null);
      this.hashCode = 0;
    }

    @Override
    public void clear() {
      // Noop
    }

    @Override
    public boolean enqueue() {
      // noop
      return false;
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public int hashCode() {
      return this == IDENTITY ? System.identityHashCode(Thread.currentThread()) : hashCode;
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof ThreadRef)) {
        return false;
      }
      Thread that = ((ThreadRef) obj).get();
      Thread thiz = this == IDENTITY ? Thread.currentThread() : get();
      return thiz == that;
    }
  }
}
