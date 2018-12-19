/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yan.rxlifehelper;

import io.reactivex.Observable;
import io.reactivex.functions.Predicate;

import static com.yan.rxlifehelper.Preconditions.checkNotNull;

class RxLifecycle {

  private RxLifecycle() {
    throw new AssertionError("No instances");
  }

  /**
   * Binds the given source to a lifecycle.
   * <p>
   * When the lifecycle event occurs, the source will cease to emit any notifications.
   *
   * @param lifecycle the lifecycle sequence
   * @param event the event which should conclude notifications from the source
   * @return a reusable {@link LifecycleTransformer} that unsubscribes the source at the specified event
   */
  static <T, R> LifecycleTransformer<T> bindUntilEvent(final Observable<R> lifecycle,
      final R event) {
    checkNotNull(lifecycle, "lifecycle == null");
    checkNotNull(event, "event == null");
    return bind(takeUntilEvent(lifecycle, event));
  }

  private static <R> Observable<R> takeUntilEvent(final Observable<R> lifecycle, final R event) {
    return lifecycle.filter(new Predicate<R>() {
      @Override
      public boolean test(R lifecycleEvent) throws Exception {
        return lifecycleEvent.equals(event);
      }
    });
  }

  /**
   * Binds the given source to a lifecycle.
   * <p>
   * This helper automatically determines (based on the lifecycle sequence itself) when the source
   * should stop emitting items. Note that for this method, it assumes <em>any</em> event
   * emitted by the given lifecycle indicates that the lifecycle is over.
   *
   * @param lifecycle the lifecycle sequence
   * @return a reusable {@link LifecycleTransformer} that unsubscribes the source whenever the
   * lifecycle emits
   */
  static <T, R> LifecycleTransformer<T> bind(final Observable<R> lifecycle) {
    return new LifecycleTransformer<>(lifecycle);
  }
}
