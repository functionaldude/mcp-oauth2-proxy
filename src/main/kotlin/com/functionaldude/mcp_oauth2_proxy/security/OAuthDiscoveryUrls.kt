package com.functionaldude.mcp_oauth2_proxy.security

import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.util.UriComponentsBuilder

object OAuthDiscoveryUrls {
  const val MCP_RESOURCE_PATH = "/mcp"
  const val PROTECTED_RESOURCE_METADATA_PATH = "/.well-known/oauth-protected-resource/mcp"

  fun currentMcpResource(appProperties: AppProperties): String {
    return currentRequest()?.let(::mcpResource) ?: appProperties.mcpResource
  }

  fun currentProtectedResourceMetadata(appProperties: AppProperties): String {
    return currentRequest()?.let(::protectedResourceMetadata) ?: appProperties.mcpProtectedResourceMetadataUrl
  }

  fun mcpResource(request: HttpServletRequest): String {
    return externalUrl(request, MCP_RESOURCE_PATH)
  }

  fun protectedResourceMetadata(request: HttpServletRequest): String {
    return externalUrl(request, PROTECTED_RESOURCE_METADATA_PATH)
  }

  private fun currentRequest(): HttpServletRequest? {
    return (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
  }

  private fun externalUrl(
    request: HttpServletRequest,
    path: String,
  ): String {
    val origin = origin(request)
    return UriComponentsBuilder.newInstance()
      .scheme(origin.scheme)
      .host(origin.host)
      .port(origin.normalizedPort)
      .replacePath(request.contextPath + path)
      .replaceQuery(null)
      .fragment(null)
      .build()
      .toUriString()
  }

  private fun origin(request: HttpServletRequest): Origin {
    val forwarded = forwardedValues(request)
    val scheme = firstHeaderValue(request, "X-Forwarded-Proto")
      ?: forwarded["proto"]
      ?: request.scheme
    val forwardedHost = firstHeaderValue(request, "X-Forwarded-Host")
      ?: forwarded["host"]
    val hostAndPort = parseHostAndPort(forwardedHost)
      ?: HostAndPort(request.serverName, request.serverPort)
    val port = firstHeaderValue(request, "X-Forwarded-Port")?.toIntOrNull()
      ?: hostAndPort.port
      ?: request.serverPort

    return Origin(scheme = scheme, host = hostAndPort.host, port = port)
  }

  private fun firstHeaderValue(
    request: HttpServletRequest,
    name: String,
  ): String? {
    return request.getHeader(name)
      ?.split(",")
      ?.firstOrNull()
      ?.trim()
      ?.takeIf(String::isNotBlank)
  }

  private fun forwardedValues(request: HttpServletRequest): Map<String, String> {
    val forwarded = firstHeaderValue(request, "Forwarded") ?: return emptyMap()
    return forwarded
      .split(";")
      .mapNotNull { segment ->
        val parts = segment.split("=", limit = 2)
        if (parts.size != 2) {
          null
        } else {
          parts[0].trim().lowercase() to parts[1].trim().trim('"')
        }
      }
      .toMap()
  }

  private fun parseHostAndPort(value: String?): HostAndPort? {
    val hostPort = value?.trim()?.trim('"')?.takeIf(String::isNotBlank) ?: return null
    if (hostPort.startsWith("[")) {
      val closingBracket = hostPort.indexOf(']')
      if (closingBracket < 0) {
        return HostAndPort(hostPort, null)
      }
      val host = hostPort.substring(1, closingBracket)
      val port = hostPort.substring(closingBracket + 1).removePrefix(":").toIntOrNull()
      return HostAndPort(host, port)
    }

    val colonCount = hostPort.count { it == ':' }
    if (colonCount == 1) {
      val host = hostPort.substringBeforeLast(':')
      val port = hostPort.substringAfterLast(':').toIntOrNull()
      return HostAndPort(host, port)
    }

    return HostAndPort(hostPort, null)
  }

  private data class HostAndPort(
    val host: String,
    val port: Int?,
  )

  private data class Origin(
    val scheme: String,
    val host: String,
    val port: Int,
  ) {
    val normalizedPort: Int
      get() = if ((scheme == "http" && port == 80) || (scheme == "https" && port == 443)) {
        -1
      } else {
        port
      }
  }
}
