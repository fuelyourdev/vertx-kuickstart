package dev.fuelyour.tools

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.parser.ResolverCache

class SwaggerTraverser() {
  fun traverseSwaggerFile(
    swaggerFile: OpenAPI,
    specifyRoute: (SwaggerRoute) -> Unit
  ) {
    val swaggerCache =
      ResolverCache(swaggerFile, null, null)

    val srBuilder = SwaggerRouteBuilder(
      file = swaggerFile,
      cache = swaggerCache
    )
    swaggerFile.paths.forEach { (path, pathItem) ->
      srBuilder.path = path
      processPathItem(pathItem, srBuilder, specifyRoute)
    }
  }

  private fun processPathItem(
    pathItem: PathItem,
    srBuilder: SwaggerRouteBuilder,
    specifyRoute: (SwaggerRoute) -> Unit
  ) {
    pathItem.readOperationsMap().forEach { (verb, op) ->
      srBuilder.verb = verb
      srBuilder.op = op
      processOperation(op, srBuilder, specifyRoute)
    }
  }

  fun processOperation(
    op: Operation,
    srBuilder: SwaggerRouteBuilder,
    specifyRoute: (SwaggerRoute) -> Unit
  ) {
    srBuilder.authRoles = op.authRoles
    srBuilder.opId = op.operationId ?: ""
    val sr = srBuilder.build()
    specifyRoute(sr)
  }

  @Suppress("UNCHECKED_CAST")
  private val Operation.authRoles: Roles
    get() = extensions
      ?.get("x-auth-roles") as? Roles
      ?: mapOf()
}

class SwaggerRouteBuilder(
  var verb: PathItem.HttpMethod? = null,
  var path: String? = null,
  var opId: String? = null,
  var op: Operation? = null,
  var authRoles: Roles? = null,
  var file: OpenAPI? = null,
  var cache: ResolverCache? = null
) {
  fun build(): SwaggerRoute {
    val throwException = {
      throw Exception(
        "Can't build swagger route, not all fields have been provided."
      )
    }
    val verb = this.verb ?: throwException()
    val path = this.path ?: throwException()
    val opId = this.opId ?: throwException()
    val op = this.op ?: throwException()
    val authRoles = this.authRoles ?: throwException()
    val file = this.file ?: throwException()
    val cache = this.cache ?: throwException()
    return SwaggerRoute(verb, path, opId, op, authRoles, file, cache)
  }
}

data class SwaggerRoute(
  val verb: PathItem.HttpMethod,
  val path: String,
  val opId: String,
  val op: Operation,
  val authRoles: Roles,
  val swaggerFile: OpenAPI,
  val swaggerCache: ResolverCache
)
