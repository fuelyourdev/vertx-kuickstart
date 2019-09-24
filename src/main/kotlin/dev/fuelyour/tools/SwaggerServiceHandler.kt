package dev.fuelyour.tools

import dev.fuelyour.annotations.Body
import dev.fuelyour.exceptions.HTTPStatusCode
import dev.fuelyour.exceptions.ResponseCodeException
import io.reactivex.Single
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.parameters.Parameter
import io.vertx.core.Handler
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.shareddata.impl.ClusterSerializable
import io.vertx.reactivex.ext.web.RoutingContext
import org.koin.core.KoinComponent
import java.lang.reflect.InvocationTargetException
import kotlin.Exception
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.jvmErasure

class SwaggerServiceHandler(
  private val controllerPackage: String
) : KoinComponent {
  fun createServiceHandlers(op: Operation, opId: String): RouteHandlers {
    val (controllerName, methodName) = opId.split('.')
    val kclass = Class.forName("${controllerPackage}.$controllerName").kotlin
    val controller = getKoin().get<Any>(kclass, null, null)
    val method = controller::class.members.find { it.name == methodName }
      ?: throw Exception("Unable to parse operation $opId")

    return listOf(
      object : Handler<RoutingContext> {
        override fun handle(context: RoutingContext) {
          method.callWithParams(controller, context, op.parameters)
        }
      }
    )
  }

  private fun KCallable<*>.callWithParams(
    instance: Any?,
    context: RoutingContext,
    swaggerParams: List<Parameter>?
  ) {
    try {
      val params: MutableMap<KParameter, Any?> = mutableMapOf()
      inejctInstanceParam(params, instance)
      parameters.forEach {param ->
        if (param.isSubclassOf(RoutingContext::class)) {
          params[param] = context
        } else if (param.findAnnotation<Body>() != null) {
          params[param] = injectBody(param, context)
        } else if (param.kind != KParameter.Kind.INSTANCE) {
          params[param] = injectParams(param, context, swaggerParams)
        }

      }
      handleResponse(context, this.callBy(params))
    } catch (e: Exception) {
      if (e is InvocationTargetException) {
        val ex = e.targetException
        ex.printStackTrace()
        throw ex
      } else {
        e.printStackTrace()
        throw e
      }
    }
  }

  private fun KCallable<*>.inejctInstanceParam(
    params: MutableMap<KParameter, Any?>,
    instance: Any?
  ) {
    this.instanceParameter?.let { params.put(it, instance) }
  }

  private fun injectBody(
    param: KParameter,
    context: RoutingContext
  ): Any? {
    val bodyAnn = param.findAnnotation<Body>()
    return if (bodyAnn != null && bodyAnn.key.isNotBlank()) {
      context.bodyAsJson.getValue(bodyAnn.key)
    } else if (param.isSubclassOf(JsonObject::class)) {
      context.bodyAsJson
    } else if (param.isSubclassOf(JsonArray::class)) {
      context.bodyAsJsonArray
    } else if (param.isSubclassOf(String::class)) {
      context.bodyAsString
    } else {
      null
    }
  }

  private fun injectParams(
    param: KParameter,
    context: RoutingContext,
    swaggerParams: List<Parameter>?
  ): Any? {
    return swaggerParams?.find { it.name == param.name }?.let { sp ->
      when (sp.`in`) {
        "path" -> parseParam(param, context.pathParam(param.name))
        "query" -> {
          val queryParam = context.queryParam(param.name)
          if (param.isSubclassOf(List::class))
            queryParam
          else if (queryParam.isNotEmpty())
            parseParam(param, queryParam[0])
          else
            null
        }
        else -> null
      }
    }
  }

  private fun parseParam(param: KParameter, value: String): Any {
    return if (param.isSubclassOf(Int::class))
      value.toInt()
    else if (param.isSubclassOf(Boolean::class))
      value.toBoolean()
    else value
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
    when (response) {
      is Single<*> -> {
        response.subscribe({ onComplete ->
          handleResponse(context, onComplete)
        }, { error -> replyWithError(context, error) })
      }
      is ClusterSerializable -> {
        context.response().putHeader("content-type", "application/json")
        context.response().end(response.encode())
      }
      !is Unit -> {
        context.response().end(response.toString())
      }
    }
  }

  private fun replyWithError(context: RoutingContext, failure: Throwable) {
    val response = context.response()
    if (failure is ResponseCodeException) {
      response.putHeader("content-type", "application/json")
      response
        .setStatusCode(failure.statusCode.value())
        .end(failure.asJson().encode())
    } else if (context.statusCode() <= 0) {
      response
        .setStatusCode(HTTPStatusCode.INTERNAL_ERROR.value())
        .end(failure.message ?: "")
    } else {
      response
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