package com.yan.rxlifehelper.lifeobervable;

import androidx.lifecycle.LifecycleOwner;
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.disposables.Disposable;

/**
 * @author genius
 * @date 2019/11/3
 */
public final class LiveSingle<T> extends Single<T> {
  private Single<T> upstream;
  private LifecycleOwner lifecycleOwner;

  public LiveSingle(Single<T> upstream, LifecycleOwner lifecycleOwner) {
    this.upstream = upstream;
    this.lifecycleOwner = lifecycleOwner;
  }

  @Override protected void subscribeActual(SingleObserver<? super T> observer) {
    upstream.subscribe(new LiveObserver<>(lifecycleOwner, observer));
  }

  static class LiveObserver<T> implements SingleObserver<T>, Disposable {
    final SingleObserver<? super T> downstream;
    final AbsLiveDataObserver<T> liveDataObserver;
    private final LifecycleOwner lifecycleOwner;
    Disposable ups;

    LiveObserver(final LifecycleOwner lifecycleOwner, final SingleObserver<? super T> downstream) {
      this.downstream = downstream;
      this.lifecycleOwner = lifecycleOwner;
      liveDataObserver = new AbsLiveDataObserver<T>(lifecycleOwner) {
        @Override void onValue(T data) {
          downstream.onSuccess(data);
          removeObserver(this);
        }
      };
    }

    @Override public void onSubscribe(Disposable d) {
      ups = d;
      downstream.onSubscribe(this);
    }

    @Override public void onSuccess(T data) {
      liveDataObserver.onNext(data);
    }

    @Override public void onError(Throwable e) {
      downstream.onError(e);
    }

    @Override public void dispose() {
      ups.dispose();
      liveDataObserver.removeObservers(lifecycleOwner);
    }

    @Override public boolean isDisposed() {
      return ups.isDisposed();
    }
  }
}
