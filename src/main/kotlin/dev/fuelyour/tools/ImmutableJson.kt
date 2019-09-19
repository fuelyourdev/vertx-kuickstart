package dev.fuelyour.tools

import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.shareddata.Shareable
import io.vertx.core.shareddata.impl.ClusterSerializable
import java.time.Instant
import java.util.stream.Stream

fun immutable(obj: JsonObject): ImmutableJsonObject =
  ImmutableJsonObject(obj)

fun immutable(arr: JsonArray): ImmutableNullableJsonArray =
  ImmutableNullableJsonArray(arr)

fun JsonObject.toImmutable(): ImmutableJsonObject =
  immutable(this)

fun JsonArray.toImmutable(): ImmutableNullableJsonArray =
  immutable(this)

sealed class ImmutableJson<T> :
  Iterable<T>,
  ClusterSerializable,
  Shareable {
  abstract fun encode(): String
  abstract fun encodePrettily(): String
  abstract val isEmpty: Boolean
  abstract fun size(): Int
  abstract fun toBuffer(): Buffer
  abstract fun stream(): Stream<T>

  protected fun <T> makeImmutable(obj: T): Any? =
    when (obj) {
      is JsonObject -> ImmutableJsonObject(obj, false)
      is JsonArray -> ImmutableNullableJsonArray(obj, false)
      else -> obj
    }
}

class ImmutableJsonObject : ImmutableJson<Pair<String, Any?>> {

  companion object {
    fun mapFrom(obj: Any): ImmutableJsonObject =
      ImmutableJsonObject(JsonObject.mapFrom(obj), false)
  }

  private val obj: JsonObject

  constructor(obj: JsonObject) : super() {
    this.obj = obj.copy()
  }

  internal constructor(obj: JsonObject, copy: Boolean) : super() {
    this.obj = if (copy) obj.copy() else obj
  }

  fun containsKey(key: String): Boolean =
    obj.containsKey(key)

  override fun copy(): ImmutableJsonObject =
    immutable(obj)

  fun copy(copyFunc: JsonObject.() -> Unit): ImmutableJsonObject =
    immutable(obj.copy().apply(copyFunc))

  override fun encode(): String =
    obj.encode()

  override fun encodePrettily(): String =
    obj.encodePrettily()

  override fun equals(other: Any?): Boolean =
    when (other) {
      is ImmutableJsonObject -> obj.equals(other.obj)
      else -> obj.equals(other)
    }

  fun fieldNames(): Set<String> =
    obj.fieldNames()

  fun getBinary(key: String): ByteArray? =
    obj.getBinary(key)

  fun getBinary(key: String, def: ByteArray?): ByteArray? =
    obj.getBinary(key, def)

  fun getBoolean(key: String): Boolean? =
    obj.getBoolean(key)

  fun getBoolean(key: String, def: Boolean?): Boolean? =
    obj.getBoolean(key, def)

  fun getDouble(key: String): Double? =
    obj.getDouble(key)

  fun getDouble(key: String, def: Double?): Double? =
    obj.getDouble(key, def)

  fun getFloat(key: String): Float? =
    obj.getFloat(key)

  fun getFloat(key: String, def: Float?): Float? =
    obj.getFloat(key, def)

  fun getInstant(key: String): Instant? =
    obj.getInstant(key)

  fun getInstant(key: String, def: Instant?): Instant? =
    obj.getInstant(key, def)

  fun getInteger(key: String): Int? =
    obj.getInteger(key)

  fun getInteger(key: String, def: Int?): Int? =
    obj.getInteger(key, def)

  fun getJsonArray(key: String): ImmutableNullableJsonArray? =
    obj.getJsonArray(key)?.let {
      ImmutableNullableJsonArray(it, false)
    }

  fun getJsonArray(key: String, def: JsonArray?): ImmutableNullableJsonArray? =
    obj.getJsonArray(key, def)?.let {
      ImmutableNullableJsonArray(it, false)
    }

  fun getJsonObject(key: String): ImmutableJsonObject? =
    obj.getJsonObject(key)?.let {
      ImmutableJsonObject(it, false)
    }

  fun getJsonObject(key: String, def: JsonObject?): ImmutableJsonObject? =
    obj.getJsonObject(key, def)?.let {
      ImmutableJsonObject(it, false)
    }

  fun getLong(key: String): Long? =
    obj.getLong(key)

  fun getLong(key: String, def: Long?): Long? =
    obj.getLong(key, def)

  val map: Map<String, Any?>
    get() = obj.map

  fun getString(key: String): String? =
    obj.getString(key)

  fun getString(key: String, def: String?): String? =
    obj.getString(key, def)

  fun getValue(key: String): Any? =
    makeImmutable(obj.getValue(key))

  fun getValue(key: String, def: Any?): Any? =
    makeImmutable(obj.getValue(key, def))

  override fun hashCode(): Int =
    obj.hashCode()

  override val isEmpty: Boolean
    get() = obj.isEmpty

  override fun iterator(): Iterator<Pair<String, Any?>> {
    val objIterator = obj.iterator()
    return object : Iterator<Pair<String, Any?>> {
      override fun hasNext(): Boolean =
        objIterator.hasNext()

      override fun next(): Pair<String, Any?> =
        with(objIterator.next()) {
          key to makeImmutable(value)
        }
    }
  }

  fun <T : Any> mapTo(type: Class<T>): T =
    obj.mapTo(type)

  inline fun <reified T : Any> mapTo(): T =
    mapTo(T::class.java)

  override fun readFromBuffer(pos: Int, buffer: Buffer): Int =
    obj.readFromBuffer(pos, buffer)

  override fun size(): Int =
    obj.size()

  override fun stream(): Stream<Pair<String, Any?>> =
    obj.stream()
      .map { (key, value) -> key to makeImmutable(value) }

  override fun toBuffer(): Buffer =
    obj.toBuffer()

  override fun toString(): String =
    obj.toString()

  override fun writeToBuffer(buffer: Buffer): Unit =
    obj.writeToBuffer(buffer)

  fun toJsonObject(): JsonObject =
    obj.copy()

  operator fun plus(other: JsonObject): ImmutableJsonObject =
    with(obj.copy()) {
      other.forEach { (key, value) -> put(key, value) }
      ImmutableJsonObject(this, false)
    }

  operator fun plus(pair: Pair<String, *>): ImmutableJsonObject =
    with(obj.copy()) {
      put(pair.first, pair.second)
      ImmutableJsonObject(this, false)
    }

  operator fun plus(other: ImmutableJsonObject): ImmutableJsonObject =
    with(obj.copy()) {
      other.obj.forEach { (key, value) -> put(key, value) }
      ImmutableJsonObject(this, false)
    }

  operator fun minus(key: String): ImmutableJsonObject =
    with(obj.copy()) {
      remove(key)
      ImmutableJsonObject(this, false)
    }

  operator fun minus(keys: Collection<String>): ImmutableJsonObject =
    with(obj.copy()) {
      keys.forEach { remove(it) }
      ImmutableJsonObject(this, false)
    }
}

sealed class ImmutableJsonArray<T> : ImmutableJson<T> {

  companion object {}

  internal val arr: JsonArray

  constructor(arr: JsonArray, copy: Boolean) : super() {
    this.arr = if (copy) arr.copy() else arr
  }

  fun contains(value: Any?): Boolean =
    arr.contains(value)

  override fun encode(): String =
    arr.encode()

  override fun encodePrettily(): String =
    arr.encodePrettily()

  override fun equals(other: Any?): Boolean =
    when (other) {
      is ImmutableJsonArray<*> -> arr.equals(other.arr)
      else -> arr.equals(other)
    }

  val list: List<Any?>
    get() = arr.list

  override fun hashCode(): Int =
    arr.hashCode()

  fun hasNull(pos: Int): Boolean =
    arr.hasNull(pos)

  override val isEmpty: Boolean
    get() = arr.isEmpty

  override fun readFromBuffer(pos: Int, buffer: Buffer): Int =
    arr.readFromBuffer(pos, buffer)

  override fun size(): Int =
    arr.size()

  override fun toBuffer(): Buffer =
    arr.toBuffer()

  override fun toString(): String =
    arr.toString()

  override fun writeToBuffer(buffer: Buffer): Unit =
    arr.writeToBuffer(buffer)

  fun toJsonArray(): JsonArray =
    arr.copy()

  operator fun plus(other: JsonArray): ImmutableNullableJsonArray =
    with(arr.copy()) {
      addAll(other)
      ImmutableNullableJsonArray(this, false)
    }

  operator fun plus(item: Any?): ImmutableNullableJsonArray =
    with(arr.copy()) {
      add(item)
      ImmutableNullableJsonArray(this, false)
    }

  operator fun plus(other: ImmutableJsonArray<*>): ImmutableNullableJsonArray =
    with(arr.copy()) {
      addAll(other.arr)
      ImmutableNullableJsonArray(this, false)
    }

  abstract operator fun minus(other: JsonArray): ImmutableJsonArray<T>
  abstract operator fun minus(item: Any?): ImmutableJsonArray<T>
  abstract operator fun minus(index: Int): ImmutableJsonArray<T>
  abstract operator fun minus(other: ImmutableJsonArray<*>): ImmutableJsonArray<T>
}

class ImmutableNullableJsonArray : ImmutableJsonArray<Any?> {

  companion object {}

  constructor(arr: JsonArray) : super(arr, true)

  internal constructor(arr: JsonArray, copy: Boolean) : super(arr, copy)

  override fun copy(): ImmutableNullableJsonArray =
    immutable(arr)

  fun copy(copyFunc: JsonArray.() -> Unit): ImmutableNullableJsonArray =
    immutable(arr.copy().apply(copyFunc))

  fun getBinary(pos: Int): ByteArray? =
    arr.getBinary(pos)

  fun getBoolean(pos: Int): Boolean? =
    arr.getBoolean(pos)

  fun getDouble(pos: Int): Double? =
    arr.getDouble(pos)

  fun getFloat(pos: Int): Float? =
    arr.getFloat(pos)

  fun getInstant(pos: Int): Instant? =
    arr.getInstant(pos)

  fun getInteger(pos: Int): Int? =
    arr.getInteger(pos)

  fun getJsonArray(pos: Int): ImmutableNullableJsonArray? =
    arr.getJsonArray(pos)?.let {
      ImmutableNullableJsonArray(it, false)
    }

  fun getJsonObject(pos: Int): ImmutableJsonObject? =
    arr.getJsonObject(pos)?.let {
      ImmutableJsonObject(it, false)
    }

  fun getLong(pos: Int): Long? =
    arr.getLong(pos)

  fun getString(pos: Int): String? =
    arr.getString(pos)

  fun getValue(pos: Int): Any? =
    makeImmutable(arr.getValue(pos))

  override fun iterator(): Iterator<Any?> {
    val arrIterator = arr.iterator()
    return object : Iterator<Any?> {
      override fun hasNext(): Boolean =
        arrIterator.hasNext()

      override fun next(): Any? =
        with(arrIterator.next()) {
          makeImmutable(this)
        }
    }
  }

  override fun stream(): Stream<Any?> =
    arr.stream().map { makeImmutable(it) }

  override operator fun minus(other: JsonArray): ImmutableNullableJsonArray =
    with(arr.copy()) {
      other.forEach { remove(it) }
      ImmutableNullableJsonArray(this, false)
    }

  override operator fun minus(item: Any?): ImmutableNullableJsonArray =
    with(arr.copy()) {
      remove(item)
      ImmutableNullableJsonArray(this, false)
    }

  override operator fun minus(index: Int): ImmutableNullableJsonArray =
    with(arr.copy()) {
      remove(index)
      ImmutableNullableJsonArray(this, false)
    }

  override operator fun minus(other: ImmutableJsonArray<*>): ImmutableNullableJsonArray =
    with(arr.copy()) {
      other.arr.forEach { remove(it) }
      ImmutableNullableJsonArray(this, false)
    }

  fun noNulls(): ImmutableNoNullsJsonArray =
    ImmutableNoNullsJsonArray(arr, false)

  fun filterNulls(): ImmutableNoNullsJsonArray =
    ImmutableNoNullsJsonArray(arr, true)
}

class ImmutableNoNullsJsonArray : ImmutableJsonArray<Any> {

  companion object {}

  internal constructor(arr: JsonArray, filterNulls: Boolean) :
      super(
        if (filterNulls)
          JsonArray(arr.copy().filterNotNull())
        else if (!arr.contains(null))
          arr
        else
          throw Exception(),
        false
      )

  private constructor(arr: JsonArray) : super(arr, false)

  override fun copy(): ImmutableNoNullsJsonArray =
    ImmutableNoNullsJsonArray(arr.copy(), true)

  fun copy(copyFunc: JsonArray.() -> Unit): ImmutableNullableJsonArray =
    ImmutableNullableJsonArray(arr.copy().apply(copyFunc), true)

  fun getBinary(pos: Int): ByteArray =
    arr.getBinary(pos)

  fun getBoolean(pos: Int): Boolean =
    arr.getBoolean(pos)

  fun getDouble(pos: Int): Double =
    arr.getDouble(pos)

  fun getFloat(pos: Int): Float =
    arr.getFloat(pos)

  fun getInstant(pos: Int): Instant =
    arr.getInstant(pos)

  fun getInteger(pos: Int): Int =
    arr.getInteger(pos)

  fun getJsonArray(pos: Int): ImmutableNullableJsonArray =
    ImmutableNullableJsonArray(arr.getJsonArray(pos), false)

  fun getJsonObject(pos: Int): ImmutableJsonObject =
    ImmutableJsonObject(arr.getJsonObject(pos), false)

  fun getLong(pos: Int): Long =
    arr.getLong(pos)

  fun getString(pos: Int): String =
    arr.getString(pos)

  fun getValue(pos: Int): Any =
    makeImmutable(arr.getValue(pos)) as Any

  override fun iterator(): Iterator<Any> {
    val arrIterator = arr.iterator()
    return object : Iterator<Any> {
      override fun hasNext(): Boolean =
        arrIterator.hasNext()

      override fun next(): Any =
        with(arrIterator.next()) {
          makeImmutable(this) as Any
        }
    }
  }

  override fun stream(): Stream<Any> =
    arr.stream().map { makeImmutable(it) as Any }

  operator fun plus(other: ImmutableNoNullsJsonArray): ImmutableNoNullsJsonArray =
    with(arr.copy()) {
      addAll(other.arr)
      ImmutableNoNullsJsonArray(this)
    }

  override operator fun minus(other: JsonArray): ImmutableNoNullsJsonArray =
    with(arr.copy()) {
      other.forEach { remove(it) }
      ImmutableNoNullsJsonArray(this)
    }

  override operator fun minus(item: Any?): ImmutableNoNullsJsonArray =
    with(arr.copy()) {
      remove(item)
      ImmutableNoNullsJsonArray(this)
    }

  override operator fun minus(index: Int): ImmutableNoNullsJsonArray =
    with(arr.copy()) {
      remove(index)
      ImmutableNoNullsJsonArray(this)
    }

  override operator fun minus(other: ImmutableJsonArray<*>): ImmutableNoNullsJsonArray =
    with(arr.copy()) {
      other.arr.forEach { remove(it) }
      ImmutableNoNullsJsonArray(this)
    }
}
