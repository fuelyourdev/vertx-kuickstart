package dev.fuelyour.exceptions

import dev.fuelyour.exceptions.HTTPStatusCode.*
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.Json
import io.vertx.kotlin.core.json.obj

open class ResponseCodeException(
  message: String = "",
  val details: JsonArray = JsonArray(),
  val statusCode: HTTPStatusCode = BAD_REQUEST,
  throwable: Throwable? = null
) : RuntimeException(message, throwable) {

  companion object {
    fun fromStatusCode(
      statusCode: HTTPStatusCode?,
      errorMessage: String,
      details: JsonArray
    ): ResponseCodeException {
      return when (statusCode) {
        UNAUTHORIZED -> AuthorizationException(errorMessage, details)
        BAD_REQUEST -> BadRequestException(errorMessage, details)
        CONFLICT -> ConflictException(errorMessage, details)
        FORBIDDEN -> ForbiddenException(errorMessage, details)
        NOT_FOUND -> ModelNotFoundException(errorMessage, details)
        TOO_MANY_REQUESTS -> TooManyRequestsException(errorMessage, details)
        UNAVAILABLE,
        BAD_GATEWAY,
        GATEWAY_TIMEOUT -> UnavailableException(errorMessage, details)
        else -> ServiceException(errorMessage, details)
      }
    }
  }

  fun asJson(): JsonObject {
    return Json.obj("message" to message, "details" to details)
  }
}

class AuthorizationException(
  message: String = "",
  details: JsonArray = JsonArray(),
  throwable: Throwable? = null
) : ResponseCodeException(message, details, UNAUTHORIZED, throwable)

class BadRequestException(
  message: String = "",
  details: JsonArray = JsonArray(),
  throwable: Throwable? = null
) : ResponseCodeException(message, details, BAD_REQUEST, throwable)

class ConflictException(
  message: String = "",
  details: JsonArray = JsonArray(),
  throwable: Throwable? = null
) : ResponseCodeException(message, details, CONFLICT, throwable)

class ForbiddenException(
  message: String = "",
  details: JsonArray = JsonArray(),
  throwable: Throwable? = null
) : ResponseCodeException(message, details, FORBIDDEN, throwable)

class ModelNotFoundException(
  message: String = "",
  details: JsonArray = JsonArray(),
  throwable: Throwable? = null
) : ResponseCodeException(message, details, NOT_FOUND, throwable)

class TooManyRequestsException(
  message: String = "",
  details: JsonArray = JsonArray(),
  throwable: Throwable? = null
) : ResponseCodeException(message, details, TOO_MANY_REQUESTS, throwable)

class UnavailableException(
  message: String = "",
  details: JsonArray = JsonArray(),
  throwable: Throwable? = null
) : ResponseCodeException(message, details, UNAVAILABLE, throwable)

class ServiceException(
  message: String = "",
  details: JsonArray = JsonArray(),
  throwable: Throwable? = null
) : ResponseCodeException(message, details, INTERNAL_ERROR, throwable)


class TimeoutException(
  message: String = "",
  details: JsonArray = JsonArray(),
  throwable: Throwable? = null
) : ResponseCodeException(message, details, GATEWAY_TIMEOUT, throwable)