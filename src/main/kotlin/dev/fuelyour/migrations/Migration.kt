package dev.fuelyour.migrations

import dev.fuelyour.config.config
import io.vertx.core.Vertx

fun main() {
  // Create the Flyway instance and point it to the database
  val vertx = Vertx.vertx()
  val dbConfig = vertx.config()
  migrate(dbConfig)
  vertx.close()
}
