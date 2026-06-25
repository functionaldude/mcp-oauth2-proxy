# mcp-oauth2-proxy

Spring Boot/Kotlin proxy that adds OAuth2 resource-server protection in front of an HTTP MCP server.

Run it as a sidecar or edge proxy for an existing MCP server: clients connect to this proxy's `/mcp` endpoint, the proxy
validates bearer tokens from your OIDC provider, and authenticated traffic is forwarded to the upstream MCP endpoint.

## Installation

The proxy is usually deployed beside the MCP server it protects. In Docker Compose, set `MCP_UPSTREAM_URL` to the
upstream service name on the Compose network, not to `localhost`.

Minimal example:

```yaml
services:
  mcp-oauth2-proxy:
    image: ghcr.io/functionaldude/mcp-oauth2-proxy:latest
    ports:
      - "8080:8080"
    environment:
      MCP_UPSTREAM_URL: http://mcp-server:8080/mcp
      APP_PUBLIC_URL: http://localhost:8080
      OIDC_ISSUER_URI: https://idp.example.com/application/o/mcp/
      OIDC_SCOPES: openid,profile,email,offline_access

  mcp-server:
    image: your-mcp-server-image
```

After startup, configure your MCP client to use the proxy URL, for example `http://localhost:8080/mcp`. The upstream MCP
server remains private to the Compose network; only the OAuth2-protected proxy is exposed.

## Runtime configuration

| Variable                         | Default                                    | Purpose                                                                                                                         |
|----------------------------------|--------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| `MCP_UPSTREAM_URL`               | Required                                   | Exact upstream MCP endpoint, for example `http://internal-server:8081/mcp`.                                                     |
| `APP_PUBLIC_URL`                 | `http://localhost:8080`                    | Externally reachable proxy base URL used as fallback for audience defaults. May be either the site root or the `/mcp` endpoint. |
| `OIDC_ISSUER_URI`                | `http://localhost:9000/application/o/mcp/` | Issuer that signs ChatGPT access tokens. The JWKS URL is derived as `<issuer>/jwks/`.                                           |
| `OIDC_AUDIENCE`                  | `<APP_PUBLIC_URL>/mcp`                     | Required JWT audience. Override when the provider emits another audience.                                                       |
| `OIDC_SCOPES`                    | `openid,profile,email,offline_access`      | Comma-separated scopes advertised in protected-resource metadata and bearer challenges.                                         |
| `MCP_PROXY_EXPOSE_ERROR_DETAILS` | `false`                                    | Includes exception details in proxy-generated 5xx JSON responses. Use only for local debugging.                                 |

The proxy strips inbound `Authorization` and cookies before forwarding requests upstream. It does not parse or rewrite
MCP JSON-RPC/SSE bodies.

Proxy-generated failures include an `X-Request-Id` response header and a small JSON body. Upstream responses, including
upstream `403` responses, are passed through unchanged except for safe proxy headers such as `X-Request-Id` and
`X-Mcp-Proxy-Upstream-Status`.

## OAuth client setup

Do not set a client ID or client secret in this proxy. This app is an OAuth2 resource server: it validates bearer tokens
on `/mcp`, but it does not perform the authorization-code flow and it must not know the MCP client's secret.

For a confidential client:

1. Create an OAuth2/OIDC application in your identity provider.
2. Set the client/application type to confidential.
3. Enable authorization code with PKCE.
4. Add the ChatGPT connector callback URL as an allowed redirect URI.
5. Copy the identity provider's client ID and client secret into the ChatGPT MCP connector's OAuth settings.
6. Point the proxy at the same issuer with `OIDC_ISSUER_URI`.
7. Set `OIDC_AUDIENCE` to the value your provider places in the JWT `aud` claim.

Common audience values:

- If tokens use this MCP resource as the audience, leave `OIDC_AUDIENCE` empty and it defaults to
  `<APP_PUBLIC_URL>/mcp`.
- If tokens use the OAuth client ID as the audience, set `OIDC_AUDIENCE=<client-id>`.

The proxy owns MCP protected-resource discovery. Bearer challenges point clients to
`<proxy-origin>/.well-known/oauth-protected-resource/mcp`, and that page is served by this proxy, not by the upstream MCP
server. Behind a reverse proxy, forward `X-Forwarded-Proto`, `X-Forwarded-Host`, and `X-Forwarded-Port` so discovery
uses the public origin.

For OAuth debugging, use the [MCPJam OAuth Debugger](https://docs.mcpjam.com/inspector/guided-oauth). It walks through
the MCP OAuth handshake step by step and shows the HTTP requests and responses involved in discovery, registration, and
token exchange.

Confidential client support therefore depends on the identity provider accepting the client secret at its token endpoint,
usually via `client_secret_basic` or `client_secret_post`, and advertising that in its authorization server metadata.
The proxy will accept the resulting JWT as long as issuer, signature, expiry, and audience validation pass.

## Local development

```bash
MCP_UPSTREAM_URL=http://localhost:8081/mcp ./gradlew bootRun
```

Run tests:

```bash
./gradlew test
```

## Complete Docker Compose example

This example protects a [DBHub](https://github.com/bytebase/dbhub) MCP server backed by PostgreSQL:

```yaml
services:
  database:
    image: pgvector/pgvector:pg17
    ports:
      - "4432:5432"
    networks:
      - default
    environment:
      POSTGRES_DB: postgres
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: changeme
    volumes:
      - postgres_store:/var/lib/postgresql/data

  dbhub:
    image: bytebase/dbhub:latest
    ports:
      - "5080:5080"
    networks:
      - default
    environment:
      DBHUB_LOG_LEVEL: error
    command:
      - --transport
      - http
      - --allowed-hosts
      - dbhub
      - --port
      - "5080"
      - --dsn
      - postgres://postgres:changeme@database:5432/postgres
    depends_on:
      - database

  mcp-oauth2-proxy:
    image: ghcr.io/functionaldude/mcp-oauth2-proxy:latest
    ports:
      - "8080:8080"
    networks:
      - default
    environment:
      OIDC_ISSUER_URI: https://idp.example.com/application/o/dbhub-proxy/
      APP_PUBLIC_URL: http://localhost:8080
      MCP_UPSTREAM_URL: http://dbhub:5080/mcp
      OIDC_SCOPES: openid,profile,email,offline_access
      MCP_PROXY_EXPOSE_ERROR_DETAILS: "true"
    depends_on:
      - dbhub

networks:
  default:
    name: dbhub-stack_default

volumes:
  postgres_store:
    driver: local
```
