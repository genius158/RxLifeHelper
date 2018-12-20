package com.yan.rxlifecycledemo;

import android.arch.lifecycle.Lifecycle;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.yan.rxlifehelper.RxLifeHelper;
import io.reactivex.Observable;
import io.reactivex.functions.Consumer;
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
    Observable.interval(1000, TimeUnit.MILLISECONDS)
        .compose(RxLifeHelper.<Long>bindUntilLifeEvent(this, Lifecycle.Event.ON_PAUSE))
        .subscribe(new Consumer<Long>() {
          @Override public void accept(Long aLong) throws Exception {
            Log.e("RxLifeHelper", "MainFragment interval ---------");
          }
        });
  }
}