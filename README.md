# FDDB Exporter

[![Artifact Hub](https://img.shields.io/endpoint?url=https://artifacthub.io/badge/repository/fddb-exporter)](https://artifacthub.io/packages/search?repo=fddb-exporter)

## Overview

FDDB Exporter is a tool designed to extract nutritional data from [FDDB.info](https://fddb.info/) and store it in a MongoDB database. This application is especially useful for individuals who want to keep their FDDB diaries for themselves. FDDB only stores entries for up to 2 years for premium members, and even less for free users. Additionally, it is very handy if you want to query your data to see on which days you have entered specific products.

## Key Features

- Exports daily nutritional totals: calories, fat, carbohydrates, sugar, protein, and fiber
- Stores detailed information on consumed products, including name, amount, nutritional values, and link to the product page
- Supports scheduled daily exports and manual exports for specific date ranges
- Provides a RESTful API for data retrieval and export operations

An example of a stored document can be seen [here](./doc/example-document.bson).

## Prerequisites

- Docker or Java 21+ runtime environment
- A valid FDDB.info account
- MongoDB instance (can be run using the provided Docker Compose file)

## Technology Stack

- [Spring Boot](https://spring.io/projects/spring-boot)
- Java 21
- MongoDB
- Docker (optional)

## Installation and Setup

### Option 1: Using Pre-built Docker Image

1. Pull the pre-built Docker image:
   ```
   docker pull ghcr.io/itobey/fddb-exporter:latest
   ```

2. Use the provided [docker-compose.yaml](./docker/docker-compose.yml) file to start both the MongoDB and FDDB Exporter containers:
   ```
   docker-compose -f docker/docker-compose.yml up -d
   ```

### Option 2: Using Pre-built Helm Chart

[![Artifact Hub](https://img.shields.io/endpoint?url=https://artifacthub.io/badge/repository/fddb-exporter)](https://artifacthub.io/packages/search?repo=fddb-exporter)

- Use the pre-built Helm Chart:
   ```
   helm pull oci://ghcr.io/itobey/charts/fddb-exporter
   ```

- or checkout the [Fddb-Exporter Chart](https://github.com/itobey/charts/tree/master/fddb-exporter) yourself

### Option 3: Building from Source

1. Clone the repository:
   ```
   git clone https://github.com/itobey/fddb-exporter.git
   cd fddb-exporter
   ```

2. Build the application:
   ```
   mvn clean install
   ```

3. Build the Docker image:
   ```
   docker build -f docker/Dockerfile -t fddb-exporter .
   ```

## Configuration

Configure the application using environment variables:

| Variable                           | Default               | Description                                     | Required |
|------------------------------------|-----------------------|-------------------------------------------------|----------|
| `FDDB-EXPORTER_FDDB_USERNAME`      | -                     | Your FDDB.info username or email                | Yes      |
| `FDDB-EXPORTER_FDDB_PASSWORD`      | -                     | Your FDDB.info password                         | Yes      |
| `FDDB-EXPORTER_FDDB_URL`           | https://fddb.info     | FDDB website URL                                | No       |
| `FDDB-EXPORTER_FDDB_MIN-DAYS-BACK` | 1                     | Min limit of days back export for REST API      | No       |
| `FDDB-EXPORTER_FDDB_MAX-DAYS-BACK` | 365                   | Max limit of days back export for REST API      | No       |
| `FDDB-EXPORTER_SCHEDULER_ENABLED`  | true                  | Enable/disable the daily export scheduler       | No       |
| `FDDB-EXPORTER_SCHEDULER_CRON`     | 0 0 3 * * *           | Scheduler cron expression (default: 3 AM daily) | No       |
| `SPRING_DATA_MONGODB_HOST`         | localhost             | MongoDB host                                    | No       |
| `SPRING_DATA_MONGODB_PORT`         | 27017                 | MongoDB port                                    | No       |
| `SPRING_DATA_MONGODB_DATABASE`     | fddb                  | MongoDB database name                           | No       |
| `SPRING_DATA_MONGODB_USERNAME`     | mongodb_fddb_user     | MongoDB username                                | No       |
| `SPRING_DATA_MONGODB_PASSWORD`     | mongodb_fddb_password | MongoDB password                                | No       |
| `LOGGING_LEVEL_ROOT`               | info                  | Application log level                           | No       |

## Usage

### Automated Daily Export

By default, the application will automatically export data for the previous day at 3 AM. You can adjust this schedule using the `FDDB-EXPORTER_SCHEDULER_CRON` environment variable.

### REST API Documentation

#### Overview

This API allows you to retrieve and export data from the database. The endpoints support operations such as retrieving
all data, filtering by date, searching for specific products, and exporting data for a specified date range.

Example responses:
1. [example response for querying data from the database](./doc/example-response.json)
2. [example response when querying a product name](./doc/example-response-products.json)
3. [example response when running an export](./doc/example-response-exports.json)
4. [example response when retrieving stats](./doc/example-response-stats.json)

---

#### Endpoints

##### Retrieve All Data

> **GET** `/api/v1/fddbdata`

- **Description:** Retrieves all data from the database as JSON.
- **Response:** A JSON array containing all entries (see [example response](./doc/example-response.json)).

---

##### Retrieve Data by Date

> **GET** `/api/v1/fddbdata/{date}`

- **Description:** Retrieves data for a specific day from the database as JSON.
- **Path Parameter:**
  - `date` _(required)_: The specific date in `YYYY-MM-DD` format.
- **Example:** `/api/v1/fddbdata/2024-08-24`
- **Response:** A JSON object containing the data for the specified date (
  see [example response](./doc/example-response.json)).

---

##### Search Products by Name

> **GET** `/api/v1/fddbdata/products?name={product}`

- **Description:** Retrieves all entries matching the given product name as JSON. The search is fuzzy, allowing for
  partial matches.
- **Query Parameter:**
  - `name` _(required)_: The name of the product to search for.
- **Example:** `/api/v1/fddbdata/products?name=mountain` _(This will find entries like `Mountain Dew`.)_
- **Response:** A JSON array containing all matching entries (
  see [example response](./doc/example-response-products.json)).

---

##### Export Data by Date Range

> **POST** `/api/v1/fddbdata`

- **Description:** Exports all entries within a specified date range.
- **Request Body:**
  - `fromDate` _(required)_: The start date in `YYYY-MM-DD` format.
  - `toDate` _(required)_: The end date in `YYYY-MM-DD` format.
- **Example Payload:**
  ```json
  {
    "fromDate": "2021-05-13",
    "toDate": "2021-08-18"
  }
- **Response:** A JSON object containing the data (see [example response](./doc/example-response-exports.json)).

---

##### Export Data for Last N Days

> **GET** `/api/v1/fddbdata/export?days={amount}&includeToday={bool}`

- **Description:** Exports entries for the last specified number of days.
- **Query Parameters:**
  - `days` _(required)_: The number of days to export.
  - `includeToday` _(optional)_: Whether to include the current day in the export. (`true` or `false`)
- **Example:** `/api/v1/fddbdata/export?days=5&includeToday=true`
- **Response:** A JSON object containing the data (see [example response](./doc/example-response-exports.json)).

---

##### Retrieve Stats to Data

> **GET** `/api/v1/fddbdata/stats`

- **Description:** Retrieve the stats to the saved data.
- **Response:** A JSON object containing the data (see [example response](./doc/example-response-stats.json)).

## Visualization
After gathering all the data in a database, it's easy to display graphs based on it in Grafana. These screenshots
are from version 0.3 as this version did use Postgresql and I didn't have time yet to figure out how to use MongoDB
as a datasource for Grafana.
![image](https://user-images.githubusercontent.com/22119845/131020061-a65e9b6b-6b44-4ba9-8438-10e5ef81e708.png)
![image](https://user-images.githubusercontent.com/22119845/131022068-6479fdb5-1926-4adf-914b-c7bdf6905c15.png)

## Roadmap
I plan on implementing the following features in the future:
- [x] Helm Chart for deployment
- [x] product search API: to get only relevant data instead of the entire day
- [ ] product search API: limit search by date or weekday instead of searching and returning every day
- [x] new stats endpoint: display some stats of your data
- [x] ARM container release (~~currently only x86 available~~) (available with 1.2.2+)
- [ ] Alerting feature to notify when the Scheduler run failed
- [ ] accompanying Flutter app as a frontend

If you have another feature in mind please open up an issue or contact me.

## Changelog

### 1.3.0

- added endpoint to retrieve stats for saved data

### 1.2.2

- fixed an issue with the scheduler not running as intended

### 1.2.1

- fixed an issue with updating database entries

### 1.2.0

- updated product query endpoint

### 1.1.0
- added Spring Actuator for healthchecks

### 1.0.0
- Complete redesign of the application
- Switched persistence layer to MongoDB
- Updated API endpoints

### 0.3
- Upgraded to Spring Boot 3 and JDK 21

### 0.2.1
- Fixed login button detection due to FDDB website changes

### 0.2
- Added endpoint to retrieve data for a specific number of past days

## Resource Usage

The service typically uses around 285 MB of RAM with minimal CPU usage when idle.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.
