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
