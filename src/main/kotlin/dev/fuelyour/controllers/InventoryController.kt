package dev.fuelyour.controllers

import dev.fuelyour.annotations.Body
import dev.fuelyour.tools.RequestHelper
import io.reactivex.Single
import io.vertx.core.json.JsonObject
import io.vertx.core.shareddata.impl.ClusterSerializable
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj

class InventoryController(
  private val requestHelper: RequestHelper
) {

  fun get(
    id: String?,
    searchString: String?,
    limit: Int = 100
  ): Single<ClusterSerializable> {
    return requestHelper.request<ClusterSerializable>(
      "db.exampledb",
      json { obj() },
      mapOf("resource" to "inventory", "action" to "all")
    )
      .map {
        it.body()
      }
  }

  fun post(@Body body: JsonObject): JsonObject {
    return JsonObject(mapOf("body" to body))
  }
}