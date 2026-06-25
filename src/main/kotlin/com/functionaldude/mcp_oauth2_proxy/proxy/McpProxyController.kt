package com.functionaldude.mcp_oauth2_proxy.proxy

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.util.UriComponentsBuilder
import java.io.IOException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@RestController
class McpProxyController(
  private val properties: McpProxyProperties,
  private val httpClient: HttpClient,
) {

  @RequestMapping(path = ["/mcp", "/mcp/**"])
  fun proxy(request: HttpServletRequest, response: HttpServletResponse) {
    try {
      val upstreamRequest = HttpRequest.newBuilder(targetUri(request))
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
      copyResponse(upstreamResponse, response)
    } catch (ex: InterruptedException) {
      Thread.currentThread().interrupt()
      sendBadGateway(response)
    } catch (ex: IOException) {
      sendBadGateway(response)
    } catch (ex: IllegalArgumentException) {
      sendBadGateway(response)
    } catch (ex: IllegalStateException) {
      sendBadGateway(response)
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

  private fun targetUri(request: HttpServletRequest) = UriComponentsBuilder
    .fromUri(properties.upstreamUri)
    .replacePath(targetPath(request))
    .replaceQuery(targetQuery(request))
    .build(true)
    .toUri()

  private fun targetPath(request: HttpServletRequest): String {
    val upstreamPath = properties.upstreamUri.rawPath.ifBlank { "/" }
    val requestPath = request.requestURI.removePrefix(request.contextPath)
    val suffix = requestPath.removePrefix("/mcp")

    return if (suffix.isBlank()) {
      upstreamPath
    } else {
      upstreamPath.trimEnd('/') + suffix
    }
  }

  private fun targetQuery(request: HttpServletRequest): String? {
    val upstreamQuery = properties.upstreamUri.rawQuery
    val requestQuery = request.queryString

    return listOfNotNull(upstreamQuery, requestQuery)
      .filter(String::isNotBlank)
      .joinToString("&")
      .ifBlank { null }
  }

  private fun copyResponse(
    upstreamResponse: HttpResponse<java.io.InputStream>,
    response: HttpServletResponse,
  ) {
    response.status = upstreamResponse.statusCode()
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

  private fun sendBadGateway(response: HttpServletResponse) {
    if (!response.isCommitted) {
      response.reset()
      response.status = HttpServletResponse.SC_BAD_GATEWAY
    }
  }
}
