package dev.fuelyour.controllers

import dev.fuelyour.config.Config
import dev.fuelyour.repositories.InventoryRepo
import dev.fuelyour.tools.DatabaseAccess
import dev.fuelyour.tools.JwtAuthHelper
import dev.fuelyour.verticles.HttpVerticle
import io.vertx.core.Vertx
import io.vertx.ext.web.client.WebClient
import io.vertx.kotlin.core.deployVerticleAwait
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.spekframework.spek2.dsl.Root
import org.spekframework.spek2.lifecycle.CachingMode

fun Root.setup() {
  val vertx by memoized { Vertx.vertx() }
  val config = Config.config(vertx)
  val webClient by memoized { WebClient.create(vertx) }
  val deployIds by memoized(mode = CachingMode.EACH_GROUP, factory = { mutableListOf<String>() } )

  val module = module(override = true) {
    single { vertx }
    single { JwtAuthHelper(config, vertx) }
    single { DatabaseAccess(config, vertx) }
    single { InventoryRepo("test") }
  }
  startKoin {
    modules(module)
  }

  runBlocking {
    deployIds.add(vertx.deployVerticleAwait(HttpVerticle()))
  }

}