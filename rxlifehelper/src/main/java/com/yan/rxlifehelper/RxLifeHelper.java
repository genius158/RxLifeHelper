package com.yan.rxlifehelper;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
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

  @MainThread public static <T> LifecycleTransformer<T> bindUntilViewDetach(final View view) {
    if (view == null) {
      return bindErrorEvent(new NullPointerException("view could not be null"));
    }
    StateAttach stateAttach = (StateAttach) view.getTag(R.id.tag_view_attach);
    if (stateAttach == null) {
      synchronized (RxLifeHelper.class) {
        stateAttach = (StateAttach) view.getTag(R.id.tag_view_attach);
        if (stateAttach == null) {
          stateAttach = new StateAttach();
          view.addOnAttachStateChangeListener(stateAttach);
          view.setTag(R.id.tag_view_attach, stateAttach);
        }
      }
    }
    return RxLifecycle.bind(stateAttach.lifecycleSubject);
  }

  @MainThread public static <T> LifecycleTransformer<T> bindUntilDetach(final View view) {
    return bindUntilViewDetach(view.getRootView());
  }

  @MainThread public static <T> LifecycleTransformer<T> bindUntilDetach(final Activity activity) {
    if (activity == null || activity.getWindow() == null || activity.isFinishing()) {
      return bindErrorEvent(new IllegalStateException("activity status not good"));
    }
    return bindUntilViewDetach(activity.getWindow().getDecorView());
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

  ///////////////////////////////////// live data ///////////////////////////////////////////
  //              配合liveData onNext、onSuccess等回调，会强制回到主线程                       //
  //         use with liveData onNext、onSuccess .etc will call on UI thread               //
  ///////////////////////////////////////////////////////////////////////////////////////////

  @MainThread
  public static <T> LifecycleTransformer<T> bindUntilLifeLiveEvent(FragmentActivity target,
      Lifecycle.Event event) {
    return bindLifeLiveOwnerUntilEvent(target, event);
  }

  @MainThread public static <T> LifecycleTransformer<T> bindUntilLifeLiveEvent(Fragment target,
      Lifecycle.Event event) {
    return bindLifeLiveOwnerUntilEvent(target, event);
  }

  @MainThread public static <T> LifecycleTransformer<T> bindUntilLifeLiveEvent(Context target,
      Lifecycle.Event event) {
    if (!(target instanceof LifecycleOwner)) {
      return bindErrorEvent(
          new IllegalArgumentException("RxLifeHelper: target must implements LifecycleOwner"));
    }
    return bindLifeLiveOwnerUntilEvent((LifecycleOwner) target, event);
  }

  @MainThread public static <T> LifecycleTransformer<T> bindLifeLiveOwnerUntilEvent(
      LifecycleOwner lifecycleOwner, Lifecycle.Event event) {
    if (lifecycleOwner == null) {
      return bindErrorEvent(new NullPointerException("RxLifeHelper: target could not be null"));
    }
    if (lifecycleOwner.getLifecycle() == null) {
      return bindErrorEvent(new NullPointerException("RxLifeHelper: lifecycle could not be null"));
    }
    if (lifecycleOwner.getLifecycle().getCurrentState() == Lifecycle.State.DESTROYED) {
      return bindErrorEvent(new NullPointerException("RxLifeHelper: lifecycle owner is destroy"));
    }
    RxLifeHelper.InnerLifeCycleManager lifeCycleManager = getLifeManager(lifecycleOwner);
    return RxLifecycle.bindUntilEvent(lifecycleOwner, lifeCycleManager.lifecycleSubject, event);
  }

  static <T> LifecycleTransformer<T> bindErrorEvent(Throwable throwable) {
    // 这里处理参数错误下，直接 异常返回
    return RxLifecycle.bind(Observable.error(throwable));
  }

  static InnerLifeCycleManager getLifeManager(@NonNull LifecycleOwner lifecycleOwner) {
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
  static class InnerLifeCycleManager extends GenericLifecycleObserver implements LifecycleOwner {
    /**
     * 绑定，即会发送一次最新数据
     */
    final BehaviorSubject<Lifecycle.Event> lifecycleSubject = BehaviorSubject.create();

    InnerLifeCycleManager(LifecycleOwner source) {
      super(source);
    }

    @Override public void onStateChanged(LifecycleOwner source, final Lifecycle.Event event) {
      lifecycleSubject.onNext(event);
      if (mLifecycleRegistry.getObserverCount() > 0) {
        mLifecycleRegistry.handleLifecycleEvent(event);
      }
      if (event == Lifecycle.Event.ON_DESTROY) {
        TAG_LIFECYCLE_MAP.remove(source.toString());
        source.getLifecycle().removeObserver(this);
        mLifecycleRegistry = null;
      }
    }

    LifecycleRegistry mLifecycleRegistry = new LifecycleRegistry(source);

    @NonNull @Override public Lifecycle getLifecycle() {
      return mLifecycleRegistry;
    }
  }

  private static class StateAttach implements View.OnAttachStateChangeListener {
    private final PublishSubject<Boolean> lifecycleSubject = PublishSubject.create();

    @Override public void onViewAttachedToWindow(View v) {
    }

    @Override public void onViewDetachedFromWindow(View v) {
      lifecycleSubject.onNext(true);
      v.removeOnAttachStateChangeListener(this);
      v.setTag(R.id.tag_view_attach, null);
    }
  }
}

