package com.functionaldude.mcp_oauth2_proxy.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AppPropertiesTest {

  @Test
  fun `jwk set uri is derived from issuer uri`() {
    val auth = AppProperties.Auth(
      issuerUri = "https://idp.example.test/application/o/mcp/"
    )

    assertThat(auth.jwkSetUri)
      .isEqualTo("https://idp.example.test/application/o/mcp/jwks/")
  }

  @Test
  fun `jwk set uri adds missing issuer trailing slash`() {
    val auth = AppProperties.Auth(
      issuerUri = "https://idp.example.test/application/o/mcp"
    )

    assertThat(auth.jwkSetUri)
      .isEqualTo("https://idp.example.test/application/o/mcp/jwks/")
  }

  @Test
  fun `expected audience defaults to public mcp resource`() {
    val properties = AppProperties(
      publicUrl = "https://proxy.example.test/",
      auth = AppProperties.Auth(audience = ""),
    )

    assertThat(properties.mcpResource).isEqualTo("https://proxy.example.test/mcp")
    assertThat(properties.mcpProtectedResourceMetadataUrl)
      .isEqualTo("https://proxy.example.test/.well-known/oauth-protected-resource/mcp")
    assertThat(properties.expectedAudience).isEqualTo("https://proxy.example.test/mcp")
  }

  @Test
  fun `public url accepts mcp endpoint and normalizes to base url`() {
    val properties = AppProperties(
      publicUrl = "https://proxy.example.test/mcp",
      auth = AppProperties.Auth(audience = ""),
    )

    assertThat(properties.mcpResource).isEqualTo("https://proxy.example.test/mcp")
    assertThat(properties.mcpProtectedResourceMetadataUrl)
      .isEqualTo("https://proxy.example.test/.well-known/oauth-protected-resource/mcp")
    assertThat(properties.expectedAudience).isEqualTo("https://proxy.example.test/mcp")
  }

  @Test
  fun `explicit audience overrides mcp resource`() {
    val properties = AppProperties(
      publicUrl = "https://proxy.example.test",
      auth = AppProperties.Auth(audience = "mcp-client-id"),
    )

    assertThat(properties.expectedAudience).isEqualTo("mcp-client-id")
  }
}
