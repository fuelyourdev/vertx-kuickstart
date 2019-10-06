package dev.fuelyour.repositories

import dev.fuelyour.exceptions.ModelNotFoundException
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.sqlclient.preparedQueryAwait
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.SqlClient
import io.vertx.sqlclient.Tuple
import kotlin.reflect.KClass

import dev.fuelyour.tools.Deserializer
import dev.fuelyour.tools.DeserializerImpl
import dev.fuelyour.tools.Serializer
import dev.fuelyour.tools.SerializerImpl

interface AllQuery<T: Any> {
  companion object {
    inline fun <reified T: Any> impl(
      schema: String, table: String
    ): AllQuery<T> =
      AllQueryImpl(schema, table, T::class, DeserializerImpl())
  }
  suspend fun all(connection: SqlClient): List<T>
}

class AllQueryImpl<T: Any>(
  schema: String,
  table: String,
  private val kclass: KClass<T>,
  deserializer: Deserializer
): AllQuery<T>, Deserializer by deserializer {

  private val tableName = "$schema.$table"

  override suspend fun all(connection: SqlClient): List<T> {
    return connection
      .preparedQueryAwait("select * from $tableName")
      .map { row ->
        val json = row.getValue("data")
          .let { it as JsonObject }
          .put("id", row.getString("id"))
        kclass.instantiate(json)
      }
  }
}

interface FindQuery<T: Any> {
  companion object {
    inline fun <reified T: Any> impl(
      schema: String, table: String
    ): FindQuery<T> =
      FindQueryImpl(schema, table, T::class, DeserializerImpl())
  }
  suspend fun find(id: String, connection: SqlClient): T
}

class FindQueryImpl<T: Any>(
  schema: String,
  table: String,
  private val kclass: KClass<T>,
  deserializer: Deserializer
): FindQuery<T>, Deserializer by deserializer {

  private val tableName = "$schema.$table"

  override suspend fun find(id: String, connection: SqlClient): T {
    return connection
      .preparedQueryAwait(
        "select * from $tableName where id = $1",
        Tuple.of(id)
      )
      .map { row ->
        val json = row.getValue("data")
          .let { it as JsonObject }
          .put("id", row.getString("id"))
        kclass.instantiate(json)
      }.firstOrNull()
      ?: throw ModelNotFoundException("No object found with ID", JsonArray(id))
  }
}

interface InsertQuery<T: Any, R: Any> {
  companion object {
    inline fun <T: Any, reified R: Any> impl(
      schema: String, table: String
    ): InsertQuery<T, R> =
      InsertQueryImpl(schema, table, R::class, SerializerImpl(), DeserializerImpl())
  }
  suspend fun insert(toInsert: T, connection: SqlClient): R
}

class InsertQueryImpl<T: Any, R: Any>(
  schema: String,
  table: String,
  private val kclass: KClass<R>,
  serializer: Serializer,
  deserializer: Deserializer
): InsertQuery<T, R>, Serializer by serializer, Deserializer by deserializer {

  private val tableName = "$schema.$table"

  override suspend fun insert(toInsert: T, connection: SqlClient): R {
    val query = "insert into $tableName (data) values ($1::jsonb) returning *"
    val data = with (toInsert.serialize()) { remove("id") }
    return connection.preparedQueryAwait(query, Tuple.of(data))
      .map { row ->
        val json = row.getValue("data")
          .let { it as JsonObject }
          .put("id", row.getString("id"))
        kclass.instantiate(json)
      }.first()
  }
}

interface UpdateQuery<T: Any, R: Any> {
  companion object {
    inline fun <T: Any, reified R: Any> impl(
      schema: String, table: String
    ): UpdateQuery<T, R> =
      UpdateQueryImpl(schema, table, R::class, SerializerImpl(), DeserializerImpl())
  }
  suspend fun update(id: String, toUpdate: T, connection: SqlClient): R
}

class UpdateQueryImpl<T: Any, R: Any>(
  schema: String,
  table: String,
  private val kclass: KClass<R>,
  serializer: Serializer,
  deserializer: Deserializer
): UpdateQuery<T, R>, Serializer by serializer, Deserializer by deserializer {

  private val tableName = "$schema.$table"

  override suspend fun update(
    id: String,
    toUpdate: T,
    connection: SqlClient
  ): R {
    val query = "update $tableName set data = $1 where id = $2 returning *"
    val data = with (toUpdate.serialize()) { remove("id") }
    return connection.preparedQueryAwait(query, Tuple.of(data, id))
      .map { row ->
        val json = row.getValue("data")
          .let { it as JsonObject }
          .put("id", row.getString("id"))
        kclass.instantiate(json)
      }.first()
  }
}

interface DeleteQuery {
  companion object {
    fun impl(schema: String, table: String): DeleteQuery =
      DeleteQueryImpl(schema, table)
  }
  suspend fun delete(id: String, connection: SqlClient): String
}

class DeleteQueryImpl(schema: String, table: String): DeleteQuery {

  private val tableName = "$schema.$table"

  override suspend fun delete(id: String, connection: SqlClient): String {
    val query = "delete from $tableName where id = $1 returning id"
    return connection.preparedQueryAwait(query, Tuple.of(id))
      .map { row -> row.getString("id") }
      .firstOrNull()
      ?: throw ModelNotFoundException(
        "Tried to delete an item that does not exist",
        JsonArray(id)
      )
  }
}

