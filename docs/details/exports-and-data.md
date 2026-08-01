# Exports and Data

## Web Scraping Approach

The FDDB Exporter uses web scraping to extract comprehensive nutritional data from fddb.info diary entries. While
fddb.info offers CSV exports, these exports lack important details like product links and complete nutritional
information. By using web scraping the application captures:

- Complete nutritional values (calories, fat, carbs, protein, sugar, fiber)
- Product names and amounts
- Direct links to product pages
- Daily totals and individual entries

The scraping process is handled through authenticated sessions to access the diary pages, ensuring all data is retrieved
accurately and completely. This approach provides richer data for analysis and storage compared to the limited CSV
export option.

## Scheduled Exports

The FDDB Exporter includes a built-in scheduler that automatically exports your nutritional data daily. By default, it
runs at 3 AM and exports the previous day's data. You can customize the schedule using the
`FDDB-EXPORTER_SCHEDULER_CRON` environment variable with any valid cron expression. Be aware that the cron expression
is a Spring Boot cron expression, so make sure to use the correct format.

Configuration options:

- `FDDB-EXPORTER_SCHEDULER_ENABLED`: Enable/disable the scheduler (defaults to true)
- `FDDB-EXPORTER_SCHEDULER_CRON`: Set custom schedule (defaults to `0 0 3 * * *`)

For cases where you need data outside the scheduled exports, the [REST API](/details/rest-api.md) provides flexible
endpoints to export data for specific timeframes or retrieve data from a certain number of days back.

## Only one export at a time

Exporting means logging in to fddb.info with your account and scraping the diary one day at a time. Running two
exports at once would double the outbound load on a third-party site under a single account and interleave writes to
the same days for no gain, so exports are serialised across the whole application - the scheduler, the REST API, the
Web UI and the MCP export tools all share one lock.

A second export requested while one is running is **refused, not queued**:

- The **REST API** answers `409 Conflict` with an `exportError` message. Retry once the running export has finished;
  how long that takes depends on how many days it covers, since each day is a separate request to fddb.info.
- The **Web UI** surfaces the same message, since it calls the REST API.
- The **scheduler** logs a warning and skips that night's run. Yesterday is picked up by the next run, and no
  notification is sent - a skipped run means an export was already happening, which is not a failure.
- The **MCP export tools** report the conflict back to the assistant, which can wait and try again.

This is in-process only: FDDB Exporter is a single-instance application, and running two instances against one
fddb.info account defeats the lock.

## Data Download

The FDDB Exporter provides a comprehensive data download feature that allows you to export your stored nutritional data
in multiple formats for backup, analysis, or integration with other tools.

### Download Options

- **Format Selection**: Choose between CSV and JSON formats
- **Date Range Filtering**: Download all data or specify a custom date range
- **Product Details**: Include detailed product information for each day, or just download daily totals (calories,
  macros, etc.)
- **CSV Customization**: Select comma or dot as the decimal separator for compatibility with different spreadsheet
  applications

### Access Methods

You can download your data through:

1. **Web UI**: Navigate to the "Data Download" page for an intuitive interface with all available options
2. **REST API**: Use the `/api/v2/fddbdata/download` endpoint for programmatic access (
   see [REST API documentation](/details/rest-api.md#download-data-in-various-formats))

For an overview of how the data is stored in MongoDB, refer to the [persistence](/details/persistence.md) section.