package dev.fuelyour.annotations

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Body(val key: String = "")