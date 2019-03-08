/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yan.rxlifehelper;

import io.reactivex.Completable;
import io.reactivex.CompletableObserver;
import io.reactivex.Flowable;
import io.reactivex.FlowableSubscriber;
import io.reactivex.Maybe;
import io.reactivex.MaybeObserver;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.disposables.Disposable;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * 为了方便事件流结束的判断
 */
class RXWrapHelper {

  private static Disposable wrapDisposable(final Disposable disposable, final Runnable complete) {
    return new Disposable() {
      @Override public void dispose() {
        disposable.dispose();
        run(complete);
      }

      @Override public boolean isDisposed() {
        return disposable.isDisposed();
      }
    };
  }

  private static void run(Runnable complete) {
    if (complete != null) {
      complete.run();
    }
  }

  static <T> Observable<T> wrap(final Observable<T> upstream, final Runnable complete) {
    return new Observable<T>() {

      @Override protected void subscribeActual(final Observer<? super T> observer) {
        upstream.subscribe(new Observer<T>() {
          @Override public void onSubscribe(final Disposable d) {
            observer.onSubscribe(wrapDisposable(d, complete));
          }

          @Override public void onNext(T t) {
            observer.onNext(t);
          }

          @Override public void onError(Throwable e) {
            observer.onError(e);
            run(complete);
          }

          @Override public void onComplete() {
            observer.onComplete();
            run(complete);
          }
        });
      }
    };
  }

  static <T> Flowable<T> wrap(final Flowable<T> upstream, final Runnable complete) {
    return new Flowable<T>() {
      @Override protected void subscribeActual(final Subscriber<? super T> subscriber) {
        upstream.subscribe(new FlowableSubscriber<T>() {
          @Override public void onSubscribe(final Subscription s) {
            subscriber.onSubscribe(new Subscription() {
              @Override public void request(long n) {
                s.request(n);
              }

              @Override public void cancel() {
                s.cancel();
                run(complete);
              }
            });
          }

          @Override public void onNext(T t) {
            subscriber.onNext(t);
          }

          @Override public void onError(Throwable t) {
            subscriber.onError(t);
            run(complete);
          }

          @Override public void onComplete() {
            subscriber.onComplete();
            run(complete);
          }
        });
      }
    };
  }

  static Completable wrap(final Completable upstream, final Runnable complete) {
    return new Completable() {
      @Override protected void subscribeActual(final CompletableObserver observer) {
        upstream.subscribe(new CompletableObserver() {
          @Override public void onSubscribe(final Disposable d) {
            observer.onSubscribe(wrapDisposable(d, complete));
          }

          @Override public void onComplete() {
            observer.onComplete();
            run(complete);
          }

          @Override public void onError(Throwable e) {
            observer.onError(e);
            run(complete);
          }
        });
      }
    };
  }

  static <T> Single<T> wrap(final Single<T> upstream, final Runnable complete) {
    return new Single<T>() {
      @Override protected void subscribeActual(final SingleObserver<? super T> observer) {
        upstream.subscribe(new SingleObserver<T>() {
          @Override public void onSubscribe(Disposable d) {
            observer.onSubscribe(wrapDisposable(d, complete));
          }

          @Override public void onSuccess(T t) {
            observer.onSuccess(t);
            run(complete);
          }

          @Override public void onError(Throwable e) {
            observer.onError(e);
            run(complete);
          }
        });
      }
    };
  }

  static <T> Maybe<T> wrap(final Maybe<T> upstream, final Runnable complete) {
    return new Maybe<T>() {
      @Override protected void subscribeActual(final MaybeObserver<? super T> observer) {
        upstream.subscribe(new MaybeObserver<T>() {
          @Override public void onSubscribe(Disposable d) {
            observer.onSubscribe(wrapDisposable(d, complete));
          }

          @Override public void onSuccess(T t) {
            observer.onSuccess(t);
            run(complete);
          }

          @Override public void onError(Throwable e) {
            observer.onError(e);
            run(complete);
          }

          @Override public void onComplete() {
            observer.onComplete();
            run(complete);
          }
        });
      }
    };
  }
}