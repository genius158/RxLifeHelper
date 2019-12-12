package com.yan.rxlifecycledemo;

import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;
import com.yan.rxlifehelper.RxLifeHelper;
import io.reactivex.Flowable;
import io.reactivex.FlowableSubscriber;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Single;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.reactivestreams.Subscription;

public class MainActivity extends AppCompatActivity {

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
  }

  @Override protected void onResume() {
    super.onResume();
    final AtomicInteger atomicInteger = new AtomicInteger();

    final long start = System.currentTimeMillis();

    //Flowable.create(new FlowableOnSubscribe<Object>() {
    //  @Override public void subscribe(final FlowableEmitter<Object> emitter) throws Exception {
    //    Single.timer(2000, TimeUnit.MILLISECONDS).subscribe(new BiConsumer<Long, Throwable>() {
    //      @Override public void accept(Long aLong, Throwable throwable) throws Exception {
    //        emitter.onError(new Exception("E"));
    //        Log.e("onNext", " " + "E");
    //      }
    //    });
    //    Observable.interval(200, TimeUnit.MILLISECONDS).subscribe(new Consumer<Long>() {
    //      @Override public void accept(Long aLong) throws Exception {
    //        emitter.onNext(aLong);
    //      }
    //    });
    //  }
    //}, BackpressureStrategy.BUFFER)
    //    .compose(RxLifeHelper.bindUntilLifeLiveEvent(this, Lifecycle.Event.ON_DESTROY))
    //    .subscribe(new FlowableSubscriber<Object>() {
    //  @Override public void onSubscribe(Subscription s) {
    //    s.request(Integer.MAX_VALUE);
    //  }
    //
    //  @Override public void onNext(Object o) {
    //    Log.e("onNext", "" + o);
    //  }
    //
    //  @Override public void onError(Throwable t) {
    //
    //  }
    //
    //  @Override public void onComplete() {
    //
    //  }
    //});

    // 1111111111111 将不会 被打印
    //getData("111111111111111111111111111111");
    //getData("222222222222222222222222222222");

    //for (int i = 0; i < 1; i++) {
    //  getSupportFragmentManager().beginTransaction()
    //      .add(R.id.fl_fragment, new MainFragment(), i + "")
    //      .commit();
    //}
  }

  private void getData(final String data) {
    Flowable.timer(2000, TimeUnit.MILLISECONDS).subscribe(new FlowableSubscriber<Long>() {
      @Override public void onSubscribe(Subscription s) {
        s.request(Integer.MAX_VALUE);
        Log.e("getDatagetData", "onSubscribe ");
      }

      @Override public void onNext(Long aLong) {
        Log.e("getDatagetData", "onNextonNext " + aLong);
      }

      @Override public void onError(Throwable t) {

      }

      @Override public void onComplete() {

      }
    });
    Single.timer(1000, TimeUnit.MILLISECONDS)
        .compose(RxLifeHelper.<Long>bindFilterTag("getData"))
        .compose(RxLifeHelper.<Long>bindUntilLifeEvent(this, Lifecycle.Event.ON_PAUSE))
        .doOnDispose(new Action() {
          @Override public void run() throws Exception {
            Log.e("doOnDispose", "run: doOnDisposedoOnDispose");
          }
        })
        .subscribe(new Consumer<Long>() {
          @Override public void accept(Long aLong) throws Exception {
            Log.e("RxLifeHelper", "event " + data);
          }
        }, new Consumer<Throwable>() {
          @Override public void accept(Throwable throwable) throws Exception {
            Log.e("getData", "accept: " + throwable);
          }
        });
  }

  @Override protected void onDestroy() {
    super.onDestroy();

    final int[] num = new int[] { 0 };
    Observable.interval(200, TimeUnit.MILLISECONDS)
        .flatMap(new Function<Long, ObservableSource<?>>() {
          @Override public ObservableSource<?> apply(Long aLong) throws Exception {
            if (num[0]++ > 20) {
              return Observable.error(new Exception("wewe"));
            }
            return Observable.just(num[0]);
          }
        })
        .compose(RxLifeHelper.bindUntilLifeLiveEvent(this, Lifecycle.Event.ON_DESTROY))
        .subscribe(new Consumer<Object>() {
          @Override public void accept(Object o) throws Exception {
            Log.e("onNext", "" + o);
          }
        }, new Consumer<Throwable>() {
          @Override public void accept(Throwable throwable) throws Exception {
            Log.e("onNext", "" + throwable.getMessage());
          }
        });

  }
}
