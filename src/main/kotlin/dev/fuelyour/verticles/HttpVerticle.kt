package dev.fuelyour.verticles

import dev.fuelyour.tools.SwaggerMerger
import dev.fuelyour.tools.route
import io.reactivex.Completable
import io.vertx.core.http.HttpServerOptions
import io.vertx.reactivex.core.AbstractVerticle
import io.vertx.reactivex.ext.web.Router
import io.vertx.reactivex.ext.web.handler.StaticHandler
import org.koin.core.KoinComponent

class HttpVerticle : AbstractVerticle(), KoinComponent {

  override fun rxStart(): Completable {
    val mainRouter = Router.router(vertx)
    mountApiRoutes(mainRouter)
    mountStaticRoutes(mainRouter)
    return startupHttpServer(mainRouter)
  }

  private fun mountApiRoutes(mainRouter: Router) {
    val apiRouter = Router.router(vertx)
    setRoutesFromSwagger(apiRouter)
    mainRouter.mountSubRouter("/api", apiRouter)
  }

  private fun setRoutesFromSwagger(apiRouter: Router) {
    val swaggerFile = SwaggerMerger.mergeAllInDirectory("swagger")
      ?: throw Exception("Unable to process Swagger file")
    apiRouter.route(swaggerFile)
  }

  private fun mountStaticRoutes(mainRouter: Router) {
    mainRouter.get().handler(
      StaticHandler.create()
        .setDirectoryListing(false)
        .setIncludeHidden(false)
    )
  }

  private fun startupHttpServer(mainRouter: Router?): Completable {
    return vertx
      .createHttpServer(HttpServerOptions().setCompressionSupported(true))
      .requestHandler(mainRouter)
      .rxListen(config().getInteger("http.port", 8080))
      .ignoreElement()
  }
}