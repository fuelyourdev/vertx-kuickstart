package dev.fuelyour.tools

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject

operator fun JsonObject.plus(other: JsonObject): JsonObject =
  copy().apply {
    other.forEach { (key, value) -> put(key, value) }
  }

operator fun JsonObject.plus(pair: Pair<String, *>): JsonObject =
  copy().put(pair.first, pair.second)

operator fun JsonObject.plus(other: ImmutableJsonObject): JsonObject =
  copy().apply {
    other.toJsonObject().forEach { (key, value) -> put(key, value) }
  }

operator fun JsonObject.minus(key: String): JsonObject =
  copy().apply { remove(key) }

operator fun JsonObject.minus(keys: Collection<String>): JsonObject =
  copy().apply {
    keys.forEach { remove(it) }
  }

operator fun JsonArray.plus(other: JsonArray): JsonArray =
  copy().addAll(other)

operator fun JsonArray.plus(item: Any?): JsonArray =
  copy().add(item)

operator fun JsonArray.plus(other: ImmutableJsonArray<*>): JsonArray =
  copy().addAll(other.toJsonArray())

operator fun JsonArray.minus(other: JsonArray): JsonArray =
  copy().apply {
    other.forEach { remove(it) }
  }

operator fun JsonArray.minus(item: Any?): JsonArray =
  copy().apply { remove(item) }

operator fun JsonArray.minus(index: Int): JsonArray =
  copy().apply { remove(index) }

operator fun JsonArray.minus(other: ImmutableJsonArray<*>): JsonArray =
  copy().apply {
    other.toJsonArray().forEach { remove(it) }
  }

