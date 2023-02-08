# FDDB Exporter
This is a small tool I created to export data from [FDDB.info](https://fddb.info/).
The following data will be exported on a daily basis:
- kcal
- fat
- carbs
- sugar
- protein
- fiber

## Prerequisites
-   a running postgres database with a table set up
-   an account on fddb.info for which you want to export the data
-   the fddb cookie necessary (see [this README](https://github.com/itobey/fddb-calories-exporter#how-it-works) of an older project)
-   Docker or Java to run the exporter

## Technology
This is based on [Micronaut](https://micronaut.io/) with some [Spring Boot](https://spring.io/projects/spring-boot) flavours, just because I'm new to Micronaut and familiar with Spring Boot. I may base it entirely on Micronaut in the future - but for now, it just works.
As a database I use Postgres to save the gathered data, because I already had a Postgres running anyway. Feel free to change the persistence layer to suit your needs.

## How does it work?
You may start the Micronaut application yourself or just use the [created Docker image](https://github.com/itobey/fddb-exporter/pkgs/container/fddb-exporter%2Ffddb-exporter). Once running a scheduler will log into FDDB every night and gather the data for the day before. This is based on a cron expression (0 3 * * *), which is currently hardcoded and which I may outsource as a property in the future. The data is then saved to the configured database.

### Batch export

#### retrieve timeframe

There's also a HTTP endpoint to get a batch export. This is available on `/batch` of your context root running the application. The payload is a simple JSON with the timerange you want the export for.

```json
# POST http://localhost:8080/batch
{
 "fromDate": "2021-05-13",
 "toDate": "2021-08-18"
}
```

The application will gather data for every day in this range, will save it to the database and return a JSON payload containing the data.

#### retrieve recent days

Sending a GET request to this endpoint with specific query params will retrieve the data for the last x amount of days. The query param `days` determines how many days back from today shall be retrieved and by adding `includeToday=true` the current day will be exported as well.

`GET  http://localhost:8080/batch?days=2&includeToday=true`

### HTML vs. CSV
FDDB offers a CSV-file containing data, which I based a [recent project](https://github.com/itobey/fddb-calories-exporter) on. However the CSV file does not contain the sugar values - only the carbs are available. Because I was highly interested in my sugar consumption, I needed to find another approach. The website of FDDB displays the sugar as well, so the approach in this application is to parse the HTML response for the values of interest. The performance is actually way better than anticipated.

## Screenshots
After gathering all the data in a database, it's easy to display graphs based on it in Grafana.
![image](https://user-images.githubusercontent.com/22119845/131020061-a65e9b6b-6b44-4ba9-8438-10e5ef81e708.png)
![image](https://user-images.githubusercontent.com/22119845/131022068-6479fdb5-1926-4adf-914b-c7bdf6905c15.png)

## Footprint
The Micronaut service is idling at around 120 MB RAM and with almost no CPU usage.
