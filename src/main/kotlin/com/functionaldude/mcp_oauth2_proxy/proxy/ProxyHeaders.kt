package com.functionaldude.mcp_oauth2_proxy.proxy

object ProxyHeaders {
  private val hopByHopHeaders = setOf(
    "connection",
    "keep-alive",
    "proxy-authenticate",
    "proxy-authorization",
    "te",
    "trailer",
    "transfer-encoding",
    "upgrade",
  )

  private val blockedRequestHeaders = hopByHopHeaders + setOf(
    "authorization",
    "content-length",
    "cookie",
    "host",
  )

  private val blockedResponseHeaders = hopByHopHeaders + setOf(
    "content-length",
    "set-cookie",
  )

  fun isForwardableRequestHeader(name: String): Boolean {
    val normalized = name.lowercase()
    return !normalized.startsWith(":") && normalized !in blockedRequestHeaders
  }

  fun isForwardableResponseHeader(name: String): Boolean {
    val normalized = name.lowercase()
    return !normalized.startsWith(":") && normalized !in blockedResponseHeaders
  }
}
