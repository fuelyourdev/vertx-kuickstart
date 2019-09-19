package dev.fuelyour.controllers

import dev.fuelyour.tools.JWTHelper
import dev.fuelyour.tools.RequestHelper
import dev.fuelyour.tools.VertxRequestHelper
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.web.client.WebClient
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.spekframework.spek2.dsl.Root
import org.spekframework.spek2.lifecycle.CachingMode

fun Root.setup() {
    val vertx by memoized { Vertx.vertx() }
    val webClient by memoized { WebClient.create(vertx) }
    val deployIds by memoized(mode = CachingMode.EACH_GROUP, factory = { mutableListOf<String>() } )
    val jwtHelper by memoized { JWTHelper(vertx) }

    val module = module {
        single { vertx }
        single { jwtHelper }
        single { VertxRequestHelper(get()) }
        single { DirectoryController(get()) }
        single { InventoryController() }
    }
    startKoin {
        modules(module)
    }

}