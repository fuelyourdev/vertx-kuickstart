package dev.fuelyour.repositories

import dev.fuelyour.controllers.Inventory
import dev.fuelyour.controllers.InventoryPost

private const val table = "inventories"

class InventoryRepo(schema: String):
    AllQuery<Inventory> by AllQuery.impl(schema, table),
    FindQuery<Inventory> by FindQuery.impl(schema, table),
    InsertQuery<InventoryPost, Inventory> by InsertQuery.impl(schema, table),
    UpdateQuery<Inventory, Inventory> by UpdateQuery.impl(schema, table),
    DeleteQuery by DeleteQuery.impl(schema, table)
