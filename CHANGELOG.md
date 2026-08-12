# Changelog

## 2.4.0

### ⚠️ Breaking Changes

- **Removed the deprecated REST API v1**, as announced for after 2026-06-30. Switch the base path from `/api/v1` to
  `/api/v2`; two endpoints also moved: `/api/v1/fddbdata/migrateToInfluxDb` → `/api/v2/migration/toInfluxDb` and
  `/api/v1/fddbdata/stats[/averages]` → `/api/v2/stats[/averages]`. Calls to `/api/v1/*` now return the Web UI's HTML
  instead of JSON. The Web UI and MCP server are unaffected.

### Changed

- **Wrong FDDB credentials no longer restart the container on Kubernetes.** `fddb-login-check` is no longer part of
  the liveness probe group, where an invalid password caused an endless restart loop. The login status is still
  reported on `/actuator/health` and in the Web UI.

### Fixed

- **Every export failed with an empty HTTP 500** after fddb.info stopped rendering a block above the diary. Sugar and
  fibre were read by their position in the page, which shifted with that block; they are now looked up by their row
  label instead. A page that cannot be parsed is reported as an unsuccessful day again rather than aborting the whole
  export.
- **Unexpected errors are logged and reported.** Failures no other handler covers used to leave nothing in the log and
  an empty body in the response; they now log a stack trace and answer with the reason, which the Web UI shows instead
  of the bare status code.
- The OpenAPI specs documented `503` for endpoints requiring MongoDB/InfluxDB, which have always answered `400`.
  Documentation only — no behaviour changed.

## 2.3.0

### Added

- **MCP Server** (opt-in): expose your diary to an AI assistant (Claude Desktop, Claude Code or any other MCP client),
  so you can ask things like "how much protein did I average last month?" or "how often do I eat oats, and on which
  weekdays?" in natural language. Read-only by default, with tools covering diary lookups, product search and
  ranking, statistics and trends, and correlating foods with events (e.g. migraines) — plus prompts, resources, and
  optional export tools to trigger a scrape from the assistant itself. Disabled by default and requires MongoDB;
  enable with `FDDB-EXPORTER_MCP_ENABLED=true`. See the [MCP server documentation](https://itobey.github.io/fddb-exporter/details/mcp-server)
  for the full tool list and setup.
- **Trends View**: A new **Trends** view charts a single metric (calories, fat, carbs, sugar, protein or fibre) over a
  date range, bucketed by day, week or month, with quick-select ranges and a summary of highs/lows and change over
  time (`/api/v2/stats/trend`).
- **Products View**: A new **Products** view combines an **Explorer** (per-product history, weekday distribution,
  autocomplete search) with **Top Products** (rank what you eat most by frequency or by calories/fat/carbs/protein).
- **Entries view**: The former **Data Query** view is now **Entries**, and can browse a full date range, not just a
  single day, including a **Missing Days** list for gaps in your logging (`/api/v2/stats/missing-days`).
- **Weekday breakdown**: Rolling Averages now includes a by-day-of-week table (`/api/v2/stats/weekdays`).
- **Logging streaks**: The stats endpoint now reports current/longest logging streaks, most recent entry date, and
  missing days since you started tracking.

### Changed

- **Anonymous usage ping now reports the MCP flags.** The daily telemetry ping additionally sends two booleans:
  whether the MCP server is enabled and whether its export (write) tools are enabled — so I can see how much the
  feature is actually used. No diary content, product names, questions or tool calls are sent, and the write flag is
  reported as `false` while the MCP server itself is off. Everything that is sent is listed in the
  [privacy and telemetry documentation](https://itobey.github.io/fddb-exporter/details/telemetry).
- **Exports no longer run in parallel.** Scraping fddb.info is now serialised across the whole application - the
  scheduler, the REST API, the Web UI and the MCP export tools share one lock, so a single account is never logged in
  and scraped twice at the same time. **If you script against the API:** `POST /api/v2/fddbdata` and
  `GET /api/v2/fddbdata/export` (and their `/api/v1` equivalents) now return **`409 Conflict`** instead of running
  alongside a request that is already exporting. The request is refused, not queued - retry once the running export
  has finished. The nightly scheduled export logs a warning and skips its run on a collision rather than sending a
  notification; the day is picked up by the next run.
- The **Macro distribution** breakdown on the Rolling Averages view is now kcal-weighted (fat 9 kcal/g, carbs and
  protein 4 kcal/g) for a more accurate picture of where your energy comes from.
- Navigation menu updated to reflect the new **Entries**, **Products** and **Trends** views.

## 2.2.0

### Technical Updates

- **Spring Boot 4**: Updated from Spring Boot 3.x to Spring Boot 4.1.0 for enhanced performance and latest framework
  improvements
- **Vaadin 25**: Migrated from Vaadin 24 to Vaadin 25 with the following technical changes:
  - Switched from deprecated `@Theme` variant annotation to CSS-based dark mode using `color-scheme: dark`
  - Added `@StyleSheet(Lumo.UTILITY_STYLESHEET)` annotation to properly load Lumo utility styles in Vaadin 25
  - Removed invalid CSS pseudo-element chaining (`::part()`) that is not supported in CSS specifications
  - Updated frontend dependencies to latest compatible versions (Vaadin Aura 25.2.5, Vaadin Lumo Styles 25.2.5)
- **Frontend Dependencies**: Updated date-fns to 4.4.0, TypeScript to 7.0.2, magic-string to 1.0.0, and Node types to
  26.1.1

### ⚠️ Breaking Changes

- **MongoDB Configuration Properties**: Spring Boot 4 has moved MongoDB configuration from Spring Data to Spring
  directly. If you use external configuration, update the following environment variables:
  - `SPRING_DATA_MONGODB_HOST` → `SPRING_MONGODB_HOST`
  - `SPRING_DATA_MONGODB_PORT` → `SPRING_MONGODB_PORT`
  - `SPRING_DATA_MONGODB_DATABASE` → `SPRING_MONGODB_DATABASE`
  - `SPRING_DATA_MONGODB_USERNAME` → `SPRING_MONGODB_USERNAME`
  - `SPRING_DATA_MONGODB_PASSWORD` → `SPRING_MONGODB_PASSWORD`

### Fixed

- Fixed CSS build failures related to invalid lightningcss minify operations
- Resolved styling inconsistencies after framework migration by properly configuring Vaadin 25 theme system
- Fixed deprecation warnings related to Vaadin theme configuration

## 2.1.0

### Added

- **Custom Rolling Average Presets**: Users can now create and save custom rolling average presets in the Settings to
  display quick-select buttons with custom time ranges on the Rolling Averages view. This allows for quick calculation
  of averages for frequently used date ranges (e.g., quarterly or seasonal analysis).

## 2.0.0

### Added

- **Built-in Web UI**: FDDB-Exporter now includes a built-in frontend (single-page application) served at the
  application root (for example: http://localhost:8080/). The web UI provides a graphical interface for all operations
  that the API exposes.
- **Automatic Version Check**: On startup and once per day, FDDB-Exporter checks whether a new stable version is
  available. The result is printed to the application logs and shown in the frontend UI.
- **Product Search**: Find products with optional day-of-week filtering to display only products consumed on specific
  days (e.g., only products eaten on Mondays).
- **Data Download**: Download exported data in multiple formats (CSV and JSON) directly from the application.

### Deprecated

- **Flutter App**: The Flutter app is now deprecated with the release of the built-in web UI. The Flutter app will
  continue to work with FDDB-Exporter version 1.7.0 but will not be updated for version 2.0.0 and later. Users are
  encouraged to migrate to the built-in web UI.

## 1.7.0

### Changed

- **BREAKING**: Removed `last7DaysAverage` and `last30DaysAverage` fields from `/api/v1/fddbdata/stats` endpoint
- Stats endpoint now returns only total averages and highest values per category
- **BREAKING**: Introduction of the new v2 API with significant changes to endpoints and structure. Please consult the
  documentation for details: [REST-API](https://itobey.github.io/fddb-exporter/details/rest-api)
- The v1 API is now **deprecated** and will be removed on **30.06.2026**. Migrate to v2 as soon as possible.

### Added

- New **v2 API** introduced with improved endpoints and structure
- New `/api/v2/stats/averages` endpoint to calculate rolling averages for an explicit date range (use
  `fromDate` and `toDate`, format YYYY-MM-DD)

### Migration Guide

If you were using the `last7DaysAverage` or `last30DaysAverage` fields:

- Replace with calls to `/api/v1/fddbdata/stats/averages?fromDate=YYYY-MM-DD&toDate=YYYY-MM-DD` (for example, to get the
  last 7 full days set `toDate` to yesterday and `fromDate` to 7 days before yesterday)

## 1.6.3

- dependency updates

## 1.6.2

- fixed the issue originally targeted for 1.6.1 but missed due to incomplete fix

## 1.6.1

- fixed issue with null amount database entries in API

## 1.6.0

- added correlation API

## 1.5.0

- added InfluxDB as additional persistence layer

## 1.4.0

- added telemetry for anonymous usage statistics

## 1.3.0

- added endpoint to retrieve stats for saved data

## 1.2.2

- fixed an issue with the scheduler not running as intended

## 1.2.1

- fixed an issue with updating database entries

## 1.2.0

- updated product query endpoint

## 1.1.0

- added Spring Actuator for healthchecks

## 1.0.0

- Complete redesign of the application
- Switched persistence layer to MongoDB
- Updated API endpoints

## 0.3

- Upgraded to Spring Boot 3 and JDK 21

## 0.2.1

- Fixed login button detection due to FDDB website changes

## 0.2

- Added endpoint to retrieve data for a specific number of past days
