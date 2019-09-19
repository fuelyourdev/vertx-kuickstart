package dev.fuelyour.repositories

import io.vertx.reactivex.pgclient.PgPool

class InventoryRepo(pool: PgPool) : Repository(pool, "inventories") {

}