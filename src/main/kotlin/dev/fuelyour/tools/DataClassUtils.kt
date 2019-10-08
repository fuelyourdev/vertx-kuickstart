package dev.fuelyour.tools

import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.declaredMemberProperties

@Suppress("UNCHECKED_CAST")
fun <T:Any> T.applyPatch(patch: Any): T {
  if (this::class.isData) {
    val copy = this::class.declaredFunctions.first { it.name == "copy" }
    val args = patch::class.declaredMemberProperties
      .filter { copy.parameters.any { param -> param.name == it.name } }
      .map {
        copy.parameters.first { param -> param.name == it.name } to
            (it as KProperty1<Any, Any?>).get(patch)
      }
      .filter { with(it.second) {
        (this is Field<*> && present) || (this !is Field<*> && this != null)
      } }
      .map { it.first to with(it.second) {
        if (this is Field<*>) value else this
      } }
      .toMap() + mapOf(copy.parameters[0] to this)
    return copy.callBy(args) as T
  } else {
    throw Exception("Must be a data class")
  }
}
