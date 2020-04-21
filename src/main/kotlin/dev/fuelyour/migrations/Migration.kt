package dev.fuelyour.migrations

import dev.fuelyour.vertxkuickstartcore.config.config
import io.vertx.core.Vertx
import dev.fuelyour.vertxkuickstartcore.migrations.migrate

fun main() {
  // Create the Flyway instance and point it to the database
  val vertx = Vertx.vertx()
  val dbConfig = vertx.config()
  migrate(dbConfig)
  vertx.close()
}
