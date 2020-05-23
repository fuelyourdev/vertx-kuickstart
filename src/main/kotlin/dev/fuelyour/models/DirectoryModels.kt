package dev.fuelyour.models

data class Login(
    val username: String,
    val password: String
)

data class JwtData(
    val roles: List<UserRole>
)
