package com.yan.rxlifehelper;

import io.reactivex.Observer;
import io.reactivex.annotations.CheckReturnValue;
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import io.reactivex.disposables.Disposable;
import io.reactivex.internal.functions.ObjectHelper;
import io.reactivex.internal.util.AppendOnlyLinkedArrayList;
import io.reactivex.internal.util.AppendOnlyLinkedArrayList.NonThrowingPredicate;
import io.reactivex.internal.util.ExceptionHelper;
import io.reactivex.internal.util.NotificationLite;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.subjects.Subject;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * code copy from BehaviorSubject
 */
final class InnerBehaviorSubject<T> extends Subject<T> {

  private final AtomicReference<Object> value;
  private final AtomicReference<BehaviorDisposable<T>[]> subscribers;
  private @SuppressWarnings("rawtypes") static final BehaviorDisposable[] EMPTY =
      new BehaviorDisposable[0];
  private @SuppressWarnings("rawtypes") static final BehaviorDisposable[] TERMINATED =
      new BehaviorDisposable[0];
  private final ReadWriteLock lock;
  private final Lock readLock;
  private final Lock writeLock;
  private final AtomicReference<Throwable> terminalEvent;
  private long index;

  /**
   * use this to clear TAG_LIFECYCLE_MAP in RxLifeHelper
   */
  private Runnable onCancel;

  @CheckReturnValue @NonNull static <T> InnerBehaviorSubject<T> create(Runnable onCancel) {
    return new InnerBehaviorSubject<T>(onCancel);
  }

  private @SuppressWarnings("unchecked") InnerBehaviorSubject(Runnable onCancel) {
    this.lock = new ReentrantReadWriteLock();
    this.readLock = lock.readLock();
    this.writeLock = lock.writeLock();
    this.subscribers = new AtomicReference<BehaviorDisposable<T>[]>(EMPTY);
    this.value = new AtomicReference<>();
    this.terminalEvent = new AtomicReference<>();
    this.onCancel = onCancel;
  }

  @Override protected void subscribeActual(final Observer<? super T> observer) {
    BehaviorDisposable<T> bs = new BehaviorDisposable<>(observer, this);
    observer.onSubscribe(bs);
    if (add(bs)) {
      if (bs.cancelled) {
        remove(bs);
      } else {
        bs.emitFirst();
      }
    } else {
      Throwable ex = terminalEvent.get();
      if (ex == ExceptionHelper.TERMINATED) {
        observer.onComplete();
      } else {
        observer.onError(ex);
      }
    }
  }

  @Override public void onSubscribe(Disposable d) {
    if (terminalEvent.get() != null) {
      d.dispose();
    }
  }

  @Override public void onNext(T t) {
    ObjectHelper.requireNonNull(t,
        "onNext called with null. Null values are generally not allowed in 2.x operators and sources.");

    if (terminalEvent.get() != null) {
      return;
    }
    Object o = NotificationLite.next(t);
    setCurrent(o);
    for (BehaviorDisposable<T> bs : subscribers.get()) {
      bs.emitNext(o, index);
    }
  }

  @Override public void onError(Throwable t) {
    ObjectHelper.requireNonNull(t,
        "onError called with null. Null values are generally not allowed in 2.x operators and sources.");
    if (!terminalEvent.compareAndSet(null, t)) {
      RxJavaPlugins.onError(t);
      return;
    }
    Object o = NotificationLite.error(t);
    for (BehaviorDisposable<T> bs : terminate(o)) {
      bs.emitNext(o, index);
    }
  }

  @Override public void onComplete() {
    if (!terminalEvent.compareAndSet(null, ExceptionHelper.TERMINATED)) {
      return;
    }
    Object o = NotificationLite.complete();
    for (BehaviorDisposable<T> bs : terminate(o)) {
      // relaxed read okay since this is the only mutator thread
      bs.emitNext(o, index);
    }
  }

  @Override public boolean hasObservers() {
    return subscribers.get().length != 0;
  }

  /* test support*/ int subscriberCount() {
    return subscribers.get().length;
  }

  @Override @Nullable public Throwable getThrowable() {
    Object o = value.get();
    if (NotificationLite.isError(o)) {
      return NotificationLite.getError(o);
    }
    return null;
  }

  @Override public boolean hasComplete() {
    Object o = value.get();
    return NotificationLite.isComplete(o);
  }

  @Override public boolean hasThrowable() {
    Object o = value.get();
    return NotificationLite.isError(o);
  }

  private boolean add(BehaviorDisposable<T> rs) {
    for (; ; ) {
      BehaviorDisposable<T>[] a = subscribers.get();
      if (a == TERMINATED) {
        return false;
      }
      int len = a.length;
      @SuppressWarnings("unchecked") BehaviorDisposable<T>[] b = new BehaviorDisposable[len + 1];
      System.arraycopy(a, 0, b, 0, len);
      b[len] = rs;
      if (subscribers.compareAndSet(a, b)) {
        return true;
      }
    }
  }

  private @SuppressWarnings("unchecked") void remove(BehaviorDisposable<T> rs) {
    for (; ; ) {
      BehaviorDisposable<T>[] a = subscribers.get();
      int len = a.length;
      if (len == 0) {
        return;
      }
      int j = -1;
      for (int i = 0; i < len; i++) {
        if (a[i] == rs) {
          j = i;
          break;
        }
      }

      if (j < 0) {
        return;
      }
      BehaviorDisposable<T>[] b;
      if (len == 1) {
        b = EMPTY;
      } else {
        b = new BehaviorDisposable[len - 1];
        System.arraycopy(a, 0, b, 0, j);
        System.arraycopy(a, j + 1, b, j, len - j - 1);
      }
      if (subscribers.compareAndSet(a, b)) {
        return;
      }
    }
  }

  private @SuppressWarnings("unchecked") BehaviorDisposable<T>[] terminate(Object terminalValue) {

    BehaviorDisposable<T>[] a = subscribers.getAndSet(TERMINATED);
    if (a != TERMINATED) {
      // either this or atomics with lots of allocation
      setCurrent(terminalValue);
    }

    return a;
  }

  private void setCurrent(Object o) {
    writeLock.lock();
    index++;
    value.lazySet(o);
    writeLock.unlock();
  }

  private static final class BehaviorDisposable<T>
      implements Disposable, NonThrowingPredicate<Object> {

    private final Observer<? super T> downstream;
    private final InnerBehaviorSubject<T> state;

    private boolean next;
    private boolean emitting;
    private AppendOnlyLinkedArrayList<Object> queue;

    private boolean fastPath;

    private volatile boolean cancelled;
    private long index;

    private BehaviorDisposable(Observer<? super T> actual, InnerBehaviorSubject<T> state) {
      this.downstream = actual;
      this.state = state;
    }

    @Override public void dispose() {
      if (!cancelled) {
        cancelled = true;
        state.remove(this);
        state.onCancel.run();
      }
    }

    @Override public boolean isDisposed() {
      return cancelled;
    }

    private void emitFirst() {
      if (cancelled) {
        return;
      }
      Object o;
      synchronized (this) {
        if (cancelled) {
          return;
        }
        if (next) {
          return;
        }

        InnerBehaviorSubject<T> s = state;
        Lock lock = s.readLock;

        lock.lock();
        index = s.index;
        o = s.value.get();
        lock.unlock();

        emitting = o != null;
        next = true;
      }

      if (o != null) {
        if (test(o)) {
          return;
        }

        emitLoop();
      }
    }

    private void emitNext(Object value, long stateIndex) {
      if (cancelled) {
        return;
      }
      if (!fastPath) {
        synchronized (this) {
          if (cancelled) {
            return;
          }
          if (index == stateIndex) {
            return;
          }
          if (emitting) {
            AppendOnlyLinkedArrayList<Object> q = queue;
            if (q == null) {
              q = new AppendOnlyLinkedArrayList<Object>(4);
              queue = q;
            }
            q.add(value);
            return;
          }
          next = true;
        }
        fastPath = true;
      }

      test(value);
    }

    @Override public boolean test(Object o) {
      return cancelled || NotificationLite.accept(o, downstream);
    }

    private void emitLoop() {
      for (; ; ) {
        if (cancelled) {
          return;
        }
        AppendOnlyLinkedArrayList<Object> q;
        synchronized (this) {
          q = queue;
          if (q == null) {
            emitting = false;
            return;
          }
          queue = null;
        }

        q.forEachWhile(this);
      }
    }
  }
}
