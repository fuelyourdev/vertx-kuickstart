package dev.fuelyour.controllers

import dev.fuelyour.models.Inventory
import dev.fuelyour.models.InventoryPatch
import dev.fuelyour.models.InventoryPost
import dev.fuelyour.repositories.InventoryRepo
import dev.fuelyour.vertxkuickstartcore.tools.DatabaseAccess
import dev.fuelyour.vertxkuickstartcore.tools.applyPatch

class InventoryController(
    private val da: DatabaseAccess,
    private val inventoryRepo: InventoryRepo
) {

    suspend fun get(id: String): Inventory {
        return da.getConnection { conn -> inventoryRepo.find(id, conn) }
    }

    suspend fun getAll(): List<Inventory> {
        return da.getConnection { conn -> inventoryRepo.all(conn) }
    }

    suspend fun post(inventory: InventoryPost): Inventory {
        return da.getConnection {
                conn -> inventoryRepo.insert(inventory, conn)
        }
    }

    suspend fun patch(id: String, patch: InventoryPatch): Inventory {
        return da.getTransaction { conn ->
            val fromDb = inventoryRepo.find(id, conn)
            val result = fromDb.applyPatch(patch)
            inventoryRepo.update(id, result, conn)
        }
    }

    suspend fun delete(id: String) {
        da.getConnection { conn -> inventoryRepo.delete(id, conn) }
    }
}
