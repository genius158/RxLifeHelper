package com.yan.rxlifehelper.lifeobervable;

import androidx.lifecycle.LifecycleOwner;
import io.reactivex.Flowable;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * @author genius
 * @date 2019/11/3
 */
public final class LiveFlowable<T> extends Flowable<T> {
  private Flowable<T> upstream;
  private LifecycleOwner lifecycleOwner;

  public LiveFlowable(Flowable<T> upstream, LifecycleOwner lifecycleOwner) {
    this.upstream = upstream;
    this.lifecycleOwner = lifecycleOwner;
  }

  @Override protected void subscribeActual(Subscriber<? super T> observer) {
    upstream.subscribe(new LiveObserver<>(lifecycleOwner, observer));
  }

  static class LiveObserver<T> implements Subscriber<T>, Subscription {
    final Subscriber<? super T> downstream;
    final AbsLiveDataObserver<T> liveDataObserver;
    final LifecycleOwner lifecycleOwner;
    Subscription upstream;

    LiveObserver(LifecycleOwner lifecycleOwner, final Subscriber<? super T> downstream) {
      this.downstream = downstream;
      this.lifecycleOwner = lifecycleOwner;
      liveDataObserver = new AbsLiveDataObserver<T>(lifecycleOwner) {
        @Override void onValue(T data) {
          downstream.onNext(data);
        }
      };
    }

    @Override public void onSubscribe(Subscription s) {
      upstream = s;
      downstream.onSubscribe(s);
    }

    @Override public void onNext(T data) {
      liveDataObserver.onNext(data);
    }

    @Override public void onError(Throwable e) {
      downstream.onError(e);
    }

    @Override public void onComplete() {
      downstream.onComplete();
    }

    @Override public void request(long n) {
      upstream.request(n);
    }

    @Override public void cancel() {
      upstream.cancel();
    }
  }
}
