package dev.fuelyour.service

import dev.fuelyour.config.config
import dev.fuelyour.controllers.DirectoryController
import dev.fuelyour.controllers.InventoryController
import dev.fuelyour.repositories.InventoryRepo
import dev.fuelyour.tools.*
import dev.fuelyour.verticles.HttpVerticle
import io.vertx.core.Vertx
import io.vertx.kotlin.core.deployVerticleAwait
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

fun main() {
  start()
}

fun start(overrideModule: Module? = null) {
  val vertx = Vertx.vertx()
  val config = vertx.config()

  val module = module {
    single(named("controllerPackage")) { "dev.fuelyour.controllers" }
    single(named("config")) { config }
    single(named("schema")) { "public" }
    single { vertx }
    single {
      JwtAuthHelper(get(named("config")), get())
    } bind SwaggerAuthHandler::class
    single { ControllerSupplier(get(named("controllerPackage")))}
    single { SwaggerServiceHandler(get(), get(), get()) }
    single { SwaggerTraverser() }
    single { SwaggerRouter(get(), get(), get()) }
    single { InventoryController(get(), get()) }
    single { DirectoryController(get(), get()) }
    single { DatabaseAccess(get(named("config")), get())}
    single { InventoryRepo(get(named("schema"))) }
    single<Serializer> { SerializerImpl() }
    single<Deserializer> { DeserializerImpl() }
  }
  startKoin {
    modules(module)
    overrideModule?.let {
      modules(it)
    }
  }

  runBlocking {
    vertx.deployVerticleAwait(HttpVerticle())
  }
}