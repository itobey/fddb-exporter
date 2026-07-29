# Privacy and telemetry

FDDB Exporter does not collect personal data. Your diary stays in your own MongoDB / InfluxDB, and your FDDB
credentials are only used to log in to FDDB.info and fetch that data.

To get a rough idea of how the tool is used — and therefore how much it is worth maintaining — the application sends a
small anonymous ping to `https://telemetry.itobey.dev`. It is sent once on startup and once a day
(`fddb-exporter.telemetry.cron`, 4 AM by default).

## What is sent

| Field                                | Example                | Meaning                                                          |
|--------------------------------------|------------------------|------------------------------------------------------------------|
| `mailHash`                           | `973dfe463ec857…`      | SHA-256 hash of your FDDB username, used only to count installs   |
| `documentCount`                      | `1245`                 | Number of documents in MongoDB (omitted if MongoDB is disabled)   |
| `pointCount`                         | `1245`                 | Number of points in InfluxDB (omitted if InfluxDB is disabled)    |
| `mongodbEnabled` / `influxdbEnabled` | `true` / `false`       | Which persistence layers are in use                              |
| `mcpEnabled`                         | `false`                | Whether the [MCP server](/details/mcp-server.md) is enabled       |
| `mcpWriteToolsEnabled`               | `false`                | Whether the MCP **export** (write) tools are enabled              |
| `executionMode`                      | `CONTAINER`            | `JAR`, `CONTAINER` or `KUBERNETES`                                |
| `appVersion`                         | `2.3.0`                | The version you are running                                      |

The mail hash is one-way and cannot be turned back into your address. It exists purely so that repeated pings from the
same installation are not counted as new users.

## The MCP flags

`mcpEnabled` and `mcpWriteToolsEnabled` are the two on/off switches described under
[MCP Server configuration](/details/configuration.md#mcp-server-configuration) — nothing more. They tell me how many
people actually turn the MCP server on and how many go the extra step of allowing it to write, which is what decides
how much of my time goes into that part of the project.

**No diary content, product names, questions, prompts, tool names or tool calls are sent.** `mcpWriteToolsEnabled` is
reported as `false` whenever the MCP server itself is off, since the flag has no effect in that case.

## Auditing it

The whole thing is about eighty lines and lives in
[`TelemetryService`](https://github.com/itobey/fddb-exporter/blob/master/src/main/java/dev/itobey/adapter/api/fddb/exporter/service/telemetry/TelemetryService.java)
and
[`TelemetryDto`](https://github.com/itobey/fddb-exporter/blob/master/src/main/java/dev/itobey/adapter/api/fddb/exporter/dto/telemetry/TelemetryDto.java).
If you still have concerns, open an issue or get in touch.
