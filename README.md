# RxLifeHelper

### implementation 'com.yan:rxlifehelper:2.3.0'
bindUntil系列尽可能的保证在subscribe的上一个操作符设置

### demo
协程扩展函数：
1. LifecycleOwner.launchUntilEvent 在配合生命周期的前提下 resumeUntil 配合LiveData
2. View.launchUntilDetach 配合View 的rootView detach 释放
3. View.launchUntilViewDetach 配合View detach 释放

```
     launchUntilEvent {
       // io代码
       val data = suspendReqeustData()
       
       // 判断生命周期,保证生命周期resume的前提下继续执行
       resumeUntil()
       
       // 继续执行
       displayUi(data)
     }
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
<br/> version 2.0.6: 增加协程扩展函数View.launchUntilDetach
<br/> version 2.0.8: 增加协程扩展函数View.launchUntilViewDetach
<br/> version 2.2.5: 增加协程扩展函数 LifecycleOwner.launchUntilEvent 在配合生命周期的前提下 resumeUntil 配合LiveData
<br/> version 2.3.0: TAG_LIFECYCLE_MAP key 改为 观察对象，toString不能保证一直相同