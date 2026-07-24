# Changelog

## Unreleased

### Added

- **Trends View**: A new **Trends** view (and `/api/v2/stats/trend` endpoint) charts a single metric — calories, fat,
  carbs, sugar, protein or fibre — over a date range, bucketed by day, ISO week or month. Where Rolling Averages tells
  you your average over a range, Trends shows you whether that average is moving: a column chart with an overall-average
  line, summary cards (highest/lowest bucket, first-to-last change, days logged) and a per-bucket table. Quick-select
  buttons preset a sensible range and granularity (e.g. Last 30 Days daily, Last Year weekly). Days with no entry are
  skipped so they never drag an average down.
- **Products View**: A new **Products** view brings everything product-centric into one place:
  - **Explorer** — search any product to see how often you ate it, when you first and last logged it, the average
    calories per serving and the totals it contributed. Search **autocompletes** with the exact, brand-prefixed names
    FDDB stores, so you no longer have to remember the precise wording. A weekday bar chart shows how your logging of a
    product distributes across the week, and you can click a day to filter the occurrences to just that weekday.
    Clicking any occurrence jumps to that day in the Entries view.
  - **Top Products** — rank the products you log most, either by how often you ate them or by the calories, fat, carbs
    or protein they contributed, over an optional date range. Click a product to drill into its full Explorer profile.
- **Date-range and full-history browsing in Entries**: The former **Data Query** view is now the **Entries** view. In
  addition to looking up a single day, you can now query a whole date range and browse all stored entries.
- **Missing Days**: The Entries view can list every day in a range with no entry (or an entry with no calories), making
  gaps in your logging easy to spot (also available via the `/api/v2/stats/missing-days` endpoint).
- **Weekday breakdown**: The Rolling Averages view now includes a by-day-of-week table alongside the averages, so you
  can see at a glance whether weekends differ from weekdays (`/api/v2/stats/weekdays` endpoint).
- **Logging streaks in the stats API**: The stats endpoint now also reports your **current** and **longest** logging
  streaks, the date of your most recent entry, and the number of missing days since you started tracking.

### Changed

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
