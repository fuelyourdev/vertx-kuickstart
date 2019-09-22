package dev.fuelyour.service

import dev.fuelyour.tools.*
import dev.fuelyour.verticles.DatabaseVerticle
import dev.fuelyour.verticles.HttpVerticle
import io.reactivex.plugins.RxJavaPlugins
import io.vertx.kotlin.core.deploymentOptionsOf
import io.vertx.reactivex.core.RxHelper
import io.vertx.reactivex.core.Vertx
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.experimental.builder.single
import org.koin.experimental.builder.singleBy

fun main() {
  start()
}

fun start(overrideModule: Module? = null) {
  val vertx = Vertx.vertx()
  RxJavaPlugins.setComputationSchedulerHandler { s -> RxHelper.scheduler(vertx) }
  RxJavaPlugins.setIoSchedulerHandler { s -> RxHelper.blockingScheduler(vertx) }
  RxJavaPlugins.setNewThreadSchedulerHandler { s -> RxHelper.scheduler(vertx) }

  val module = module {
    single { vertx }
    singleBy<RequestHelper, VertxRequestHelper>()
    single<JwtAuthHelper>() bind AuthHandlerSupplier::class
    singleBy<ServiceHandlerSupplier, ServiceHandler>()
    single<SwaggerTraverser>()
    single<SwaggerRouter>()
  }
  startKoin {
    modules(module)
    overrideModule?.let {
      modules(it)
    }
  }

  RxHelper.deployVerticle(
    vertx,
    DatabaseVerticle(),
    deploymentOptionsOf(worker = true)
  ).flatMap {
    RxHelper.deployVerticle(vertx, HttpVerticle())
  }.doOnError { err ->
    err.printStackTrace()
  }.subscribe()
}