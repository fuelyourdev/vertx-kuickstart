package dev.fuelyour.tools

import dev.fuelyour.annotations.Timeout
import dev.fuelyour.exceptions.HTTPStatusCode
import dev.fuelyour.exceptions.ResponseCodeException
import dev.fuelyour.exceptions.TimeoutException
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.parameters.Parameter
import io.vertx.core.Handler
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.shareddata.impl.ClusterSerializable
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.core.json.jsonObjectOf
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.koin.core.KoinComponent
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import kotlin.Exception
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.jvm.jvmErasure
import java.time.Instant
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.javaConstructor
import kotlin.reflect.jvm.javaMethod

class ControllerSupplier(
  private val controllerPackage: String
) : KoinComponent {
  fun getControllerInstance(controllerName: String): Any {
    val kclass = Class.forName("$controllerPackage.$controllerName").kotlin
    return getKoin().get(kclass, null, null)
  }
}

class SwaggerServiceHandler(
  private val controllerSupplier: ControllerSupplier
) {
  fun createServiceHandlers(op: Operation, opId: String): RouteHandlers {
    val (controllerName, methodName) = opId.split('.')
    val controller = controllerSupplier.getControllerInstance(controllerName)
    val function = controller::class.functions.find { it.name == methodName }
      ?: throw Exception("Unable to parse operation $opId")

    return listOf(
      object : Handler<RoutingContext> {
        override fun handle(context: RoutingContext) {
          GlobalScope.launch {
            try {
              val timeout = function.findAnnotation<Timeout>()?.length ?: 30000
              withTimeout(timeout) {
                function.callWithParams(controller, context, op.parameters)
              }
            } catch (ex: TimeoutCancellationException) {
              replyWithError(context, TimeoutException(
                "Timed out waiting for response",
                JsonArray(opId),
                ex
              ))
            } catch (ex: Exception) {
              replyWithError(context, ex)
            }
          }
        }
      }
    )
  }

  private suspend fun KFunction<*>.callWithParams(
    instance: Any?,
    context: RoutingContext,
    swaggerParams: List<Parameter>?
  ) {
    try {
      val params = buildParams(instance, context, swaggerParams)
      val response = callSuspendBy(params)
      handleResponse(context, response)
    } catch (e: InvocationTargetException) {
      val ex = e.targetException
      ex.printStackTrace()
      throw ex
    } catch (e: Exception) {
      e.printStackTrace()
      throw e
    }
  }

  private fun KFunction<*>.buildParams(
    instance: Any?,
    context: RoutingContext,
    swaggerParams: List<Parameter>?
  ): Map<KParameter, Any?> {
    val params: MutableMap<KParameter, Any?> = mutableMapOf()
    parameters.forEach { param ->
      params[param] = when {
        param.kind == KParameter.Kind.INSTANCE -> instance
        param.isSubclassOf(RoutingContext::class) -> context
        else -> buildPathOrQueryParam(swaggerParams, param, context)
          ?: buildBodyParam(this, param, context)
      }
    }
    return params
  }

  private fun buildPathOrQueryParam(
    swaggerParams: List<Parameter>?,
    param: KParameter,
    context: RoutingContext
  ): Any? {
    return swaggerParams?.find { it.name == param.name }?.let { sp ->
      when (sp.`in`) {
        "path" -> parseParam(param, context.pathParam(param.name))
        "query" -> {
          val queryParam = context.queryParam(param.name)
          when {
            param.isSubclassOf(List::class) -> queryParam
            queryParam.isNotEmpty() -> parseParam(param, queryParam[0])
            else -> null
          }
        }
        else -> null
      }
    }
  }

  private fun buildBodyParam(
    method: KFunction<*>,
    param: KParameter,
    context: RoutingContext
  ): Any? {
    return when (param.type.jvmErasure) {
      JsonObject::class -> context.bodyAsJson
      JsonArray::class -> context.bodyAsJsonArray
      List::class -> getGenericParameterType(method, param.index)
        .instantiateList(context.bodyAsJsonArray)
      Map::class -> getGenericParameterType(method, param.index)
        .instantiateMap(context.bodyAsJson)
      else -> param.type.classifier.let { it as KClass<*> }.let { kclass ->
        when {
          kclass.isData -> kclass.instantiate(context.bodyAsJson)
          else -> null
        }
      }
    }
  }

  private fun KClass<*>.instantiate(json: JsonObject): Any {
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
          List::class -> getGenericParameterType(ctor, param.index)
            .instantiateList(json.getJsonArray(name))
          Map::class -> getGenericParameterType(ctor, param.index)
            .instantiateMap(json.getJsonObject(name))
          else -> kclass.instantiate(json.getJsonObject(name))
        }
      }
    }
    return ctor.call(*params.toTypedArray())
  }

  private fun getGenericParameterType(
    function: KFunction<*>,
    index: Int
  ): Type? {
    return function.javaConstructor?.let {
      it.genericParameterTypes[index]
    } ?: function.javaMethod?.let {
      it.genericParameterTypes[index-1]
    }
  }

  private fun Type?.instantiateList(
    arr: JsonArray
  ): List<Any?> {
    val range = 0 until arr.size()
    return this?.let { type ->
      val actualTypeArgument = type.let {
        val parameterizedType = it as ParameterizedType
        when (val typeArg = parameterizedType.actualTypeArguments[0]) {
          is WildcardType -> typeArg.upperBounds[0]
          else -> typeArg
        }
      }
      val itemsKClass = when (actualTypeArgument) {
        is ParameterizedType -> actualTypeArgument.rawType.typeName
        else -> actualTypeArgument.typeName
      }.let { Class.forName(it).kotlin }
      when (itemsKClass) {
        ByteArray::class -> range.map { arr.getBinary(it) }
        Boolean::class -> range.map { arr.getBoolean(it) }
        Double::class -> range.map { arr.getDouble(it) }
        Float::class -> range.map { arr.getFloat(it) }
        Instant::class -> range.map { arr.getInstant(it) }
        Int::class -> range.map { arr.getInteger(it) }
        Long::class -> range.map { arr.getLong(it) }
        String::class -> range.map { arr.getString(it) }
        List::class -> range.map {
          actualTypeArgument.instantiateList(arr.getJsonArray(it))
        }
        Map::class -> range.map {
          actualTypeArgument.instantiateMap(arr.getJsonObject(it))
        }
        else -> range.map {
          itemsKClass.instantiate(arr.getJsonObject(it))
        }
      }
    } ?: range.map { arr.getValue(it) }
  }

  private fun Type?.instantiateMap(
    obj: JsonObject
  ): Map<String, Any?> {
    return this?.let { type ->
      val actualTypeArgument = type.let {
        val parameterizedType = it as ParameterizedType
        when (val typeArg = parameterizedType.actualTypeArguments[1]) {
          is WildcardType -> typeArg.upperBounds[0]
          else -> typeArg
        }
      }
      val itemsKClass = when (actualTypeArgument) {
        is ParameterizedType -> actualTypeArgument.rawType.typeName
        else -> actualTypeArgument.typeName
      }.let { Class.forName(it).kotlin }
      val map = mutableMapOf<String, Any?>()
      obj.forEach { (key, _) ->
        when (itemsKClass) {
          ByteArray::class -> map.put(key, obj.getBinary(key))
          Boolean::class -> map.put(key, obj.getBoolean(key))
          Double::class -> map.put(key, obj.getDouble(key))
          Float::class -> map.put(key, obj.getFloat(key))
          Instant::class -> map.put(key, obj.getInstant(key))
          Int::class -> map.put(key, obj.getInteger(key))
          Long::class -> map.put(key, obj.getLong(key))
          String::class -> map.put(key, obj.getString(key))
          List::class -> map.put(
            key,
            actualTypeArgument.instantiateList(obj.getJsonArray(key))
          )
          Map::class -> map.put(
            key,
            actualTypeArgument.instantiateMap(obj.getJsonObject(key))
          )
          else -> map.put(key, itemsKClass.instantiate(obj.getJsonObject(key)))
        }
      }
      map
    } ?: obj.map
  }

  private fun parseParam(param: KParameter, value: String): Any {
    return when {
      param.isSubclassOf(Int::class) -> value.toInt()
      param.isSubclassOf(Boolean::class) -> value.toBoolean()
      else -> value
    }
  }

  private fun KParameter.isSubclassOf(clazz: KClass<*>): Boolean =
    this.type.jvmErasure.isSubclassOf(clazz)

  fun createFailureHandlers(): RouteHandlers =
    listOf(
      object : Handler<RoutingContext> {
        override fun handle(context: RoutingContext) {
          replyWithError(context, context.failure())
        }
      }
    )

  private fun handleResponse(context: RoutingContext, response: Any?) {
    if (response == Unit) {
      context.response().end()
    } else {
      context.response().putHeader("content-type", "application/json")
      if (response == null) {
        context.response().end(jsonObjectOf("response" to null).encode())
      } else when (response) {
        is ClusterSerializable -> context.response().end(response.encode())
        is List<*> -> context.response().end(serializeList(response).encode())
        is Map<*, *> -> context.response().end(serializeMap(response).encode())
        else -> if (response::class.isData) {
          context.response().end(serializeObject(response).encode())
        } else {
          context.response()
            .end(jsonObjectOf("response" to response.toString()).encode())
        }
      }
    }
  }

  private fun serializeList(list: List<*>): JsonArray {
    val arr = JsonArray()
    list.forEach { item ->
      when (item) {
        is ByteArray -> arr.add(item)
        is Boolean -> arr.add(item)
        is Double -> arr.add(item)
        is Float -> arr.add(item)
        is Instant -> arr.add(item)
        is Int -> arr.add(item)
        is Long -> arr.add(item)
        is String -> arr.add(item)
        is List<*> -> arr.add(serializeList(item))
        is Map<*, *> -> arr.add(serializeMap(item))
        null -> arr.add(null as Any?)
        else -> arr.add(serializeObject(item))
      }
    }
    return arr
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T: Any> serializeObject(obj: T): JsonObject {
    val json = JsonObject()
    obj::class.declaredMemberProperties.forEach { prop ->
      val key = prop.name
      when (val value = (prop as KProperty1<T, Any?>).get(obj)) {
        is ByteArray -> json.put(key, value)
        is Boolean -> json.put(key, value)
        is Double -> json.put(key, value)
        is Float -> json.put(key, value)
        is Instant -> json.put(key, value)
        is Int -> json.put(key, value)
        is Long -> json.put(key, value)
        is String -> json.put(key, value)
        is List<*> -> json.put(key, serializeList(value))
        is Map<*, *> -> json.put(key, serializeMap(value))
        else -> if (value != null) {
          json.put(key, serializeObject(value))
        }
      }
    }
    return json
  }

  private fun serializeMap(map: Map<*, *>): JsonObject {
    val json = JsonObject()
    map.forEach { (keyObj, value) ->
      val key = keyObj.toString()
      when (value) {
        is ByteArray -> json.put(key, value)
        is Boolean -> json.put(key, value)
        is Double -> json.put(key, value)
        is Float -> json.put(key, value)
        is Instant -> json.put(key, value)
        is Int -> json.put(key, value)
        is Long -> json.put(key, value)
        is String -> json.put(key, value)
        is List<*> -> json.put(key, serializeList(value))
        is Map<*, *> -> json.put(key, serializeMap(value))
        else -> if (value != null) {
          json.put(key, serializeObject(value))
        }
      }
    }
    return json
  }

  private fun replyWithError(context: RoutingContext, failure: Throwable) {
    val response = context.response()
    when {
      failure is ResponseCodeException -> {
        response.putHeader("content-type", "application/json")
        response
          .setStatusCode(failure.statusCode.value())
          .end(failure.asJson().encode())
      }
      context.statusCode() <= 0 -> response
        .setStatusCode(HTTPStatusCode.INTERNAL_ERROR.value())
        .end(failure.message ?: "")
      else -> response
        .setStatusCode(context.statusCode())
        .end(failure.message ?: "")
    }
  }

  private fun ClusterSerializable.encode(): String {
    return when (this) {
      is JsonObject -> this.encode()
      is JsonArray -> this.encode()
      else -> this.toString()
    }
  }
}