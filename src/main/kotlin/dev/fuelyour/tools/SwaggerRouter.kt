package dev.fuelyour.tools

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.parser.ResolverCache
import io.vertx.core.Handler
import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.api.contract.openapi3.impl.OpenAPI3RequestValidationHandlerImpl
import io.vertx.reactivex.ext.web.Route
import io.vertx.reactivex.ext.web.Router
import io.vertx.reactivex.ext.web.RoutingContext
import io.vertx.reactivex.ext.web.api.contract.openapi3.OpenAPI3RequestValidationHandler
import io.vertx.reactivex.ext.web.handler.BodyHandler
import io.vertx.reactivex.ext.web.handler.TimeoutHandler

typealias Roles = Map<String, List<String>>
typealias RouteHandlers = List<Handler<RoutingContext>>

interface AuthHandlerSupplier {
  fun createAuthHandlers(roles: Roles): RouteHandlers
}

interface ServiceHandlerSupplier {
  fun createServiceHandlers(opId: String): RouteHandlers
  fun createFailureHandlers(): RouteHandlers
}

class SwaggerRouter(
  private val authHandlerSupplier: AuthHandlerSupplier,
  private val serviceHandlerSupplier: ServiceHandlerSupplier,
  private val traverser: SwaggerTraverser
) {

  fun route(router: Router, swaggerFile: OpenAPI) {
    router.route()
      .produces("application/json")
      .handler(BodyHandler.create().setBodyLimit(5120000))
      .handler(TimeoutHandler.create(30000))

    traverser.traverseSwaggerFile(swaggerFile) { swaggerRoute ->
      specifyRoute(router, swaggerRoute)
    }
  }

  private fun specifyRoute(router: Router, sr: SwaggerRoute) {
    val route = router.route(
      sr.verb.convertToVertxVerb(),
      sr.path.convertToVertxPath()
    )
    route.handleJwtAuth(sr.authRoles)
    route.handleRequestValidation(sr.op, sr.swaggerFile, sr.swaggerCache)
    route.handleServiceCall(sr.opId)
  }

  private fun String.convertToVertxPath() =
    replace('{', ':').replace("}", "")

  private fun PathItem.HttpMethod.convertToVertxVerb() =
    HttpMethod.valueOf(name)

  private fun Route.handleJwtAuth(roles: Roles) {
    if (roles.isNotEmpty()) {
      with(authHandlerSupplier.createAuthHandlers(roles)) {
        forEach { handler(it) }
      }
    }
  }

  private fun Route.handleRequestValidation(
    op: Operation,
    swaggerFile: OpenAPI,
    swaggerCache: ResolverCache
  ) {
    val impl = OpenAPI3RequestValidationHandlerImpl(
      op,
      op.parameters,
      swaggerFile,
      swaggerCache
    )
    handler(OpenAPI3RequestValidationHandler(impl))
  }

  private fun Route.handleServiceCall(opId: String) {
    with(serviceHandlerSupplier.createServiceHandlers(opId)) {
      forEach { handler(it) }
    }
    with(serviceHandlerSupplier.createFailureHandlers()) {
      forEach { handler(it) }
    }
  }
}
