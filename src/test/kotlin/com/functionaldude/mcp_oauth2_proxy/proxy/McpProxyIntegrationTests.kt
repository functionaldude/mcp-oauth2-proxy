package com.functionaldude.mcp_oauth2_proxy.proxy

import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.net.InetAddress
import java.net.InetSocketAddress
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue

@SpringBootTest(
  properties = [
    "app.public-url=https://proxy.example.test",
    "app.auth.issuer-uri=https://idp.example.test/application/o/mcp/",
    "app.auth.audience=https://proxy.example.test/mcp",
    "app.auth.scopes=openid,mcp_proxy",
  ]
)
@AutoConfigureMockMvc
class McpProxyIntegrationTests(
  @Autowired private val mockMvc: MockMvc,
) {

  @BeforeEach
  fun clearRequests() {
    requests.clear()
  }

  @Test
  fun `authenticated mcp request is proxied without inbound authorization`() {
    val requestBody = """{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}"""

    val response = mockMvc.perform(
      post("/mcp?cursor=abc")
        .header("Authorization", "Bearer should-not-leak")
        .header("Cookie", "session=private")
        .header("Mcp-Session-Id", "client-session")
        .contentType("application/json")
        .accept("application/json", "text/event-stream")
        .content(requestBody)
    )
      .andExpect(status().isCreated)
      .andReturn()
      .response

    assertThat(response.contentAsString).isEqualTo("""{"proxied":true}""")
    assertThat(response.getHeader("Content-Type")).contains("application/json")
    assertThat(response.getHeader("Mcp-Session-Id")).isEqualTo("upstream-session")
    assertThat(response.getHeader("Set-Cookie")).isNull()

    val recorded = requests.single()
    assertThat(recorded.method).isEqualTo("POST")
    assertThat(recorded.path).isEqualTo("/upstream/mcp")
    assertThat(recorded.query).isEqualTo("cursor=abc")
    assertThat(recorded.body).isEqualTo(requestBody)
    assertThat(recorded.header("Authorization")).isNull()
    assertThat(recorded.header("Cookie")).isNull()
    assertThat(recorded.header("Mcp-Session-Id")).isEqualTo("client-session")
    assertThat(recorded.header("Content-Type")).contains("application/json")
    assertThat(recorded.header("Accept")).contains("application/json")
    assertThat(recorded.header("Accept")).contains("text/event-stream")
  }

  @Test
  fun `mcp subpaths are appended to upstream endpoint`() {
    mockMvc.perform(
      post("/mcp/messages?stream=true")
        .header("Authorization", "Bearer accepted-test-token")
        .header("Mcp-Session-Id", "client-session")
        .contentType("application/json")
        .content("""{"jsonrpc":"2.0","method":"notifications/initialized"}""")
    )
      .andExpect(status().isCreated)

    val recorded = requests.single()
    assertThat(recorded.path).isEqualTo("/upstream/mcp/messages")
    assertThat(recorded.query).isEqualTo("stream=true")
  }

  companion object {
    private val requests = ConcurrentLinkedQueue<RecordedRequest>()
    private val server: HttpServer = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
      createContext("/upstream/mcp") { exchange ->
        val body = exchange.requestBody.bufferedReader().use { it.readText() }
        requests.add(
          RecordedRequest(
            method = exchange.requestMethod,
            path = exchange.requestURI.path,
            query = exchange.requestURI.query,
            headers = exchange.requestHeaders.mapValues { it.value.toList() },
            body = body,
          )
        )

        val responseBody = """{"proxied":true}""".toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.responseHeaders.add("Mcp-Session-Id", "upstream-session")
        exchange.responseHeaders.add("Set-Cookie", "internal=secret")
        exchange.sendResponseHeaders(201, responseBody.size.toLong())
        exchange.responseBody.use { it.write(responseBody) }
      }
      start()
    }

    @JvmStatic
    @DynamicPropertySource
    fun registerProperties(registry: DynamicPropertyRegistry) {
      registry.add("mcp.proxy.upstream-url") {
        "http://${server.address.hostString}:${server.address.port}/upstream/mcp"
      }
    }

    @JvmStatic
    @AfterAll
    fun stopServer() {
      server.stop(0)
    }
  }

  @TestConfiguration
  class TestJwtDecoderConfig {
    @Bean
    @Primary
    fun testJwtDecoder(): JwtDecoder = JwtDecoder { token ->
      val now = Instant.now()
      Jwt.withTokenValue(token)
        .header("alg", "none")
        .subject("mcp-user")
        .audience(listOf("https://proxy.example.test/mcp"))
        .issuedAt(now)
        .expiresAt(now.plusSeconds(300))
        .build()
    }
  }

  private data class RecordedRequest(
    val method: String,
    val path: String,
    val query: String?,
    val headers: Map<String, List<String>>,
    val body: String,
  ) {
    fun header(name: String): String? {
      return headers.entries
        .firstOrNull { it.key.equals(name, ignoreCase = true) }
        ?.value
        ?.joinToString(",")
    }
  }
}
