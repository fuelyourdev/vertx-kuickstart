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
    val pkg = this.javaClass.`package`.name.substringBeforeLast('.') +
        ".controllers"
    val swaggerFile =
      SwaggerMerger.mergeAllInDirectory("swagger")
        ?: throw RuntimeException("Unable to process Swagger file")
    val staticHandler = StaticHandler.create()
      .setDirectoryListing(false)
      .setIncludeHidden(false)

    val apiRouter = Router.router(vertx)
    SwaggerRouter.route(apiRouter, swaggerFile, pkg)
    mainRouter.mountSubRouter("/api", apiRouter)

    mainRouter.get().handler(staticHandler)

    return vertx.createHttpServer(
      HttpServerOptions().setCompressionSupported(true)
    )
      .requestHandler(mainRouter)
      .rxListen(config().getInteger("http.port", 8080))
      .ignoreElement()
  }
}