package com.functionaldude.mcp_oauth2_proxy.proxy

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ProxyHeadersTest {

  @Test
  fun `request filter strips sensitive and hop by hop headers`() {
    assertThat(ProxyHeaders.isForwardableRequestHeader("Authorization")).isFalse()
    assertThat(ProxyHeaders.isForwardableRequestHeader("Cookie")).isFalse()
    assertThat(ProxyHeaders.isForwardableRequestHeader("Host")).isFalse()
    assertThat(ProxyHeaders.isForwardableRequestHeader("Content-Length")).isFalse()
    assertThat(ProxyHeaders.isForwardableRequestHeader("Connection")).isFalse()
    assertThat(ProxyHeaders.isForwardableRequestHeader("Transfer-Encoding")).isFalse()
  }

  @Test
  fun `request filter preserves mcp and content negotiation headers`() {
    assertThat(ProxyHeaders.isForwardableRequestHeader("Mcp-Session-Id")).isTrue()
    assertThat(ProxyHeaders.isForwardableRequestHeader("Accept")).isTrue()
    assertThat(ProxyHeaders.isForwardableRequestHeader("Content-Type")).isTrue()
  }

  @Test
  fun `response filter preserves safe mcp response headers`() {
    assertThat(ProxyHeaders.isForwardableResponseHeader("Mcp-Session-Id")).isTrue()
    assertThat(ProxyHeaders.isForwardableResponseHeader("Content-Type")).isTrue()
    assertThat(ProxyHeaders.isForwardableResponseHeader("Set-Cookie")).isFalse()
    assertThat(ProxyHeaders.isForwardableResponseHeader("Transfer-Encoding")).isFalse()
  }
}
