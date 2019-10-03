package dev.fuelyour.controllers

import com.google.common.reflect.TypeToken
import dev.fuelyour.annotations.Body
import dev.fuelyour.repositories.InventoryRepo
import dev.fuelyour.tools.DatabaseAccess
import io.vertx.core.json.JsonObject
import io.vertx.core.shareddata.impl.ClusterSerializable
import io.vertx.kotlin.core.json.jsonArrayOf
import io.vertx.kotlin.core.json.jsonObjectOf
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

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

  suspend fun post(body: InventoryPost): InventoryPost {
    val mytype = type<List<Map<String, InventoryPost>>>()
    return body//da.getConnection { conn -> inventoryRepo.insert(body, conn) }
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

private inline fun <reified T> type(): Type {
  return object: TypeWrapper<T>() {}::class.java
    .let { it.genericSuperclass as ParameterizedType }
    .actualTypeArguments[0]
}

open class TypeWrapper<T>
