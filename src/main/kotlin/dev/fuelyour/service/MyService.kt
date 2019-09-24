package dev.fuelyour.service

import dev.fuelyour.controllers.DirectoryController
import dev.fuelyour.controllers.InventoryController
import dev.fuelyour.tools.*
import dev.fuelyour.verticles.ServiceVerticle
import dev.fuelyour.verticles.HttpVerticle
import io.reactivex.plugins.RxJavaPlugins.*
import io.vertx.kotlin.core.deploymentOptionsOf
import io.vertx.reactivex.core.RxHelper
import io.vertx.reactivex.core.Vertx
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.experimental.builder.single
import org.koin.experimental.builder.singleBy

fun main() {
  start()
}

fun start(overrideModule: Module? = null) {
  val vertx = Vertx.vertx()
  setComputationSchedulerHandler { s -> RxHelper.scheduler(vertx) }
  setIoSchedulerHandler { s -> RxHelper.blockingScheduler(vertx) }
  setNewThreadSchedulerHandler { s -> RxHelper.scheduler(vertx) }

  val module = module {
    single(named("controllerPackage")) { "dev.fuelyour.controllers" }
    single { vertx }
    singleBy<RequestHelper, VertxRequestHelper>()
    single<JwtAuthHelper>() bind SwaggerAuthHandler::class
    single { SwaggerServiceHandler(get(named("controllerPackage")))}
    single<SwaggerTraverser>()
    single<SwaggerRouter>()
    single<InventoryController>()
    single<DirectoryController>()
  }
  startKoin {
    modules(module)
    overrideModule?.let {
      modules(it)
    }
  }

  RxHelper.deployVerticle(
    vertx,
    ServiceVerticle(),
    deploymentOptionsOf(worker = true)
  ).flatMap {
    RxHelper.deployVerticle(vertx, HttpVerticle())
  }.doOnError { err ->
    err.printStackTrace()
  }.subscribe()
}