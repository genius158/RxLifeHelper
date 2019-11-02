package com.yan.rxlifecycledemo;

import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;
import com.yan.rxlifehelper.RxLifeHelper;
import io.reactivex.Flowable;
import io.reactivex.FlowableSubscriber;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.functions.Action;
import io.reactivex.functions.BiConsumer;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
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

    Single.just(1)
        .delay(1, TimeUnit.SECONDS)
        .compose(RxLifeHelper.<Integer>bindUntilDetach(this))
        .subscribe(new BiConsumer<Integer, Throwable>() {
          @Override public void accept(Integer integer, Throwable throwable) throws Exception {
            Log.e("RxLifeHelper", "BiConsumer111111 --------- " + throwable);
          }
        });

    Single.just(1)
        .delay(5, TimeUnit.SECONDS)
        .compose(RxLifeHelper.<Integer>bindUntilDetach(this))
        .subscribe(new BiConsumer<Integer, Throwable>() {
          @Override public void accept(Integer integer, Throwable throwable) throws Exception {
            Log.e("RxLifeHelper", "BiConsumer222222 --------- " + throwable);
          }
        });

    Single.just(1)
        .delay(10, TimeUnit.SECONDS)
        .compose(RxLifeHelper.<Integer>bindUntilDetach(this))
        .subscribe(new BiConsumer<Integer, Throwable>() {
          @Override public void accept(Integer integer, Throwable throwable) throws Exception {
            Log.e("RxLifeHelper", "BiConsumer3333333 --------- " + throwable);
          }
        });

    Observable.interval(1000, TimeUnit.MILLISECONDS)
        .compose(RxLifeHelper.<Long>bindUntilLifeEvent(this, Lifecycle.Event.ON_PAUSE))
        .subscribeOn(Schedulers.io())
        .subscribe(new Consumer<Long>() {
          @Override public void accept(Long aLong) throws Exception {
            Log.e("RxLifeHelper", "interval --------- ");
          }
        }, new Consumer<Throwable>() {
          @Override public void accept(Throwable throwable) throws Exception {
            Log.e("getData", "accept: " + throwable);
          }
        });

    // 1111111111111 将不会 被打印
    getData("111111111111111111111111111111");
    getData("222222222222222222222222222222");

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
}
