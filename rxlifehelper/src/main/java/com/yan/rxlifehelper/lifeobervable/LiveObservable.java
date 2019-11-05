package com.yan.rxlifehelper.lifeobervable;

import androidx.lifecycle.LifecycleOwner;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.internal.disposables.DisposableHelper;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author genius
 * @date 2019/11/3
 */
public final class LiveObservable<T> extends Observable<T> {
  private Observable<T> upstream;
  private LifecycleOwner lifecycleOwner;

  public LiveObservable(Observable<T> upstream, LifecycleOwner lifecycleOwner) {
    this.upstream = upstream;
    this.lifecycleOwner = lifecycleOwner;
  }

  @Override protected void subscribeActual(Observer<? super T> observer) {
    upstream.subscribe(new LiveObserver<>(lifecycleOwner, observer));
  }

  static class LiveObserver<T> extends AbsLiveDataObserver<T> implements Observer<T>, Disposable {
    private final Observer<? super T> downstream;
    private final LifecycleOwner lifecycleOwner;
    private final AtomicReference<Disposable> upstream = new AtomicReference<>();

    LiveObserver(LifecycleOwner lifecycleOwner, final Observer<? super T> downstream) {
      super(lifecycleOwner);
      this.lifecycleOwner = lifecycleOwner;
      this.downstream = downstream;
    }

    @Override public void onSubscribe(Disposable d) {
      DisposableHelper.setOnce(this.upstream, d);
      downstream.onSubscribe(this);
    }

    @Override public void onNext(T data) {
      onLiveNext(data);
    }

    @Override public void onError(Throwable e) {
      removeObservers(lifecycleOwner);
      downstream.onError(e);
    }

    @Override public void onComplete() {
      removeObservers(lifecycleOwner);
      downstream.onComplete();
    }

    @Override public void dispose() {
      removeObservers(lifecycleOwner);
      DisposableHelper.dispose(upstream);
    }

    @Override public final boolean isDisposed() {
      return upstream.get() == DisposableHelper.DISPOSED;
    }

    @Override public void onChanged(T data) {
      downstream.onNext(data);
    }
  }
}
