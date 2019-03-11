# RxLifeHelper

### implementation 'com.yan:rxlifehelper:1.2.1'

冲突，尝试
<br/>
implementation('com.yan:rxlifehelper:1.2.1'){
<br/> exclude group: 'com.android.support'
<br/> exclude group: 'io.reactivex.rxjava2'
<br/> }

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