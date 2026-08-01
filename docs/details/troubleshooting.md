# Troubleshooting

Every symptom below has a distinct cause in the application, and most of them are visible in the log or in
`/actuator/health` before you have to guess.

Two things are worth checking first, whatever the symptom:

```bash
curl http://localhost:8080/actuator/health   # FDDB login, MongoDB, InfluxDB
curl http://localhost:8080/api/v2/stats      # is there any data at all, and how recent
```

## Nothing is exported at all

### The application exits immediately after startup

```text
ERROR: Both MongoDB and InfluxDB are disabled. At least one persistence layer must be enabled.
```

The application stops itself (exit code 1) rather than running with nowhere to write. Enable at least one of
`FDDB-EXPORTER_PERSISTENCE_MONGODB_ENABLED` / `FDDB-EXPORTER_PERSISTENCE_INFLUXDB_ENABLED`. See
[Persistence](/details/persistence.md).

### `Login to FDDB not successful, please check credentials`

The scraper reached fddb.info but was not logged in. The scheduled run logs `not logged in - skipping job execution`
and stops; a REST call answers `500 Internal Server Error`.

- Check `FDDB-EXPORTER_FDDB_USERNAME` and `FDDB-EXPORTER_FDDB_PASSWORD`. The username is the one you log in to
  fddb.info with — an email address is fine.
- Verify the credentials by logging in to [fddb.info](https://fddb.info/) in a browser. A password change, a locked
  account or a pending confirmation all present the same way here.
- `/actuator/health` reports this as `fddb-login-check`, with
  `"FDDB Status": "Not functioning properly, Authentication seems invalid"`. The check does a real request to fddb.info
  for yesterday's diary, so it reflects the current state rather than a cached one.
- Watch out for shell quoting if the password contains `$`, `!` or spaces. In a `docker run -e` flag, single-quote the
  whole assignment.

::: tip On Kubernetes, wrong FDDB credentials do not restart the pod <Badge type="tip" text="2.3.1+" />
`fddb-login-check` is deliberately part of **neither** probe group, so a failing login leaves
`/actuator/health/liveness` and `/actuator/health/readiness` reporting `UP`: the application itself is healthy, only
the credentials are wrong, and neither restarting the container nor taking it out of the Service would fix that. The
failure is visible on `/actuator/health` and in the Web UI instead.

Up to and including 2.3.0 the check *was* in the liveness group, which turned wrong credentials into a permanent
`CrashLoopBackOff`. If you are on an older release and see a pod restarting with nothing obviously wrong, check the
credentials before anything else.
:::

### The scheduler never runs

- `FDDB-EXPORTER_SCHEDULER_ENABLED` has to be `true` (the default). When it is `false`, the export task is not
  registered at all — the telemetry and version-check tasks still are.
- `/actuator/scheduledtasks` lists every registered cron task with its expression. If the export task is absent, the
  flag is off; if it is present with an unexpected expression, `FDDB-EXPORTER_SCHEDULER_CRON` is not what you think.
- The cron expression is a **Spring** expression with six fields (seconds first): `0 0 3 * * *` is 3 AM daily. A
  five-field Unix expression fails at startup rather than being silently misread.
- The schedule fires in the container's timezone. See [Days are shifted by one](#days-are-shifted-by-one) below.

## A day comes back as `unsuccessful`

An export response lists `successfulDays` and `unsuccessfulDays`, and the log says:

```text
cannot parse input. it's likely there is no data available for the given day
```

**Nine times out of ten this is a day you did not log anything on**, and the message is accurate rather than alarming.
There is no diary table on the page to parse, so the day is reported as unsuccessful and whatever was stored for it
before is left untouched — nothing is deleted or overwritten with zeros.

It is worth investigating when:

- **Every** day in a range fails, including days you know you logged. That points at a change on fddb.info's side
  (the diary page layout is scraped with XPath selectors) rather than at your data. Check whether a newer version of
  FDDB Exporter exists, and open an issue if it is the latest.
- The day is one you definitely logged and neighbouring days work. Open the diary for that date on fddb.info and check
  it renders normally.

For a scheduled run, this is the one condition that sends a [Telegram notification](/details/notifications.md).

## Exports are empty or a day looks wrong

### The export succeeded but the day has no products

The daily totals come from the diary overview, and so does the product list. A day with totals but no products is
unusual; a day with neither is an unlogged day (see above). Note that **sugar values of individual products are never
stored** — only the daily sugar total — because they are not part of the diary overview. That is expected, not data
loss. See [Persistence](/details/persistence.md#mongodb-collection).

### Meals logged with the app's AI FoodScan or recipes are missing

FDDB Exporter scrapes the diary page on fddb.info — it can only export what that page shows. Meals entered through
the **AI FoodScan** or the **recipe section** of the FDDB mobile app exist in the app only and are not rendered on the
website, so there is nothing for the exporter to parse. FDDB support has confirmed this is a limitation on their side
and named website support as a possible future addition
([#187](https://github.com/itobey/fddb-exporter/issues/187)).

To check a specific day, open its diary on fddb.info in a browser: whatever you see there is exactly what gets
exported. A day logged *entirely* through the app's AI FoodScan therefore has no diary table at all and comes back as
[unsuccessful](#a-day-comes-back-as-unsuccessful).

Until fddb.info shows these entries on the website, the only way to get them exported is to log them as regular
products. If you notice they have started appearing on the website and the exporter still misses them, that is a
different problem — open an issue.

### Days are shifted by one

Everything is keyed on the diary date, and the timestamp stored is midnight of that date in the **container's**
timezone, converted to UTC. With `TZ=Europe/Berlin`, January 15th is stored as `2024-01-14T22:00:00Z`. That is correct
and intentional.

It goes wrong when the container's timezone is not yours. A container defaults to UTC, so if you are in `UTC+2` and
never set `TZ`, the stored timestamps are two hours off — which is enough to put an entry on the previous day in
anything that renders it in local time (Grafana, for instance).

Set `TZ` to your own timezone, on the container or in the Helm chart's `timezone` value:

```bash
docker run -e 'TZ=Europe/Berlin' ghcr.io/itobey/fddb-exporter
```

Existing entries are **not** rewritten when you change `TZ` — they keep the offset they were written with. Re-export
the affected range to normalise them.

The scheduler is affected too: it exports "yesterday" relative to the container's clock, so a wrong timezone can make a
3 AM run pick the wrong day.

## API and UI errors

### `409 Conflict` / "An export is already running"

Exports are serialised across the whole application. A second one is refused rather than queued — see
[only one export at a time](/details/exports-and-data.md#only-one-export-at-a-time). Wait for the running export to
finish and retry. Each day is a separate request to fddb.info taking roughly a second, so a long range takes a while.

If you get this and believe nothing is running, a previous export may still be working through its range — check the
log for progress, or `/api/v2/stats` for the days appearing one by one.

### `400 Bad Request` with "This operation requires MongoDB to be enabled"

The endpoint queries individual entries, which only MongoDB stores. InfluxDB holds daily totals only. Either enable
MongoDB or use an endpoint that works from totals.

### `400 Bad Request` on an export by days

`days` has to be within `FDDB-EXPORTER_FDDB_MIN-DAYS-BACK` and `FDDB-EXPORTER_FDDB_MAX-DAYS-BACK` (1 to 365 by
default). Raise the maximum if you deliberately want a longer backfill.

### Stats show `null` for missing days and streaks

`missingDaysCount`, `currentStreak` and `longestStreak` need per-day entries, so they are `null` when MongoDB is
disabled. Everything else on `/api/v2/stats` works from totals.

### `currentStreak` is 0 although I logged all week

The scheduler exports **yesterday**, so today usually has no entry yet, and a streak that counts today breaks on it.
`currentStreak` only counts today once it has an entry. Nothing is wrong with your data.

## The Web UI

- **The UI loads but every panel errors.** The UI is a server-side Vaadin application that calls the REST API over
  HTTP on `localhost:8080`. If the API answers on a different port or path, the views cannot reach it. Keep the
  application on its own default port and let a reverse proxy do the remapping.
- **It does not work behind a reverse proxy.** Vaadin keeps a websocket open; a proxy that does not forward
  `Upgrade`/`Connection` headers breaks the UI while leaving the REST API fine. There is a working nginx configuration
  under [Securing your instance](/details/security.md#a-reverse-proxy-with-basic-auth).
- **"Install as app" is missing.** A PWA install prompt requires HTTPS (or `localhost`).

## The MCP server

- **The client sees no tools.** Both `FDDB-EXPORTER_MCP_ENABLED` and
  `FDDB-EXPORTER_PERSISTENCE_MONGODB_ENABLED` have to be `true`; with MongoDB off, no tools are registered at all.
- **The export tools are missing.** They need the third flag, `FDDB-EXPORTER_MCP_WRITE-TOOLS-ENABLED`, and a restart.
  `get_server_info` reports `writeToolsEnabled` so you can confirm the flag took effect.
- **A tool says the request failed for a server-side reason.** That is the deliberate replacement for an unexpected
  exception — the real one, with its stack trace, is in the application log. Do not have the assistant retry it.
- **The flags are set in a shell script and nothing happens.** `export` cannot set a name containing hyphens. Use
  `FDDB_EXPORTER_MCP_ENABLED=true` instead; Spring Boot binds either spelling. See the
  [note on the hyphens](/details/configuration.md#a-note-on-the-hyphens).

See the [MCP Server](/details/mcp-server.md) page for what each tool does.

## Grafana shows no data

Covered on the [Grafana Dashboard](/visualization/grafana-dashboard.md#no-data-in-the-panels) page: the bucket name is
written into the Flux queries, and the exporter writes one measurement, `dailyTotals`.

## Outbound requests I did not ask for

Two scheduled tasks talk to the internet besides fddb.info: the anonymous telemetry ping and the version check. Both
are described under [Privacy and telemetry](/details/telemetry.md), and both fail harmlessly if you block them — you
get a log line, and nothing else changes.

## Getting more detail

```bash
docker run -e 'LOGGING_LEVEL_ROOT=debug' ghcr.io/itobey/fddb-exporter
```

`debug` logs the scheduled run and each health check; `trace` adds the export steps. Both are noisy — turn them back to
`info` afterwards.

Still stuck? Open an [issue](https://github.com/itobey/fddb-exporter/issues) with the version you run (the startup log
names it, and so does the Web UI's sidebar), which persistence layers are enabled, and the log lines around the
failure. Do not paste your credentials or a full diary export.
