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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
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
    loader: suspend CoroutineScope.() -> T): Job = innerLaunchUntil(context,
    exHandler, RxLifeHelper.bindUntilViewDetach(this), loader)

/**
 * 结合OnAttachStateChangeListener实现自动释放
 */
fun <T> View.launchUntilDetach(
    context: CoroutineContext = Dispatchers.Main, exHandler: ((Throwable) -> Unit)? = null,
    loader: suspend CoroutineScope.() -> T): Job = innerLaunchUntil(context,
    exHandler, RxLifeHelper.bindUntilViewDetach(rootView), loader)

/**
 * 结合LifecycleOwner 实现自动释放
 */
fun <T> LifecycleOwner.launchUntilEvent(
    context: CoroutineContext = Dispatchers.Main,
    event: Event = ON_DESTROY,
    exHandler: ((Throwable) -> Unit)? = null,
    loader: suspend CoroutineScope.() -> T): Job = innerLaunchUntil(context,
    exHandler, RxLifeHelper.bindLifeOwnerUntilEvent(this, event), loader)


private fun <T> innerLaunchUntil(context: CoroutineContext = Dispatchers.Main,
    exHandler: ((Throwable) -> Unit)? = null,
    transformer: LifecycleTransformer<Any>,
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

  Single.create<Any> { emitter = it }.compose(transformer)
      .subscribe(object : SingleObserver<Any> {
        override fun onSuccess(t: Any) {
          if (!job.isCancelled) job.cancel()
        }

        override fun onSubscribe(d: Disposable) {}
        override fun onError(e: Throwable) = onSuccess(1)
      })
  return job
}

suspend fun LifecycleOwner.resumeUntil(event: Event = ON_DESTROY): Boolean {
  return suspendCancellableCoroutine { continuation ->
    Single.create<Boolean> { emmit ->
      continuation.invokeOnCancellation { exception ->
        if (!emmit.isDisposed && exception != null) emmit.onError(exception)
      }
      emmit.onSuccess(true)
    }.compose(RxLifeHelper.bindLifeLiveOwnerUntilEvent(this, event))
        .subscribe(object : SingleObserver<Boolean> {
          override fun onSubscribe(d: Disposable) {}
          override fun onError(e: Throwable) {
            continuation.resumeWithException(e)
          }

          override fun onSuccess(data: Boolean) {
            if (!continuation.isCancelled) continuation.resume(true)
          }
        })
  }
}
