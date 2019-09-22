package dev.fuelyour.tools

import dev.fuelyour.config.Config
import dev.fuelyour.exceptions.AuthorizationException
import io.vertx.core.Handler
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.PubSecKeyOptions
import io.vertx.ext.auth.jwt.JWTAuthOptions
import io.vertx.ext.jwt.JWTOptions
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.auth.jwt.JWTAuth
import io.vertx.reactivex.ext.web.RoutingContext
import io.vertx.reactivex.ext.web.handler.JWTAuthHandler

class JwtAuthHelper(
  private val vertx: Vertx
): AuthHandlerSupplier {
  private val config = Config.config(vertx).blockingGet()
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

  fun generateToken(json: JsonObject): String {
    return authProvider.generateToken(json, JWTOptions())
  }

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