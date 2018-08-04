package net.cordaclub.marge

import io.vertx.core.Future

abstract class Initializer {
    protected var initialised = false

    fun isInitialised(): Boolean {
        return initialised
    }

    abstract fun initialiseDemo(): Future<Unit>
}