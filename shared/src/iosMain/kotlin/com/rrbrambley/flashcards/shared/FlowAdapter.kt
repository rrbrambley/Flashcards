package com.rrbrambley.flashcards.shared

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Bridges a Kotlin [Flow] to Swift, which has no native `Flow` type. Swift wraps this in an
 * `AsyncStream` (see `asyncStream(_:)` in the iOS app): [collect] starts collecting on the main
 * dispatcher, invokes [onEach] per emission and [onCompletion] when the flow finishes (or with the
 * error that ended it), and returns a [Cancellable] so Swift can stop collection (e.g. when the
 * stream's consumer goes away).
 *
 * Suspend functions need no equivalent — Kotlin `suspend` bridges to Swift `async` automatically.
 */
class FlowAdapter<T>(private val flow: Flow<T>) {
    fun collect(onEach: (T) -> Unit, onCompletion: (error: Throwable?) -> Unit): Cancellable {
        val job = Job()
        CoroutineScope(Dispatchers.Main + job).launch {
            try {
                flow.collect { onEach(it) }
                onCompletion(null)
            } catch (e: Throwable) {
                // A cancelled collection completes silently; anything else is a real error.
                onCompletion(if (job.isCancelled) null else e)
            }
        }
        return Cancellable { job.cancel() }
    }
}

/** A one-shot cancellation handle handed to Swift (Kotlin `Job` doesn't bridge ergonomically). */
class Cancellable(private val onCancel: () -> Unit) {
    fun cancel() = onCancel()
}
