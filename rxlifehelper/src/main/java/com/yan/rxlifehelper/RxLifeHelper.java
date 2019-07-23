package com.yan.rxlifehelper;

import android.app.Activity;
import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleOwner;
import android.content.Context;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import io.reactivex.Observable;
import io.reactivex.functions.Action;
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
  private static volatile HashMap<String, InnerLifeCycleManager> TAG_LIFECYCLE_MAP =
      new HashMap<>();

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
    }));
  }

  public static void sendFilterTag(String tag) {
    TAG_EVENT_SUBJECT.onNext(tag);
  }

  public static <T> LifecycleTransformer<T> bindUntilActivityDetach(final Activity activity) {
    if (activity == null || activity.getWindow() == null || activity.isFinishing()) {
      return bindErrorEvent(new IllegalStateException("activity status not good"));
    }
    final PublishSubject<Object> publishSubject = PublishSubject.create();
    final View.OnAttachStateChangeListener stateChangeListener;
    final View decor = activity.getWindow().getDecorView();
    decor.addOnAttachStateChangeListener(
        stateChangeListener = new View.OnAttachStateChangeListener() {
          @Override public void onViewAttachedToWindow(View v) {
          }

          @Override public void onViewDetachedFromWindow(View v) {
            v.removeOnAttachStateChangeListener(this);
            publishSubject.onNext(new Object());
          }
        });
    return RxLifecycle.bind(publishSubject.doFinally(new Action() {
      @Override public void run() throws Exception {
        decor.removeOnAttachStateChangeListener(stateChangeListener);
      }
    }));
  }

  @MainThread public static <T> LifecycleTransformer<T> bindUntilLifeEvent(FragmentActivity target,
      Lifecycle.Event event) {
    return bindLifeOwnerUntilEvent(target, event);
  }

  @MainThread public static <T> LifecycleTransformer<T> bindUntilLifeEvent(Fragment target,
      Lifecycle.Event event) {
    return bindLifeOwnerUntilEvent(target, event);
  }

  @MainThread public static <T> LifecycleTransformer<T> bindUntilLifeEvent(Context target,
      Lifecycle.Event event) {
    if (!(target instanceof LifecycleOwner)) {
      return bindErrorEvent(
          new IllegalArgumentException("RxLifeHelper: target must implements LifecycleOwner"));
    }
    return bindLifeOwnerUntilEvent((LifecycleOwner) target, event);
  }

  @MainThread
  public static <T> LifecycleTransformer<T> bindLifeOwnerUntilEvent(LifecycleOwner lifecycleOwner,
      Lifecycle.Event event) {
    if (lifecycleOwner == null) {
      return bindErrorEvent(new NullPointerException("RxLifeHelper: target could not be null"));
    }
    if (lifecycleOwner.getLifecycle() == null) {
      return bindErrorEvent(new NullPointerException("RxLifeHelper: lifecycle could not be null"));
    }
    if (lifecycleOwner.getLifecycle().getCurrentState() == Lifecycle.State.DESTROYED) {
      return bindErrorEvent(new NullPointerException("RxLifeHelper: lifecycle owner is destroy"));
    }
    return RxLifecycle.bindUntilEvent(getLifeManager(lifecycleOwner).lifecycleSubject, event);
  }

  private static <T> LifecycleTransformer<T> bindErrorEvent(Throwable throwable) {
    // 这里处理参数错误下，直接 异常返回
    return RxLifecycle.bind(Observable.error(throwable));
  }

  private static InnerLifeCycleManager getLifeManager(@NonNull LifecycleOwner lifecycleOwner) {
    String key = lifecycleOwner.toString();
    InnerLifeCycleManager lifeCycleManager = TAG_LIFECYCLE_MAP.get(key);
    if (lifeCycleManager == null) {
      synchronized (key.intern()) {
        lifeCycleManager = TAG_LIFECYCLE_MAP.get(key);
        if (lifeCycleManager == null) {
          lifeCycleManager = new InnerLifeCycleManager(lifecycleOwner);
          lifecycleOwner.getLifecycle().addObserver(lifeCycleManager);
          TAG_LIFECYCLE_MAP.put(key, lifeCycleManager);
        }
      }
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

    @Override public void onStateChanged(LifecycleOwner source, final Lifecycle.Event event) {
      lifecycleSubject.onNext(event);
      if (event == Lifecycle.Event.ON_DESTROY) {
        TAG_LIFECYCLE_MAP.remove(source.toString());
        source.getLifecycle().removeObserver(this);
      }
    }
  }
}

