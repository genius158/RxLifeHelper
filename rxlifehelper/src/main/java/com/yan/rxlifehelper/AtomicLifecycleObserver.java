package com.yan.rxlifehelper;

import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.OnLifecycleEvent;
import java.util.concurrent.atomic.AtomicInteger;

abstract class AtomicLifecycleObserver extends AtomicInteger implements LifecycleObserver {
  LifecycleOwner source;

  AtomicLifecycleObserver(LifecycleOwner source) {
    this.source = source;
  }

  @OnLifecycleEvent(Lifecycle.Event.ON_CREATE) void onCreate() {
    onStateChanged(source, Lifecycle.Event.ON_CREATE);
  }

  @OnLifecycleEvent(Lifecycle.Event.ON_START) void onStart() {
    onStateChanged(source, Lifecycle.Event.ON_START);
  }

  @OnLifecycleEvent(Lifecycle.Event.ON_RESUME) void onResume() {
    onStateChanged(source, Lifecycle.Event.ON_RESUME);
  }

  @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE) void onPause() {
    onStateChanged(source, Lifecycle.Event.ON_PAUSE);
  }

  @OnLifecycleEvent(Lifecycle.Event.ON_STOP) void onStop() {
    onStateChanged(source, Lifecycle.Event.ON_STOP);
  }

  @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY) void onDestroy() {
    onStateChanged(source, Lifecycle.Event.ON_DESTROY);
  }

  abstract void onStateChanged(final LifecycleOwner source, Lifecycle.Event event);
}
