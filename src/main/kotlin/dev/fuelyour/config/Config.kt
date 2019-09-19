package dev.fuelyour.config

import io.reactivex.Single
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.config.configRetrieverOptionsOf
import io.vertx.kotlin.config.configStoreOptionsOf
import io.vertx.kotlin.core.json.jsonObjectOf
import io.vertx.reactivex.config.ConfigRetriever
import io.vertx.reactivex.core.Vertx


fun main() {
    val vertx = Vertx.vertx()
    Config.config(vertx).doOnSuccess { println(it) }.subscribe()
}

object Config {
    private lateinit var retriever: ConfigRetriever

    fun config(vertx: Vertx): Single<JsonObject> {
        if (!this::retriever.isInitialized) {
            val fileStore = configStoreOptionsOf(type = "file", config = jsonObjectOf("path" to "config.json"))
            val envStore = configStoreOptionsOf(type = "env")
            retriever = ConfigRetriever.create(vertx, configRetrieverOptionsOf(stores = listOf(fileStore, envStore)))
        }

        return if (!retriever.cachedConfig.isEmpty)
            Single.just(retriever.cachedConfig)
        else
            retriever.rxGetConfig()
    }
}
