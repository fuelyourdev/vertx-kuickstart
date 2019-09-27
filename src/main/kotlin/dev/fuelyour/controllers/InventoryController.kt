package dev.fuelyour.controllers

import dev.fuelyour.annotations.Body
import dev.fuelyour.repositories.InventoryRepo
import dev.fuelyour.tools.DatabaseAccess
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.shareddata.impl.ClusterSerializable

class InventoryController(
  private val da: DatabaseAccess,
  private val inventoryRepo: InventoryRepo
) {

  suspend fun get(id: String?): ClusterSerializable {
    return if (id != null)
      da.getConnection { conn -> inventoryRepo.find(id, conn) }
    else
      da.getConnection { conn -> inventoryRepo.all(conn) }
  }

  suspend fun post(@Body body: JsonObject): JsonObject {
    return da.getConnection { conn -> inventoryRepo.insert(body, conn) }
  }

  suspend fun patch(id: String, @Body body: JsonObject): JsonObject {
    return da.getTransaction { conn ->
      val fromDb = inventoryRepo.find(id, conn)
      fromDb.mergeIn(body)
      body.forEach { (key, value) -> if (value == null) fromDb.remove(key) }
      inventoryRepo.update(id, fromDb, conn)
    }
  }

  suspend fun delete(id: String) {
    da.getConnection { conn -> inventoryRepo.delete(id, conn) }
  }
}