package com.yan.rxlifehelper;

import io.reactivex.annotations.CheckReturnValue;
import io.reactivex.annotations.Nullable;
import io.reactivex.annotations.NonNull;
import io.reactivex.subjects.Subject;
import java.lang.reflect.Array;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.*;

import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.internal.functions.ObjectHelper;
import io.reactivex.internal.util.*;
import io.reactivex.internal.util.AppendOnlyLinkedArrayList.NonThrowingPredicate;
import io.reactivex.plugins.RxJavaPlugins;

public final class InnerBehaviorSubject<T> extends Subject<T> {

  /** An empty array to avoid allocation in getValues(). */
  private static final Object[] EMPTY_ARRAY = new Object[0];

  final AtomicReference<Object> value;

  final AtomicReference<BehaviorDisposable<T>[]> subscribers;

  @SuppressWarnings("rawtypes") static final BehaviorDisposable[] EMPTY = new BehaviorDisposable[0];

  @SuppressWarnings("rawtypes") static final BehaviorDisposable[] TERMINATED =
      new BehaviorDisposable[0];
  final ReadWriteLock lock;
  final Lock readLock;
  final Lock writeLock;

  final AtomicReference<Throwable> terminalEvent;

  long index;

  /**
   * Creates a {@link InnerBehaviorSubject} without a default item.
   *
   * @param <T> the type of item the Subject will emit
   * @return the constructed {@link InnerBehaviorSubject}
   */
  @CheckReturnValue @NonNull public static <T> InnerBehaviorSubject<T> create() {
    return new InnerBehaviorSubject<T>();
  }

  /**
   * Creates a {@link InnerBehaviorSubject} that emits the last item it observed and all subsequent
   * items to each
   * {@link Observer} that subscribes to it.
   *
   * @param <T> the type of item the Subject will emit
   * @param defaultValue the item that will be emitted first to any {@link Observer} as long as the
   * {@link InnerBehaviorSubject} has not yet observed any items from its source {@code Observable}
   * @return the constructed {@link InnerBehaviorSubject}
   */
  @CheckReturnValue @NonNull public static <T> InnerBehaviorSubject<T> createDefault(
      T defaultValue) {
    return new InnerBehaviorSubject<T>(defaultValue);
  }

  /**
   * Constructs an empty InnerBehaviorSubject.
   *
   * @since 2.0
   */
  @SuppressWarnings("unchecked") InnerBehaviorSubject() {
    this.lock = new ReentrantReadWriteLock();
    this.readLock = lock.readLock();
    this.writeLock = lock.writeLock();
    this.subscribers = new AtomicReference<BehaviorDisposable<T>[]>(EMPTY);
    this.value = new AtomicReference<Object>();
    this.terminalEvent = new AtomicReference<Throwable>();
  }

  /**
   * Constructs a InnerBehaviorSubject with the given initial value.
   *
   * @param defaultValue the initial value, not null (verified)
   * @throws NullPointerException if {@code defaultValue} is null
   * @since 2.0
   */
  InnerBehaviorSubject(T defaultValue) {
    this();
    this.value.lazySet(ObjectHelper.requireNonNull(defaultValue, "defaultValue is null"));
  }

  @Override protected void subscribeActual(final Observer<? super T> inObserver) {
    Observer<T> observer = new Observer<T>() {
      BehaviorDisposable disposable;

      @Override public void onSubscribe(Disposable d) {
        disposable = (BehaviorDisposable) d;
        inObserver.onSubscribe(d);
      }

      @Override public void onNext(T t) {
        inObserver.onNext(t);
        if(disposable.isDisposed()){
          remove(disposable);
        }
      }

      @Override public void onError(Throwable e) {
        inObserver.onError(e);
        remove(disposable);
      }

      @Override public void onComplete() {
        inObserver.onComplete();
        remove(disposable);
      }
    };
    BehaviorDisposable<T> bs = new BehaviorDisposable<T>(observer, this);
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
      bs.emitNext(o, index);  // relaxed read okay since this is the only mutator thread
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

  /**
   * Returns a single value the Subject currently has or null if no such value exists.
   * <p>The method is thread-safe.
   *
   * @return a single value the Subject currently has or null if no such value exists
   */
  @Nullable public T getValue() {
    Object o = value.get();
    if (NotificationLite.isComplete(o) || NotificationLite.isError(o)) {
      return null;
    }
    return NotificationLite.getValue(o);
  }

  /**
   * Returns an Object array containing snapshot all values of the Subject.
   * <p>The method is thread-safe.
   *
   * @return the array containing the snapshot of all values of the Subject
   * @deprecated in 2.1.14; put the result of {@link #getValue()} into an array manually, will be removed in 3.x
   */
  @Deprecated public Object[] getValues() {
    @SuppressWarnings("unchecked") T[] a = (T[]) EMPTY_ARRAY;
    T[] b = getValues(a);
    if (b == EMPTY_ARRAY) {
      return new Object[0];
    }
    return b;
  }

  /**
   * Returns a typed array containing a snapshot of all values of the Subject.
   * <p>The method follows the conventions of Collection.toArray by setting the array element
   * after the last value to null (if the capacity permits).
   * <p>The method is thread-safe.
   *
   * @param array the target array to copy values into if it fits
   * @return the given array if the values fit into it or a new array containing all values
   * @deprecated in 2.1.14; put the result of {@link #getValue()} into an array manually, will be removed in 3.x
   */
  @Deprecated @SuppressWarnings("unchecked") public T[] getValues(T[] array) {
    Object o = value.get();
    if (o == null || NotificationLite.isComplete(o) || NotificationLite.isError(o)) {
      if (array.length != 0) {
        array[0] = null;
      }
      return array;
    }
    T v = NotificationLite.getValue(o);
    if (array.length != 0) {
      array[0] = v;
      if (array.length != 1) {
        array[1] = null;
      }
    } else {
      array = (T[]) Array.newInstance(array.getClass().getComponentType(), 1);
      array[0] = v;
    }
    return array;
  }

  @Override public boolean hasComplete() {
    Object o = value.get();
    return NotificationLite.isComplete(o);
  }

  @Override public boolean hasThrowable() {
    Object o = value.get();
    return NotificationLite.isError(o);
  }

  /**
   * Returns true if the subject has any value.
   * <p>The method is thread-safe.
   *
   * @return true if the subject has any value
   */
  public boolean hasValue() {
    Object o = value.get();
    return o != null && !NotificationLite.isComplete(o) && !NotificationLite.isError(o);
  }

  boolean add(BehaviorDisposable<T> rs) {
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

  @SuppressWarnings("unchecked") void remove(BehaviorDisposable<T> rs) {
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

  @SuppressWarnings("unchecked") BehaviorDisposable<T>[] terminate(Object terminalValue) {

    BehaviorDisposable<T>[] a = subscribers.getAndSet(TERMINATED);
    if (a != TERMINATED) {
      // either this or atomics with lots of allocation
      setCurrent(terminalValue);
    }

    return a;
  }

  void setCurrent(Object o) {
    writeLock.lock();
    index++;
    value.lazySet(o);
    writeLock.unlock();
  }

  static final class BehaviorDisposable<T> implements Disposable, NonThrowingPredicate<Object> {

    final Observer<? super T> downstream;
    final InnerBehaviorSubject<T> state;

    boolean next;
    boolean emitting;
    AppendOnlyLinkedArrayList<Object> queue;

    boolean fastPath;

    volatile boolean cancelled;

    long index;

    BehaviorDisposable(Observer<? super T> actual, InnerBehaviorSubject<T> state) {
      this.downstream = actual;
      this.state = state;
    }

    @Override public void dispose() {
      if (!cancelled) {
        cancelled = true;

        state.remove(this);
      }
    }

    @Override public boolean isDisposed() {
      return cancelled;
    }

    void emitFirst() {
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

    void emitNext(Object value, long stateIndex) {
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

    void emitLoop() {
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
