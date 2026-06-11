import Shared

/// Bridges a Kotlin `FlowAdapter` to a Swift `AsyncStream`, the project's chosen Kotlin↔Swift
/// interop for Flows (hand-rolled, no SKIE). Suspend functions need no helper — Kotlin `suspend`
/// already bridges to Swift `async` (call them with `try await`).
///
/// Emissions are optional because the Obj-C bridge types every Flow element as nullable; wrap
/// non-null Kotlin Flows (see `Bridging.kt`) so `nil` only appears when it's meaningful.
/// Cancelling/terminating the stream cancels the underlying Kotlin collection.
func asyncStream<T>(_ adapter: FlowAdapter<T>) -> AsyncStream<T?> {
    AsyncStream { continuation in
        let cancellable = adapter.collect(
            onEach: { value in continuation.yield(value) },
            onCompletion: { _ in continuation.finish() }
        )
        continuation.onTermination = { _ in cancellable.cancel() }
    }
}

/// Like `asyncStream(_:)`, but surfaces the error a Flow ends with so callers can react to a failed
/// refresh (e.g. an offline `observeHomeData()` emits the cached feed, then throws). Iterate with
/// `for try await`; a clean completion ends the loop, an error throws out of it.
func asyncThrowingStream<T>(_ adapter: FlowAdapter<T>) -> AsyncThrowingStream<T?, Error> {
    AsyncThrowingStream { continuation in
        let cancellable = adapter.collect(
            onEach: { value in continuation.yield(value) },
            onCompletion: { error in continuation.finish(throwing: error.map(KotlinFlowError.init)) }
        )
        continuation.onTermination = { _ in cancellable.cancel() }
    }
}

/// Wraps the Kotlin `Throwable` a Flow ended with as a Swift `Error` (Kotlin throwables don't
/// conform to `Error`). Callers usually only care that a failure happened, not its details.
struct KotlinFlowError: Error {
    let cause: KotlinThrowable
    init(_ cause: KotlinThrowable) { self.cause = cause }
}
