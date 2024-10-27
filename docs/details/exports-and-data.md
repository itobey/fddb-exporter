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
endpoints to export data for
specific timeframes or retrieve data from a certain number of days back.

For an overview of how the data is stored in MongoDB, refer to the [persistence](/details/persistence.md) section.