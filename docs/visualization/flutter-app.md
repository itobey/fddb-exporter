# Flutter App

The FDDB Exporter App is a Flutter-based mobile application that serves as a user-friendly frontend for
the [FDDB Exporter](https://github.com/itobey/fddb-exporter) backend system. This companion app provides convenient
access to the backend API endpoints through an intuitive mobile interface.

## Overview

The Flutter app is available on [GitHub](https://github.com/itobey/fddb-exporter-app) and provides a complete mobile
interface for interacting with the FDDB Exporter backend. Without the self-hosted backend, this app will not function
properly as it relies on those API endpoints.

## Features

The app supports the following key features:

- **Export Data**: Fetch nutrition data by days-back or by a specific date range
- **Daily Search**: Retrieve and view daily nutrition information by date
- **Product Search**: Find specific products by name
- **Statistics**: View aggregated nutrition statistics
- **Correlation Analysis**: Explore correlations between nutritional data using filters for keywords, date ranges, and
  occurrences
- **Settings**: Configure the API endpoint connection to your backend

## Screenshots

|                                                        Daily Search                                                        |                                                         Product Search                                                         |                                                          Correlation Analysis                                                          |
|:--------------------------------------------------------------------------------------------------------------------------:|:------------------------------------------------------------------------------------------------------------------------------:|:--------------------------------------------------------------------------------------------------------------------------------------:|
| ![Daily Search](https://raw.githubusercontent.com/itobey/fddb-exporter-app/refs/heads/master/docs/images/daily-search.jpg) | ![Product Search](https://raw.githubusercontent.com/itobey/fddb-exporter-app/refs/heads/master/docs/images/product-search.jpg) | ![Correlation Output](https://raw.githubusercontent.com/itobey/fddb-exporter-app/refs/heads/master/docs/images/correlation-output.jpg) |

## Installation

You can either:

- Build the application yourself following the instructions in the GitHub repository
- Download a prebuilt APK from the [GitHub releases page](https://github.com/itobey/fddb-exporter-app/releases)

## Configuration

The app requires minimal configuration:

- The API endpoint is configurable via the in-app Settings screen
- Default endpoint is set to `http://localhost:8080`
- The configuration is stored using SharedPreferences

## Requirements

- Flutter SDK: ^3.5.1
- Dart SDK: ^3.5.1
- A running instance of the [FDDB Exporter](https://github.com/itobey/fddb-exporter) backend

## Technical Details

The application is built with Flutter, making it compatible with both Android and iOS platforms. It communicates with
the backend REST API to fetch and display data, offering a complete mobile experience for managing your nutrition data
from the FDDB Exporter system.

For more information about the backend system, refer to
the [FDDB Exporter GitHub repository](https://github.com/itobey/fddb-exporter).