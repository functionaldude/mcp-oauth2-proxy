package com.functionaldude.mcp_oauth2_proxy

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
  properties = [
    "app.public-url=http://upstream.example.test:5080/mcp",
    "app.auth.issuer-uri=https://idp.example.test/application/o/mcp/",
    "app.auth.audience=https://proxy.example.test/mcp",
    "app.auth.scopes=openid,mcp_proxy",
    "mcp.proxy.upstream-url=http://upstream.example.test:5080/mcp",
  ]
)
@AutoConfigureMockMvc
class McpDiscoveryIntegrationTests(
  @Autowired private val mockMvc: MockMvc,
) {

  @Test
  fun `bearer challenge points resource metadata to proxy origin`() {
    val response = mockMvc.perform(
      post("/mcp")
        .proxyOrigin()
        .contentType("application/json")
        .content("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""")
    )
      .andExpect(status().isUnauthorized)
      .andReturn()
      .response

    assertThat(response.getHeader("WWW-Authenticate"))
      .contains("""resource_metadata="https://proxy.example.test/.well-known/oauth-protected-resource/mcp"""")
      .doesNotContain("upstream.example.test")
      .doesNotContain("/mcp/.well-known")
  }

  @Test
  fun `protected resource metadata advertises proxy mcp resource`() {
    val response = mockMvc.perform(
      get("/.well-known/oauth-protected-resource/mcp").proxyOrigin()
    )
      .andExpect(status().isOk)
      .andReturn()
      .response

    assertThat(response.contentAsString).contains("\"resource\":\"https://proxy.example.test/mcp\"")
    assertThat(response.contentAsString).doesNotContain("upstream.example.test")
  }

  private fun MockHttpServletRequestBuilder.proxyOrigin(): MockHttpServletRequestBuilder {
    return this
      .secure(true)
      .header("Host", "proxy.example.test")
      .header("X-Forwarded-Proto", "https")
      .header("X-Forwarded-Host", "proxy.example.test")
      .header("X-Forwarded-Port", "443")
  }
}
