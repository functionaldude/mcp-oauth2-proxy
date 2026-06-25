# mcp-oauth2-proxy

Spring Boot/Kotlin proxy that terminates OAuth2 for an HTTP MCP server and forwards authenticated `/mcp` traffic to an
upstream MCP endpoint.

## Runtime configuration

| Variable | Default                                    | Purpose |
| --- |--------------------------------------------| --- |
| `MCP_UPSTREAM_URL` | Required                                   | Exact upstream MCP endpoint, for example `http://internal-server:8081/mcp`. |
| `APP_PUBLIC_URL` | `http://localhost:8080`                    | Externally reachable proxy base URL used as fallback for audience defaults. May be either the site root or the `/mcp` endpoint. |
| `OIDC_ISSUER_URI` | `http://localhost:9000/application/o/mcp/` | Issuer that signs ChatGPT access tokens. The JWKS URL is derived as `<issuer>/jwks/`. |
| `OIDC_AUDIENCE` | `<APP_PUBLIC_URL>/mcp`                     | Required JWT audience. Override when the provider emits another audience. |
| `OIDC_SCOPES` | `openid,profile,email,offline_access`                    | Comma-separated scopes advertised in protected-resource metadata and bearer challenges. |

The proxy strips inbound `Authorization` and cookies before forwarding requests upstream. It does not parse or rewrite
MCP JSON-RPC/SSE bodies.

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

## Container image

Build an OCI image locally with Spring Boot Buildpacks:

```bash
./gradlew bootBuildImage --imageName ghcr.io/<owner>/<repo>:local
```

Run it with the required upstream and OAuth settings:

```bash
docker run --rm -p 8080:8080 \
  -e MCP_UPSTREAM_URL=http://internal-server:8081/mcp \
  -e APP_PUBLIC_URL=https://mcp-proxy.example.com \
  -e OIDC_ISSUER_URI=https://idp.example.com/application/o/mcp/ \
  -e OIDC_AUDIENCE=https://mcp-proxy.example.com/mcp \
  ghcr.io/<owner>/<repo>:local
```

## GitHub Actions publishing

The workflow in `.github/workflows/docker-image.yml` builds the application with `bootBuildImage` and publishes it to
GitHub Container Registry.

- Registry: `ghcr.io`
- Image name: `ghcr.io/${{ github.repository }}`
- Triggers: pushes to `main` or `master`, `v*` tags, and manual `workflow_dispatch`
- Tags: `latest` on the default branch, short commit SHA, and the exact Git tag when the commit is tagged

Consumers can pull and run the published image:

```bash
docker pull ghcr.io/<owner>/<repo>:latest
docker run --rm -p 8080:8080 \
  -e MCP_UPSTREAM_URL=http://internal-server:8081/mcp \
  -e APP_PUBLIC_URL=https://mcp-proxy.example.com \
  -e OIDC_ISSUER_URI=https://idp.example.com/application/o/mcp/ \
  ghcr.io/<owner>/<repo>:latest
```
