# Securing your instance

::: danger FDDB Exporter has no authentication of its own
The Web UI, the REST API, the MCP endpoint and the actuator endpoints are **all unauthenticated**. Anyone who can reach
the port can read your complete nutrition history, trigger exports against your fddb.info account, and — through the
MCP endpoint, if you enable it — have an AI assistant do the same.

There is no username, no password and no API key to configure. Access control has to come from what you put in front of
the application.
:::

This is a deliberate design decision for a single-user, self-hosted tool on a private network, not an oversight. It does
mean the deployment decisions below are yours to make.

## What is exposed

| Path | Serves |
|---|---|
| `/` | The [Web UI](/visualization/web-ui.md) — everything, including triggering exports |
| `/api/v1/**`, `/api/v2/**` | The [REST API](/details/rest-api.md) — read, export, download, migrate |
| `/mcp` | The [MCP server](/details/mcp-server.md), if enabled |
| `/swagger-ui.html`, `/api-docs` | Interactive API documentation, with a working "try it out" |
| `/actuator/health` | Health details, including whether the fddb.info login works |
| `/actuator/scheduledtasks` | The registered cron schedules |

The data itself is personal health data. Treat the port as you would treat the database behind it.

## The short version

**Do not publish the port to the internet.** If you only use FDDB Exporter from your own network or over a VPN
(WireGuard, Tailscale, your router's VPN), you are done — this is the simplest correct answer and needs no reverse
proxy at all.

If you do want it reachable from outside, put a reverse proxy with authentication and TLS in front of it, and do not
expose the application port directly.

## Binding to localhost only

By default a published Docker port listens on every interface. Restrict it to the host itself, so only a reverse proxy
on the same machine can reach it:

```yaml
services:
  fddb-exporter:
    image: ghcr.io/itobey/fddb-exporter
    ports:
      - "127.0.0.1:8080:8080"   # not "8080:8080"
```

On Kubernetes, a `ClusterIP` service (the chart's default) is already not reachable from outside the cluster. Be
deliberate about the Ingress you point at it.

## A reverse proxy with basic auth

Basic auth over TLS is enough for a single-user tool. With nginx:

```nginx
server {
    listen 443 ssl;
    server_name fddb.example.com;

    ssl_certificate     /etc/letsencrypt/live/fddb.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/fddb.example.com/privkey.pem;

    location / {
        auth_basic           "FDDB Exporter";
        auth_basic_user_file /etc/nginx/.htpasswd;

        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # the Web UI is a Vaadin application and keeps a websocket open
        proxy_http_version 1.1;
        proxy_set_header Upgrade    $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

Create the password file with `htpasswd -c /etc/nginx/.htpasswd yourname`.

Caddy does the same in a few lines, with certificates handled for you:

```text
fddb.example.com {
    basic_auth {
        yourname <bcrypt-hash-from-caddy-hash-password>
    }
    reverse_proxy 127.0.0.1:8080
}
```

If you would rather not authenticate the Web UI in the browser, restrict by source address instead — nginx `allow`/
`deny`, or Traefik's `ipAllowList` middleware — and keep the proxy off the public internet entirely.

## Exposing the MCP endpoint

`/mcp` deserves its own paragraph, because it is the one endpoint you may be tempted to expose so that a hosted
assistant can reach it.

- It is **disabled by default** (`FDDB-EXPORTER_MCP_ENABLED`). Leave it off unless you use it.
- The [export (write) tools](/details/mcp-server.md#export-tools-optional) need a second flag of their own
  (`FDDB-EXPORTER_MCP_WRITE-TOOLS-ENABLED`, default `false`). Without it, an assistant cannot make the server log in to
  fddb.info at all.
- If you put authentication in front of `/mcp`, your MCP client has to be able to send it. Clients differ in whether
  they can attach a header to a streamable-HTTP server — check yours before assuming a basic-auth proxy works.
- A local assistant reaching a local server over `127.0.0.1` needs none of this, and is the arrangement the feature was
  built for.

## Protecting the credentials

The application needs your fddb.info password to scrape, and possibly a MongoDB password, an InfluxDB token and a
Telegram bot token. Environment variables are plain text and readable by anything that can inspect the process or the
container definition.

- **Kubernetes:** the [Helm chart](/details/helm.md) supports `secretRef` for each credential, so nothing has to sit in
  your values file. Prefer that over the inline `username`/`password` options.
- **Docker Compose:** keep credentials in a `.env` file that is not committed, or use Docker secrets.
- **Anywhere:** the application supports Jasypt-encrypted properties, so a config file can carry
  `ENC(...)` values instead of cleartext — see
  [encrypting credentials](/details/configuration.md#encrypting-credentials).
- Use an fddb.info password you do not use anywhere else. It is stored in a form the application must be able to read
  back, and it is sent to a third party by design.

## Related

- [MCP Server security](/details/mcp-server.md#security)
- [Privacy and telemetry](/details/telemetry.md) — what the application itself sends outward
- [Configuration](/details/configuration.md)
