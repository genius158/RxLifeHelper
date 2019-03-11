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
  private static final AtomicInteger MAP_ATOMIC = new AtomicInteger();

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
  private static class InnerLifeCycleManager extends AtomicLifecycleObserver {

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
                boolean mapOp = MAP_ATOMIC.compareAndSet(0, 1);
                if (mapOp) {
                  boolean set = compareAndSet(0, 1);
                  if (set) {
                    if (isDestroy) {
                      break;
                    }

                    mgr = TAG_LIFECYCLE_MAP.remove(key);
                    if (mgr != null) {
                      mgr.clear();
                    }
                  }
                  compareAndSet(1, 0);
                  if (MAP_ATOMIC.compareAndSet(1, 0)) {
                    return;
                  }
                }
              }
            }
          }
        });

    private InnerLifeCycleManager(LifecycleOwner source) {
      super(source);
    }

    boolean isDestroy = false;

    @Override public void onStateChanged(LifecycleOwner source, Lifecycle.Event event) {
      lifecycleSubject.onNext(event);

      if (event == Lifecycle.Event.ON_DESTROY) {
        isDestroy = true;
        for (; ; ) {
          boolean set = compareAndSet(0, 1);
          if (set) {
            clear();
            TAG_LIFECYCLE_MAP.remove(getKey(source));
            if (compareAndSet(1, 0)) {
              break;
            }
          }
        }
      }
    }

    private static <T> LifecycleTransformer<T> bind(LifecycleOwner lifecycleOwner,
        Lifecycle.Event event) {
      final String key = getKey(lifecycleOwner);
      InnerLifeCycleManager lifeCycleMgr = TAG_LIFECYCLE_MAP.get(key);

      // 多线程的情况下，保证InnerLifeCycleManager不会创建多个，
      // 否则后面创建的会覆盖前面的记录
      if (lifeCycleMgr == null) {
        for (; ; ) {
          boolean mapOp = MAP_ATOMIC.compareAndSet(0, 1);
          LifecycleTransformer<T> transformer = null;
          if (mapOp) {
            lifeCycleMgr = TAG_LIFECYCLE_MAP.get(key);
            if (lifeCycleMgr == null) {
              lifeCycleMgr = new InnerLifeCycleManager(lifecycleOwner);
              transformer = lifeCycleMgr.put2Map(lifecycleOwner, event);
            }
            if (MAP_ATOMIC.compareAndSet(1, 0)) {
              return transformer;
            }
          }
        }
      }
      return lifeCycleMgr.put2Map(lifecycleOwner, event);
    }

    private <T> LifecycleTransformer<T> put2Map(LifecycleOwner lifecycleOwner,
        Lifecycle.Event event) {
      final String key = getKey(lifecycleOwner);
      for (; ; ) {
        boolean set = compareAndSet(0, 1);
        if (set) {
          if (isDestroy) {
            break;
          }
          if (!TAG_LIFECYCLE_MAP.containsKey(key)) {
            TAG_LIFECYCLE_MAP.put(key, this);
            lifecycleOwner.getLifecycle().addObserver(this);
          }
          reset(lifecycleOwner);
          if (compareAndSet(1, 0)) {
            break;
          }
        }
      }
      return RxLifecycle.bindUntilEvent(lifecycleSubject, event);
    }

    void clear() {
      if (source != null) {
        source.getLifecycle().removeObserver(this);
        source = null;
      }
    }

    void reset(LifecycleOwner lifecycleOwner) {
      if (source == null) {
        source = lifecycleOwner;
        source.getLifecycle().addObserver(this);
      }
    }

    String getKey() {
      return getKey(source);
    }

    static String getKey(Object object) {
      if (object == null) {
        return "null";
      }
      return object.toString();
    }
  }
}

