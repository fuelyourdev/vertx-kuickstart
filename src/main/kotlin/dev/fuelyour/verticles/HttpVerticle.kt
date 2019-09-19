package dev.fuelyour.verticles

import dev.fuelyour.tools.SwaggerMerger
import dev.fuelyour.tools.SwaggerRouter
import io.reactivex.Completable
import io.vertx.core.http.HttpServerOptions
import io.vertx.reactivex.core.AbstractVerticle
import io.vertx.reactivex.ext.web.Router
import io.vertx.reactivex.ext.web.handler.StaticHandler

class HttpVerticle : AbstractVerticle() {

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
    val pkg = javaClass.`package`.name.substringBeforeLast('.') + ".controllers"
    val swaggerFile = SwaggerMerger.mergeAllInDirectory("swagger")
      ?: throw Exception("Unable to process Swagger file")
    SwaggerRouter.route(apiRouter, swaggerFile, pkg)
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