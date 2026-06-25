plugins {
  kotlin("jvm") version "2.2.21"
  kotlin("plugin.spring") version "2.2.21"
  id("org.springframework.boot") version "4.0.2"
  id("io.spring.dependency-management") version "1.1.7"
}

group = "com.functionaldude"
version = "0.0.1-SNAPSHOT"
description = "OAuth2 terminating proxy for HTTP MCP servers"

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(21)
  }
}

repositories {
  mavenCentral()
}

val mcpSecurityVersion = "0.1.2"

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.boot:spring-boot-starter-restclient")
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
  implementation("org.springframework.boot:spring-boot-starter-webmvc")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation("org.springaicommunity:mcp-server-security:${mcpSecurityVersion}")

  developmentOnly("org.springframework.boot:spring-boot-devtools")

  testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
  testImplementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server-test")
  testImplementation("org.springframework.boot:spring-boot-starter-security-test")
  testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
  compilerOptions {
    freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
  }
}

tasks.withType<Test> {
  useJUnitPlatform()
}
