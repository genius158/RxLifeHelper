package com.yan.rxlifehelper;

import android.arch.lifecycle.GenericLifecycleObserver;
import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleOwner;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.SparseArray;
import io.reactivex.Observable;
import io.reactivex.functions.Predicate;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.PublishSubject;
import java.util.HashMap;

/**
 * 基于 rxlifehelper
 *
 * 作用:1. 多次调用相同方法，可以取消上一次方法的调用（bindMethodTag）
 * 2. 生命周期绑定
 *
 * @author yanxianwei
 */
public class RxLifeHelper {
  private static final HashMap<String, InnerLifeCycleManager> TAG_LIFECYCLE_MAP = new HashMap<>();
  private static final SparseArray<String> TARGET_TAG_MAP = new SparseArray<>();

  /**
   * 处理tag 发送事件形式的绑定处理
   */
  private static final PublishSubject<String> TAG_EVENT_SUBJECT = PublishSubject.create();

  /**
   * 绑定目标生命周期
   *
   * @param targetTag connect with FragmentActivity and InnerLifeCycleManager
   */
  public static void bind(FragmentActivity activity, String targetTag) {
    bindLifeOwner(activity, targetTag);
  }

  public static void bind(FragmentActivity activity) {
    bindLifeOwner(activity);
  }

  /**
   * 绑定目标生命周期
   */
  public static void bind(Fragment fragment, String targetTag) {
    bindLifeOwner(fragment, targetTag);
  }

  public static void bind(Fragment fragment) {
    bindLifeOwner(fragment);
  }

  public static void bindLifeOwner(LifecycleOwner lifecycleOwner, String targetTag) {
    if (getLifecycleFromTarget(lifecycleOwner) == null) {
      try {
        throw new Exception("RxLifeHelper: parameter in method bind is not in correct type");
      } catch (Exception e) {
        e.printStackTrace();
      }
      return;
    }

    if (targetTag == null) {
      targetTag = lifecycleOwner.getClass().getSimpleName();
    }

    InnerLifeCycleManager lifeCycleManager = TAG_LIFECYCLE_MAP.get(targetTag);
    if (lifeCycleManager != null) {
      lifeCycleManager.clear();
      try {
        throw new Exception("RxLifeHelper: bind multiple in "
            + lifecycleOwner.getClass().getName()
            + ", anything bind before was cleared");
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    lifeCycleManager = new InnerLifeCycleManager(targetTag, lifecycleOwner);
    lifecycleOwner.getLifecycle().addObserver(lifeCycleManager);
    TAG_LIFECYCLE_MAP.put(targetTag, lifeCycleManager);
    TARGET_TAG_MAP.put(lifecycleOwner.hashCode(), targetTag);
  }

  public static void bindLifeOwner(LifecycleOwner lifecycleOwner) {
    bindLifeOwner(lifecycleOwner, null);
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

  /**
   * 返回 Lifecycle 用于判断target是否可用
   */
  private static Lifecycle getLifecycleFromTarget(Object targetOriginal) {
    if (targetOriginal instanceof LifecycleOwner) {
      return ((LifecycleOwner) targetOriginal).getLifecycle();
    }
    return null;
  }

  /**
   * 一定确保tag被绑定，否着执行将被取消
   *
   * @param targetTag 标记
   * @param event 事件
   * @param <T> Observable 类型
   * @return LifecycleTransformer
   */
  public static <T> LifecycleTransformer<T> bindUntilLifeEvent(String targetTag,
      Lifecycle.Event event) {
    return innerBindUntilLifeEvent(targetTag, event);
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
      LifecycleOwner lifecycleOwner,
      Lifecycle.Event event) {
    if (getLifecycleFromTarget(lifecycleOwner) == null) {
      return bindEmptyEvent(
          "RxLifeHelper: parameter target in method bindUntilLifeEvent is not in correct type");
    }

    String targetTag = TARGET_TAG_MAP.get(lifecycleOwner.hashCode());
    if (targetTag == null) {
      return bindEmptyEvent("RxLifeHelper: check the methods that related bind is called");
    }

    return innerBindUntilLifeEvent(targetTag, event);
  }

  private static <T> LifecycleTransformer<T> innerBindUntilLifeEvent(
      String targetTag,
      Lifecycle.Event event) {

    final InnerLifeCycleManager lifeCycleManager = TAG_LIFECYCLE_MAP.get(targetTag);
    if (lifeCycleManager == null) {
      return bindEmptyEvent("RxLifeHelper: check the methods that related bind was called");
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
    private final String targetTag;
    private final LifecycleOwner target;

    private InnerLifeCycleManager(String targetTag, LifecycleOwner target) {
      this.targetTag = targetTag;
      this.target = target;
    }

    private void clear() {
      onStateChanged(target, Lifecycle.Event.ON_DESTROY);
    }

    @Override public void onStateChanged(LifecycleOwner source, Lifecycle.Event event) {
      lifecycleSubject.onNext(event);
      if (event == Lifecycle.Event.ON_DESTROY) {
        TAG_LIFECYCLE_MAP.remove(targetTag);
        TARGET_TAG_MAP.remove(source.hashCode());
        source.getLifecycle().removeObserver(this);
      }
    }
  }
}

