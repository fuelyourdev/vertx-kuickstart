package dev.fuelyour.tools

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.parser.OpenAPIV3Parser
import io.vertx.ext.web.api.contract.openapi3.impl.OpenApi3Utils
import org.apache.commons.collections4.ListUtils
import org.reflections.Reflections
import org.reflections.scanners.ResourcesScanner

object SwaggerMerger {

  fun mergeAllInDirectory(path: String): OpenAPI? =
    findSwaggerFilesInPath(path)
      .map { loadSwagger("/$it") }
      .fold(null as OpenAPI?) {
          s1, s2 -> s1?.let { it.merge(s2) } ?: s2
      }

  private fun findSwaggerFilesInPath(path: String): Set<String> =
    Reflections(path, ResourcesScanner())
      .getResources { it?.endsWith(".yaml") ?: false }

  private fun loadSwagger(filename: String): OpenAPI =
    OpenAPIV3Parser()
      .readLocation(filename, null, OpenApi3Utils.getParseOptions())
      .openAPI

  private fun OpenAPI.merge(new: OpenAPI): OpenAPI {
    servers = combineLists(servers, new.servers)
    security = combineLists(security, new.security)
    tags = combineLists(tags, new.tags)
    new.paths?.forEach { it -> paths.addPathItem(it.key, it.value) }
    extensions = combineMaps(extensions, new.extensions)
    components.merge(new.components)
    if (info == null)
      info = new.info
    return this
  }

  private fun Components.merge(other: Components) {
    schemas = combineMaps(schemas, other.schemas)
    responses = combineMaps(responses, other.responses)
    parameters = combineMaps(parameters, other.parameters)
    examples = combineMaps(examples, other.examples)
    requestBodies = combineMaps(requestBodies, other.requestBodies)
    headers = combineMaps(headers, other.headers)
    securitySchemes = combineMaps(securitySchemes, other.securitySchemes)
    links = combineMaps(links, other.links)
    callbacks = combineMaps(callbacks, other.callbacks)
    extensions = combineMaps(extensions, other.extensions)
  }

  private fun <T> combineLists(list1: List<T>?, list2: List<T>?): List<T>? {
    val combined = ListUtils.union(
      list1 ?: listOf<T>(),
      list2 ?: listOf<T>())
    return if (combined.isEmpty()) null else combined
  }

  private fun <T, R> combineMaps(
    map1: MutableMap<T, R>?,
    map2: MutableMap<T, R>?
  ): Map<T, R>? {
    return when {
      map1 == null -> map2
      map2 == null -> map1
      else -> {
        val combined = linkedMapOf<T, R>()
        combined.putAll(map1)
        combined.putAll(map2)
        combined
      }
    }
  }
}