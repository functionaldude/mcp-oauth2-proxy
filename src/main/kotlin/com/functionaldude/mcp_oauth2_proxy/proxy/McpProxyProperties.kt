package com.functionaldude.mcp_oauth2_proxy.proxy

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI

@ConfigurationProperties(prefix = "mcp.proxy")
data class McpProxyProperties(
  val upstreamUrl: String = "",
) {
  val upstreamUri: URI
    get() = upstreamUrl.trim()
      .ifBlank { throw IllegalStateException("mcp.proxy.upstream-url / MCP_UPSTREAM_URL must not be blank") }
      .let(URI::create)
}
