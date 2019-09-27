package dev.fuelyour.controllers

import io.vertx.ext.web.client.WebClient
import io.vertx.kotlin.ext.web.client.sendAwait
import kotlinx.coroutines.runBlocking
import org.amshove.kluent.`should be`
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object InventoryControllerTest : Spek({
  setup()
  val webClient: WebClient by memoized()

  group("Inventory Controller Testing") {
    describe("get all inventory objects") {
      context("there is no data") {
        val body = runBlocking {
          val response = webClient.get(8080, "localhost", "/api/inventory").sendAwait()
          response.bodyAsJsonArray()
        }
        it("should return an empty array") {
          body.isEmpty `should be` true
        }
      }
    }
  }
})