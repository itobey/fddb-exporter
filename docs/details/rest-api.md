# REST API

## API version changes

This documentation now references the v2 API. The main changes are:

- Base path changed from `/api/v1` to `/api/v2`.
- The migration endpoint moved from `/api/v1/fddbdata/migrateToInfluxDb` to `/api/v2/migration/toInfluxDb` (breaking
  change).
- The stats endpoints were moved to top-level under `/api/v2` (`/api/v2/stats` and `/api/v2/stats/averages`).
- The stats endpoint for last 7 and 30 days averages has been removed in `/api/v1`. Use the new rolling averages
  endpoint in `/api/v2` instead.

**Important dates:**

- **v2 Release:** 2025-12-26
- **v1 Deprecation:** 2025-12-26 (deprecated but still functional)
- **v1 Removal:** 2026-06-30 (v1 will be completely removed)

## Overview

This API allows you to retrieve and export data from the database. The endpoints support operations such as retrieving
all data, filtering by date, searching for specific products, and exporting data for a specified date range.

Example responses:

1. [example response for querying data from the database](../resources/example-response.json)
2. [example response when querying a product name](../resources/example-response-products.json)
3. [example response when retrieving stats](../resources/example-response-stats.json)

## Endpoints

### Retrieve All Data

> **GET** `/api/v2/fddbdata`

- **Description:** Retrieves all data from the database as JSON.
- **Response:** A JSON array containing all entries (see full [example response](../resources/example-response.json) of
  an entry in this array).

    ```
    [
        {
          "id": "66d18658bc73187ea859f67c",
          "date": "2024-08-28",
          "products": [
            {
              "name": "Panini Rolls",
              "amount": "75 g",
              "calories": 176.0,
              "fat": 2.2,
              "carbs": 33.8,
              "protein": 2.8,
              "link": "/db/en/food/schaer_panini_rolls/index.html"
            },
            [...]
          ],
          "totalCalories": 2437.0,
          "totalFat": 93.5,
          "totalCarbs": 285.5,
          "totalSugar": 76.3,
          "totalProtein": 103.9,
          "totalFibre": 10.3
        },
      [...]
    ]
    ```

---


### Retrieve Data by Date

> **GET** `/api/v2/fddbdata/{date}`

- **Description:** Retrieves data for a specific day from the database as JSON.
- **Path Parameter:**
    - `date` _(required)_: The specific date in `YYYY-MM-DD` format.
- **Example:** `/api/v2/fddbdata/2024-08-24`
- **Response:** A JSON object containing the data for the specified date (see
  full [example response](../resources/example-response.json)).

    ```
    {
      "id": "66d18658bc73187ea859f67c",
      "date": "2024-08-28",
      "products": [
        {
          "name": "Panini Rolls",
          "amount": "75 g",
          "calories": 176.0,
          "fat": 2.2,
          "carbs": 33.8,
          "protein": 2.8,
          "link": "/db/en/food/schaer_panini_rolls/index.html"
        },
        [...]
      ],
      "totalCalories": 2437.0,
      "totalFat": 93.5,
      "totalCarbs": 285.5,
      "totalSugar": 76.3,
      "totalProtein": 103.9,
      "totalFibre": 10.3
    }
    ```

---

### Retrieve Data by Date Range

> **GET** `/api/v2/fddbdata/range?fromDate={startDate}&toDate={endDate}&includeProducts={bool}`

- **Description:** Retrieves all entries between two dates (both inclusive), oldest first. Product lists are omitted
  unless explicitly requested, since a long range with products is a very large response.
- **Query Parameters:**
  - `fromDate` _(required)_: The start date in `YYYY-MM-DD` format.
  - `toDate` _(required)_: The end date in `YYYY-MM-DD` format.
  - `includeProducts` _(optional)_: Whether to include each day's product list. Defaults to `false`.
- **Example:** `/api/v2/fddbdata/range?fromDate=2024-12-01&toDate=2024-12-31`
- **Response:** A JSON array of entries, same shape as [Retrieve All Data](#retrieve-all-data).
- **Error Responses:**
  - Returns HTTP 400 Bad Request if `fromDate` is after `toDate`.
  - Returns HTTP 400 Bad Request if the range exceeds 366 days.

---

### Search Products by Name

> **GET** `/api/v2/fddbdata/products?name={product}`

- **Description:** Retrieves all entries matching the given product name as JSON. The search is fuzzy, allowing for
  partial matches. Optionally you can restrict results to specific days of the week, a date range, and/or cap the
  number of results.
- **Query Parameters:**
    - `name` _(required)_: The name of the product to search for.
  - `days` _(optional)_: One or more day names (ISO weekday names) to filter results by day-of-week. Accepts a
    comma-separated list of values. Valid values: `MONDAY`, `TUESDAY`, `WEDNESDAY`, `THURSDAY`, `FRIDAY`, `SATURDAY`,
    `SUNDAY`.
  - `fromDate` _(optional)_: Restrict matches to this date and later, format: `YYYY-MM-DD`.
  - `toDate` _(optional)_: Restrict matches to this date and earlier, format: `YYYY-MM-DD`.
  - `limit` _(optional)_: Maximum number of results to return.

    - If `days` is omitted or empty, the endpoint returns matches for all dates.
    - If one or more days are provided, the endpoint returns only the product occurrences whose date falls on any of the
      specified weekdays.

- **Examples:**
  - All matches for "Strawberry":
    `/api/v2/fddbdata/products?name=Strawberry`

  - Matches for "Banana" that occurred on Mondays only:
    `/api/v2/fddbdata/products?name=Banana&days=MONDAY`

  - Matches for "Banana" that occurred on Mondays and Saturdays:
    `/api/v2/fddbdata/products?name=Banana&days=MONDAY,SATURDAY`

  - Latest 10 matches for "Banana" in 2024:
    `/api/v2/fddbdata/products?name=Banana&fromDate=2024-01-01&toDate=2024-12-31&limit=10`

- **Behavior notes:**
  - The `days` parameter uses the standard Java `DayOfWeek` names (ISO weekdays). Provide the weekday names in uppercase
    to match the enum values; the OpenAPI spec documents the permitted values.
  - The filtering is performed server-side in the v2 API. The response contains a list of objects with the `date` and a
    `product` object. When filtered by days, only those entries whose `date`'s weekday matches any of the provided days
    are returned.

- **Response:** A JSON array containing all matching entries (see
  full [example response](../resources/example-response-products.json)).

    ```
    [
      {
        "date": "2023-01-06",
        "product": {
          "name": "Pizza Brot",
          "amount": "150 g",
          "calories": 391.0,
          "fat": 5.1,
          "carbs": 71.1,
          "protein": 15.0,
          "link": "/db/en/food/marziale_pizza_brot/index.html"
        }
      },
      [...]
    ]
    ```
- **Error Responses:**
  - Returns HTTP 400 Bad Request if `fromDate` is after `toDate`.

---

### List Distinct Product Names

> **GET** `/api/v2/fddbdata/products/distinct?search={term}&limit={amount}`

- **Description:** Lists the distinct product names in the database, so a fuzzy term (e.g. "oats") can be resolved to
  the exact, brand-prefixed name FDDB stores (e.g. "Haferflocken kernig").
- **Query Parameters:**
  - `search` _(optional)_: Case-insensitive substring the name has to contain. If omitted, all distinct names are
    considered.
  - `limit` _(optional)_: Maximum number of names to return (1-1000). Defaults to `100`.
- **Example:** `/api/v2/fddbdata/products/distinct?search=hafer&limit=20`
- **Response:** A JSON array of matching names in alphabetical order.

    ```
    [
      "Haferflocken kernig",
      "Haferflocken zart",
      [...]
    ]
    ```

---

### Get Product Summary

> **GET** `/api/v2/fddbdata/products/summary?name={product}&fromDate={startDate}&toDate={endDate}`

- **Description:** Aggregates every occurrence of the products matching a search term into a single summary: how
  often they were logged, first and last date, the totals they contributed, and the weekday distribution.
- **Query Parameters:**
  - `name` _(required)_: The product name to search for.
  - `fromDate` _(optional)_: Restrict the aggregation to this date and later, format: `YYYY-MM-DD`.
  - `toDate` _(optional)_: Restrict the aggregation to this date and earlier, format: `YYYY-MM-DD`.
- **Example:** `/api/v2/fddbdata/products/summary?name=Haferflocken`
- **Response:** A JSON object containing the aggregated summary.

    ```
    {
      "searchTerm": "haferflocken",
      "matchedProductNames": [
        "Haferflocken kernig",
        "Haferflocken zart"
      ],
      "timesEaten": 42,
      "firstDate": "2024-01-03",
      "lastDate": "2024-12-19",
      "totalCalories": 12600.5,
      "totalFat": 310.2,
      "totalCarbs": 1850.7,
      "totalProtein": 540.3,
      "averageCalories": 300.0,
      "weekdayDistribution": {
        "MONDAY": 8,
        "TUESDAY": 5,
        "WEDNESDAY": 6,
        "THURSDAY": 7,
        "FRIDAY": 6,
        "SATURDAY": 5,
        "SUNDAY": 5
      }
    }
    ```
- **Error Responses:**
  - Returns HTTP 400 Bad Request if `fromDate` is after `toDate`.

---

### Get Top Products

> **GET** `/api/v2/fddbdata/products/top?by={ranking}&fromDate={startDate}&toDate={endDate}&limit={amount}`

- **Description:** Ranks products by how often they were logged (`FREQUENCY`) or by the nutrient totals they
  contributed - "what do I actually eat the most, and where do my calories come from?"
- **Query Parameters:**
  - `by` _(optional)_: Ranking criterion. Valid values: `FREQUENCY`, `CALORIES`, `FAT`, `CARBS`, `PROTEIN`. Defaults to
    `FREQUENCY`.
  - `fromDate` _(optional)_: Restrict the ranking to this date and later, format: `YYYY-MM-DD`.
  - `toDate` _(optional)_: Restrict the ranking to this date and earlier, format: `YYYY-MM-DD`.
  - `limit` _(optional)_: Maximum number of products to return (1-500). Defaults to `20`.
- **Example:** `/api/v2/fddbdata/products/top?by=CALORIES&limit=10`
- **Response:** A JSON array of ranked products, highest first.

    ```
    [
      {
        "name": "Haferflocken kernig",
        "timesEaten": 42,
        "totalCalories": 12600.5,
        "totalFat": 310.2,
        "totalCarbs": 1850.7,
        "totalProtein": 540.3,
        "averageCalories": 300.0
      },
      [...]
    ]
    ```
- **Error Responses:**
  - Returns HTTP 400 Bad Request if `fromDate` is after `toDate`.

---

### Export Data by Date Range

> **POST** `/api/v2/fddbdata`

- **Description:** Exports all entries within a specified date range.
- **Request Body:**
    - `fromDate` _(required)_: The start date in `YYYY-MM-DD` format.
    - `toDate` _(required)_: The end date in `YYYY-MM-DD` format.
- **Example Payload:**

    ```
    {
    "fromDate": "2021-05-13",
    "toDate": "2021-08-18"
    }

- **Response:** A JSON object containing the data:

    ```
    {
      "successfulDays": [
        "2024-08-30",
        "2024-08-31"
      ],
      "unsuccessfulDays": [
        "2024-08-29"
      ]
    }
    ```

---

### Export Data for Last N Days

> **GET** `/api/v2/fddbdata/export?days={amount}&includeToday={bool}`

- **Description:** Exports entries for the last specified number of days.
- **Query Parameters:**
    - `days` _(required)_: The number of days to export.
    - `includeToday` _(optional)_: Whether to include the current day in the export. (`true` or `false`)
- **Example:** `/api/v2/fddbdata/export?days=5&includeToday=true`
- **Response:** A JSON object containing the data:

    ```
    {
      "successfulDays": [
        "2024-08-30",
        "2024-08-31"
      ],
      "unsuccessfulDays": [
        "2024-08-29"
      ]
    }
    ```

---

### Retrieve Stats to Data

> **GET** `/api/v2/stats`

- **Description:** Retrieve the stats to the saved data.
- **Note:** The `last7DaysAverage` and `last30DaysAverage` fields have been removed from this endpoint. Use the new
  rolling averages endpoint for flexible period-based averages.
- **Note:** `missingDaysCount`, `currentStreak` and `longestStreak` are `null` when MongoDB is not configured, since
  they require querying individual entries rather than aggregated totals. `currentStreak` only counts today once it
  has an entry, so a day still in progress does not break the streak.
- **Response:** A JSON object containing the data (see [example response](../resources/example-response-stats.json)).

    ```
    {
      "amountEntries": 606,
      "firstEntryDate": "2023-01-01",
      "lastEntryDate": "2024-12-22",
      "mostRecentMissingDay": "2024-12-20",
      "missingDaysCount": 18,
      "currentStreak": 2,
      "longestStreak": 94,
      "entryPercentage": 95.1,
      "uniqueProducts": 150,
      "averageTotals": {
        "avgTotalCalories": 2505.7,
        "avgTotalFat": 125.7,
        "avgTotalCarbs": 204.4,
        "avgTotalSugar": 63.4,
        "avgTotalProtein": 118.0,
        "avgTotalFibre": 18.4
      },
      "highestCaloriesDay": {
        "date": "2024-07-31",
        "total": 5317.0
      },
      "highestFatDay": {
        "date": "2023-09-23",
        "total": 260.3
      },
      "highestCarbsDay": {
        "date": "2024-07-31",
        "total": 501.7
      },
      "highestProteinDay": {
        "date": "2024-08-03",
        "total": 234.1
      },
      "highestFibreDay": {
        "date": "2023-05-09",
        "total": 53.8
      },
      "highestSugarDay": {
        "date": "2023-10-21",
        "total": 220.4
      }
    }
    ```

---

### Retrieve Rolling Averages

> **GET** `/api/v2/stats/averages?fromDate={startDate}&toDate={endDate}`

- **Description:** Retrieve rolling averages for a specified date range. This endpoint calculates averages for all
  entries between the from and to dates (inclusive).
- **Query Parameters:**
  - `fromDate` _(required)_: The start date in `YYYY-MM-DD` format.
  - `toDate` _(required)_: The end date in `YYYY-MM-DD` format.
- **Example:** `/api/v2/stats/averages?fromDate=2024-01-01&toDate=2024-01-31`
- **Response:** A JSON object containing the rolling averages (
  see [example response](../resources/example-response-rolling-averages.json)).

    ```
    {
      "fromDate": "2024-01-01",
      "toDate": "2024-01-31",
      "averages": {
        "avgTotalCalories": 3054.4,
        "avgTotalFat": 123.9,
        "avgTotalCarbs": 303.7,
        "avgTotalSugar": 85.6,
        "avgTotalProtein": 136.8,
        "avgTotalFibre": 25.8
      }
    }
    ```
- **Error Responses:**
  - Returns HTTP 400 Bad Request if `fromDate` is after `toDate`.
  - Returns HTTP 400 Bad Request if dates are not in the format `YYYY-MM-DD`.

---

### Get a Trend Time Series

> **GET** `/api/v2/stats/trend?metric={metric}&fromDate={startDate}&toDate={endDate}&granularity={granularity}`

- **Description:** Builds a time series of one metric over a date range, bucketed by day, ISO week or month. Buckets
  without a single entry are omitted, so unlogged days never drag an average down.
- **Query Parameters:**
  - `metric` _(optional)_: Metric to trend. Valid values: `CALORIES`, `FAT`, `CARBS`, `SUGAR`, `PROTEIN`, `FIBRE`.
    Defaults to `CALORIES`.
  - `fromDate` _(required)_: The start date in `YYYY-MM-DD` format.
  - `toDate` _(required)_: The end date in `YYYY-MM-DD` format.
  - `granularity` _(optional)_: Bucket size. Valid values: `DAY`, `WEEK` (ISO week, Monday-Sunday), `MONTH`. Defaults
    to `DAY`.
- **Example:** `/api/v2/stats/trend?metric=CALORIES&fromDate=2024-01-01&toDate=2024-03-31&granularity=WEEK`
- **Response:** A JSON array of buckets in chronological order. For `DAY` granularity, `average` and `total` are
  identical and `dayCount` is `1`.

    ```
    [
      {
        "bucket": "2024-W03",
        "fromDate": "2024-01-15",
        "toDate": "2024-01-21",
        "dayCount": 7,
        "average": 2143.7,
        "total": 15005.9
      },
      [...]
    ]
    ```
- **Error Responses:**
  - Returns HTTP 400 Bad Request if `fromDate` is after `toDate`.

---

### Get a Weekday Breakdown

> **GET** `/api/v2/stats/weekdays?fromDate={startDate}&toDate={endDate}`

- **Description:** Averages the daily totals grouped by day of the week - "do my weekends wreck the average?". Days
  of the week without a single entry are omitted.
- **Query Parameters:**
  - `fromDate` _(optional)_: Restrict the aggregation to this date and later, format: `YYYY-MM-DD`.
  - `toDate` _(optional)_: Restrict the aggregation to this date and later, format: `YYYY-MM-DD`.
- **Example:** `/api/v2/stats/weekdays?fromDate=2024-01-01&toDate=2024-12-31`
- **Response:** A JSON array of averages per day of the week, Monday first.

    ```
    [
      {
        "dayOfWeek": "MONDAY",
        "dayCount": 48,
        "averages": {
          "avgTotalCalories": 2400.1,
          "avgTotalFat": 110.3,
          "avgTotalCarbs": 190.2,
          "avgTotalSugar": 55.4,
          "avgTotalProtein": 120.5,
          "avgTotalFibre": 17.8
        }
      },
      [...]
    ]
    ```
- **Error Responses:**
  - Returns HTTP 400 Bad Request if `fromDate` is after `toDate`.

---

### Get the Macro Split

> **GET** `/api/v2/stats/macro-split?fromDate={startDate}&toDate={endDate}`

- **Description:** Share of energy from fat, carbs and protein over a date range. The split is kcal-weighted (fat 9
  kcal/g, carbs and protein 4 kcal/g), not gram-weighted. Because of that, `macroCalories` is derived from the macros
  and will usually differ slightly from `averageCalories`, which is the calorie figure FDDB itself reports.
- **Query Parameters:**
  - `fromDate` _(required)_: The start date in `YYYY-MM-DD` format.
  - `toDate` _(required)_: The end date in `YYYY-MM-DD` format.
- **Example:** `/api/v2/stats/macro-split?fromDate=2024-01-01&toDate=2024-01-31`
- **Response:** A JSON object containing the macro split.

    ```
    {
      "fromDate": "2024-01-01",
      "toDate": "2024-01-31",
      "fatPercentage": 34.5,
      "carbsPercentage": 45.2,
      "proteinPercentage": 20.3,
      "fatCalories": 690.3,
      "carbsCalories": 904.8,
      "proteinCalories": 406.4,
      "macroCalories": 2001.5,
      "averageCalories": 2000.5
    }
    ```
- **Error Responses:**
  - Returns HTTP 400 Bad Request if `fromDate` is after `toDate`.

---

### List Missing Days

> **GET** `/api/v2/stats/missing-days?fromDate={startDate}&toDate={endDate}`

- **Description:** Lists every day in the range that has no entry at all or an entry without a single calorie -
  "when did I forget to log?"
- **Query Parameters:**
  - `fromDate` _(required)_: The start date in `YYYY-MM-DD` format.
  - `toDate` _(required)_: The end date in `YYYY-MM-DD` format.
- **Example:** `/api/v2/stats/missing-days?fromDate=2024-01-01&toDate=2024-01-31`
- **Response:** A JSON array of missing dates in chronological order.

    ```
    [
      "2024-01-05",
      "2024-01-12",
      [...]
    ]
    ```
- **Error Responses:**
  - Returns HTTP 400 Bad Request if `fromDate` is after `toDate`.

---

### Download Data in Various Formats

> **GET** `/api/v2/fddbdata/download`

- **Description:** Download your FDDB data in CSV or JSON format. This endpoint allows you to export your nutritional
  data for further analysis or backup purposes. You can choose to download all data or filter by a specific date range,
  include product details or just daily totals, and customize CSV formatting options.
- **Query Parameters:**
  - `fromDate` _(optional)_: Start date for filtering (inclusive), format: `YYYY-MM-DD`. If not provided, downloads from
    the beginning.
  - `toDate` _(optional)_: End date for filtering (inclusive), format: `YYYY-MM-DD`. If not provided, downloads until
    the most recent entry.
  - `format` _(required)_: Download format. Valid values: `CSV`, `JSON`.
  - `includeProducts` _(optional)_: Whether to include product details (`true`) or just daily totals (`false`). Defaults
    to `false`.
  - `decimalSeparator` _(optional)_: Decimal separator for CSV format. Valid values: `comma`, `dot`. Defaults to
    `comma`. Only applicable when format is `CSV`.

- **Examples:**
  - Download all data as CSV with daily totals only:
    `/api/v2/fddbdata/download?format=CSV&includeProducts=false`

  - Download data for January 2024 as JSON with product details:
    `/api/v2/fddbdata/download?fromDate=2024-01-01&toDate=2024-01-31&format=JSON&includeProducts=true`

  - Download all data as CSV with dot decimal separator and product details:
    `/api/v2/fddbdata/download?format=CSV&includeProducts=true&decimalSeparator=dot`

- **Response:** Binary file download with appropriate content type and filename. The filename is automatically generated
  based on the selected parameters (e.g., `fddb-export-2024-01-01-to-2024-01-31-with-products.csv`).

- **CSV Format:**
  - **Daily totals only** (`includeProducts=false`): Each row represents one day with columns for date, total calories,
    total fat, total carbs, total sugar, total protein, and total fiber.
  - **With product details** (`includeProducts=true`): Each row represents one product consumed on a specific date,
    including all nutritional values and product information.

- **JSON Format:**
  - Returns data in the same structure as the `/api/v2/fddbdata` endpoint, but filtered by the specified date range if
    provided.

- **Error Responses:**
  - Returns HTTP 400 Bad Request if `fromDate` is after `toDate`.
  - Returns HTTP 400 Bad Request if an invalid `format`, `decimalSeparator`, or date format is provided.
  - Returns HTTP 503 Service Unavailable if MongoDB is not enabled.

---

### Migrate MongoDB data to InfluxDb

> **POST** `/api/v2/migration/toInfluxDb`

- **Description:** Migrates existing data from MongoDB to InfluxDb.
- **Response:** HTTP 200 if successful.
