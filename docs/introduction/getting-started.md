<script setup>
import { useData } from 'vitepress'

const { theme } = useData()
</script>

# Getting started

This section shortly describes how to get started with the FDDB Exporter.

## Prerequisites

- Docker or Java 21+ runtime environment
- A valid FDDB.info account
- MongoDB instance (optional, but recommended for all data)
- InfluxDB instance (optional, but recommended for storing daily totals)

## Installation and Deployment

Using a containerized environment is the recommended way to run this application. But you can also run it on your
local machine with a Java 21+ runtime environment.

### Pre-built Docker Image

You can use the pre-built Docker image to quickly set up the application. You have two options here if you do **not**
want to deploy a Helm Chart.
More information about the Docker image configuration can be found on this [detail page](/details/docker.md).

1. Pull the pre-built Docker image:
   ```
   docker run ghcr.io/itobey/fddb-exporter:latest
   ```

2. Use the provided [docker-compose.yaml](https://github.com/itobey/fddb-exporter/blob/master/docker/docker-compose.yml)
   file to start the FDDB Exporter container along with
   a MongoDB and InfluxDB container.
   ```
   docker-compose -f docker/docker-compose.yml up -d
   ```

### Pre-built Helm Chart

[![Artifact Hub](https://img.shields.io/endpoint?url=https://artifacthub.io/badge/repository/fddb-exporter)](https://artifacthub.io/packages/helm/fddb-exporter/fddb-exporter)

You may also use the pre-built Helm Chart to deploy the application. More information about the Helm Chart
configuration can be found on this [detail page](/details/helm.md).

- Use the pre-built Helm Chart:
   ```
   helm install fddb-exporter oci://ghcr.io/itobey/charts/fddb-exporter --version 1.1.5
   ```

- or checkout the [Fddb-Exporter Chart](https://github.com/itobey/charts/tree/master/fddb-exporter) yourself

### Pre-built Jar File

Download the pre-built jar file from the [release page](https://github.com/itobey/fddb-exporter/releases) and run it
with Java 21+ runtime environment.

### Building from Source

You can also build the application from source yourself. For the following steps, you need a Java 21+ runtime
environment and Maven installed.

1. Clone the repository:
   ```
   git clone https://github.com/itobey/fddb-exporter.git
   cd fddb-exporter
   ```

2. Build the application:
   ```
   mvn clean install
   ```

3. (optionally) Build the Docker image:
   ```
   docker build -f docker/Dockerfile -t fddb-exporter .
   ```