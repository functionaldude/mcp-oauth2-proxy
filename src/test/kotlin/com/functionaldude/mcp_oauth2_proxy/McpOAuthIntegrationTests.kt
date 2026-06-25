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
    "app.public-url=https://proxy.example.test",
    "app.auth.issuer-uri=https://idp.example.test/application/o/mcp/",
    "app.auth.audience=https://proxy.example.test/mcp",
    "app.auth.scopes=openid,mcp_proxy",
    "mcp.proxy.upstream-url=http://upstream.example.test/mcp",
  ]
)
@AutoConfigureMockMvc
class McpOAuthIntegrationTests(
  @Autowired private val mockMvc: MockMvc,
) {

  @Test
  fun `oauth protected resource metadata advertises oidc issuer for mcp`() {
    val response = mockMvc.perform(get("/.well-known/oauth-protected-resource/mcp").proxyOrigin())
      .andExpect(status().isOk)
      .andReturn()
      .response

    assertThat(response.contentAsString).contains("\"resource\":\"https://proxy.example.test/mcp\"")
    assertThat(response.contentAsString).contains("\"authorization_servers\":[\"https://idp.example.test/application/o/mcp/\"]")
    assertThat(response.contentAsString).contains("\"scopes_supported\":[\"openid\",\"mcp_proxy\"]")
    assertThat(response.contentAsString).contains("bearer_methods_supported")
    assertThat(response.contentAsString).doesNotContain("tls_client_certificate_bound_access_tokens")
  }

  @Test
  fun `mcp requires authentication`() {
    val response = mockMvc.perform(
      post("/mcp")
        .proxyOrigin()
        .contentType("application/json")
        .accept("application/json", "text/event-stream")
        .content("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""")
    )
      .andExpect(status().isUnauthorized)
      .andReturn()
      .response

    assertThat(response.getHeader("WWW-Authenticate"))
      .contains("""resource_metadata="https://proxy.example.test/.well-known/oauth-protected-resource/mcp"""")
      .contains("""scope="openid mcp_proxy"""")
    assertThat(response.contentAsString).isBlank()
  }

  @Test
  fun `actuator health is public`() {
    mockMvc.perform(get("/actuator/health"))
      .andExpect(status().isOk)
  }

  @Test
  fun `non public endpoints require authentication`() {
    val response = mockMvc.perform(get("/private").proxyOrigin())
      .andExpect(status().isUnauthorized)
      .andReturn()
      .response

    assertThat(response.getHeader("WWW-Authenticate"))
      .contains("""resource_metadata="https://proxy.example.test/.well-known/oauth-protected-resource/mcp"""")
      .contains("""scope="openid mcp_proxy"""")
    assertThat(response.contentAsString).isBlank()
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
