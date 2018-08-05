package net.cordaclub.marge.util

import io.vertx.core.Future
import net.corda.core.concurrent.CordaFuture
import net.corda.core.messaging.FlowHandle
import net.corda.core.utilities.getOrThrow

fun <V> CordaFuture<V>.toEasyFuture() : Future<V> {
    val result = Future.future<V>()
    this.then {
        try {
            result.complete(this.getOrThrow())
        } catch (err: Throwable) {
            result.fail(err)
        }
    }
    return result
}

fun <V> FlowHandle<V>.toEasyFuture() : Future<V> = this.returnValue.toEasyFuture()

fun <V> Future<V>.onSuccess(fn: (V) -> Unit) : Future<V> {
    val result = Future.future<V>()
    this.setHandler {
        if (succeeded()) {
            try {
                fn(result())
                result.complete(result())
            } catch (err: Throwable) {
                result.fail(err)
            }
        } else {
            result.fail(cause())
        }
    }
    return result
}

fun <V> Future<V>.onFail(fn: (Throwable) -> Unit) : Future<V> {
    val result = Future.future<V>()
    this.setHandler {
        if (failed()) {
            try {
                fn(cause())
                result.fail(cause())
            } catch (err: Throwable) {
                result.fail(err)
            }
        } else {
            result.complete(result())
        }
    }
    return result
}