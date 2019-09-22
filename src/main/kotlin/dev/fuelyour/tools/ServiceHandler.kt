package dev.fuelyour.tools

import dev.fuelyour.exceptions.HTTPStatusCode
import dev.fuelyour.exceptions.ResponseCodeException
import io.reactivex.Single
import io.vertx.core.Handler
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.shareddata.impl.ClusterSerializable
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.web.RoutingContext

class ServiceHandler(
  private val vertx: Vertx
): ServiceHandlerSupplier {
  override fun createServiceHandlers(opId: String): RouteHandlers =
    listOf(
      object : Handler<RoutingContext> {
        override fun handle(context: RoutingContext) {
          vertx.eventBus()
            .rxRequest<ClusterSerializable>(opId, context.bodyAsJson)
            .subscribe(
              { handleResponse(context, it) },
              { replyWithError(context, it) }
            )
        }
      }
    )

  override fun createFailureHandlers(): RouteHandlers =
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