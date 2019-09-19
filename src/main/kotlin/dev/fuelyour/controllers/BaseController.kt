package dev.fuelyour.controllers

import dev.fuelyour.tools.RequestHelper
import org.koin.core.KoinComponent
import org.koin.core.inject

open class BaseController : KoinComponent {
  protected val requestHelper: RequestHelper by inject()
}