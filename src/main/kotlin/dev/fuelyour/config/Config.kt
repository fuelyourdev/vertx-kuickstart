package dev.fuelyour.config

import io.vertx.config.ConfigRetriever
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.config.configRetrieverOptionsOf
import io.vertx.kotlin.config.configStoreOptionsOf
import io.vertx.kotlin.config.getConfigAwait
import io.vertx.kotlin.core.json.jsonObjectOf
import kotlinx.coroutines.runBlocking

private lateinit var retriever: ConfigRetriever

fun Vertx.config(): JsonObject {
  if (!::retriever.isInitialized) initRetriever()

  return runBlocking {
    if (retriever.cachedConfig.isEmpty) retriever.getConfigAwait()
    else retriever.cachedConfig
  }
}

private fun Vertx.initRetriever() {
  val fileConfig = jsonObjectOf("path" to "config.json")
  val stores = listOf(
    configStoreOptionsOf(type = "file", config = fileConfig),
    configStoreOptionsOf(type = "env")
  )
  val options = configRetrieverOptionsOf(stores = stores)
  retriever = ConfigRetriever.create(this, options)
}
