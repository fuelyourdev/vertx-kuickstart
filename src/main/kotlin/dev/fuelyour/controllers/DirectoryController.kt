package dev.fuelyour.controllers

import dev.fuelyour.annotations.Body
import dev.fuelyour.exceptions.AuthorizationException
import dev.fuelyour.models.User
import dev.fuelyour.tools.JWTHelper
import dev.fuelyour.tools.RequestHelper
import io.vertx.kotlin.core.json.array
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj

class DirectoryController(
  private val jwtHelper: JWTHelper
) {

  fun post(@Body("username") username: String, @Body("password") password: String): String {
    if (username == "bob" && password == "secret") {
      return jwtHelper.generateToken(json {
        obj("roles" to array(User.Role.ADMIN))
      })
    } else {
      throw AuthorizationException()
    }
  }
}