# Privacy and telemetry

FDDB Exporter does not collect personal data. Your diary stays in your own MongoDB / InfluxDB, and your FDDB credentials
are only used to log in to FDDB.info and fetch that data.

To get a rough idea of how the tool is used — and therefore how much it is worth maintaining — the application sends a
small anonymous ping. It is sent once on startup and once a day (`fddb-exporter.telemetry.cron`, 4 AM by default).

## What is sent

| Field                                | Example           | Meaning                                                         |
|--------------------------------------|-------------------|-----------------------------------------------------------------|
| `mailHash`                           | `973dfe463ec857…` | SHA-256 hash of your FDDB username, used only to count installs |
| `documentCount`                      | `1245`            | Number of documents in MongoDB (omitted if MongoDB is disabled) |
| `pointCount`                         | `1245`            | Number of points in InfluxDB (omitted if InfluxDB is disabled)  |
| `mongodbEnabled` / `influxdbEnabled` | `true` / `false`  | Which persistence layers are in use                             |
| `mcpEnabled`                         | `false`           | Whether the [MCP server](/details/mcp-server.md) is enabled     |
| `mcpWriteToolsEnabled`               | `false`           | Whether the MCP **export** (write) tools are enabled            |
| `executionMode`                      | `CONTAINER`       | `JAR`, `CONTAINER` or `KUBERNETES`                              |
| `appVersion`                         | `2.4.0`           | The version you are running                                     |

The mail hash is one-way and cannot be turned back into your address. It exists purely so that repeated pings from the
same installation are not counted as new users.

## Turning it off

There is currently **no `enabled` flag for telemetry** — the ping is registered unconditionally, so it cannot be
switched off in the configuration. I really want to know how many people are using the tool, so I ask you to leave it
on. If this is a dealbreaker for you, you can still stop the ping in two ways:

- **Point it somewhere harmless.** `FDDB-EXPORTER_TELEMETRY_URL` is a normal configuration property. Set it to an
  address that goes nowhere and the send fails, which costs one log line a day and nothing else — the application does
  not care whether the ping succeeded.

  ```bash
  docker run -e 'FDDB-EXPORTER_TELEMETRY_URL=http://localhost:1' ghcr.io/itobey/fddb-exporter
  ```

- **Block it at the network level.** `telemetry.itobey.dev` in your DNS sinkhole, or an egress rule that only lets the
  container reach fddb.info and your databases. Same outcome, enforced outside the application.

## Outbound requests, in full

Three hosts, and nothing else:

| Host                   | Why                                                                      | When                |
|------------------------|--------------------------------------------------------------------------|---------------------|
| `fddb.info`            | Logging in and scraping your diary — the entire point of the application | On every export     |
| `telemetry.itobey.dev` | The anonymous ping described above                                       | Startup, then daily |
| `api.github.com`       | Checking whether a newer release exists, so the UI can tell you          | Startup, then daily |

The version check reads the public *latest release* endpoint of this repository. It sends no data about you — it is an
unauthenticated `GET`, and GitHub sees only that some installation asked. It shares
`FDDB-EXPORTER_TELEMETRY_CRON` with the ping. Blocking it costs you the update notice in the Web UI and nothing else.

## The MCP flags

`mcpEnabled` and `mcpWriteToolsEnabled` are the two on/off switches described under
[MCP Server configuration](/details/configuration.md#mcp-server-configuration) — nothing more. They tell me how many
people actually turn the MCP server on and how many go the extra step of allowing it to write, which is what decides how
much of my time goes into that part of the project.

**No diary content, product names, questions, prompts, tool names or tool calls are sent.** `mcpWriteToolsEnabled` is
reported as `false` whenever the MCP server itself is off, since the flag has no effect in that case.

## Auditing it

The whole thing is about eighty lines and lives in
[
`TelemetryService`](https://github.com/itobey/fddb-exporter/blob/master/src/main/java/dev/itobey/adapter/api/fddb/exporter/service/telemetry/TelemetryService.java)
and
[
`TelemetryDto`](https://github.com/itobey/fddb-exporter/blob/master/src/main/java/dev/itobey/adapter/api/fddb/exporter/dto/telemetry/TelemetryDto.java).
If you still have concerns, open an issue or get in touch.
