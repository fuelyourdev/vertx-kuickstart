package dev.fuelyour.tools

import io.reactivex.Single
import io.vertx.core.json.JsonObject
import io.vertx.core.shareddata.impl.ClusterSerializable
import io.vertx.kotlin.core.eventbus.deliveryOptionsOf
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.core.eventbus.Message

interface RequestHelper {
  fun <T : ClusterSerializable> request(
    address: String,
    message: JsonObject?,
    headers: Map<String, String>?
  ): Single<Message<T>>
}

class VertxRequestHelper(val vertx: Vertx) : RequestHelper {
  override fun <T : ClusterSerializable> request(
    address: String,
    message: JsonObject?,
    headers: Map<String, String>?
  ): Single<Message<T>> {
    if (headers != null)
      return vertx.eventBus()
        .rxRequest<T>(address, message, deliveryOptionsOf(headers = headers))
    return vertx.eventBus().rxRequest<T>(address, message)
  }
}