# Configuration

## Configuration Options

The FDDB Exporter application is a [Spring Boot](https://spring.io/projects/spring-boot) 4 application.
It is pre-configured with a basic configuration embedded in the application. However, some properties need to be
configured to make the application work for your use case and environment. The easiest way to do this is via
environment variables.

For further methods of configuring Spring Boot applications, please refer to
the [official documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config).

## Environment Variables

This page lists all available environment variables and their default values. Usually, you only need to change
the username and password for the FDDB connection and the settings for your preferred database connection.

- For more information about how to configure the Docker image, please refer to
  the [Docker details](/details/docker.md).
- For more information about how to configure the Helm Chart, please refer to the [Helm details](/details/helm.md).

### A note on the hyphens

The `FDDB-EXPORTER_*` spelling used on this page works wherever the variable is set as data — a Docker `-e` flag, a
`docker-compose.yml` `environment:` block, a Kubernetes env var, an IDE run configuration:

```bash
docker run -e 'FDDB-EXPORTER_MCP_WRITE-TOOLS-ENABLED=true' ghcr.io/itobey/fddb-exporter
```

It does **not** work with a POSIX shell's `export`, which rejects hyphens in a variable name. In a shell script use the
all-underscore form instead — Spring Boot's relaxed binding accepts either, and this applies to every
`FDDB-EXPORTER_*` variable on this page:

```bash
export FDDB_EXPORTER_MCP_WRITE_TOOLS_ENABLED=true
```

### FDBB Configuration

The application requires a valid FDDB.info account to work. The following environment variables are used to configure
the FDDB connection.

| Variable                      | Default           | Description                      |
|-------------------------------|-------------------|----------------------------------|
| `FDDB-EXPORTER_FDDB_USERNAME` | -                 | Your FDDB.info username or email |
| `FDDB-EXPORTER_FDDB_PASSWORD` | -                 | Your FDDB.info password          |
| `FDDB-EXPORTER_FDDB_URL`      | https://fddb.info | FDDB website URL                 |

### Export Configuration

For more information about the scheduler and how the export works, see [Export details](/details/exports-and-data.md).

| Variable                           | Default     | Description                                                   |
|------------------------------------|-------------|---------------------------------------------------------------|
| `FDDB-EXPORTER_FDDB_MIN-DAYS-BACK` | 1           | Min limit of days back export for REST API                    |
| `FDDB-EXPORTER_FDDB_MAX-DAYS-BACK` | 365         | Max limit of days back export for REST API                    |
| `FDDB-EXPORTER_SCHEDULER_ENABLED`  | true        | Enable/disable the daily export scheduler                     |
| `FDDB-EXPORTER_SCHEDULER_CRON`     | 0 0 3 * * * | Scheduler cron expression (default: 3 AM daily) (Spring cron) |

The cron expression is a **Spring** expression and has six fields, the first being seconds. A five-field Unix
expression is rejected at startup. `/actuator/scheduledtasks` lists the schedules actually in use.

### Timezone

FDDB Exporter keys every entry on its diary date and stores it as midnight of that date in the configured timezone, so
the timezone is semantically relevant rather than cosmetic — see
[time and date](/details/persistence.md#time-and-date). A container defaults to UTC, so set this to your own timezone.

| Variable | Default | Description                                                                |
|----------|---------|----------------------------------------------------------------------------|
| `TZ`     | UTC     | Timezone of the container, e.g. `Europe/Berlin`. The Helm chart calls this value `timezone` |

Changing `TZ` does not rewrite entries that already exist. If days look shifted by one, see
[Troubleshooting](/details/troubleshooting.md#days-are-shifted-by-one).

### MongoDB Configuration

MongoDB is used by default as persistence for the application. The following environment variables are used to configure
the MongoDB connection. You can disable MongoDB persistence by setting `FDDB-EXPORTER_PERSISTENCE_MONGODB_ENABLED` to
`false`. However, in this case, InfluxDB is necessary as persistence. For more information about persistence, see
[Persistence details](/details/persistence.md).

| Variable                                    | Default               | Description                |
|---------------------------------------------|-----------------------|----------------------------|
| `FDDB-EXPORTER_PERSISTENCE_MONGODB_ENABLED` | true                  | Use MongoDB as persistence |
| `SPRING_MONGODB_HOST`                       | localhost             | MongoDB host               |
| `SPRING_MONGODB_PORT`                       | 27017                 | MongoDB port               |
| `SPRING_MONGODB_DATABASE`                   | fddb                  | MongoDB database name      |
| `SPRING_MONGODB_USERNAME`                   | mongodb_fddb_user     | MongoDB username           |
| `SPRING_MONGODB_PASSWORD`                   | mongodb_fddb_password | MongoDB password           |

::: warning Renamed in 2.2.0
Spring Boot 4 moved these out of Spring Data, so `SPRING_DATA_MONGODB_*` became `SPRING_MONGODB_*`. The old spelling is
silently ignored, which means the application falls back to `localhost` instead of complaining.
:::

### InfluxDB Configuration

InfluxDB is disabled by default. The following environment variables are used to configure the InfluxDB connection. You
can enable InfluxDB persistence by setting `FDDB-EXPORTER_PERSISTENCE_INFLUXDB_ENABLED` to `true`. The token needs to
have permissions to write to the specified bucket. The application will only work with InfluxDB 2.x.
For more information about persistence, see [Persistence details](/details/persistence.md).

| Variable                                     | Default               | Description                          |
|----------------------------------------------|-----------------------|--------------------------------------|
| `FDDB-EXPORTER_PERSISTENCE_INFLUXDB_ENABLED` | false                 | Use InfluxDB as persistence          |
| `FDDB-EXPORTER_INFLUXDB_URL`                 | http://localhost:8086 | URL to InfluxDB                      |
| `FDDB-EXPORTER_INFLUXDB_ORG`                 | primary               | InfluxDB Org                         |
| `FDDB-EXPORTER_INFLUXDB_TOKEN`               | token                 | Token for authentication in InfluxDB |
| `FDDB-EXPORTER_INFLUXDB_BUCKET`              | fddb-exporter         | InfluxDB bucket                      |

At least one of the two persistence layers has to be enabled. With both disabled, the application logs an error and
stops itself at startup rather than running with nowhere to write.

### Notification Configuration

A Telegram message is sent when the scheduled export cannot parse a day. Notifications are enabled by default but
nothing is sent until a token and a chat id are configured. See [Notifications](/details/notifications.md) for what
triggers a message and how to obtain the two values.

| Variable                                    | Default | Description                                                   |
|---------------------------------------------|---------|---------------------------------------------------------------|
| `FDDB-EXPORTER_NOTIFICATION_ENABLED`        | true    | Send a Telegram message when a scheduled export fails to parse |
| `FDDB-EXPORTER_NOTIFICATION_TELEGRAM_TOKEN` | -       | Bot token from BotFather                                       |
| `FDDB-EXPORTER_NOTIFICATION_TELEGRAM_CHATID`| -       | Id of the chat to send messages to                             |

### MCP Server Configuration <Badge type="tip" text="2.3.0+" />

The MCP server lets an AI assistant query your diary in natural language. It is **disabled by default** and requires
MongoDB persistence. For what it exposes and how to connect a client, see [MCP Server](/details/mcp-server.md).

| Variable                                | Default | Description                                                                       |
|-----------------------------------------|---------|-----------------------------------------------------------------------------------|
| `FDDB-EXPORTER_MCP_ENABLED`             | false   | Enable the MCP server endpoint at `/mcp`, with the read-only tools                 |
| `FDDB-EXPORTER_MCP_WRITE-TOOLS-ENABLED` | false   | Additionally expose the export tools, which scrape FDDB and write to the database  |

Both flags are read at startup, so changing either needs a restart. Mind
[the hyphens](#a-note-on-the-hyphens) if you set them from a shell script.

### Web UI Configuration

| Variable                             | Default           | Description                                                              |
|--------------------------------------|-------------------|--------------------------------------------------------------------------|
| `FDDB-EXPORTER_UI_FDDB-LINK-PREFIX`  | https://fddb.info | Base URL the [Web UI](/visualization/web-ui.md) prefixes to product links |

Products are stored with the site-relative link FDDB itself uses, so this is what turns them into a URL you can click.

### Health and Probes

The health endpoint is exposed at `/actuator/health`, with liveness and readiness groups for Kubernetes. The defaults
are usually fine; they are listed here because they matter to anyone writing probe configuration.

| Variable                                        | Default                  | Description                              |
|-------------------------------------------------|--------------------------|------------------------------------------|
| `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE`     | health, scheduledtasks   | Which actuator endpoints are exposed     |
| `MANAGEMENT_ENDPOINT_HEALTH_SHOW-DETAILS`       | always                   | Whether health details are in the response |

Three custom indicators contribute to the health response: `fddb-login-check` (does the fddb.info login still work —
it performs a real request), plus `mongodb` and `influxdb` when the respective store is enabled.

::: tip `fddb-login-check` is in neither probe group <Badge type="tip" text="2.3.1+" />
Both `liveness` and `readiness` exclude it, so a failed fddb.info login does not restart the container and does not
take it out of the Service — wrong credentials are a configuration problem, not a lifecycle event, and the check is a
real request to a third party that should not gate the pod. `/actuator/health` still reports it. Before 2.3.1 the
check was part of the liveness group and wrong credentials caused a restart loop; see
[Troubleshooting](/details/troubleshooting.md#login-to-fddb-not-successful-please-check-credentials).
:::

Note that the health endpoint is **not** authenticated, like everything else the application serves — see
[Securing your instance](/details/security.md).

### Logging Configuration

| Variable             | Default | Description           |
|----------------------|---------|-----------------------|
| `LOGGING_LEVEL_ROOT` | info    | Application log level |

`debug` adds the scheduled run and the health checks, `trace` the individual export steps.

## Encrypting credentials

Environment variables are plain text, and this application needs several credentials — your FDDB password above all.
Rather than passing them in the clear, the application supports [Jasypt](https://github.com/ulisesbocchio/jasypt-spring-boot)
encrypted properties: any property value written as `ENC(...)` is decrypted at startup.

1. Encrypt a value, using the Maven plugin that ships with the project:

   ```bash
   mvn jasypt:encrypt-value -Djasypt.encryptor.password=your-master-password -Djasypt.plugin.value=your-fddb-password
   ```

2. Put the result into your configuration wrapped in `ENC(...)`:

   ```yaml
   fddb-exporter:
     fddb:
       password: ENC(kMS9Zx8u2Fh1...)
   ```

3. Supply the master password at runtime, and only that one:

   ```bash
   docker run -e 'JASYPT_ENCRYPTOR_PASSWORD=your-master-password' ghcr.io/itobey/fddb-exporter
   ```

This moves the problem rather than removing it — the master password still has to reach the application somehow — but it
means the config file itself, and anything that reads it, no longer carries usable credentials. If you deploy with the
[Helm chart](/details/helm.md), its `secretRef` support covers the same ground with Kubernetes secrets and is the more
natural fit there.

For the wider question of who can reach your instance at all, see [Securing your instance](/details/security.md).
