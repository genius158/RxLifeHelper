package com.yan.rxlifehelper;

import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.OnLifecycleEvent;
import android.content.Context;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import io.reactivex.Observable;
import io.reactivex.functions.Predicate;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.PublishSubject;
import java.util.HashMap;

/**
 * 基于 rxlifecycle
 *
 * 作用:1. 多次调用相同方法，可以取消上一次方法的调用（bindMethodTag）
 * 2. 生命周期绑定
 *
 * @author yanxianwei
 */
public class RxLifeHelper {
  private static final HashMap<String, InnerLifeCycleManager> TAG_LIFECYCLE_MAP = new HashMap<>();

  /**
   * 处理tag 发送事件形式的绑定处理
   */
  private static final PublishSubject<String> TAG_EVENT_SUBJECT = PublishSubject.create();

  public static <T> LifecycleTransformer<T> bindFilterTag(final String tag) {
    return bindFilterTag(tag, true);
  }

  public static <T> LifecycleTransformer<T> bindFilterTag(final String tag, boolean disposeBefore) {
    if (tag == null) {
      return bindErrorEvent(
          new NullPointerException("RxLifeHelper: parameter tag can not be null"));
    }
    if (disposeBefore) {
      sendFilterTag(tag);
    }
    return RxLifecycle.bind(TAG_EVENT_SUBJECT.filter(new Predicate<String>() {
      @Override public boolean test(String innerTag) throws Exception {
        return tag.equals(innerTag);
      }
    }), null);
  }

  public static void sendFilterTag(String tag) {
    TAG_EVENT_SUBJECT.onNext(tag);
  }

  public static <T> LifecycleTransformer<T> bindUntilLifeEvent(FragmentActivity target,
      Lifecycle.Event event) {
    return bindLifeOwnerUntilEvent(target, event);
  }

  public static <T> LifecycleTransformer<T> bindUntilLifeEvent(Fragment target,
      Lifecycle.Event event) {
    return bindLifeOwnerUntilEvent(target, event);
  }

  public static <T> LifecycleTransformer<T> bindUntilLifeEvent(Context target,
      Lifecycle.Event event) {
    if (!(target instanceof LifecycleOwner)) {
      return bindErrorEvent(
          new IllegalArgumentException("RxLifeHelper: target must implements LifecycleOwner"));
    }
    return bindLifeOwnerUntilEvent((LifecycleOwner) target, event);
  }

  public static <T> LifecycleTransformer<T> bindLifeOwnerUntilEvent(LifecycleOwner lifecycleOwner,
      Lifecycle.Event event) {
    final String key = lifecycleOwner.getClass().getName();
    final InnerLifeCycleManager lifeCycleManager = getLifeManager(lifecycleOwner, key);
    if (lifeCycleManager == null) {
      return bindErrorEvent(new NullPointerException("RxLifeHelper: target could not be null"));
    }
    return RxLifecycle.bindUntilEvent(lifeCycleManager.lifecycleSubject, event, new Runnable() {
      @Override public void run() {
        if (!lifeCycleManager.lifecycleSubject.hasObservers()) {
          TAG_LIFECYCLE_MAP.remove(key);
        }
      }
    });
  }

  /**
   * 空事件流绑定
   */
  private static <T> LifecycleTransformer<T> bindErrorEvent(Throwable throwable) {
    // 这里处理参数错误下，直接 异常返回
    return RxLifecycle.bind(Observable.error(throwable), null);
  }

  private static InnerLifeCycleManager getLifeManager(LifecycleOwner lifecycleOwner, String key) {
    if (lifecycleOwner == null) {
      return null;
    }
    InnerLifeCycleManager lifeCycleManager = TAG_LIFECYCLE_MAP.get(key);
    if (lifeCycleManager == null) {
      lifeCycleManager = new InnerLifeCycleManager(lifecycleOwner);
      lifecycleOwner.getLifecycle().addObserver(lifeCycleManager);
      TAG_LIFECYCLE_MAP.put(key, lifeCycleManager);
    }
    return lifeCycleManager;
  }

  /**
   * 生命周期管理, 生命周期各个阶段分发
   */
  private static class InnerLifeCycleManager extends GenericLifecycleObserver {
    /**
     * 绑定，即会发送一次最新数据
     */
    private final BehaviorSubject<Lifecycle.Event> lifecycleSubject = BehaviorSubject.create();

    InnerLifeCycleManager(LifecycleOwner source) {
      super(source);
    }

    @Override public void onStateChanged(LifecycleOwner source, Lifecycle.Event event) {
      lifecycleSubject.onNext(event);
      if (event == Lifecycle.Event.ON_DESTROY) {
        TAG_LIFECYCLE_MAP.remove(source.getClass().getName());
        source.getLifecycle().removeObserver(this);
      }
    }
  }

  abstract static class GenericLifecycleObserver implements LifecycleObserver {
    LifecycleOwner source;

    GenericLifecycleObserver(LifecycleOwner source) {
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
}

