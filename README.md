<div align="center">
   <h1>FDDB Exporter</h1>
   <p>Export data from FDDB.info with ease and flexibility</p>

   <p align="center">
      <a href="https://itobey.github.io/fddb-exporter/" target="_blank"><strong>Documentation</strong></a>
   </p>

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

# Key Features

- Exports daily nutritional totals: calories, fat, carbohydrates, sugar, protein, and fiber
- Stores detailed information on consumed products, including name, amount, nutritional values, and link to the product page
- Supports scheduled daily exports and manual exports for specific date ranges
- Provides a RESTful API for data retrieval and export operations
- A special API endpoint to find correlations to matching dates for checking food allergies

# Prerequisites

- Docker or Java 21+ runtime environment
- A valid FDDB.info account
- MongoDB instance (can be run using the provided Docker Compose file)
- InfluxDB instance (optional, but recommended for storing daily totals)

# Technology Stack

- [Spring Boot](https://spring.io/projects/spring-boot)
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
- [ ] product search API: limit search by date or weekday instead of searching and returning every day
- [x] correlation API: find products correlating with given dates
- [x] new stats endpoint: display some stats of your data
- [x] ARM container release
- [ ] Alerting feature to notify when the Scheduler run failed
- [ ] accompanying Flutter app as a frontend
- [x] InfluxDB as additional persistence layer

If you have another feature in mind please open up an issue or contact me.

# Resource Usage

The service typically uses around 300 MB of RAM with minimal CPU usage when idle.

# Contributing

Contributions are welcome! Please feel free to submit a Pull Request or open an issue.
