package com.functionaldude.mcp_oauth2_proxy.proxy

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.util.UriComponentsBuilder
import java.io.IOException
import java.net.ConnectException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.*

@RestController
class McpProxyController(
  private val properties: McpProxyProperties,
  private val httpClient: HttpClient,
) {
  private val logger = LoggerFactory.getLogger(javaClass)

  @RequestMapping(path = ["/mcp", "/mcp/**"])
  fun proxy(request: HttpServletRequest, response: HttpServletResponse) {
    val requestId = request.getHeader("X-Request-Id")?.takeIf(String::isNotBlank)
      ?: UUID.randomUUID().toString()
    response.setHeader("X-Request-Id", requestId)

    try {
      val targetUri = targetUri(request)
      logger.debug(
        "Proxying MCP request id={} method={} path={} target={}",
        requestId,
        request.method,
        request.requestURI,
        targetUri,
      )

      val upstreamRequest = HttpRequest.newBuilder(targetUri)
        .method(request.method, bodyPublisher(request))
        .apply {
          request.headerNames.asSequence()
            .filter(ProxyHeaders::isForwardableRequestHeader)
            .forEach { headerName ->
              request.getHeaders(headerName).asSequence().forEach { headerValue ->
                header(headerName, headerValue)
              }
            }
        }
        .build()

      val upstreamResponse = httpClient.send(upstreamRequest, HttpResponse.BodyHandlers.ofInputStream())
      if (upstreamResponse.statusCode() >= 400) {
        logger.warn(
          "Upstream MCP server returned error id={} status={} method={} target={}",
          requestId,
          upstreamResponse.statusCode(),
          request.method,
          targetUri,
        )
      }
      copyResponse(upstreamResponse, response, requestId)
    } catch (ex: InterruptedException) {
      Thread.currentThread().interrupt()
      logger.warn("Interrupted while proxying MCP request id={}", requestId, ex)
      sendProxyError(
        response = response,
        status = HttpServletResponse.SC_BAD_GATEWAY,
        error = "upstream_interrupted",
        message = "Interrupted while waiting for the upstream MCP server",
        requestId = requestId,
        cause = ex,
      )
    } catch (ex: ProxyConfigurationException) {
      logger.error("Invalid MCP proxy configuration id={}: {}", requestId, ex.message, ex)
      sendProxyError(
        response = response,
        status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
        error = "proxy_configuration_error",
        message = "MCP proxy is not configured correctly",
        requestId = requestId,
        cause = ex,
      )
    } catch (ex: ConnectException) {
      logger.warn("Could not connect to upstream MCP server id={}: {}", requestId, ex.message)
      sendProxyError(
        response = response,
        status = HttpServletResponse.SC_BAD_GATEWAY,
        error = "upstream_unavailable",
        message = "Could not connect to the upstream MCP server",
        requestId = requestId,
        cause = ex,
      )
    } catch (ex: IOException) {
      logger.warn("I/O error while proxying MCP request id={}: {}", requestId, ex.message, ex)
      sendProxyError(
        response = response,
        status = HttpServletResponse.SC_BAD_GATEWAY,
        error = "upstream_io_error",
        message = "I/O error while communicating with the upstream MCP server",
        requestId = requestId,
        cause = ex,
      )
    } catch (ex: IllegalArgumentException) {
      logger.error("Invalid MCP proxy request id={}: {}", requestId, ex.message, ex)
      sendProxyError(
        response = response,
        status = HttpServletResponse.SC_BAD_GATEWAY,
        error = "proxy_request_error",
        message = "Could not build the upstream MCP request",
        requestId = requestId,
        cause = ex,
      )
    }
  }

  private fun bodyPublisher(request: HttpServletRequest): HttpRequest.BodyPublisher {
    val hasBody = request.contentLengthLong > 0 || request.getHeader("Transfer-Encoding") != null
    return if (hasBody) {
      HttpRequest.BodyPublishers.ofInputStream { request.inputStream }
    } else {
      HttpRequest.BodyPublishers.noBody()
    }
  }

  private fun targetUri(request: HttpServletRequest): URI {
    val upstreamUri = properties.upstreamUri
    return UriComponentsBuilder
      .fromUri(upstreamUri)
      .replacePath(targetPath(request, upstreamUri))
      .replaceQuery(targetQuery(request, upstreamUri))
      .build(true)
      .toUri()
  }

  private fun targetPath(
    request: HttpServletRequest,
    upstreamUri: URI,
  ): String {
    val upstreamPath = upstreamUri.rawPath.ifBlank { "/" }
    val requestPath = request.requestURI.removePrefix(request.contextPath)
    val suffix = requestPath.removePrefix("/mcp")

    return if (suffix.isBlank()) {
      upstreamPath
    } else {
      upstreamPath.trimEnd('/') + suffix
    }
  }

  private fun targetQuery(
    request: HttpServletRequest,
    upstreamUri: URI,
  ): String? {
    val upstreamQuery = upstreamUri.rawQuery
    val requestQuery = request.queryString

    return listOfNotNull(upstreamQuery, requestQuery)
      .filter(String::isNotBlank)
      .joinToString("&")
      .ifBlank { null }
  }

  private fun copyResponse(
    upstreamResponse: HttpResponse<java.io.InputStream>,
    response: HttpServletResponse,
    requestId: String,
  ) {
    response.status = upstreamResponse.statusCode()
    response.setHeader("X-Request-Id", requestId)
    response.setHeader("X-Mcp-Proxy-Upstream-Status", upstreamResponse.statusCode().toString())
    upstreamResponse.headers().map()
      .filterKeys(ProxyHeaders::isForwardableResponseHeader)
      .forEach { (headerName, headerValues) ->
        headerValues.forEach { headerValue -> response.addHeader(headerName, headerValue) }
      }

    upstreamResponse.body().use { body ->
      body.copyTo(response.outputStream)
    }
    response.flushBuffer()
  }

  private fun sendProxyError(
    response: HttpServletResponse,
    status: Int,
    error: String,
    message: String,
    requestId: String,
    cause: Throwable,
  ) {
    if (!response.isCommitted) {
      response.reset()
      response.status = status
      response.contentType = MediaType.APPLICATION_JSON_VALUE
      response.characterEncoding = Charsets.UTF_8.name()
      response.setHeader("X-Request-Id", requestId)
      response.setHeader("Cache-Control", "no-store")
      response.writer.write(errorBody(error, message, requestId, cause))
    }
  }

  private fun errorBody(
    error: String,
    message: String,
    requestId: String,
    cause: Throwable,
  ): String {
    val details = if (properties.exposeErrorDetails) {
      ""","details":"${jsonEscape(cause.message ?: cause.javaClass.simpleName)}""""
    } else {
      ""
    }

    return """{"error":"$error","message":"${jsonEscape(message)}","requestId":"$requestId"$details}"""
  }

  private fun jsonEscape(value: String): String {
    return value
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")
  }
}
