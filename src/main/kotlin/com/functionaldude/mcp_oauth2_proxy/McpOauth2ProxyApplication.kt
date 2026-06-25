package com.functionaldude.mcp_oauth2_proxy

import com.functionaldude.mcp_oauth2_proxy.proxy.McpProxyProperties
import com.functionaldude.mcp_oauth2_proxy.security.AppProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(AppProperties::class, McpProxyProperties::class)
class McpOauth2ProxyApplication

fun main(args: Array<String>) {
  runApplication<McpOauth2ProxyApplication>(*args)
}
