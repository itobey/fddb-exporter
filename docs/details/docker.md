# Docker

## Docker Image

The application can be run in a Docker container.
Githubs [GHCR Repository](https://github.com/itobey/fddb-exporter/pkgs/container/fddb-exporter) currently
hosts an amd64 and arm64 architecture official image. There is also a [Helm Chart](/details/helm.md) available for
deployment.
If you want to build your own image, you can use the
[Dockerfile](https://github.com/itobey/fddb-exporter/blob/master/docker/Dockerfile) in this repository.

Running the image is as simple as:

```
docker run ghcr.io/itobey/fddb-exporter:latest
```

The following environment variables are used to configure the Docker image.

## Container Configuration

The configuration of the Docker image can be done via environment variables. See the table on the
[configuration](/details/configuration.md) page for a list of all available options.
FDDB-Exporter uses the timezone of the environment for persisting data. For this reason, it is recommended to
configure the timezone of the container with the `TZ` environment variable.

## Docker Compose

The following `docker-compose.yml` file can be used to start the FDDB Exporter container.

```
docker-compose -f docker/docker-compose.yml up -d
```

This is an excerpt from the
full [docker-compose.yaml](https://github.com/itobey/fddb-exporter/blob/master/docker/docker-compose.yml)
file which starts the FDDB Exporter container along with
a MongoDB and InfluxDB container:

```yaml
version: '3.8'

services:
  fddb-exporter:
    image: ghcr.io/itobey/fddb-exporter
    container_name: fddb-exporter
    ports:
      - "8080:8080"
    environment:
      SPRING_DATA_MONGODB_HOST: mongodb
      SPRING_DATA_MONGODB_PORT: 27017
      SPRING_DATA_MONGODB_DATABASE: fddb
      SPRING_DATA_MONGODB_USERNAME: mongodb_fddb_user
      SPRING_DATA_MONGODB_PASSWORD: mongodb_fddb_password
      FDDB-EXPORTER_FDDB_USERNAME: ---
      FDDB-EXPORTER_FDDB_PASSWORD: ---
      TZ: Europe/Berlin
...
```