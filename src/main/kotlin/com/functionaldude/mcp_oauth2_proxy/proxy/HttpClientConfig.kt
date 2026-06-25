package com.functionaldude.mcp_oauth2_proxy.proxy

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.http.HttpClient
import java.time.Duration

@Configuration
class HttpClientConfig {

  @Bean
  fun mcpProxyHttpClient(): HttpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .followRedirects(HttpClient.Redirect.NEVER)
    .build()
}
