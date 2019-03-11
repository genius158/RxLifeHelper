package com.yan.rxlifehelper;

import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleOwner;
import android.content.Context;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import io.reactivex.Observable;
import io.reactivex.functions.Predicate;
import io.reactivex.subjects.PublishSubject;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
    if (lifecycleOwner == null) {
      return bindErrorEvent(new NullPointerException("RxLifeHelper: target could not be null"));
    }
    return InnerLifeCycleManager.bind(lifecycleOwner, event);
  }

  /**
   * 空事件流绑定
   */
  private static <T> LifecycleTransformer<T> bindErrorEvent(Throwable throwable) {
    // 这里处理参数错误下，直接 异常返回
    return RxLifecycle.bind(Observable.error(throwable));
  }

  /**
   * 生命周期管理, 生命周期各个阶段分发
   */
  private static class InnerLifeCycleManager extends GenericLifecycleObserver {
    /**
     * BehaviorSubject 绑定，即会发送一次最新数据
     */
    private InnerBehaviorSubject<Lifecycle.Event> lifecycleSubject =
        InnerBehaviorSubject.create(new Runnable() {
          @Override public void run() {
            //确定没有在执行绑定，同时没有绑定对象
            if (!lifecycleSubject.hasObservers()) {
              InnerLifeCycleManager mgr;
              String key = getKey();
              for (; ; ) {
                if (lifecycleSubject.hasObservers()) {
                  break;
                }
                boolean set = atomic.compareAndSet(0, 2);
                if (set) {
                  if (lifecycleSubject.hasObservers()) {
                    break;
                  }
                  mgr = TAG_LIFECYCLE_MAP.remove(key);
                  if (mgr != null) {
                    mgr.clear();
                  }
                }
                if (atomic.compareAndSet(2, 0)) {
                  break;
                }
              }
            }
          }
        });

    /**
     * 判断当前状态
     * 0.正常 1.add 2.remove
     */
    private AtomicInteger atomic = new AtomicInteger();

    private InnerLifeCycleManager(LifecycleOwner source) {
      super(source);
    }

    @Override public void onStateChanged(LifecycleOwner source, Lifecycle.Event event) {
      lifecycleSubject.onNext(event);
      if (event == Lifecycle.Event.ON_DESTROY) {
        TAG_LIFECYCLE_MAP.remove(source.getClass().getName());
        clear();
      }
    }

    private static <T> LifecycleTransformer<T> bind(LifecycleOwner lifecycleOwner,
        Lifecycle.Event event) {
      // 绑定在执行，则这个加1

      final String key = lifecycleOwner.getClass().getName();
      InnerLifeCycleManager lifeCycleMgr = TAG_LIFECYCLE_MAP.get(key);

      // 多线程的情况下，保证InnerLifeCycleManager不会创建多个，
      // 否则后面创建的会覆盖前面的记录
      if (lifeCycleMgr == null) {
        synchronized (RxLifeHelper.class) {
          lifeCycleMgr = TAG_LIFECYCLE_MAP.get(key);
          if (lifeCycleMgr == null) {
            lifeCycleMgr = new InnerLifeCycleManager(lifecycleOwner);
            TAG_LIFECYCLE_MAP.put(key, lifeCycleMgr);
          }
        }
      }
      return lifeCycleMgr.bindInner(event, lifecycleOwner);
    }

    private <T> LifecycleTransformer<T> bindInner(Lifecycle.Event event,
        LifecycleOwner lifecycleOwner) {
      LifecycleTransformer<T> lifecycleTransformer =
          RxLifecycle.bindUntilEvent(lifecycleSubject, event);
      for (; ; ) {
        boolean set = atomic.compareAndSet(0, 1);
        if (set) {
          final String key = getKey();
          // 绑定在执行结束，则这个减一
          if (!TAG_LIFECYCLE_MAP.containsKey(key)) {
            TAG_LIFECYCLE_MAP.put(key, this);
          }
        }
        lifecycleOwner.getLifecycle().addObserver(this);
        if (atomic.compareAndSet(1, 0)) {
          break;
        }
      }
      return lifecycleTransformer;
    }
  }
}

