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
import kotlin.Exception
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.jvm.jvmErasure
import kotlin.reflect.KFunction
import kotlin.reflect.full.*

class ControllerSupplier(
  private val controllerPackage: String
) : KoinComponent {
  fun getControllerInstance(controllerName: String): Any {
    val kclass = Class.forName("$controllerPackage.$controllerName").kotlin
    return getKoin().get(kclass, null, null)
  }
}

class SwaggerServiceHandler(
  private val controllerSupplier: ControllerSupplier,
  serializer: Serializer,
  deserializer: Deserializer
): Serializer by serializer, Deserializer by deserializer {
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
      List::class -> method.getTypeForParam(param)
        .instantiateList(context.bodyAsJsonArray)
      Map::class -> method.getTypeForParam(param)
        .instantiateMap(context.bodyAsJson)
      Field::class -> throw Exception(
        "Field not allowed as a controller function param"
      )
      else -> param.type.classifier.let { it as KClass<*> }.let { kclass ->
        when {
          kclass.isData -> kclass.instantiate(context.bodyAsJson)
          else -> null
        }
      }
    }
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
        is List<*> -> context.response().end(response.serialize().encode())
        is Map<*, *> -> context.response().end(response.serialize().encode())
        else -> if (response::class.isData) {
          context.response().end(response.serialize().encode())
        } else {
          context.response()
            .end(jsonObjectOf("response" to response.toString()).encode())
        }
      }
    }
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

