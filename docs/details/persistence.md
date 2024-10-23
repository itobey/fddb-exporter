# Persistence

## Persistence Layers

The FDDB Exporter supports two persistence layers: MongoDB and InfluxDB.
Either one can be enabled or disabled, while MongoDB is the default. You can also choose to use both at the same time.
Only InfluxDB 2.x is supported, as the application uses the new InfluxDB 2.x API.

Because of missing AVX instruction support of an older NUC model, MongoDB has been tested with the ancient version
`4.4.13`.
In principle, the application should work with any newer version of MongoDB. Integration-tests have been performed with
MongoDB `7.0.9`.

You can enable each persistence layer with an environment variable set to `true`.

- `FDDB-EXPORTER_PERSISTENCE_INFLUXDB_ENABLED`
- `FDDB-EXPORTER_PERSISTENCE_MONGODB_ENABLED`

For further configuration, see the [configuration details](/details/configuration.md).

If you enabled InfluxDB after already having exported data, you can use the [REST API](/details/rest-api.md) to migrate
your data from MongoDB to InfluxDB, so you don't have to re-export your data. Depending on the size of your
data, this may take a while.

## Data Structure

Depending on the persistence layer chosen, the exported data is structured differently. MongoDB contains all
data in a collection, while InfluxDB stores only daily totals for graphical representation in Grafana.

### MongoDB Collection

The exported data is structured in a MongoDB collection, with each document representing a single diary entry.
Sugar values of single products are not stored in the collection, because they are not part of the FDDB diary overview.
This data could be retrieved from the product page, which would require a lot of additional requests. For this reason,
this data is currently missing and only daily totals of sugar are stored.

The collection contains the following fields - this is an abbreviated example. For a full example, see the
[example data](../resources/example-document.bson).

```json
{
  "_id": ObjectId(
  "66d18658bc73187ea859f67d"
  ),
  "date": ISODate(
  "2024-08-28T22:00:00.000+0000"
  ),
  "products": [
    {
      "name": "Cevapcici",
      "amount": "400 g",
      "calories": 936.0,
      "fat": 71.2,
      "carbs": 2.0,
      "protein": 70.8,
      "link": "/db/en/food/kaufland_cevapcici/index.html"
    },
    ...
  ],
  "totalCalories": 2633.0,
  "totalFat": 130.8,
  "totalCarbs": 244.2,
  "totalSugar": 33.8,
  "totalProtein": 112.2,
  "totalFibre": 25.6
}
```

### InfluxDB Points

The FDDB Exporter stores daily totals as measurement points in InfluxDB, which is ideal for time-series data
visualization. Each day's nutritional values are stored as separate points with the following metrics:

- calories
- fat
- carbs
- sugar
- fibre
- protein

This structure makes it particularly effective for creating time-based visualizations in tools like Grafana, where you
can track trends and patterns in your nutritional data over time.

### Time and Date

Both MongoDB and InfluxDB store timestamps at the beginning of each day (00:00:00) in UTC, derived from the configured
timezone of the environment. For example, if your timezone is set to Europe/Berlin, a diary entry for January 15th will
be stored with the timestamp "2024-01-14T22:00:00Z" (UTC). This consistent UTC timestamp handling ensures accurate data
representation across both persistence layers while respecting local time zones for display purposes. For information on
how to configure the timezone, see the [configuration details](/details/configuration.md).

### Querying Data

The FDDB Exporter provides a REST API to query data. This is an easy way to retrieve data from the database in JSON
format. For more information, see the [REST API](/details/rest-api.md).