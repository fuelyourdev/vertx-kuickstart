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
import org.koin.core.KoinComponent
import org.koin.core.inject

typealias Roles = Map<String, List<String>>
typealias RouteHandlers = List<Handler<RoutingContext>>

interface SwaggerAuthHandler {
  fun createAuthHandlers(roles: Roles): RouteHandlers
}

fun Router.route(swaggerFile: OpenAPI) {
  SwaggerRouterSingleton.route(this, swaggerFile)
}

private object SwaggerRouterSingleton : KoinComponent {

  private val impl: SwaggerRouter by inject()

  fun route(router: Router, swaggerFile: OpenAPI) {
    impl.route(router, swaggerFile)
  }
}

class SwaggerRouter(
  private val swaggerAuthHandler: SwaggerAuthHandler,
  private val swaggerServiceHandler: SwaggerServiceHandler,
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
    route.handleServiceCall(sr.op, sr.opId)
  }

  private fun String.convertToVertxPath() =
    replace('{', ':').replace("}", "")

  private fun PathItem.HttpMethod.convertToVertxVerb() =
    HttpMethod.valueOf(name)

  private fun Route.handleJwtAuth(roles: Roles) {
    if (roles.isNotEmpty()) {
      with(swaggerAuthHandler.createAuthHandlers(roles)) {
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

  private fun Route.handleServiceCall(op: Operation, opId: String) {
    with(swaggerServiceHandler.createServiceHandlers(op, opId)) {
      forEach { handler(it) }
    }
    with(swaggerServiceHandler.createFailureHandlers()) {
      forEach { handler(it) }
    }
  }
}
