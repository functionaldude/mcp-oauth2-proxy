package com.functionaldude.mcp_oauth2_proxy.proxy

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI

@ConfigurationProperties(prefix = "mcp.proxy")
data class McpProxyProperties(
  val upstreamUrl: String = "",
  val exposeErrorDetails: Boolean = false,
) {
  val upstreamUri: URI
    get() {
      val uri = upstreamUrl.trim()
        .ifBlank { throw ProxyConfigurationException("mcp.proxy.upstream-url / MCP_UPSTREAM_URL must not be blank") }
        .let {
          try {
            URI.create(it)
          } catch (ex: IllegalArgumentException) {
            throw ProxyConfigurationException("mcp.proxy.upstream-url / MCP_UPSTREAM_URL is not a valid URI", ex)
          }
        }

      if (uri.scheme !in setOf("http", "https")) {
        throw ProxyConfigurationException("mcp.proxy.upstream-url / MCP_UPSTREAM_URL must use http or https")
      }
      if (uri.host.isNullOrBlank()) {
        throw ProxyConfigurationException("mcp.proxy.upstream-url / MCP_UPSTREAM_URL must include a host")
      }
      if (uri.fragment != null) {
        throw ProxyConfigurationException("mcp.proxy.upstream-url / MCP_UPSTREAM_URL must not include a fragment")
      }

      return uri
    }
}

class ProxyConfigurationException(
  message: String,
  cause: Throwable? = null,
) : RuntimeException(message, cause)
