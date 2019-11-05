# RxLifeHelper

### implementation 'com.yan:rxlifehelper:2.2.1'
bindUntil系列尽可能的保证在subscribe的上一个操作符设置

### demo
协程扩展函数：
1. LifecycleOwner.launchLiveUntil 在配合生命周期的前提下，配合LiveData
2. View.launchUntilDetach 配合View 的rootView detach 释放
3. View.launchUntilViewDetach 配合View detach 释放

```
 launchLiveUntil(Dispatchers.Main) {
      ...
      doOnUI() 
    }
    
  Observable.timer(1000, TimeUnit.MILLISECONDS)
     .compose(RxLifeHelper.<Long>bindFilterTag("getData"))
     //配合生命周期
     .compose(RxLifeHelper.<Long>bindUntilLifeEvent(this, Lifecycle.Event.ON_PAUSE))
     // 在配合生命周期的前提下，配合LiveData
     .compose(RxLifeHelper.<Long>bindUntilLifeLiveEvent(this, Lifecycle.Event.ON_PAUSE))
     .subscribe(new Consumer<Long>() {
       @Override public void accept(Long aLong) throws Exception {
         Log.e("RxLifeHelper", "event " + data);
       }
     });
```
### change log: 
version 1.2.6: 添加bindUntilActivityDetach(rootView 的 OnAttachStateChangeListener 实现) 弥补普通的activity没有lifeCircle，无法实现compose(RxLifeHelper.<Long>bindUntilLifeEvent(this, Lifecycle.Event.ON_DESTROY))
<br/> version 1.2.7: androidx
<br/> version 1.3.1: 增加bindUntilDetach 处理view detached 取消绑定
<br/> 
<br/> version 2.0.2: 配置增加liveData 配和方式
<br/> version 2.0.6: 增加协程扩展函数LifecycleOwner.launchLiveUntil、View.launchUntilDetach
<br/> version 2.0.8: 增加协程扩展函数View.launchUntilViewDetach