package dev.fuelyour.tools

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import java.time.Instant
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.jvm.javaConstructor
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.jvmErasure

interface Deserializer {
  fun KFunction<*>.getTypeForParam(param: KParameter): Type?
  fun <T: Any> KClass<T>.instantiate(json: JsonObject): T
  fun Type?.instantiateList(arr: JsonArray): List<Any?>
  fun Type?.instantiateMap(obj: JsonObject): Map<String, Any?>
}

class DeserializerImpl: Deserializer {

  override fun KFunction<*>.getTypeForParam(
    param: KParameter
  ): Type? {
    val index = param.index
    return javaConstructor?.let {
      it.genericParameterTypes[index]
    } ?: javaMethod?.let {
      it.genericParameterTypes[index-1]
    }
  }

  override fun <T: Any> KClass<T>.instantiate(json: JsonObject): T {
    val ctor = constructors.first()
    val params = ctor.parameters.map { param ->
      param.name?.let { name ->
        when (val kclass = param.type.jvmErasure) {
          ByteArray::class -> json.getBinary(name)
          Boolean::class -> json.getBoolean(name)
          Double::class -> json.getDouble(name)
          Float::class -> json.getFloat(name)
          Instant::class -> json.getInstant(name)
          Int::class -> json.getInteger(name)
          Long::class -> json.getLong(name)
          String::class -> json.getString(name)
          Field::class -> ctor.getTypeForParam(param)
            .instantiateField(json, name)
          List::class -> ctor.getTypeForParam(param)
            .instantiateList(json.getJsonArray(name))
          Map::class -> ctor.getTypeForParam(param)
            .instantiateMap(json.getJsonObject(name))
          else -> kclass.instantiate(json.getJsonObject(name))
        }
      }
    }
    return ctor.call(*params.toTypedArray())
  }

  override fun Type?.instantiateList(arr: JsonArray): List<Any?> {
    val range = 0 until arr.size()
    return this?.let { type ->
      val genericType = type.getGenericType(0)
      when (val itemsKClass = genericType.kClass) {
        ByteArray::class -> range.map { arr.getBinary(it) }
        Boolean::class -> range.map { arr.getBoolean(it) }
        Double::class -> range.map { arr.getDouble(it) }
        Float::class -> range.map { arr.getFloat(it) }
        Instant::class -> range.map { arr.getInstant(it) }
        Int::class -> range.map { arr.getInteger(it) }
        Long::class -> range.map { arr.getLong(it) }
        String::class -> range.map { arr.getString(it) }
        Field::class -> range.map { genericType.instantiateField(arr, it) }
        List::class -> range.map {
          genericType.instantiateList(arr.getJsonArray(it))
        }
        Map::class -> range.map {
          genericType.instantiateMap(arr.getJsonObject(it))
        }
        else -> range.map { itemsKClass.instantiate(arr.getJsonObject(it)) }
      }
    } ?: range.map { arr.getValue(it) }
  }

  override fun Type?.instantiateMap(obj: JsonObject): Map<String, Any?> {
    return this?.let { type ->
      val genericType = type.getGenericType(1)
      val itemsKClass = genericType.kClass
      val map = mutableMapOf<String, Any?>()
      obj.forEach { (key, _) ->
        when (itemsKClass) {
          ByteArray::class -> map[key] = obj.getBinary(key)
          Boolean::class -> map[key] = obj.getBoolean(key)
          Double::class -> map[key] = obj.getDouble(key)
          Float::class -> map[key] = obj.getFloat(key)
          Instant::class -> map[key] = obj.getInstant(key)
          Int::class -> map[key] = obj.getInteger(key)
          Long::class -> map[key] = obj.getLong(key)
          String::class -> map[key] = obj.getString(key)
          Field::class -> map[key] =
            genericType.instantiateField(obj, key)
          List::class -> map[key] =
            genericType.instantiateList(obj.getJsonArray(key))
          Map::class -> map[key] =
            genericType.instantiateMap(obj.getJsonObject(key))
          else -> map[key] = itemsKClass.instantiate(obj.getJsonObject(key))
        }
      }
      map
    } ?: obj.map
  }

  private fun Type?.instantiateField(
    arr: JsonArray,
    pos: Int
  ): Field<out Any?> {
    return this?.let { type ->
      val genericType = type.getGenericType(0)
      when (val itemsKClass = genericType.kClass) {
        ByteArray::class -> Field(arr.getBinary(pos), true)
        Boolean::class -> Field(arr.getBoolean(pos), true)
        Double::class -> Field(arr.getDouble(pos), true)
        Float::class -> Field(arr.getFloat(pos), true)
        Instant::class -> Field(arr.getInstant(pos), true)
        Int::class -> Field(arr.getInteger(pos), true)
        Long::class -> Field(arr.getLong(pos), true)
        String::class -> Field(arr.getString(pos), true)
        Field::class -> throw Exception("Field of Field type not allowed")
        List::class -> Field(
          genericType.instantiateList(arr.getJsonArray(pos)),
          true
        )
        Map::class -> Field(
          genericType.instantiateMap(arr.getJsonObject(pos)),
          true
        )
        else -> Field(itemsKClass.instantiate(arr.getJsonObject(pos)), true)
      }
    } ?: Field(arr.getValue(pos), true)
  }

  private fun Type?.instantiateField(
    json: JsonObject,
    key: String
  ): Field<out Any?> {
    return this?.let { type ->
      val genericType = type.getGenericType(0)
      when (val itemsKClass = genericType.kClass) {
        ByteArray::class -> Field(json.getBinary(key), json.containsKey(key))
        Boolean::class -> Field(json.getBoolean(key), json.containsKey(key))
        Double::class -> Field(json.getDouble(key), json.containsKey(key))
        Float::class -> Field(json.getFloat(key), json.containsKey(key))
        Instant::class -> Field(json.getInstant(key), json.containsKey(key))
        Int::class -> Field(json.getInteger(key), json.containsKey(key))
        Long::class -> Field(json.getLong(key), json.containsKey(key))
        String::class -> Field(json.getString(key), json.containsKey(key))
        Field::class -> throw Exception("Field of Field type not allowed")
        List::class -> Field(
          genericType.instantiateList(json.getJsonArray(key)),
          json.containsKey(key)
        )
        Map::class -> Field(
          genericType.instantiateMap(json.getJsonObject(key)),
          json.containsKey(key)
        )
        else -> Field(
          itemsKClass.instantiate(json.getJsonObject(key)),
          json.containsKey(key)
        )
      }
    } ?: Field(json.getValue(key), json.containsKey(key))
  }

  private fun Type.getGenericType(index: Int): Type =
    let {
      val parameterizedType = it as ParameterizedType
      when (val typeArg = parameterizedType.actualTypeArguments[index]) {
        is WildcardType -> typeArg.upperBounds[0]
        else -> typeArg
      }
    }

  private val Type.kClass: KClass<*>
    get() = when (this) {
      is ParameterizedType -> rawType.typeName
      else -> typeName
    }.let { Class.forName(it).kotlin }
}

inline fun <reified T> type(): Type {
  return object: TypeWrapper<T>() {}::class.java
    .let { it.genericSuperclass as ParameterizedType }
    .actualTypeArguments[0]
}

open class TypeWrapper<T>
