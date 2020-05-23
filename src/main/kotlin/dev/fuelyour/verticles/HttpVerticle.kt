package dev.fuelyour.verticles

import dev.fuelyour.vertxkuickstartcore.tools.SwaggerMerger
import dev.fuelyour.vertxkuickstartcore.tools.SwaggerRouter
import io.swagger.v3.oas.models.OpenAPI
import io.vertx.core.http.HttpServerOptions
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.StaticHandler
import io.vertx.kotlin.coroutines.CoroutineVerticle
import org.koin.core.KoinComponent
import org.koin.core.inject

class HttpVerticle : CoroutineVerticle(), KoinComponent {

    private val swaggerRouter: SwaggerRouter by inject()

    override suspend fun start() {
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

    private fun startupHttpServer(mainRouter: Router) {
        vertx
            .createHttpServer(HttpServerOptions().setCompressionSupported(true))
            .requestHandler(mainRouter)
            .listen(config.getInteger("http.port", 8080))
    }

    fun Router.route(swaggerFile: OpenAPI) {
        swaggerRouter.route(this, swaggerFile)
    }
}
