package com.yan.rxlifecycledemo;

import androidx.lifecycle.Lifecycle;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.yan.rxlifehelper.RxLifeHelper;
import io.reactivex.Observable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import java.util.concurrent.TimeUnit;

public class MainFragment extends Fragment {

  @Nullable @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return new TextView(getContext());
  }

  @Override public void onResume() {
    super.onResume();

    // onPause 自动取消
    for (int i = 0; i < 1; i++) {
      final int finalI = i;
      Observable.timer(1500, TimeUnit.MILLISECONDS)
          .subscribeOn(Schedulers.newThread())
          .compose(RxLifeHelper.<Long>bindUntilLifeEvent(this, Lifecycle.Event.ON_PAUSE))
          .subscribe(new Consumer<Long>() {
            @Override public void accept(Long aLong) throws Exception {
              Log.e("RxLifeHelper", "MainFragment interval ---------   value "
                  + finalI
                  + "    "
                  + Thread.currentThread().getName());
            }
          });
    }
  }
}