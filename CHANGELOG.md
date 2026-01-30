# Changelog

## 2.0.0 (Unreleased)

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
