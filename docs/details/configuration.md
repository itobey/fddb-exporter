# Configuration

## Configuration Options

The FDDB Exporter application is a [Spring Boot](https://spring.io/projects/spring-boot) 3 application.
It is pre-configured with a basic configuration embedded in the application. However, some properties need to be
configured to make the application work for your use case and environment. The easiest way to do this is via
environment variables.

For further methods of configuring Spring Boot applications, please refer to
the [official documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config).

## Environment Variables

This page lists all available environment variables and their default values. Usually, you only need to change
the username and password for the FDDB connection and the settings for your preferred database connection.

- For more information about how to configure the Docker image, please refer to
  the [Docker details](/details/docker.md).
- For more information about how to configure the Helm Chart, please refer to the [Helm details](/details/helm.md).

### FDBB Configuration

The application requires a valid FDDB.info account to work. The following environment variables are used to configure
the FDDB connection.

| Variable                      | Default           | Description                      |
|-------------------------------|-------------------|----------------------------------|
| `FDDB-EXPORTER_FDDB_USERNAME` | -                 | Your FDDB.info username or email |
| `FDDB-EXPORTER_FDDB_PASSWORD` | -                 | Your FDDB.info password          |
| `FDDB-EXPORTER_FDDB_URL`      | https://fddb.info | FDDB website URL                 |

### Export Configuration

For more information about the scheduler and how the export works, see [Export details](/details/exports-and-data.md).

| Variable                           | Default     | Description                                                   |
|------------------------------------|-------------|---------------------------------------------------------------|
| `FDDB-EXPORTER_FDDB_MIN-DAYS-BACK` | 1           | Min limit of days back export for REST API                    |
| `FDDB-EXPORTER_FDDB_MAX-DAYS-BACK` | 365         | Max limit of days back export for REST API                    |
| `FDDB-EXPORTER_SCHEDULER_ENABLED`  | true        | Enable/disable the daily export scheduler                     |
| `FDDB-EXPORTER_SCHEDULER_CRON`     | 0 0 3 * * * | Scheduler cron expression (default: 3 AM daily) (Spring cron) |

### MongoDB Configuration

MongoDB is used by default as persistence for the application. The following environment variables are used to configure
the MongoDB connection. You can disable MongoDB persistence by setting `FDDB-EXPORTER_PERSISTENCE_MONGODB_ENABLED` to
`false`. However, in this case, InfluxDB is necessary as persistence. For more information about persistence, see
[Persistence details](/details/persistence.md).

| Variable                                    | Default               | Description                |
|---------------------------------------------|-----------------------|----------------------------|
| `FDDB-EXPORTER_PERSISTENCE_MONGODB_ENABLED` | true                  | Use MongoDB as persistence |
| `SPRING_DATA_MONGODB_HOST`                  | localhost             | MongoDB host               |
| `SPRING_DATA_MONGODB_PORT`                  | 27017                 | MongoDB port               |
| `SPRING_DATA_MONGODB_DATABASE`              | fddb                  | MongoDB database name      |
| `SPRING_DATA_MONGODB_USERNAME`              | mongodb_fddb_user     | MongoDB username           |
| `SPRING_DATA_MONGODB_PASSWORD`              | mongodb_fddb_password | MongoDB password           |

### InfluxDB Configuration

InfluxDB is disabled by default. The following environment variables are used to configure the InfluxDB connection. You
can enable InfluxDB persistence by setting `FDDB-EXPORTER_PERSISTENCE_INFLUXDB_ENABLED` to `true`. The token needs to
have permissions to write to the specified bucket. The application will only work with InfluxDB 2.x.
For more information about persistence, see [Persistence details](/details/persistence.md).

| Variable                                     | Default               | Description                          |
|----------------------------------------------|-----------------------|--------------------------------------|
| `FDDB-EXPORTER_PERSISTENCE_INFLUXDB_ENABLED` | false                 | Use InfluxDB as persistence          |
| `FDDB-EXPORTER_INFLUXDB_URL`                 | http://localhost:8086 | URL to InfluxDB                      |
| `FDDB-EXPORTER_INFLUXDB_ORG`                 | primary               | InfluxDB Org                         |
| `FDDB-EXPORTER_INFLUXDB_TOKEN`               | token                 | Token for authentication in InfluxDB |
| `FDDB-EXPORTER_INFLUXDB_BUCKET`              | fddb-exporter         | InfluxDB bucket                      |

### Logging Configuration

| Variable             | Default | Description           |
|----------------------|---------|-----------------------|
| `LOGGING_LEVEL_ROOT` | info    | Application log level |

---

Because environment variables are plain text, it is recommended to use a secure method of storing and passing the
credentials. If you are using the Helm chart, this is already handled for you.