package com.yan.rxlifehelper.lifeobervable;

import androidx.lifecycle.LifecycleOwner;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

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

  static class LiveObserver<T> implements Observer<T>, Disposable {
    final Observer<? super T> downstream;
    final AbsLiveDataObserver<T> liveDataObserver;
    final LifecycleOwner lifecycleOwner;
    Disposable upstream;

    LiveObserver(LifecycleOwner lifecycleOwner, final Observer<? super T> downstream) {
      this.lifecycleOwner = lifecycleOwner;
      this.downstream = downstream;
      liveDataObserver = new AbsLiveDataObserver<T>(lifecycleOwner) {
        @Override void onValue(T data) {
          downstream.onNext(data);
        }
      };
    }

    @Override public void onSubscribe(Disposable d) {
      upstream = d;
      downstream.onSubscribe(this);
    }

    @Override public void onNext(T data) {
      if (!isDisposed()) {
        liveDataObserver.onNext(data);
      }
    }

    @Override public void onError(Throwable e) {
      downstream.onError(e);
    }

    @Override public void onComplete() {
      downstream.onComplete();
    }

    @Override public void dispose() {
      upstream.dispose();
      liveDataObserver.removeObservers(lifecycleOwner);
    }

    @Override public boolean isDisposed() {
      return upstream.isDisposed();
    }
  }
}
