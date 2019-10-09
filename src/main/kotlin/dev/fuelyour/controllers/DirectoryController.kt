package dev.fuelyour.controllers

import dev.fuelyour.exceptions.AuthorizationException
import dev.fuelyour.models.JwtData
import dev.fuelyour.models.Login
import dev.fuelyour.models.UserRole
import dev.fuelyour.tools.JwtAuthHelper
import dev.fuelyour.tools.Serializer

class DirectoryController(
  private val jwtAuthHelper: JwtAuthHelper,
  private val serializer: Serializer
): Serializer by serializer {

  fun post(body: Login): String = with(body) {
    if (username == "bob" && password == "secret") {
      return jwtAuthHelper.generateToken(JwtData(
        roles = listOf(UserRole.ADMIN)
      ).serialize())
    } else {
      throw AuthorizationException()
    }
  }
}