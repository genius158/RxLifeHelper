# RxLifeHelper

### implementation 'com.yan:rxlifehelper:1.2.7'

### demo

```
public class MainActivity extends AppCompatActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
  }

  @Override protected void onResume() {
    super.onResume();

    // onPause 自动取消
    Observable.interval(1000, TimeUnit.MILLISECONDS)
        .compose(RxLifeHelper.<Long>bindUntilLifeEvent((Fragment) null, Lifecycle.Event.ON_PAUSE))
        .subscribe(new Consumer<Long>() {
          @Override public void accept(Long aLong) throws Exception {
            Log.e("RxLifeHelper", "interval ---------");
          }
        });

    // 1111111111111 将不会 被打印
    getData("111111111111111111111111111111");//执行将会被取消
    getData("222222222222222222222222222222");
  }

  private void getData(final String data) {
    Observable.timer(1000, TimeUnit.MILLISECONDS)
        .compose(RxLifeHelper.<Long>bindFilterTag("getData"))
        .compose(RxLifeHelper.<Long>bindUntilLifeEvent(this, Lifecycle.Event.ON_PAUSE))
        .subscribe(new Consumer<Long>() {
          @Override public void accept(Long aLong) throws Exception {
            Log.e("RxLifeHelper", "event " + data);
          }
        });
  }
}
```
### change log: 
version 1.2.6: 添加bindUntilActivityDetach(rootView 的 OnAttachStateChangeListener 实现) 弥补普通的activity没有lifeCircle，无法实现compose(RxLifeHelper.<Long>bindUntilLifeEvent(this, Lifecycle.Event.ON_DESTROY))
<br/> version 1.2.7: androidx
<br/> version 1.3.1: 增加bindUntilDetach 处理view detached 取消绑定
<br/> 
<br/> version 2.0.0: 配置增加liveData 配和方式