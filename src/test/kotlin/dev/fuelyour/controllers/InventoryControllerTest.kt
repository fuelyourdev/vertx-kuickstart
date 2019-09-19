package dev.fuelyour.controllers

import dev.fuelyour.verticles.DatabaseVerticle
import dev.fuelyour.verticles.HttpVerticle
import io.reactivex.Observable
import io.vertx.kotlin.core.deploymentOptionsOf
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.reactivex.core.RxHelper
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.web.client.WebClient
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.concurrent.TimeUnit


object InventoryControllerTest : Spek({
  setup()
  val vertx: Vertx by memoized()
  val deployIds: MutableList<String> by memoized()
  val webClient: WebClient by memoized()

  beforeGroup {
    RxHelper.deployVerticle(
      vertx,
      DatabaseVerticle(),
      deploymentOptionsOf(worker = true, config = json { obj("schema" to "test") })
    ).flatMap {
      deployIds.add(it)
      RxHelper.deployVerticle(vertx, HttpVerticle()).map {
        deployIds.add(it)
      }
    }.blockingGet()
  }

  afterGroup {
    Observable.fromIterable(deployIds).forEach {
      vertx.undeploy(it)
    }
    vertx.close()
  }
  group("Inventory Controller Testing") {
    describe("get all inventory objects") {
      context("there is no data") {
        it("should return an empty array") {
          webClient.get(8080, "localhost", "/api/inventory").rxSend().map {
            it.bodyAsJsonArray()
          }.test()
            .assertEmpty()
            .await(1, TimeUnit.SECONDS)
        }
      }
    }
  }
})