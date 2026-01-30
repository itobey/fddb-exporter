<div align="center">
  <img src="docs/images/FDDB-Exporter-Logo.png" width=854/>
  <p><i>Export data from FDDB.info with ease and flexibility</i></p>

## [Documentation](https://itobey.github.io/fddb-exporter/)

[![Artifact Hub](https://img.shields.io/endpoint?url=https://artifacthub.io/badge/repository/fddb-exporter)](https://artifacthub.io/packages/search?repo=fddb-exporter)
[![Build Status](https://img.shields.io/github/actions/workflow/status/itobey/fddb-exporter/ci.yml?style=flat-square)](https://github.com/itobey/fddb-exporter/actions/workflows/ci.yml)
[![Release Version](https://img.shields.io/github/release/itobey/fddb-exporter.svg?style=flat-square&color=9CF)](https://github.com/itobey/fddb-exporter/releases)
[![MIT License](https://img.shields.io/badge/license-MIT-blue.svg?style=flat-square)](https://www.gnu.org/licenses/mit.txt)
[![Commit Activity](https://img.shields.io/github/commit-activity/m/itobey/fddb-exporter.svg?style=flat-square)](https://github.com/itobey/fddb-exporter/commits/master)
[![Last Commit](https://img.shields.io/github/last-commit/itobey/fddb-exporter.svg?style=flat-square&color=FF9900)](https://github.com/itobey/fddb-exporter/commits/master)

</div>

# Overview

FDDB Exporter is a tool designed to extract nutritional data from [FDDB.info](https://fddb.info/) and store it in a
MongoDB/InfluxDB database.
This application is especially useful for individuals who want to keep their FDDB diaries for themselves.
FDDB only stores entries for up to 2 years for premium members, and even less for free users.
Additionally, it is very handy if you want to query your data to see on which days you have entered specific products.
See the [documentation](https://itobey.github.io/fddb-exporter/) for a deep dive.
There is also a [Flutter app](https://github.com/itobey/fddb-exporter-app) available as a frontend.

# Key Features

- **Built-in Web UI** for easy access to all features without API knowledge
- **Mobile-friendly** web interface that can be installed as a Progressive Web App (PWA)
- Exports daily nutritional totals: calories, fat, carbohydrates, sugar, protein, and fiber
- Stores detailed information on consumed products, including name, amount, nutritional values, and link to the product
  page
- Supports scheduled daily exports and manual exports for specific date ranges
- **Product search** with optional filtering by day of the week (e.g., only Mondays and Thursdays)
- Provides a RESTful API for data retrieval and export operations
- Interactive Swagger UI for easy API exploration and testing
- A special API endpoint to find correlations to matching dates for checking food allergies
- **Automatic version checks** with notifications when updates are available

# Prerequisites

- Docker or Java 21+ runtime environment
- A valid FDDB.info account
- A running database (MongoDB and/or InfluxDB)
  - MongoDB instance (used for storing all data)
  - InfluxDB instance (used for storing daily totals)

# Quick Start

Once the application is running, you can access the web interface at:

**Web UI:** `http://localhost:8080/`

The interactive API documentation is available at:

**Swagger UI:** `http://localhost:8080/swagger-ui.html`

See the [documentation](https://itobey.github.io/fddb-exporter/) for detailed setup instructions.

# Web UI

The FDDB Exporter comes with a modern, built-in web interface that provides access to all features. The UI is fully
responsive and works great on mobile devices. You can even install it as a Progressive Web App (PWA) for quick access
from your home screen.

<details>
<summary><b>📸 Click to view screenshots</b></summary>

<br>

|                   Dashboard                   |                     Product Search                      |
|:---------------------------------------------:|:-------------------------------------------------------:|
| ![Dashboard](docs/resources/ui-dashboard.png) | ![Product Search](docs/resources/ui-product-search.png) |

|                   Statistics                    |               Correlation Analysis                |
|:-----------------------------------------------:|:-------------------------------------------------:|
| ![Statistics](docs/resources/ui-statistics.png) | ![Correlation](docs/resources/ui-correlation.png) |

</details>

# Technology Stack

- [Spring Boot](https://spring.io/projects/spring-boot)
- [Vaadin](https://vaadin.com/) / [Hilla](https://hilla.dev/) (Web UI)
- Java 21
- MongoDB
- Docker (optional)

# Privacy

This application does not collect any personal data. All data is stored locally on your device. Your FDDB credentials
are only used to log in to the FDDB website and fetch the data. To determine how this tool is used (and how important it
is to maintain it), the application sends some anonymous data to my server. The mail address is hashed and cannot be
used to identify you. Along with the hash of the mail address, the following data is sent: amount of documents in the
database, what persistence layer is used, the version of the application and the environment (container, Kubernetes or
plain java). Feel free to audit the code
yourself [here](./src/main/java/dev/itobey/adapter/api/fddb/exporter/service/telemetry/TelemetryService.java).
If you still have any concerns, feel free to contact me or open an issue.

# Roadmap

I plan on implementing the following features in the future:

- [x] Helm Chart for deployment
- [x] product search API: to get only relevant data instead of the entire day
- [x] product search API: filter search by weekday to only return matches on specific days
- [x] correlation API: find products correlating with given dates
- [x] new stats endpoint: display some stats of your data
- [x] ARM container release
- [x] ~~Alerting feature to notify when the Scheduler run failed~~
- [x] accompanying Flutter app as a frontend
- [x] InfluxDB as additional persistence layer
- [x] embedded Vaadin Frontend UI
- [x] automatic version check with notifications for new releases

If you have another feature in mind please open up an issue or contact me.

# Resource Usage

The service typically uses around 300 MB of RAM with minimal CPU usage when idle.

# Contributing

Contributions are welcome! Please feel free to submit a Pull Request or open an issue.
