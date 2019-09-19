package dev.fuelyour.verticles

import dev.fuelyour.config.Config
import dev.fuelyour.repositories.Repository
import io.reactivex.Completable
import io.reactivex.Single
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.pgclient.pgConnectOptionsOf
import io.vertx.kotlin.sqlclient.poolOptionsOf
import io.vertx.reactivex.core.AbstractVerticle
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.pgclient.PgPool
import org.reflections.Reflections
import java.lang.reflect.Parameter


class DatabaseVerticle : AbstractVerticle() {

  lateinit var resources: Map<String, Any>
  lateinit var dbName: String

  override fun rxStart(): Completable = configureConnections(vertx).doOnSuccess { pgPool ->
    resources = modelResources(pgPool)
    vertx.eventBus().consumer<JsonObject>("db.$dbName") { message ->
      val headers = message.headers()
      val resource = headers["resource"]
      val action = headers["action"]
      val body = message.body()
      val resourceObj = resources[resource]
      if (resourceObj != null) {
        val method = resourceObj.javaClass.methods.filter { it.name.toLowerCase() == action.toLowerCase() }.first()
        (method.invoke(resourceObj, *params(body, method.parameters)) as Single<*>).subscribe(
          { message.reply(it) },
          { message.reply("error") })
      } else {
        throw IllegalArgumentException("invalid action: $action, on resource: $resource")
      }
    }
  }.ignoreElement()

  private fun modelResources(pool: PgPool) =
    Reflections(this.javaClass.`package`.name.substringBeforeLast('.')).getSubTypesOf(Repository::class.java).map {
      it.simpleName.toLowerCase().removeSuffix("repo").removeSuffix("repository") to it.constructors.first().newInstance(
        pool
      )
    }.toMap()

  private fun configureConnections(vertx: Vertx): Single<PgPool> = Config.config(vertx).map { dbConfig ->
    dbName = dbConfig.getString("SERVICE_DB_NAME")
    val connectOptions = pgConnectOptionsOf(
      port = dbConfig.getInteger("SERVICE_DB_PORT"),
      host = dbConfig.getString("SERVICE_DB_HOST"),
      database = dbName,
      user = dbConfig.getString("SERVICE_DB_USER"),
      password = dbConfig.getString("SERVICE_DB_PASSWORD"),
      properties = mapOf("search_path" to config().getString("schema", "public"))
    )
    val poolOptions = poolOptionsOf(maxSize = 10)
    val pool = PgPool.pool(vertx, connectOptions, poolOptions)
    pool
  }

  private fun params(body: JsonObject, params: Array<Parameter>) =
    params.fold(listOf<Any>()) { acc, param ->
      acc + body.getValue(param.name)
    }.toTypedArray()
}