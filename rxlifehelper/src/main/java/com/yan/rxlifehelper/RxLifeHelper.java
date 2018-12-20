package com.yan.rxlifehelper;

import android.arch.lifecycle.GenericLifecycleObserver;
import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleOwner;
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

  private static InnerLifeCycleManager getLifeManager(LifecycleOwner lifecycleOwner) {
    if (lifecycleOwner == null) {
      try {
        throw new Exception("RxLifeHelper: parameter could not be null");
      } catch (Exception e) {
        e.printStackTrace();
      }
      return null;
    }
    String key = lifecycleOwner.getClass().getName();
    InnerLifeCycleManager lifeCycleManager = TAG_LIFECYCLE_MAP.get(key);
    if (lifeCycleManager == null) {
      lifeCycleManager = new InnerLifeCycleManager();
      lifecycleOwner.getLifecycle().addObserver(lifeCycleManager);
      TAG_LIFECYCLE_MAP.put(lifecycleOwner.getClass().getName(), lifeCycleManager);
    }
    return lifeCycleManager;
  }

  public static <T> LifecycleTransformer<T> bindFilterTag(final String tag) {
    return bindFilterTag(tag, true);
  }

  public static <T> LifecycleTransformer<T> bindFilterTag(final String tag, boolean disposeBefore) {
    if (tag == null) {
      return bindEmptyEvent("RxLifeHelper: parameter tag can not be null");
    }
    if (disposeBefore) {
      sendFilterTag(tag);
    }
    return RxLifecycle.bind(TAG_EVENT_SUBJECT.filter(new Predicate<String>() {
      @Override
      public boolean test(String innerTag) throws Exception {
        return tag.equals(innerTag);
      }
    }));
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

  public static <T> LifecycleTransformer<T> bindLifeOwnerUntilEvent(
      LifecycleOwner lifecycleOwner, Lifecycle.Event event) {
    InnerLifeCycleManager lifeCycleManager = getLifeManager(lifecycleOwner);
    if (lifeCycleManager == null) {
      return bindEmptyEvent("RxLifeHelper: target must implements LifecycleOwner");
    }
    return RxLifecycle.bindUntilEvent(lifeCycleManager.lifecycleSubject, event);
  }

  /**
   * 空事件流绑定
   */
  private static <T> LifecycleTransformer<T> bindEmptyEvent(String message) {
    try {
      throw new NullPointerException(message);
    } catch (NullPointerException e) {
      e.printStackTrace();
    }

    // 这里处理参数错误下，直接 subscribe 达到dispose 事件流的效果
    Observable<Lifecycle.Event> observable = Observable.empty();
    observable.subscribe();
    return RxLifecycle.bind(observable);
  }

  /**
   * 生命周期管理, 生命周期各个阶段分发
   */
  private static class InnerLifeCycleManager implements GenericLifecycleObserver {
    /**
     * 绑定，即会发送一次最新数据
     */
    private final BehaviorSubject<Lifecycle.Event> lifecycleSubject = BehaviorSubject.create();

    @Override public void onStateChanged(LifecycleOwner source, Lifecycle.Event event) {
      lifecycleSubject.onNext(event);
      if (event == Lifecycle.Event.ON_DESTROY) {
        TAG_LIFECYCLE_MAP.remove(source.getClass().getName());
        source.getLifecycle().removeObserver(this);
      }
    }
  }
}

