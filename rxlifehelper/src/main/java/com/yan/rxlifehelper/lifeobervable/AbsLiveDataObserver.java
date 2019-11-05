package com.yan.rxlifehelper.lifeobervable;

import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;

/**
 * @author genius
 * @date 2019/11/3
 */
abstract class AbsLiveDataObserver<T> extends LiveData<T> implements Observer<T> {
  private boolean isActive;
  private boolean isExecute = false;
  private T data;

  AbsLiveDataObserver(LifecycleOwner lifecycleOwner) {
    checkMainTread();
    observe(lifecycleOwner, this);
  }

  @Override protected void onInactive() {
    isActive = false;
  }

  @Override protected void onActive() {
    isActive = true;
    realOnNext();
  }

  void onLiveNext(T data) {
    isExecute = true;
    this.data = data;
    realOnNext();
  }

  private void realOnNext() {
    if (isExecute && isActive) {
      isExecute = false;
      if (isMain()) {
        setValue(data);
      } else {
        postValue(data);
      }
    }
  }

  @Override public void removeObservers(@NonNull final LifecycleOwner owner) {
    if (isMain()) {
      super.removeObservers(owner);
      return;
    }
    AndroidSchedulers.mainThread().scheduleDirect(new Runnable() {
      @Override public void run() {
        AbsLiveDataObserver.super.removeObservers(owner);
      }
    });
  }

  private boolean isMain() {
    return Looper.getMainLooper().getThread() == Thread.currentThread();
  }

  private void checkMainTread() {
    if (!isMain()) {
      throw new RuntimeException("the methods in LiveData must call in UI Thread");
    }
  }
}
