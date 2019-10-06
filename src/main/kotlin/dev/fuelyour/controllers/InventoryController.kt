package dev.fuelyour.controllers

import dev.fuelyour.repositories.InventoryRepo
import dev.fuelyour.tools.DatabaseAccess
import dev.fuelyour.tools.Field
import io.vertx.core.json.JsonObject
import io.vertx.core.shareddata.impl.ClusterSerializable

data class ManufacturerPost(
  val name: String,
  val homePage: String? = null,
  val phone: String? = null
)

data class InventoryPost(
  val id: String,
  val name: String,
  val releaseDate: String,
  val manufacturer: ManufacturerPost,
  val count: Int
)

data class ManufacturerPatch(
  val name: Field<String?>,
  val homePage: Field<String?>,
  val phone: Field<String?>
)

data class InventoryPatch(
  val id: Field<String?>,
  val name: Field<String?>,
  val releaseDate: Field<String?>,
  val manufacturer: Field<ManufacturerPatch?>,
  val count: Field<Int?>
)

data class Manufacturer(
  val id: String,
  val name: String,
  val homePage: String? = null,
  val phone: String? = null
)

data class Inventory(
  val id: String,
  val name: String,
  val releaseDate: String,
  val manufacturer: Manufacturer,
  val count: Int
)

class InventoryController(
  private val da: DatabaseAccess,
  private val inventoryRepo: InventoryRepo
) {

  suspend fun get(id: String): Inventory {
    return da.getConnection { conn -> inventoryRepo.find(id, conn) }
  }

  suspend fun getAll(): List<Inventory> {
    return da.getConnection { conn -> inventoryRepo.all(conn) }
  }

  suspend fun post(body: InventoryPost): Inventory {
    return da.getConnection { conn -> inventoryRepo.insert(body, conn) }
  }

  suspend fun patch(id: String, body: InventoryPatch): Inventory {
    return da.getTransaction { conn ->
      val fromDb = inventoryRepo.find(id, conn)
      //fromDb.mergeIn(body)
      //body.forEach { (key, value) -> if (value == null) fromDb.remove(key) }
      inventoryRepo.update(id, fromDb, conn)
    }
  }

  suspend fun delete(id: String) {
    da.getConnection { conn -> inventoryRepo.delete(id, conn) }
  }
}

fun Any.applyPatch(patch: Any) {

}