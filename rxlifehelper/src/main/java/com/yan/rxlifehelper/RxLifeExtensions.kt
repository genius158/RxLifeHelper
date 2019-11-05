package com.yan.rxlifehelper

import android.view.View
import androidx.lifecycle.Lifecycle.Event
import androidx.lifecycle.Lifecycle.Event.ON_DESTROY
import androidx.lifecycle.LifecycleOwner
import io.reactivex.Single
import io.reactivex.SingleEmitter
import io.reactivex.SingleObserver
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 结合OnAttachStateChangeListener实现自动释放
 */
fun <T> View.launchUntilViewDetach(
    context: CoroutineContext = Dispatchers.Main, exHandler: ((Throwable) -> Unit)? = null,
    loader: suspend CoroutineScope.() -> T): Job = innerLaunchUntilViewDetach(this, context,
    exHandler, loader)


/**
 * 结合OnAttachStateChangeListener实现自动释放
 */
fun <T> View.launchUntilDetach(
    context: CoroutineContext = Dispatchers.Main, exHandler: ((Throwable) -> Unit)? = null,
    loader: suspend CoroutineScope.() -> T): Job = innerLaunchUntilViewDetach(rootView, context,
    exHandler, loader)

private fun <T> innerLaunchUntilViewDetach(view: View,
    context: CoroutineContext = Dispatchers.Main, exHandler: ((Throwable) -> Unit)? = null,
    loader: suspend CoroutineScope.() -> T): Job {

  var emitter: SingleEmitter<Any>? = null
  val handler = CoroutineExceptionHandler { _, _ -> }
  val job = GlobalScope.launch(handler + context) { loader() }
  job.invokeOnCompletion { e ->
    e?.let {
      it.printStackTrace()
      exHandler?.invoke(it)
    }
    if (emitter?.isDisposed == false) emitter?.onSuccess(1)
  }

  Single.create<Any> { emitter = it }.compose(RxLifeHelper.bindUntilViewDetach<Any>(view))
      .subscribe(object : SingleObserver<Any> {
        override fun onSuccess(t: Any) {
          if (!job.isCancelled) job.cancel()
        }

        override fun onSubscribe(d: Disposable) {}
        override fun onError(e: Throwable) = onSuccess(1)
      })
  return job

}

///////////////////////////////////////////////////////////////////////////////////////////

/**
 * 结合lifecycle实现自动释放
 * call in where which implements LifecycleOwner like fragment or fragmentActivity
 */
fun <T> LifecycleOwner.launchLiveUntil(
    context: CoroutineContext = Dispatchers.Main, event: Event = ON_DESTROY,
    exHandler: ((Throwable) -> Unit)? = null,
    loader: suspend CoroutineScope.() -> T): Job {
  val handler = CoroutineExceptionHandler { _, _ -> }
  val job = GlobalScope.launch(handler + context) {
    val deferred = async { loader() }
    wrapData(this, this@launchLiveUntil, event, deferred)
  }
  job.invokeOnCompletion {
    it?.let {
      it.printStackTrace()
      exHandler?.invoke(it)
    }
  }
  return job
}

private suspend fun <T> wrapData(scope: CoroutineScope, lifecycleOwner: LifecycleOwner,
    event: Event, deferred: Deferred<T>): T {
  return suspendCancellableCoroutine { continuation ->
    Single.create<T> { emmit ->
      continuation.invokeOnCancellation { exception ->
        if (!emmit.isDisposed && exception != null) emmit.onError(exception)
      }
      scope.launch {
        emmit.onSuccess(deferred.await())
      }
    }.compose(RxLifeHelper.bindLifeLiveOwnerUntilEvent(lifecycleOwner, event))
        .subscribe(object : SingleObserver<T> {
          override fun onSubscribe(d: Disposable) {}
          override fun onError(e: Throwable) {
            continuation.resumeWithException(e)
          }

          override fun onSuccess(data: T) {
            if (!continuation.isCancelled) continuation.resume(data)
          }
        })
  }
}
