package dev.fuelyour.controllers

import dev.fuelyour.repositories.InventoryRepo
import dev.fuelyour.tools.DatabaseAccess
import dev.fuelyour.tools.Field
import dev.fuelyour.tools.applyPatch

data class ManufacturerPost(
  val name: String,
  val homePage: String?,
  val phone: String?
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
  val homePage: String?,
  val phone: String?
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

  suspend fun post(inventory: InventoryPost): Inventory {
    return da.getConnection { conn -> inventoryRepo.insert(inventory, conn) }
  }

  suspend fun patch(id: String, patch: InventoryPatch): Inventory {
    return da.getTransaction { conn ->
      val fromDb = inventoryRepo.find(id, conn)
      val result = fromDb.applyPatch(patch)
      inventoryRepo.update(id, result, conn)
    }
  }

  suspend fun delete(id: String) {
    da.getConnection { conn -> inventoryRepo.delete(id, conn) }
  }
}
