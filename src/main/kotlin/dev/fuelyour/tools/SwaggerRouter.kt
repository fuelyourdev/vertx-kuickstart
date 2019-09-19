package dev.fuelyour.tools

import dev.fuelyour.annotations.Body
import dev.fuelyour.exceptions.AuthorizationException
import dev.fuelyour.exceptions.HTTPStatusCode
import dev.fuelyour.exceptions.ResponseCodeException
import io.reactivex.Single
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.parser.ResolverCache
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.shareddata.impl.ClusterSerializable
import io.vertx.ext.web.api.contract.openapi3.impl.OpenAPI3RequestValidationHandlerImpl
import io.vertx.reactivex.ext.web.Router
import io.vertx.reactivex.ext.web.RoutingContext
import io.vertx.reactivex.ext.web.api.contract.openapi3.OpenAPI3RequestValidationHandler
import io.vertx.reactivex.ext.web.handler.BodyHandler
import io.vertx.reactivex.ext.web.handler.JWTAuthHandler
import io.vertx.reactivex.ext.web.handler.TimeoutHandler
import org.koin.core.KoinComponent
import org.koin.core.context.GlobalContext.get
import org.koin.core.inject
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.jvmErasure

object SwaggerRouter : KoinComponent {

  private val jwtHelper: JWTHelper by inject()

  fun route(router: Router, swaggerFile: OpenAPI, controllerPackage: String) {
    router.route()
      .produces("application/json")
      .handler(BodyHandler.create().setBodyLimit(5120000))
      .handler(TimeoutHandler.create(30000))

    addRoutesFromSwaggerFile(router, swaggerFile, controllerPackage)
  }

  private fun addRoutesFromSwaggerFile(router: Router, swaggerFile: OpenAPI, controllerPackage: String) {
    val swaggerCache = ResolverCache(swaggerFile, null, null)

    val controllerInstances = mutableMapOf<String, Any>()
    swaggerFile.paths.forEach { (path, pathItem) ->
      val convertedPath = path.replace('{', ':').replace("}", "")
      pathItem.readOperationsMap().forEach { (verb, op) ->
        val opId = op.operationId ?: ""
        val split = opId.split('.')
        if (split.size < 2)
          throw RuntimeException("Unable to parse operation $opId for path $path")
        val controllerName = split[0]
        val methodName = split[1]
        val roles = op.extensions?.get("x-auth-roles") as? Map<String, List<String>>

        val controller = controllerInstances.getOrElse(controllerName, {
          val kclass = Class.forName("${controllerPackage}.$controllerName").kotlin
          val inst = get().koin.get<Any>(kclass, null, null)
          controllerInstances[controllerName] = inst
          inst
        })

        val method = controller::class.members.find { it.name == methodName }
          ?: throw RuntimeException("Method $methodName not found for controller $controllerName")

        val route = router.route(HttpMethod.valueOf(verb.name), convertedPath)
        if (roles?.isNotEmpty() == true)
          route.handler(JWTAuthHandler.create(jwtHelper.authProvider))
        route.handler(
          OpenAPI3RequestValidationHandler(
            OpenAPI3RequestValidationHandlerImpl(op, op.parameters, swaggerFile, swaggerCache)
          )
        )
        route.handler { context ->
          if (roles?.isNotEmpty() == true)
            authenticateUser(
              roles,
              context.user().principal().getJsonArray("roles", JsonArray())
            )
          method.callWithParams(controller, context, op.parameters)
        }.failureHandler { replyWithError(it, it.failure()) }
      }
    }
  }

  private fun authenticateUser(requiredRoles: Map<String, List<String>>, userRoles: JsonArray) {
    if ((requiredRoles["oneOf"] != null && !userRoles.oneOf(JsonArray(requiredRoles["oneOf"]))) ||
      (requiredRoles["anyOf"] != null && !userRoles.anyOf(JsonArray(requiredRoles["anyOf"]))) ||
      (requiredRoles["allOf"] != null && !userRoles.allOf(JsonArray(requiredRoles["allOf"])))
    )
      throw AuthorizationException()
  }

  private fun replyWithError(context: RoutingContext, failure: Throwable) {
    var response = context.response()
    if (failure is ResponseCodeException) {
      response.putHeader("content-type", "application/json")
      response.setStatusCode(failure.statusCode.value()).end(failure.asJson().encode())
    } else if (context.statusCode() <= 0) {
      response.setStatusCode(HTTPStatusCode.INTERNAL_ERROR.value()).end(failure?.message ?: "")
    } else {
      response.setStatusCode(context.statusCode()).end(failure?.message ?: "")
    }
  }

  private fun KCallable<*>.callWithParams(instance: Any?, context: RoutingContext, swaggerParams: List<Parameter>?) {
    try {
      val params: MutableMap<KParameter, Any?> = mutableMapOf()
      this.instanceParameter?.let { params.put(it, instance) }
      this.parameters.forEach { param ->
        if (param.isSubclassOf(RoutingContext::class)) {
          params[param] = context
        } else if (param.findAnnotation<Body>() != null) {
          val bodyAnn = param.findAnnotation<Body>()
          if (bodyAnn != null && bodyAnn.key.isNotBlank()) {
            params[param] = context.bodyAsJson.getValue(bodyAnn.key)
          } else if (param.isSubclassOf(JsonObject::class)) {
            params[param] = context.bodyAsJson
          } else if (param.isSubclassOf(JsonArray::class)) {
            params[param] = context.bodyAsJsonArray
          } else if (param.isSubclassOf(String::class)) {
            params[param] = context.bodyAsString
          }
        } else if (param.kind != KParameter.Kind.INSTANCE) {
          swaggerParams?.find { it.name == param.name }?.let { sp ->
            when (sp.`in`) {
              "path" -> params[param] =
                parseParam(param, context.pathParam(param.name))
              "query" -> {
                val queryParam = context.queryParam(param.name)
                if (param.isSubclassOf(List::class))
                  params[param] = queryParam
                else if (queryParam.isNotEmpty())
                  params[param] = parseParam(param, queryParam[0])
              }
            }
          }
          if (params[param] == null && !param.isOptional)
            params[param] = null
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

  private fun parseParam(param: KParameter, value: String): Any {
    return if (param.isSubclassOf(Int::class))
      value.toInt()
    else if (param.isSubclassOf(Boolean::class))
      value.toBoolean()
    else value
  }

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

  private fun ClusterSerializable.encode(): String {
    return when (this) {
      is JsonObject -> this.encode()
      is JsonArray -> this.encode()
      else -> this.toString()
    }
  }

  private inline fun <reified T : Annotation> KAnnotatedElement.findAnnotation() =
    this.annotations.find { it is T } as? T

  private fun KParameter.isSubclassOf(clazz: KClass<*>): Boolean = this.type.jvmErasure.isSubclassOf(clazz)

  private fun JsonArray.oneOf(other: JsonArray): Boolean {
    var hasOne = false
    other.forEach {
      if (this.contains(it)) {
        if (hasOne)
          return false
        hasOne = true
      }
    }
    return hasOne
  }

  private fun JsonArray.anyOf(other: JsonArray): Boolean {
    var hasOne = false
    other.forEach { if (this.contains(it)) hasOne = true }
    return hasOne
  }

  private fun JsonArray.allOf(other: JsonArray) = this == other
}