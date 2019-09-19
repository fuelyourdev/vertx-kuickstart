package dev.fuelyour.migrations

import dev.fuelyour.config.Config
import io.vertx.reactivex.core.Vertx
import org.flywaydb.core.Flyway

fun main() {
  // Create the Flyway instance and point it to the database
  val vertx = Vertx.vertx()
  Config.config(vertx).map { dbConfig ->
    val host = dbConfig.getString("SERVICE_DB_HOST")
    val port = dbConfig.getString("SERVICE_DB_PORT")
    val name = dbConfig.getString("SERVICE_DB_NAME")
    val url = "jdbc:postgresql://$host:$port/$name"
    val user = dbConfig.getString("SERVICE_DB_USER")
    val password = dbConfig.getString("SERVICE_DB_PASSWORD")
    listOf("public", "test").forEach {
      println("schema: $it")
      val flyway = Flyway
        .configure()
        .placeholders(mutableMapOf("schema" to it))
        .schemas(it)
        .dataSource(url, user, password)
        .load()
      flyway.migrate()
    }

  }.doAfterTerminate { vertx.rxClose().subscribe() }
    .subscribe(
      { println("Successfully completed migration") },
      { println(it.printStackTrace()) }
    )
}
