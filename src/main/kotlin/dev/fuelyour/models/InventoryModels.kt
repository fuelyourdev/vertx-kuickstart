package dev.fuelyour.models

import dev.fuelyour.tools.Field

data class ManufacturerPost(
  val name: String,
  val homePage: String?,
  val phone: String?
)

data class InventoryPost(
  val name: String,
  val releaseDate: String,
  val manufacturer: Manufacturer,
  val count: Int?
)

data class ManufacturerPatch(
  val name: Field<String?>,
  val homePage: Field<String?>,
  val phone: Field<String?>
)

data class InventoryPatch(
  val name: Field<String?>,
  val releaseDate: Field<String?>,
  val manufacturer: Field<Manufacturer?>,
  val count: Field<Int?>
)

data class Manufacturer(
  val name: String,
  val homePage: String?,
  val phone: String?
)

data class Inventory(
  val id: String,
  val name: String,
  val releaseDate: String,
  val manufacturer: Manufacturer,
  val count: Int?
)
