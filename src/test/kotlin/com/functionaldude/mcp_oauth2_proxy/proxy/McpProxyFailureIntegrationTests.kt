package com.functionaldude.mcp_oauth2_proxy.proxy

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.net.ServerSocket

@SpringBootTest(
  properties = [
    "app.public-url=https://proxy.example.test",
    "app.auth.issuer-uri=https://idp.example.test/application/o/mcp/",
    "app.auth.audience=https://proxy.example.test/mcp",
    "app.auth.scopes=openid,mcp_proxy",
  ]
)
@AutoConfigureMockMvc
class McpProxyFailureIntegrationTests(
  @Autowired private val mockMvc: MockMvc,
) {

  @Test
  fun `upstream connection failure returns bad gateway`() {
    val response = mockMvc.perform(
      post("/mcp")
        .contentType("application/json")
        .with(jwt())
        .content("""{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}""")
    )
      .andExpect(status().isBadGateway)
      .andReturn()
      .response

    assertThat(response.getHeader("X-Request-Id")).isNotBlank()
    assertThat(response.contentType).contains("application/json")
    assertThat(response.contentAsString).contains(""""error":"upstream_""")
    assertThat(response.contentAsString).contains(""""requestId":"""")
  }

  companion object {
    private val unusedPort = ServerSocket(0).use { it.localPort }

    @JvmStatic
    @DynamicPropertySource
    fun registerProperties(registry: DynamicPropertyRegistry) {
      registry.add("mcp.proxy.upstream-url") { "http://127.0.0.1:$unusedPort/mcp" }
    }
  }
}
