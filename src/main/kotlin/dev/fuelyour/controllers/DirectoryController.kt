package dev.fuelyour.controllers

import dev.fuelyour.exceptions.AuthorizationException
import dev.fuelyour.models.User
import dev.fuelyour.tools.JwtAuthHelper
import io.vertx.kotlin.core.json.array
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj

data class Login(
  val username: String,
  val password: String
)

class DirectoryController(
  private val jwtAuthHelper: JwtAuthHelper
) {

  fun post(body: Login): String = with(body) {
    if (username == "bob" && password == "secret") {
      return jwtAuthHelper.generateToken(json {
        obj("roles" to array(User.Role.ADMIN))
      })
    } else {
      throw AuthorizationException()
    }
  }
}