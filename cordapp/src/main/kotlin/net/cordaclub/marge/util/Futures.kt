package net.cordaclub.marge.util

import io.vertx.core.Future
import net.corda.core.concurrent.CordaFuture
import net.corda.core.messaging.FlowHandle

fun <V> CordaFuture<V>.toEasyFuture() : Future<V> {
    val result = Future.future<V>()
    this.then {
        try {
            result.complete(this.get())
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
        }
    }
    return result
}