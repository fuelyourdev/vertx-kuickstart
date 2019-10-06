package dev.fuelyour.repositories

import dev.fuelyour.controllers.Inventory
import dev.fuelyour.controllers.InventoryPost

class InventoryRepo(schema: String):
    AllQuery<Inventory> by AllQuery.impl(schema, "inventories"),
    FindQuery<Inventory> by FindQuery.impl(schema, "inventories"),
    InsertQuery<InventoryPost, Inventory> by InsertQuery.impl(schema, "inventories"),
    UpdateQuery<Inventory, Inventory> by UpdateQuery.impl(schema, "inventories"),
    DeleteQuery by DeleteQuery.impl(schema, "inventories")
