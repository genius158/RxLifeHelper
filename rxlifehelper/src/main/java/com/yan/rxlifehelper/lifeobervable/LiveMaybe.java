package com.yan.rxlifehelper.lifeobervable;

import androidx.lifecycle.LifecycleOwner;
import io.reactivex.Maybe;
import io.reactivex.MaybeObserver;
import io.reactivex.disposables.Disposable;

/**
 * @author genius
 * @date 2019/11/3
 */
public final class LiveMaybe<T> extends Maybe<T> {
  private Maybe<T> upstream;
  private LifecycleOwner lifecycleOwner;

  public LiveMaybe(Maybe<T> upstream, LifecycleOwner lifecycleOwner) {
    this.upstream = upstream;
    this.lifecycleOwner = lifecycleOwner;
  }

  @Override protected void subscribeActual(MaybeObserver<? super T> observer) {
    upstream.subscribe(new LiveObserver<>(lifecycleOwner, observer));
  }

  static class LiveObserver<T> implements MaybeObserver<T>, Disposable {
    final MaybeObserver<? super T> downstream;
    final AbsLiveDataObserver<T> liveDataObserver;
    private LifecycleOwner lifecycleOwner;
    Disposable upstream;

    LiveObserver(LifecycleOwner lifecycleOwner, final MaybeObserver<? super T> downstream) {
      this.downstream = downstream;
      this.lifecycleOwner = lifecycleOwner;
      liveDataObserver = new AbsLiveDataObserver<T>(lifecycleOwner) {
        @Override void onValue(T data) {
          downstream.onSuccess(data);
        }
      };
    }

    @Override public void onSubscribe(Disposable d) {
      upstream = d;
      downstream.onSubscribe(this);
    }

    @Override public void onSuccess(T data) {
      liveDataObserver.onNext(data);
    }

    @Override public void onError(Throwable e) {
      downstream.onError(e);
    }

    @Override public void onComplete() {

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
