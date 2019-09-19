package dev.fuelyour.repositories

import io.reactivex.Observable
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.reactivex.pgclient.PgPool
import io.vertx.reactivex.sqlclient.Row
import io.vertx.reactivex.sqlclient.Tuple


abstract class Repository(val pool: PgPool, val table: String) {

  fun all() = pool.rxBegin().flatMap { connection ->
    connection.rxPrepare("select * from $table")
      .flatMapObservable { preparedQuery ->
        val rowStream = preparedQuery.createStream(50, Tuple.tuple())
        rowStream.toObservable();
      }.map {
        jsonRow(it)
      }.toJsonArray()
  }

  fun find(id: String) = pool.rxPreparedQuery("select * from $table where id = $1", Tuple.of(id)).flatMapObservable {
    Observable.fromIterable(it.asIterable())
  }.map {
    jsonRow(it)
  }.singleOrError()

  fun findBy(query: String, params: Tuple) = pool.rxPreparedQuery(query, params).flatMapObservable {
    Observable.fromIterable(it.asIterable())
  }.map {
    jsonRow(it)
  }.singleOrError()

  fun delete(id: String) = pool.rxPreparedQuery("delete from $table where id = $1", Tuple.of(id)).flatMapObservable {
    Observable.fromIterable(it.asIterable())
  }.map {
    jsonRow(it)
  }.singleOrError()

  fun where(query: String, params: Tuple) = pool.rxBegin().flatMap { connection ->
    connection.rxPrepare(query)
      .flatMapObservable { preparedQuery ->
        val rowStream = preparedQuery.createStream(50, params)
        rowStream.toObservable();
      }.map {
        jsonRow(it)
      }.toJsonArray()
  }

  private fun jsonRow(row: Row): JsonObject {
    return buildOutJsonRow(row, json { obj() }, 0)
  }

  private fun buildOutJsonRow(dbRow: Row, currentJson: JsonObject, currentColumn: Int): JsonObject {
    return when (dbRow.size()) {
      currentColumn -> currentJson
      else -> {
        val columnName = dbRow.getColumnName(currentColumn)
        val dbRowValue = dbRow.getValue(columnName)
        val newJsonRow: JsonObject = if (columnName == "data") {
          val dataJsonString = dbRow.getString("data")
          val json = JsonObject(dataJsonString)
          json.copy().map.putAll(currentJson.map)
          json
        } else {
          currentJson.put(columnName, dbRowValue)
        }
        buildOutJsonRow(dbRow, newJsonRow, currentColumn + 1)
      }
    }
  }

  private fun <T> Observable<T>.toJsonArray() = this.toList().map { JsonArray(it) }
}