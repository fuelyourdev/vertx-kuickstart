package dev.fuelyour.tools

import dev.fuelyour.exceptions.AuthorizationException
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.PubSecKeyOptions
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.auth.jwt.JWTAuthOptions
import io.vertx.ext.jwt.JWTOptions
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.JWTAuthHandler
import java.time.LocalDateTime
import java.time.ZoneOffset

class JwtAuthHelper(
  config: JsonObject,
  vertx: Vertx
): SwaggerAuthHandler {

  val EXPIRATION_MILLIS = 1000 * 60 * 60 * 30

  private val authProvider = JWTAuth.create(
    vertx, JWTAuthOptions()
      .addPubSecKey(
        PubSecKeyOptions()
          .setAlgorithm("HS256")
          .setPublicKey(config.getString("JWT_PUB_KEY"))
          .setSecretKey(config.getString("JWT_PRIVATE_KEY"))
          .setSymmetric(true)
      )
  )

  fun generateToken(json: JsonObject): String {
    json.put("created", getCurrentUTCMillis())
    return authProvider.generateToken(json, JWTOptions())
  }

  fun isTokenExpired(created: Long): Boolean {
    return getCurrentUTCMillis() - created > EXPIRATION_MILLIS
  }

  private fun getCurrentUTCMillis(): Long {
    val now = LocalDateTime.now(ZoneOffset.UTC)
    return now.atZone(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()!!
  }

  override fun createAuthHandlers(roles: Roles): RouteHandlers =
    listOf(
      JWTAuthHandler.create(authProvider) as Handler<RoutingContext>,
      object : Handler<RoutingContext> {
        override fun handle(context: RoutingContext) {
          val userRoles = context.user().principal()
            .getJsonArray("roles", JsonArray()) ?: JsonArray()
          authenticateUserRoles(roles, userRoles)
        }
      }
    )

  fun authenticateUserRoles(
    requiredRoles: Roles,
    userRoles: JsonArray
  ) {
    with (requiredRoles) {
      if ((taggedWith("oneOf") && !userRoles.oneOf(rolesIn("oneOf"))) ||
        (taggedWith("anyOf") && !userRoles.anyOf(rolesIn("anyOf"))) ||
        (taggedWith("allOf") && !userRoles.allOf(rolesIn("allOf")))
      )
        throw AuthorizationException()
    }
  }

  private fun Roles.taggedWith(tag: String): Boolean =
    this[tag] != null

  private fun Roles.rolesIn(tag: String): JsonArray =
    JsonArray(this[tag])

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
    other.forEach { if (this.contains(it)) return true }
    return false
  }

  private fun JsonArray.allOf(other: JsonArray) = this == other
}